package com.hardcoreworldtoolkit.ui;

import static com.hardcoreworldtoolkit.ui.Format.ago;
import static com.hardcoreworldtoolkit.ui.Format.describe;
import static com.hardcoreworldtoolkit.ui.Format.when;
import static com.hardcoreworldtoolkit.ui.PanelComponents.BUY;
import static com.hardcoreworldtoolkit.ui.PanelComponents.SELL;
import static com.hardcoreworldtoolkit.ui.PanelComponents.bookColumn;
import static com.hardcoreworldtoolkit.ui.PanelComponents.column;
import static com.hardcoreworldtoolkit.ui.PanelComponents.iconButton;
import static com.hardcoreworldtoolkit.ui.PanelComponents.legend;
import static com.hardcoreworldtoolkit.ui.PanelComponents.lineRow;
import static com.hardcoreworldtoolkit.ui.PanelComponents.mutedLabel;
import static com.hardcoreworldtoolkit.ui.PanelComponents.sectionLabel;
import static com.hardcoreworldtoolkit.ui.PanelComponents.sideColor;
import static com.hardcoreworldtoolkit.ui.PanelComponents.smallLabel;
import static com.hardcoreworldtoolkit.ui.PanelComponents.stat;
import static com.hardcoreworldtoolkit.ui.PanelComponents.styleTab;
import static com.hardcoreworldtoolkit.ui.PanelComponents.tabButton;
import static com.hardcoreworldtoolkit.ui.PanelComponents.wrapText;
import com.hardcoreworldtoolkit.ActiveOffer;
import com.hardcoreworldtoolkit.HardcoreWorldToolKitConfig;
import com.hardcoreworldtoolkit.MarketClient;
import com.hardcoreworldtoolkit.MarketData;
import com.hardcoreworldtoolkit.OfferReport;
import com.hardcoreworldtoolkit.OfferWatch;
import com.hardcoreworldtoolkit.ProfitSnapshot;
import com.hardcoreworldtoolkit.TradeEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.text.JTextComponent;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.QuantityFormatter;

/**
 * Item card: header with refresh, your open offers for the item, stats, price chart, open-offer book, recent trades
 * from all players, your own trades and your profit on the item.
 * <p>
 * Every section is built once and keeps the data it last showed; a refresh only re-renders sections whose data
 * changed, and keeps the scroll position, so the periodic refresh doesn't flicker. "Your offer" and "Your trades"
 * and "Your profit" come from local data and update as soon as your offers change. EDT only.
 */
class ItemView
{
	/** After an upload that included this item, refresh it from the server once, this long after (coalesced). */
	private static final int UPLOAD_REFRESH_MS = 750;
	/** How long the chart's price history is kept before the periodic reload fetches it again. */
	private static final long SERIES_REFRESH_MS = 5 * 60_000L;
	private int seriesItem = -1;
	private ChartRange seriesRange;
	private long seriesAt;
	static final String NOT_CONNECTED = "Connect to see market data.";

	private final HardcoreWorldToolKitConfig config;
	private final ItemManager itemManager;
	private final MarketClient market;
	private final Supplier<List<TradeEvent>> myTrades;
	private final Supplier<List<ActiveOffer>> myOffers;
	private final Supplier<OfferReport> offerReport;
	private final Supplier<ProfitSnapshot> profit;
	private final Runnable onBack;

	private final JPanel panel = column();
	private final JPanel geAsk = column();
	private final JButton detailButton = PanelComponents.primaryButton("");
	private Runnable onConnect = () -> { };
	private Runnable geNo = () -> { };
	private Runnable geYes = () -> { };
	private final Timer geAskHide = new Timer(6000, e -> geAsk.setVisible(false));
	private final JLabel updatedLabel = mutedLabel("");
	private final JLabel iconLabel = new JLabel();
	private final JLabel nameLabel = new JLabel();
	/** Why the last refresh failed, e.g. "Update failed: server unreachable", while the old data is still shown. */
	private final JLabel failedLabel = smallLabel("", ColorScheme.PROGRESS_ERROR_COLOR);
	private final JTextComponent message = (JTextComponent) wrapText("", ColorScheme.LIGHT_GRAY_COLOR);
	private final JPanel offerSection = column();
	private final RowList<OfferLine> offerRows;
	private final JPanel statsSection = column();
	private final JPanel chartSection = column();
	private final PriceChart chart = new PriceChart();
	private final JButton[] rangeButtons = new JButton[ChartRange.values().length];
	private final JPanel bookSection = column();
	private final JPanel bookHolder = column();
	private final JPanel tradesSection = column();
	private final RowList<RowList.Keyed<MarketData.Trade>> tradeRows;
	private final JPanel mineSection = column();
	private final RowList<RowList.Keyed<TradeEvent>> mineRows;
	private final JPanel profitSection = column();
	private final JLabel profitLine = smallLabel("", ColorScheme.TEXT_COLOR);
	private final JLabel profitUnknown = mutedLabel("");
	private final Timer uploadRefresh;

