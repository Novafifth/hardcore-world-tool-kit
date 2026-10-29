package com.hardcoregetracker.ui;

import static com.hardcoregetracker.ui.PanelComponents.column;
import static com.hardcoregetracker.ui.PanelComponents.linkLabel;
import static com.hardcoregetracker.ui.PanelComponents.pinTop;
import static com.hardcoregetracker.ui.PanelComponents.smallLabel;
import static com.hardcoregetracker.ui.PanelComponents.tradeRow;
import static com.hardcoregetracker.ui.PanelComponents.wrapText;
import com.hardcoregetracker.ActiveOffer;
import com.hardcoregetracker.HardcoreGeTrackerConfig;
import com.hardcoregetracker.MarketClient;
import com.hardcoregetracker.MarketData;
import com.hardcoregetracker.OfferReport;
import com.hardcoregetracker.Onboarding;
import com.hardcoregetracker.ProfitSnapshot;
import com.hardcoregetracker.TradeEvent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.LinkBrowser;
import net.runelite.http.api.item.ItemPrice;
import okhttp3.HttpUrl;

/**
 * Side panel: the header strip, item search with a settings (gear) button, connection status, then the market feed /
 * your trades, or a per-item view with price chart. Settings live on RuneLite's configuration page.
 * This class owns the header, the cards, the refresh timer and the 1 s ticker; the views render each card.
 */
public class HardcoreGeTrackerPanel extends PluginPanel
{
	private static final String CARD_HOME = "home";
	private static final String CARD_ITEM = "item";
	private static final String CARD_SEARCH = "search";
	private static final int MAX_SEARCH_RESULTS = 30;
	private static final int REFRESH_MS = 15_000;
	/**
	 * Every this many incremental loads (about 5 minutes), load the newest page again. That page is the same on every
	 * server, so it recovers from event ids that start over (another server behind the same URL, a database reset).
	 */
	static final int FULL_FEED_EVERY = 20;
	private static final int TICK_MS = 1_000;
	/** How long (visible seconds) the "Imported N trades" line stays. */
	private static final int IMPORT_NOTICE_SECONDS = 30;
	private static final int HEADER_HEIGHT = 30;
	static final String GEAR_TOOLTIP = "Settings: RuneLite Configuration > Hardcore GETracker";
	static final String DELETE_ITEM = "Delete my shared data...";
	static final String DELETE_TITLE = "Delete my shared data";
	static final String DELETE_CONFIRM = "Delete everything this computer has shared with the Hardcore GETracker server"
		+ " (your trades, offers and ids)? Market totals are recalculated without them. Your local trade log and profit"
		+ " stay on this PC. This disconnects the plugin.";
	static final String NOTHING_SHARED = "Nothing has been shared from this computer.";

	private final HardcoreGeTrackerConfig config;
	private final ItemManager itemManager;
	private final MarketClient market;
	/** Your recent trades, newest first. */
	private final Supplier<List<TradeEvent>> myTrades;

	/**
	 * Sized by the shown card only (CardLayout uses the tallest card), so a short item view doesn't scroll on into
	 * blank space as tall as the home view.
	 */
	private final CardLayout cards = new CardLayout()
	{
		@Override
		public Dimension preferredLayoutSize(Container parent)
		{
			synchronized (parent.getTreeLock())
			{
				for (Component c : parent.getComponents())
				{
					if (c.isVisible())
					{
						Insets in = parent.getInsets();
						Dimension d = c.getPreferredSize();
						return new Dimension(d.width + in.left + in.right + getHgap() * 2,
							d.height + in.top + in.bottom + getVgap() * 2);
					}
				}
			}
			return super.preferredLayoutSize(parent);
		}
	};
	private final JPanel cardPanel = new JPanel(cards);
	private final IconTextField searchField = new IconTextField();
	private final JPanel searchResults = column();
	private final JLabel statusLabel = smallLabel("", ColorScheme.LIGHT_GRAY_COLOR);
	private final WelcomeCard welcomeCard;
	private final OffersSection offersSection;
	private final ProfitSection profitSection;
	private final HomeView homeView;
	private final ItemView itemView;
	private final Timer refreshTimer;
	private final Timer searchDebounce;
	/** Drives the per-second labels, only while the panel is shown. */
	private final Timer ticker;
	private final String privacyUrl;
	private final Runnable checkOffers;
	private final SharedDataActions sharedData;
	/** The status line's "Privacy" link; its menu opens below it. */
	private JLabel privacyLink;
	/** Developer mode: show the panel as a brand-new install sees it. UI only; nothing real changes. */
	private boolean previewFresh;
	private final JLabel previewStrip = smallLabel("Fresh-install preview (dev) - click to end", ColorScheme.BRAND_ORANGE);