	private int currentItem = -1;
	private String currentItemName;
	/** Kept across items and loads. */
	private ChartRange range = ChartRange.DEFAULT;
	/** What the stats and book sections show now, to skip re-rendering identical data. */
	private Object shownStats;
	private MarketData.Book shownBook;
	/** The shown item's latest market summary, to compare your offers with; null until loaded. */
	private static final int GRID_CELLS = 6;
	private MarketData.Summary summary;
	/** Looks up what High Level Alchemy pays for an item and answers on the EDT (0: unknown); null: not shown. */
	private BiConsumer<Integer, IntConsumer> alchLookup;
	/** The current item's high alch value, or null until known. */
	private Integer highAlch;
	/** The shown item's recent market trades, for the live fills your offers are compared with. */
	private List<MarketData.Trade> marketTrades = Collections.emptyList();
	private boolean hasDetail;
	/** Epoch ms of the last successful item load, or 0. */
	private long lastUpdated;
	/** Why the last refresh failed, while its old data is still shown; null if it didn't. */
	private String failed;

	/**
	 * @param offerReport how your offers compare with the market, used until this item's own summary has loaded
	 * @param profit      your profit numbers (the item's line is taken from them)
	 * @param onBack      run after Back clears the current item, to show the home card
	 */
	ItemView(HardcoreWorldToolKitConfig config, ItemManager itemManager, MarketClient market,
		Supplier<List<TradeEvent>> myTrades, Supplier<List<ActiveOffer>> myOffers, Supplier<OfferReport> offerReport,
		Supplier<ProfitSnapshot> profit, Runnable onBack)
	{
		this.config = config;
		this.itemManager = itemManager;
		this.market = market;
		this.myTrades = myTrades;
		this.myOffers = myOffers;
		this.offerReport = offerReport;
		this.profit = profit;
		this.onBack = onBack;

		offerRows = new RowList<>(OfferLine::key, l -> l.getText() + "|" + l.getBadge() + "|" + l.getTooltip(),
			PanelComponents::offerRow);
		tradeRows = new RowList<>(k -> k.key,
			k -> when(k.value.getKind(), k.value.getTs()) + "|" + k.value.getConfirmed(),
			k -> tradeRow(k.value));
		tradeRows.setEmptyText("None yet.");
		mineRows = new RowList<>(k -> k.key, k -> when(k.value.getKind(), k.value.getTs()),
			k -> mineRow(k.value));
		// A normal load, not a forced one: the player's own trades are shown from local data already.
		uploadRefresh = new Timer(UPLOAD_REFRESH_MS, e ->
		{
			if (hasItem())
			{
				load(currentItem, false);
			}
		});
		uploadRefresh.setRepeats(false);

		geAsk.setVisible(false);
		panel.add(geAsk);
		panel.add(buildHeader());
		panel.add(Box.createVerticalStrut(8));
		panel.add(buildHead());
		panel.add(Box.createVerticalStrut(6));
		panel.add(buildDetailButton());
		failedLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		failedLabel.setVisible(false);
		panel.add(failedLabel);
		panel.add(Box.createVerticalStrut(6));
		message.setVisible(false);
		panel.add(message);

		offerSection.add(sectionLabel("Your offer"));
		offerSection.add(offerRows.getComponent());
		offerSection.add(Box.createVerticalStrut(6));
		offerSection.setVisible(false);
		panel.add(offerSection);

		statsSection.setVisible(false);
		panel.add(statsSection);

		chartSection.add(Box.createVerticalStrut(8));
		chartSection.add(sectionLabel("Price history"));
		chartSection.add(buildRangeButtons());
		chartSection.add(Box.createVerticalStrut(4));
		chart.setAlignmentX(Component.LEFT_ALIGNMENT);
		chartSection.add(chart);
		chartSection.add(legend());
		chartSection.setVisible(false);
		panel.add(chartSection);

		bookSection.add(Box.createVerticalStrut(8));
		bookSection.add(sectionLabel("Open offers"));
		bookSection.add(bookHolder);
		bookSection.setVisible(false);
		panel.add(bookSection);

		tradesSection.add(Box.createVerticalStrut(8));
		tradesSection.add(sectionLabel("Recent trades (all players)"));
		tradesSection.add(tradeRows.getComponent());
		tradesSection.setVisible(false);
		panel.add(tradesSection);

		mineSection.add(Box.createVerticalStrut(8));
		mineSection.add(sectionLabel("Your trades"));
		mineSection.add(mineRows.getComponent());
		mineSection.setVisible(false);
		panel.add(mineSection);

		profitSection.add(Box.createVerticalStrut(8));
		profitSection.add(sectionLabel("Your profit"));
		profitLine.setAlignmentX(Component.LEFT_ALIGNMENT);
		profitSection.add(profitLine);
		profitUnknown.setAlignmentX(Component.LEFT_ALIGNMENT);
		profitSection.add(profitUnknown);
		profitSection.setVisible(false);
		panel.add(profitSection);
	}