	/** Your open offers, newest snapshot from the plugin. */
	private List<ActiveOffer> offers = Collections.emptyList();
	/** How they compare with the market, newest from the plugin. */
	private OfferReport offerReport = OfferReport.EMPTY;
	/** Profit of the logged-in (or last) account; empty until it has loaded. */
	private ProfitSnapshot profit = ProfitSnapshot.EMPTY;

	// The Market feed, merged across incremental loads (FeedMerge). Reset when the connection changes.
	private List<MarketData.FeedEvent> feedEvents = Collections.emptyList();
	private MarketData.Stats feedStats;
	private long lastFeedId;
	/** Incremental loads since the last full one; see {@link #FULL_FEED_EVERY}. */
	private int feedPollsSinceFull;
	/** The next feed load fetches the newest page (after=0) instead of only newer events. */
	private boolean feedFullNext = true;
	/** Bumped on reset, so a response to a request made before it is dropped. */
	private int feedGeneration;
	/** The connected setting the feed was loaded with. */
	private boolean feedConnected;

	/** Whether the logged-in account's GE History was imported; null when not logged in on a Hardcore World. */
	private Boolean historyImported;
	/** Trades added by the first GE History import, while that notice is showing. */
	private Integer importNotice;
	private int importNoticeSecondsLeft;

	private String currentCard = CARD_HOME;
	/** Where the home card was scrolled to when an item was opened from it; restored on Back. */
	private Point homeScroll;
	/** True while the panel is shown. Nothing is fetched from the server while it's hidden. Read from any thread. */
	private volatile boolean active;