	JComponent getComponent()
	{
		return panel;
	}

	boolean hasItem()
	{
		return currentItem >= 0;
	}

	int getCurrentItem()
	{
		return currentItem;
	}

	/** The item's page on the Market Dashboard (the website). */
	static String marketUrl(int itemId)
	{
		return "https://hcwtoolkit.com/tools?from=plugin&utm_source=plugin&utm_medium=item_button#item=" + itemId;
	}

	private JComponent buildHeader()
	{
		JPanel top = new JPanel(new BorderLayout(6, 0));
		top.setOpaque(false);
		JButton back = new JButton("< Back");
		back.addActionListener(e ->
		{
			currentItem = -1;
			uploadRefresh.stop();
			onBack.run();
		});
		top.add(back, BorderLayout.WEST);
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 3));
		right.setOpaque(false);
		right.add(updatedLabel);
		JButton refresh = iconButton(Icons.refresh(), "Refresh now", 22);
		refresh.addActionListener(e ->
		{
			if (hasItem())
			{
				load(currentItem, true);
			}
		});
		right.add(refresh);
		top.add(right, BorderLayout.EAST);
		top.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		top.setAlignmentX(Component.LEFT_ALIGNMENT);
		return top;
	}

	private JComponent buildDetailButton()
	{
		JButton b = detailButton;
		b.addActionListener(e ->
		{
			if (!config.connected())
			{
				if (WelcomeCard.WARNING_DIALOG.confirm(b))
				{
					onConnect.run();
				}
			}
			else if (hasItem())
			{
				LinkBrowser.browse(marketUrl(currentItem));
			}
		});
		b.setAlignmentX(Component.LEFT_ALIGNMENT);
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		b.setPreferredSize(new Dimension(0, 28));
		return b;
	}

	void setConnectAction(Runnable action)
	{
		onConnect = action;
	}

	void setGeAskActions(Runnable onNo, Runnable onYes)
	{
		geNo = onNo;
		geYes = onYes;
	}

	/** Asks whether GE offers should keep opening the sidebar. */
	void showGeAsk()
	{
		geAskHide.stop();
		geAsk.removeAll();
		JPanel card = column();
		card.setOpaque(true);
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new CompoundBorder(new MatteBorder(0, 3, 0, 0, PanelComponents.HC_RED), new EmptyBorder(8, 8, 8, 8)));
		JLabel title = new JLabel("Keep auto-opening?");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.add(title);
		card.add(Box.createVerticalStrut(4));
		card.add(wrapText("The sidebar opened because you set up a GE offer. Should it keep doing that?", ColorScheme.LIGHT_GRAY_COLOR));
		card.add(Box.createVerticalStrut(6));
		JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
		buttons.setOpaque(false);
		JButton no = new JButton("No, stop");
		no.setFocusPainted(false);
		no.addActionListener(e -> geNo.run());
		JButton yes = new JButton("Yes, keep");
		yes.setFocusPainted(false);
		yes.addActionListener(e -> geYes.run());
		buttons.add(no);
		buttons.add(yes);
		buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
		buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		card.add(buttons);
		card.add(Box.createVerticalStrut(4));
		card.add(smallLabel("Change it any time in the settings.", ColorScheme.LIGHT_GRAY_COLOR));
		geAsk.add(card);
		geAsk.add(Box.createVerticalStrut(8));
		geAsk.setVisible(true);
		geAsk.revalidate();
		geAsk.repaint();
	}

	/** Hides the card, after showing {@code message} for a few seconds if given. */
	void hideGeAsk(String message)
	{
		if (message == null)
		{
			geAsk.setVisible(false);
			return;
		}
		geAsk.removeAll();
		JPanel card = column();
		card.setOpaque(true);
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new CompoundBorder(new MatteBorder(0, 3, 0, 0, PanelComponents.GOOD), new EmptyBorder(8, 8, 8, 8)));
		card.add(wrapText(message, Color.WHITE));
		geAsk.add(card);
		geAsk.add(Box.createVerticalStrut(8));
		geAsk.revalidate();
		geAsk.repaint();
		geAskHide.setRepeats(false);
		geAskHide.restart();
	}

	private JComponent buildHead()
	{
		JPanel head = new JPanel(new BorderLayout(8, 0));
		head.setOpaque(false);
		iconLabel.setPreferredSize(new Dimension(36, 32));
		head.add(iconLabel, BorderLayout.WEST);
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		nameLabel.setForeground(Color.WHITE);
		head.add(nameLabel, BorderLayout.CENTER);
		head.setAlignmentX(Component.LEFT_ALIGNMENT);
		head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		return head;
	}

	private JComponent buildRangeButtons()
	{
		JPanel row = new JPanel(new GridLayout(1, ChartRange.values().length, 0, 0));
		row.setOpaque(false);
		for (ChartRange r : ChartRange.values())
		{
			JButton b = tabButton(r.label());
			b.setFont(FontManager.getRunescapeSmallFont());
			b.addActionListener(e -> selectRange(r));
			rangeButtons[r.ordinal()] = b;
			row.add(b);
		}
		styleRangeButtons();
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		return row;
	}

	private void styleRangeButtons()
	{
		for (ChartRange r : ChartRange.values())
		{
			styleTab(rangeButtons[r.ordinal()], r == range);
		}
	}

	private void selectRange(ChartRange r)
	{
		if (r == range)
		{
			return;
		}
		range = r;
		styleRangeButtons();
		chart.setLoading(r);
		if (hasItem() && config.connected())
		{
			loadSeries(currentItem, r, false);
		}
	}

	/** Switch to {@code itemId}, clearing the old item's data; does nothing if it is already shown. */
	void setAlchLookup(BiConsumer<Integer, IntConsumer> lookup)
	{
		alchLookup = lookup;
	}

	void open(int itemId, String name)
	{
		if (itemId == currentItem)
		{
			return;
		}
		currentItem = itemId;
		highAlch = null;
		if (alchLookup != null)
		{
			alchLookup.accept(itemId, value ->
			{
				if (currentItem == itemId && value > 0)
				{
					highAlch = value;
					shownStats = null;
					renderStats(summary);
				}
			});
		}
		currentItemName = name;
		uploadRefresh.stop();
		itemManager.getImage(itemId).addTo(iconLabel);
		nameLabel.setText(name == null ? "Item " + itemId : name);
		nameLabel.setToolTipText(nameLabel.getText());
		shownStats = null;
		shownBook = null;
		summary = null;
		hasDetail = false;
		lastUpdated = 0;
		failed = null;
		statsSection.setVisible(false);
		bookSection.setVisible(false);
		tradesSection.setVisible(false);
		chartSection.setVisible(false);
		chart.setLoading(range);
		offerRows.reset();
		tradeRows.reset();
		mineRows.reset();
		showMessage("Loading…");
		updateLocal();
		tick();
		panel.revalidate();
		panel.repaint();
	}

	void load(int itemId)
	{
		load(itemId, false);
	}

	/**
	 * Fetch the item and its price series in parallel; each response updates its own sections. Results for an item
	 * no longer shown are dropped. A failed refresh keeps the data already shown.
	 *
	 * @param fresh skip HTTP caches (refresh button, after an upload)
	 */
	void load(int itemId, boolean fresh)
	{
		updateLocal();
		boolean connected = config.connected();
		detailButton.setText(connected ? "See detailed info" : "Connect");
		detailButton.setIcon(connected ? Icons.stats() : null);
		detailButton.setToolTipText(connected ? "Open this item on the Market Dashboard" : null);
		if (!connected)
		{
			keepScroll(() ->
			{
				showMessage(NOT_CONNECTED);
				statsSection.setVisible(false);
				chartSection.setVisible(false);
				bookSection.setVisible(false);
				tradesSection.setVisible(false);
				hasDetail = false;
				shownStats = null;
				shownBook = null;
				summary = null;
				lastUpdated = 0;
				failed = null;
				tick();
			});
			return;
		}
		chartSection.setVisible(true);
		market.item(itemId, fresh, detail -> onDetail(itemId, detail), err -> onError(itemId, err));
		// The price history moves slowly (its newest bucket is minutes wide): the periodic reload skips it while the
		// chart already shows this item and range and was fetched recently. A manual refresh, an upload, opening an
		// item, changing the range and an empty chart still fetch it.
		boolean seriesCurrent = !fresh && itemId == seriesItem && range == seriesRange && chart.hasData()
			&& System.currentTimeMillis() - seriesAt < SERIES_REFRESH_MS;
		if (!seriesCurrent)
		{
			loadSeries(itemId, range, fresh);
		}
	}

	private void loadSeries(int itemId, ChartRange r, boolean fresh)
	{
		seriesItem = itemId;
		seriesRange = r;
		seriesAt = System.currentTimeMillis();
		market.series(itemId, r.label(), r.step(), fresh, series ->
		{
			if (itemId == currentItem && r == range)
			{
				chart.setData(series.getData(), r, Instant.now().getEpochSecond());
			}
		}, err ->
		{
			// Keep a chart that's already showing data for this range.
			if (itemId == currentItem && r == range && !chart.hasData())
			{
				chart.setUnavailable(r);
			}
		});
	}

	private void onDetail(int itemId, MarketData.ItemDetail d)
	{
		if (itemId != currentItem || !config.connected())
		{
			return;
		}
		keepScroll(() ->
		{
			if (d.getName() != null && !d.getName().startsWith("Item ") && !d.getName().equals(currentItemName))
			{
				currentItemName = d.getName();
				nameLabel.setText(currentItemName);
				nameLabel.setToolTipText(currentItemName);
			}
			hasDetail = true;
			summary = d.getSummary();
			marketTrades = d.getTrades();
			failed = null;
			lastUpdated = System.currentTimeMillis();
			hideMessage();
			renderStats(d.getSummary());
			renderBook(d.getBook());
			tradeRows.setItems(RowList.withOccurrenceKeys(recentTrades(d.getTrades()),
				t -> t.getKind() + "|" + t.getSide() + "|" + t.getQty() + "|" + t.getPrice() + "|" + t.getTs()));
			tradesSection.setVisible(true);
			tick();
		});
		// The fresh summary may change how your offers compare.
		updateLocal();
	}

	private void onError(int itemId, String err)
	{
		if (itemId != currentItem || !config.connected())
		{
			return;
		}
		if (!hasDetail)
		{
			keepScroll(() -> showMessage(err));
		}
		else
		{
			failed = err;
			tick();
		}
	}

	private void renderStats(MarketData.Summary s)
	{
		if (s == null)
		{
			statsSection.setVisible(false);
			shownStats = null;
			return;
		}
		// The "ago" notes change with time even when the data doesn't.
		Object key = Arrays.asList(s, ago(s.getLastBuyTime()), ago(s.getLastSellTime()), ago(s.getTrustedPriceTime()), highAlch);
		if (key.equals(shownStats))
		{
			return;
		}
		shownStats = key;
		statsSection.removeAll();
		JPanel grid = new JPanel(new GridLayout(0, 2, 4, 4));
		grid.setOpaque(false);
		List<ItemSections.StatCell> cells = ItemSections.statCells(s, highAlch, Instant.now().getEpochSecond());
		// The six price cells in pairs; the ones after them (the typical price, whose note is long, and high alch) get
		// the whole row each, so their values aren't cut off.
		int paired = Math.min(GRID_CELLS, cells.size());
		for (ItemSections.StatCell c : cells.subList(0, paired))
		{
			grid.add(statCell(c));
		}
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		statsSection.add(grid);
		for (ItemSections.StatCell c : cells.subList(paired, cells.size()))
		{
			JPanel row = new JPanel(new BorderLayout());
			row.setOpaque(false);
			row.setBorder(new EmptyBorder(4, 0, 0, 0));
			row.add(statCell(c), BorderLayout.CENTER);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			statsSection.add(row);
		}
		statsSection.add(Box.createVerticalStrut(4));
		JLabel volume = mutedLabel("24h volume: " + QuantityFormatter.formatNumber(s.getVol24()) + " units");
		volume.setToolTipText(ItemSections.volumeTooltip(s));
		volume.setAlignmentX(Component.LEFT_ALIGNMENT);
		statsSection.add(volume);
		statsSection.setVisible(true);
		statsSection.revalidate();
		statsSection.repaint();
	}

	private static JComponent statCell(ItemSections.StatCell c)
	{
		JComponent cell = stat(c.getLabel(), c.getValue(), c.getSub(), accentColor(c.getAccent()));
		cell.setToolTipText(c.getTooltip());
		return cell;
	}

	private static Color accentColor(ItemSections.Accent a)
	{
		switch (a)
		{
			case BUY:
				return BUY;
			case SELL:
				return SELL;
			default:
				return SkullIcon.BONE;
		}
	}

	/** One of your trades. */
	static JComponent mineRow(TradeEvent e)
	{
		String text = describe(e.getKind(), e.getSide(), e.getQty(), e.getPrice());
		return lineRow(text, Format.when(e.getKind(), e.getTs()), sideColor(e.getSide()));
	}

	/** A market trade row; fills the server marked unconfirmed get a muted accent and say so in the tooltip. */
	private static JComponent tradeRow(MarketData.Trade t)
	{
		String text = describe(t.getKind(), t.getSide(), t.getQty(), t.getPrice());
		String right = Format.when(t.getKind(), t.getTs());
		if (ItemSections.unconfirmed(t))
		{
			return lineRow(text, right, ColorScheme.MEDIUM_GRAY_COLOR, ColorScheme.LIGHT_GRAY_COLOR,
				text + " (unconfirmed: not yet backed by an established player or a matching trade)");
		}
		return lineRow(text, right, sideColor(t.getSide()));
	}

	private void renderBook(MarketData.Book book)
	{
		bookSection.setVisible(true);
		if (shownBook != null && shownBook.equals(book))
		{
			return;
		}
		shownBook = book == null ? new MarketData.Book() : book;
		bookHolder.removeAll();
		JPanel cols = new JPanel(new GridLayout(1, 2, 6, 0));
		cols.setOpaque(false);
		cols.add(bookColumn("Bids", book == null ? null : book.getBids(), BUY));
		cols.add(bookColumn("Asks", book == null ? null : book.getAsks(), SELL));
		cols.setAlignmentX(Component.LEFT_ALIGNMENT);
		bookHolder.add(cols);
		bookHolder.revalidate();
		bookHolder.repaint();
	}

	/**
	 * Re-render "Your offer" and "Your trades" from local data, e.g. right after one of your offers changed or a fill
	 * was recorded. Cheap; nothing is fetched.
	 */
	void updateLocal()
	{
		if (!hasItem())
		{
			return;
		}
		keepScroll(() ->
		{
			List<ActiveOffer> offers = ItemSections.offersFor(myOffers.get(), currentItem);
			long now = Instant.now().getEpochSecond();
			Map<Integer, OfferWatch.OfferStatus> statuses = offerStatuses(offers, now);
			List<OfferLine> lines = new ArrayList<>(offers.size());
			for (ActiveOffer o : offers)
			{
				OfferWatch.OfferStatus st = statuses.get(o.getSlot());
				lines.add(OfferLine.item(o, st != null && Objects.equals(st.getOfferKey(), o.getOfferKey()) ? st : null, now));
			}
			offerRows.setItems(lines);
			offerSection.setVisible(!offers.isEmpty());
			List<TradeEvent> mine = ItemSections.tradesFor(myTrades.get(), currentItem);
			mineRows.setItems(RowList.withOccurrenceKeys(mine, ItemSections::tradeKey));
			mineSection.setVisible(config.connected() && !mine.isEmpty());
			renderProfit(profit.get().item(currentItem));
		});
	}

	/** "Your profit": units held, average cost and realized profit, plus sells with no known cost. */
	private void renderProfit(ProfitSnapshot.ItemProfit p)
	{
		boolean show = config.showProfit() && ProfitText.hasPosition(p);
		if (show)
		{
			String line = ProfitText.itemLine(p);
			if (!line.equals(profitLine.getText()))
			{
				profitLine.setText(line);
				profitLine.setToolTipText(ProfitText.itemTooltip(p));
			}
			String unknown = ProfitText.itemUnknownLine(p);
			String text = unknown == null ? "" : unknown;
			if (!text.equals(profitUnknown.getText()))
			{
				profitUnknown.setText(text);
				profitUnknown.setToolTipText(unknown);
			}
			profitUnknown.setVisible(unknown != null);
		}
		if (show != profitSection.isVisible())
		{
			profitSection.setVisible(show);
		}
	}

	/**
	 * How your offers for this item compare with the market: from this item's own summary once loaded (fresher), else
	 * from the plugin's periodic check. None when not connected.
	 */
	private Map<Integer, OfferWatch.OfferStatus> offerStatuses(List<ActiveOffer> offers, long now)
	{
		if (!config.connected() || offers.isEmpty())
		{
			return Collections.emptyMap();
		}
		if (summary != null)
		{
			return OfferWatch.evaluate(offers, Collections.singletonMap(currentItem, OfferWatch.Quote.of(summary, marketTrades)),
				myTrades.get(), now, config.staleHours() * 3600L);
		}
		return offerReport.get().getStatuses();
	}

	/** An upload that included these items succeeded: if the shown item is one, refresh it from the server soon. */
	void onUploaded(Set<Integer> itemIds)
	{
		if (hasItem() && itemIds.contains(currentItem) && config.connected())
		{
			uploadRefresh.restart();
		}
	}

	/**
	 * Once a second while shown: the "updated Xs ago" text, the reason of a failed refresh (on its own full-width line,
	 * e.g. "Update failed: server unreachable"; the header is too narrow) and the buy limit countdown.
	 */
	void tick()
	{
		String text = lastUpdated > 0 ? "Updated " + Format.updatedAgo(lastUpdated, System.currentTimeMillis()) : "";
		if (!text.equals(updatedLabel.getText()))
		{
			updatedLabel.setText(text);
		}
		String fail = failed == null ? "" : HomeView.failedText(failed);
		if (!fail.equals(failedLabel.getText()))
		{
			failedLabel.setText(fail);
			failedLabel.setToolTipText(failed == null ? null : failed + " Showing the last data received.");
		}
		if (failedLabel.isVisible() != (failed != null))
		{
			failedLabel.setVisible(failed != null);
		}
	}

	void stop()
	{
		uploadRefresh.stop();
		offerRows.stop();
		tradeRows.stop();
		mineRows.stop();
	}

	private void showMessage(String text)
	{
		if (!text.equals(message.getText()))
		{
			message.setText(text);
		}
		message.setVisible(true);
	}

	private void hideMessage()
	{
		message.setVisible(false);
	}

	/**
	 * Run a section update without moving the scroll position: section heights can change, so remember where the
	 * panel's scroll view was and put it back once the layout has caught up.
	 */
	private void keepScroll(Runnable update)
	{
		JViewport vp = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, panel);
		Point pos = vp == null ? null : vp.getViewPosition();
		update.run();
		if (vp == null || pos.y == 0)
		{
			return;
		}
		SwingUtilities.invokeLater(() ->
		{
			Component view = vp.getView();
			if (view == null)
			{
				return;
			}
			view.validate();
			int maxY = Math.max(0, view.getHeight() - vp.getExtentSize().height);
			Point target = new Point(pos.x, Math.min(pos.y, maxY));
			if (!target.equals(vp.getViewPosition()))
			{
				vp.setViewPosition(target);
			}
		});
	}

	/**
	 * Rows for "Recent trades": fills and GE History rows. Offer placements and cancellations aren't trades.
	 */
	static List<MarketData.Trade> recentTrades(List<MarketData.Trade> trades)
	{
		if (trades == null)
		{
			return Collections.emptyList();
		}
		List<MarketData.Trade> shown = new ArrayList<>();
		for (MarketData.Trade t : trades)
		{
			if (TradeEvent.isTrade(t.getKind()))
			{
				shown.add(t);
			}
		}
		return shown;
	}
}