	/**
	 * @param base       the server, for the privacy notice link
	 * @param openConfig   opens this plugin's page in RuneLite's configuration panel; run on the EDT
	 * @param checkOffers  compares your open offers with the market now (rate limited); run on the EDT, must not block
	 * @param rebuildProfit recounts your profit from the local trade log in the background; run on the EDT, must not block
	 * @param sharedData  "Delete my shared data" in the Privacy menu
	 */
	public HardcoreGeTrackerPanel(HardcoreGeTrackerConfig config, ConfigManager configManager, ItemManager itemManager,
		MarketClient market, Supplier<List<TradeEvent>> myTrades, HttpUrl base, Runnable openConfig,
		Runnable checkOffers, Runnable rebuildProfit, SharedDataActions sharedData)
	{
		this.config = config;
		this.itemManager = itemManager;
		this.market = market;
		this.myTrades = myTrades;
		this.privacyUrl = base.newBuilder().addPathSegment("privacy").addPathSegment("hardcore").build().toString();
		this.checkOffers = checkOffers;
		this.sharedData = sharedData;

		welcomeCard = new WelcomeCard(
			() ->
			{
				if (previewFresh)
				{
					// In the fresh-install preview, Connect only ends the preview.
					setFreshPreview(false);
					return;
				}
				configManager.setConfiguration(HardcoreGeTrackerConfig.GROUP, HardcoreGeTrackerConfig.KEY_CONNECTED, true);
			},
			this::openPrivacy, this::dismissImportNotice);
		offersSection = new OffersSection(checkOffers, this::openItem);
		profitSection = new ProfitSection(rebuildProfit, this::openItem);
		homeView = new HomeView(itemManager, welcomeCard.getComponent(), offersSection.getComponent(),
			profitSection.getComponent(), this::refresh,
			() -> refresh(true), this::openItem);
		itemView = new ItemView(config, itemManager, market, this::shownMyTrades, this::shownOffers, this::shownReport,
			this::shownProfit, () ->
		{
			showCard(CARD_HOME);
			refresh();
		});
		feedConnected = config.connected();

		setLayout(new BorderLayout(0, 8));
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		searchField.setIcon(IconTextField.Icon.SEARCH);
		searchField.setPreferredSize(new Dimension(PANEL_WIDTH - 20 - HEADER_HEIGHT - 4, HEADER_HEIGHT));
		searchField.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchField.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchDebounce = new Timer(250, e -> runSearch());
		searchDebounce.setRepeats(false);
		searchField.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyReleased(KeyEvent e)
			{
				if (e.getKeyCode() == KeyEvent.VK_ENTER)
				{
					openFirstResult();
				}
				else
				{
					searchDebounce.restart();
				}
			}
		});
		searchField.addClearListener(() ->
		{
			if (CARD_SEARCH.equals(currentCard))
			{
				leaveSearch();
			}
		});
		add(buildNorth(openConfig), BorderLayout.NORTH);

		cardPanel.setOpaque(false);
		cardPanel.add(pinTop(homeView.getComponent()), CARD_HOME);
		cardPanel.add(pinTop(itemView.getComponent()), CARD_ITEM);
		cardPanel.add(pinTop(searchResults), CARD_SEARCH);
		add(cardPanel, BorderLayout.CENTER);

		refreshTimer = new Timer(REFRESH_MS, e -> refresh());
		ticker = new Timer(TICK_MS, e -> tick());
		onConfigChanged();
	}

	/** The header strip, then search + gear, then the connection status line. */
	private JPanel buildNorth(Runnable openConfig)
	{
		JPanel north = column();
		north.add(new HeaderBanner().getComponent());
		north.add(Box.createVerticalStrut(6));

		JPanel searchRow = new JPanel(new BorderLayout(4, 0));
		searchRow.setOpaque(false);
		searchRow.add(searchField, BorderLayout.CENTER);
		JButton gear = new JButton(Icons.cog());
		gear.setPreferredSize(new Dimension(HEADER_HEIGHT, HEADER_HEIGHT));
		gear.setFocusPainted(false);
		gear.setToolTipText(GEAR_TOOLTIP);
		gear.addActionListener(e -> openConfig.run());
		searchRow.add(gear, BorderLayout.EAST);
		searchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, HEADER_HEIGHT));
		north.add(searchRow);
		north.add(Box.createVerticalStrut(4));

		JPanel status = new JPanel(new BorderLayout(4, 0));
		status.setOpaque(false);
		statusLabel.setIconTextGap(4);
		status.add(statusLabel, BorderLayout.WEST);
		privacyLink = linkLabel("Privacy", this::showPrivacyMenu);
		privacyLink.setToolTipText("What the server receives and keeps, and deleting your shared data");
		status.add(privacyLink, BorderLayout.EAST);
		status.setAlignmentX(Component.LEFT_ALIGNMENT);
		status.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		north.add(status);

		previewStrip.setAlignmentX(Component.LEFT_ALIGNMENT);
		previewStrip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		previewStrip.setToolTipText("Developer mode: the panel as a brand-new install sees it. Nothing real was changed.");
		previewStrip.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				setFreshPreview(false);
			}
		});
		previewStrip.setVisible(false);
		north.add(previewStrip);
		return north;
	}

	/**
	 * Developer mode: show the panel as a brand-new install sees it ("Not connected", the full welcome card, no trades of
	 * yours), or go back. UI only: the connection, settings and data are untouched.
	 */
	public void setFreshPreview(boolean on)
	{
		if (previewFresh == on)
		{
			return;
		}
		previewFresh = on;
		previewStrip.setVisible(on);
		if (on)
		{
			showCard(CARD_HOME);
		}
		offersSection.setOffers(shownOffers());
		offersSection.setReport(shownReport());
		profitSection.setSnapshot(shownProfit());
		onConfigChanged();
	}

	/** Connected, unless the fresh-install preview pretends otherwise. */
	private boolean effectiveConnected()
	{
		return config.connected() && !previewFresh;
	}

	/** Your trades, or none during the fresh-install preview. */
	private List<TradeEvent> shownMyTrades()
	{
		return previewFresh ? Collections.emptyList() : myTrades.get();
	}

	/** Your open offers, or none during the fresh-install preview. */
	private List<ActiveOffer> shownOffers()
	{
		return previewFresh ? Collections.emptyList() : offers;
	}

	private OfferReport shownReport()
	{
		return previewFresh ? OfferReport.EMPTY : offerReport;
	}

	private ProfitSnapshot shownProfit()
	{
		return previewFresh ? ProfitSnapshot.EMPTY : profit;
	}

	private void openPrivacy()
	{
		LinkBrowser.browse(privacyUrl);
	}

	/** The status line's Privacy link: the privacy notice, and deleting what this computer shared. */
	private void showPrivacyMenu()
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem notice = new JMenuItem("Privacy notice");
		notice.addActionListener(e -> openPrivacy());
		menu.add(notice);
		JMenuItem delete = new JMenuItem(DELETE_ITEM);
		boolean shared = sharedData.mayHaveShared();
		boolean deleting = sharedData.isDeleting();
		delete.setEnabled(shared && !deleting);
		delete.setToolTipText(deleting ? "A deletion is running." : shared
			? "Delete everything this computer has shared with the server, then disconnect" : NOTHING_SHARED);
		delete.addActionListener(e -> confirmDelete());
		menu.add(delete);
		menu.show(privacyLink, 0, privacyLink.getHeight());
	}

	private void confirmDelete()
	{
		// While disconnected this is the one request the plugin sends without the opt-in, so it says so first.
		String text = DELETE_CONFIRM + (config.connected() ? ""
			: "<br><br>" + HardcoreGeTrackerConfig.THIRD_PARTY_WARNING + ". You are not connected: deleting sends this one request.");
		int answer = JOptionPane.showConfirmDialog(this, "<html><body style='width: 240px'>" + text + "</body></html>",
			DELETE_TITLE, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (answer != JOptionPane.YES_OPTION)
		{
			return;
		}
		boolean started = sharedData.delete(message ->
		{
			// Back to the normal status text (disconnected after a deletion, unchanged after a failure).
			onConfigChanged();
			JOptionPane.showMessageDialog(this, "<html><body style='width: 240px'>" + message + "</body></html>",
				DELETE_TITLE, JOptionPane.INFORMATION_MESSAGE);
		});
		if (started)
		{
			statusLabel.setText("Deleting shared data…");
		}
	}

	/** Whether the panel is shown; any thread. */
	public boolean isPanelActive()
	{
		return active;
	}

	@Override
	public void onActivate()
	{
		active = true;
		// The feed may be far behind after being hidden: start from the newest page, merged with what we have.
		feedFullNext = true;
		refresh();
		// Your offers are only checked automatically while something uses them; bring them up to date now.
		checkOffers.run();
		refreshTimer.start();
		ticker.start();
	}

	@Override
	public void onDeactivate()
	{
		active = false;
		refreshTimer.stop();
		ticker.stop();
	}

	public void shutDown()
	{
		active = false;
		refreshTimer.stop();
		ticker.stop();
		searchDebounce.stop();
		homeView.stop();
		offersSection.stop();
		itemView.stop();
	}

	/** Once a second while the panel is shown. */
	private void tick()
	{
		if (importNotice != null && --importNoticeSecondsLeft <= 0)
		{
			dismissImportNotice();
		}
		if (CARD_HOME.equals(currentCard))
		{
			homeView.tick();
			offersSection.tick();
			profitSection.tick();
		}
		else if (CARD_ITEM.equals(currentCard))
		{
			itemView.tick();
		}
	}

	/**
	 * Called when a setting changes: updates the status line and welcome card, then refreshes what's on
	 * screen, which may request market data (this is what loads the feed right after Connect). Nothing is fetched
	 * while the panel is hidden.
	 */
	public void onConfigChanged()
	{
		boolean connected = effectiveConnected();
		statusLabel.setIcon(Icons.statusDot(connected));
		statusLabel.setText(!connected ? "Not connected"
			: config.shareTrades() ? "Connected · sharing on" : "Connected · sharing off");
		statusLabel.setForeground(connected ? ColorScheme.TEXT_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
		statusLabel.setToolTipText(connected ? "Connected to the Hardcore GETracker server" : GEAR_TOOLTIP);
		offersSection.setConnected(connected);
		profitSection.setLogEnabled(config.saveLocalLog());
		profitSection.setShown(config.showProfit());
		if (connected != feedConnected)
		{
			feedConnected = connected;
			resetFeed();
		}
		updateWelcome();
		refresh();
	}

	/** Forget the merged Market feed; the next load fetches the newest page again. */
	private void resetFeed()
	{
		feedEvents = Collections.emptyList();
		feedStats = null;
		lastFeedId = 0;
		feedFullNext = true;
		feedPollsSinceFull = 0;
		feedGeneration++;
		homeView.resetMarket();
	}

	/** The logged-in account's GE History import state; null when logged out or not on a Hardcore World. */
	public void setAccountState(Boolean imported)
	{
		historyImported = imported;
		if (imported == null)
		{
			importNotice = null;
		}
		updateWelcome();
	}

	/** The first GE History import for this account added {@code count} trades. */
	public void onHistoryImported(int count)
	{
		historyImported = true;
		importNotice = count;
		importNoticeSecondsLeft = IMPORT_NOTICE_SECONDS;
		updateWelcome();
	}

	private void dismissImportNotice()
	{
		importNotice = null;
		updateWelcome();
	}

	private void updateWelcome()
	{
		// The fresh-install preview: not connected, GE History not imported (when logged in), no notices yet.
		Boolean imported = previewFresh && historyImported != null ? Boolean.FALSE : historyImported;
		Integer notice = previewFresh ? null : importNotice;
		welcomeCard.update(Onboarding.state(effectiveConnected(), imported, notice), Onboarding.needsLogin(imported), notice);
	}

	/** Reload whatever is on screen. Skipped while the panel is hidden; {@link #onActivate} reloads it. */
	void refresh()
	{
		refresh(false);
	}

	/** @param fresh the user asked for it: skip HTTP caches */
	private void refresh(boolean fresh)
	{
		if (!active)
		{
			return;
		}
		if (CARD_ITEM.equals(currentCard) && itemView.hasItem())
		{
			itemView.load(itemView.getCurrentItem(), fresh);
			return;
		}
		if (homeView.isShowingMine())
		{
			homeView.showMine(shownMyTrades());
			return;
		}
		if (!effectiveConnected())
		{
			homeView.showNotConnected();
			return;
		}
		loadFeed(feedFullNext || lastFeedId <= 0 || feedPollsSinceFull >= FULL_FEED_EVERY);
	}

	/**
	 * Load the Market feed. Incremental loads ask only for events newer than the ones we have (servers that ignore
	 * "after" send the newest page, de-duplicated). A full load asks for the newest page and merges it with what we
	 * have, unless it doesn't connect to it (a gap) or the ids went back (a different or reset server).
	 */
	private void loadFeed(boolean full)
	{
		long previous = lastFeedId;
		long after = full ? 0 : previous;
		int generation = feedGeneration;
		market.recent(after, r ->
		{
			if (generation != feedGeneration)
			{
				return;
			}
			List<MarketData.FeedEvent> events = r.getEvents();
			if (!full && FeedMerge.possibleGap(events, after, MarketClient.RECENT_LIMIT))
			{
				// More happened than one page holds, and we can't tell which page this is (oldest or newest after
				// the id): fetch the newest page instead, which every server returns the same way.
				loadFeed(true);
				return;
			}
			if (full)
			{
				boolean restarted = FeedMerge.idsWentBack(events, previous);
				long base = restarted ? 0 : previous;
				feedEvents = FeedMerge.merge(restarted ? Collections.emptyList() : feedEvents, events, base,
					MarketClient.RECENT_LIMIT);
				lastFeedId = FeedMerge.maxId(events, restarted ? 0 : lastFeedId);
				feedFullNext = false;
				feedPollsSinceFull = 0;
			}
			else
			{
				feedEvents = FeedMerge.merge(feedEvents, events, after, MarketClient.RECENT_LIMIT);
				lastFeedId = FeedMerge.maxId(events, lastFeedId);
				feedPollsSinceFull++;
			}
			if (r.getStats() != null)
			{
				feedStats = r.getStats();
			}
			if (!homeView.isShowingMine() && CARD_HOME.equals(currentCard))
			{
				homeView.showMarket(feedStats, feedEvents);
			}
		}, err ->
		{
			if (generation != feedGeneration)
			{
				return;
			}
			// Whatever went wrong (server down, restarted, replaced), start over from the newest page next time.
			feedFullNext = true;
			if (!homeView.isShowingMine() && CARD_HOME.equals(currentCard))
			{
				homeView.showError(err);
			}
		});
	}

	/** A trade of yours was recorded, or the local log finished loading. */
	public void onMyTradesChanged()
	{
		if (!active)
		{
			return;
		}
		if (CARD_HOME.equals(currentCard) && homeView.isShowingMine())
		{
			homeView.showMine(shownMyTrades());
		}
		else if (CARD_ITEM.equals(currentCard))
		{
			itemView.updateLocal();
		}
	}

	/** Your open offers changed (placed, filled, cancelled, collected, or another account logged in). */
	public void onOffersChanged(List<ActiveOffer> offers)
	{
		this.offers = offers;
		offersSection.setOffers(shownOffers());
		if (active && CARD_ITEM.equals(currentCard))
		{
			itemView.updateLocal();
		}
	}

	/** Your open offers were compared with the market again (or re-evaluated). */
	public void onOfferReport(OfferReport report)
	{
		offerReport = report;
		offersSection.setReport(shownReport());
		if (active && CARD_ITEM.equals(currentCard))
		{
			itemView.updateLocal();
		}
	}

	/** New profit numbers (a trade was counted, another account logged in, or a rebuild ran). */
	public void onProfit(ProfitSnapshot snapshot)
	{
		profit = snapshot;
		profitSection.setSnapshot(shownProfit());
		if (active && CARD_ITEM.equals(currentCard))
		{
			itemView.updateLocal();
		}
	}

	/** An upload with trades of these items reached the server. */
	public void onUploaded(Set<Integer> itemIds)
	{
		if (active && CARD_ITEM.equals(currentCard))
		{
			itemView.onUploaded(itemIds);
		}
	}

	/* ------------------------------------------------------------ search */

	private void runSearch()
	{
		String q = searchField.getText().trim();
		if (q.length() < 2)
		{
			if (CARD_SEARCH.equals(currentCard))
			{
				leaveSearch();
			}
			return;
		}
		searchResults.removeAll();
		List<ItemPrice> results = itemManager.search(q);
		if (results.isEmpty())
		{
			searchResults.add(wrapText("No tradeable items match \"" + q + "\".", ColorScheme.LIGHT_GRAY_COLOR));
		}
		for (ItemPrice ip : results.subList(0, Math.min(MAX_SEARCH_RESULTS, results.size())))
		{
			searchResults.add(tradeRow(itemManager, ip.getId(), ip.getName(), null, null, this::openItem));
			searchResults.add(Box.createVerticalStrut(2));
		}
		searchResults.revalidate();
		searchResults.repaint();
		showCard(CARD_SEARCH);
	}

	private void openFirstResult()
	{
		String q = searchField.getText().trim();
		if (q.length() < 2)
		{
			// Too short to mean one item (a single letter matches hundreds): no jump to whichever sorts first.
			return;
		}
		searchDebounce.stop();
		List<ItemPrice> results = itemManager.search(q);
		if (!results.isEmpty())
		{
			openItem(results.get(0).getId(), results.get(0).getName());
		}
	}

	/**
	 * Back from the search results to the item (if one is open) or home, reloaded: neither was refreshed while the
	 * results covered it (the item view isn't, and home may have missed the newest feed page).
	 */
	private void leaveSearch()
	{
		showCard(itemView.hasItem() ? CARD_ITEM : CARD_HOME);
		feedFullNext = true;
		refresh();
	}

	/* -------------------------------------------------------------- item */

	/** Open the item view (from search, a feed row, the GE, or the right-click menu). */
	public void openItem(int itemId, String name)
	{
		searchDebounce.stop();
		if (!searchField.getText().isEmpty())
		{
			searchField.setText("");
		}
		boolean other = !itemView.hasItem() || itemView.getCurrentItem() != itemId;
		itemView.open(itemId, name);
		if (other && CARD_ITEM.equals(currentCard))
		{
			// Same card, so showCard keeps the scroll position: a different item starts at its top.
			JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
			if (vp != null)
			{
				vp.setViewPosition(new Point(0, 0));
			}
		}
		showCard(CARD_ITEM);
		// When hidden (opened from the game), onActivate loads it once the panel is shown.
		if (active)
		{
			itemView.load(itemId);
		}
	}

	/**
	 * Show a card. A different card starts at its top, except that going back home returns to where home was
	 * scrolled to when you left it.
	 */
	private void showCard(String card)
	{
		boolean changed = !card.equals(currentCard);
		JViewport vp = changed ? (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this) : null;
		if (vp != null && CARD_HOME.equals(currentCard))
		{
			homeScroll = vp.getViewPosition();
		}
		currentCard = card;
		cards.show(cardPanel, card);
		cardPanel.revalidate();
		if (vp == null)
		{
			return;
		}
		vp.setViewPosition(new Point(0, 0));
		Point back = CARD_HOME.equals(card) ? homeScroll : null;
		if (back != null && back.y > 0)
		{
			SwingUtilities.invokeLater(() ->
			{
				if (!CARD_HOME.equals(currentCard))
				{
					return;
				}
				vp.validate();
				int max = Math.max(0, vp.getViewSize().height - vp.getExtentSize().height);
				vp.setViewPosition(new Point(0, Math.min(back.y, max)));
			});
		}
	}
}
