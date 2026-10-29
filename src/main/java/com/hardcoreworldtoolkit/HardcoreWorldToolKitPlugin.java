package com.hardcoreworldtoolkit;

import com.hardcoreworldtoolkit.ui.HardcoreWorldToolKitPanel;
import com.hardcoreworldtoolkit.ui.SharedDataActions;
import com.hardcoreworldtoolkit.ui.SkullIcon;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import net.runelite.api.ItemComposition;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptID;
import net.runelite.api.WorldType;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GrandExchangeOfferChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.util.AsyncBufferedImage;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "Hardcore World Tool Kit",
	description = "Grand Exchange prices, trade history and offer alerts for Hardcore Worlds (opt-in price sharing)",
	tags = {"hardcore", "hardcore worlds", "grand exchange", "ge", "prices", "trade", "history", "flipping", "market"},
	internalName = "hardcore-world-tool-kit"
)
public class HardcoreWorldToolKitPlugin extends Plugin
{
	/**
	 * What the server calls the Hardcore Worlds' Grand Exchange. All Hardcore Worlds share one economy, so trades go to
	 * the server under this id instead of the world number (the server accepts exactly this value, see its
	 * ALLOWED_WORLDS).
	 */
	static final int HARDCORE_ECONOMY = 9000;
	/** Collection server for every normal (Plugin Hub) install (Google Cloud Run, europe-north1). Players can't change it. */
	static final String PRODUCTION_SERVER_URL = "https://hardcore-ge-tracker-107023686748.europe-north1.run.app";
	/** Server used when RuneLite runs in developer mode (the dev client): the local server from server/ (npm run dev). */
	static final String LOCAL_SERVER_URL = "http://localhost:8787";
	/** Developer-mode only: point the dev client at another server, e.g. -Dhardcoreworldtoolkit.server=https://staging.example */
	static final String SERVER_OVERRIDE_PROPERTY = "hardcoreworldtoolkit.server";
	/**
	 * Developer mode only, and only with a server that isn't the production one: -Dhardcoreworldtoolkit.anyWorld=true treats
	 * every world as a Hardcore World, so the plugin can be tried on a normal world against a local server.
	 */
	static final String ANY_WORLD_PROPERTY = "hardcoreworldtoolkit.anyWorld";
	private static final String MENU_OPTION = "HC prices";
	/** Offer updates within this many ticks of login are the client replaying state, not live trades. */
	private static final int LOGIN_BURST_TICKS = 2;
	/** Re-send the open offers this often (ticks) so the server knows they are still up. */
	private static final int OPEN_OFFERS_INTERVAL_TICKS = 600;
	/** How many ticks to keep trying to read the GE History list after it opens. */
	private static final int HISTORY_READ_TICKS = 10;
	/** How often (ticks) to record that the offers were still unchanged, bounding when a logged-out fill happened. */
	private static final int SEEN_INTERVAL_TICKS = 100;
	/** After a fill, upload within about this long instead of waiting for the next scheduled flush. */
	private static final int FLUSH_SOON_SECONDS = 2;
	/** Tries for that upload while another one is still running. */
	private static final int FLUSH_SOON_TRIES = 3;
	/** How often the offer check runs; {@link OfferMarketWatch} decides whether it actually fetches (at most once a minute). */
	private static final int OFFER_CHECK_SECONDS = 10;
	/** RuneLite script callbacks around the GE offer setup's description text (see {@link GePriceText}). */
	static final String GE_BUY_EXAMINE = "geBuyExamineText";
	static final String GE_SELL_EXAMINE = "geSellExamineText";
	static final String CHAT_HINT = "Hardcore World Tool Kit: open the side panel to connect and import your GE History.";

	@Inject
	private Client client;

	@Inject
	private HardcoreWorldToolKitConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Gson gson;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private TrackerStore store;

	@Inject
	private EventBus eventBus;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Notifier notifier;

	@Inject
	@Named("developerMode")
	private boolean developerMode;

	// Set in startUp and cleared in shutDown (EDT) but read from the client thread, executor and EDT: volatile, and
	// handlers read each one into a local once so a concurrent shutDown can't null it halfway through.
	private volatile OfferTracker tracker;
	private volatile TradeUploader uploader;
	/** Unsent uploads on disk, so closing RuneLite doesn't lose them. */
	private volatile UploadSpool spool;
	/** "Delete my shared data" (panel Privacy menu). */
	private volatile DataDeletion deletion;
	private volatile HardcoreWorldToolKitPanel panel;
	private volatile NavigationButton navButton;
	private ScheduledFuture<?> flushTask;
	private ScheduledFuture<?> offerCheckTask;
	/** Compares your open offers with the market. */
	private volatile OfferMarketWatch offerWatch;
	/** Market summaries for the GE price line. */
	private volatile ItemQuoteCache geQuotes;
	/** Your profit per account; only used on the executor (see {@link ProfitStore}). */
	private volatile ProfitStore profit;
	/** The pending one-shot upload after a fill, if any; see {@link #requestFlushSoon}. */
	private final AtomicReference<ScheduledFuture<?>> soonFlush = new AtomicReference<>();
	/** Your open offers as last sent to the panel. Written on the client thread; shutDown resets it. */
	private volatile List<ActiveOffer> publishedOffers = Collections.emptyList();
	private final MyTrades myTrades = new MyTrades();
	private final LoginDetector loginDetector = new LoginDetector();
	/** Developer mode only: the {@code ::hwtk} command, registered on the event bus; null otherwise. */
	private volatile DevCommands devCommands;
	/** Developer mode with a non-production server only: every world counts as a Hardcore World (see {@link #ANY_WORLD_PROPERTY}). */
	private volatile boolean devAnyWorld;

	// Tick state. Written on the client thread; volatile only because shutDown (EDT) also resets it.
	/** Tick of the last real login (or hop / reconnect); not loading screens. */
	private volatile int lastLoginTick = -1;
	private volatile int nextOpenOffersTick = -1;
	private volatile int historyReadTicks;
	private volatile int lastGeItem = -1;
	/** Set on login: the next tick checks the account's onboarding state and the one-time chat hint. */
	private volatile boolean loginCheckPending;
	/** The RS profile whose GE History import state was last sent to the panel. */
	private volatile String announcedProfile;
	// The GE offer setup text, as last seen by the script callback. Client thread only.
	/** Canonical item the description text was last built for, or -1. */
	private int geTextItem = -1;
	/** The fee text the script had then (empty when the offer setup shows no GE tax line). */
	private String geTextFee = "";

	// AccountId cache. Client thread only.
	private long cachedAccountHash = -1;
	private String cachedAccountId;

	/**
	 * Normal installs always use the production server. Only the developer-mode client uses the local server, or the
	 * override property if it is a valid URL.
	 */
	static String serverUrl(boolean developerMode, String override)
	{
		if (!developerMode)
		{
			return PRODUCTION_SERVER_URL;
		}
		if (override != null && HttpUrl.parse(override.trim()) != null)
		{
			return override.trim();
		}
		return LOCAL_SERVER_URL;
	}

	@Provides
	HardcoreWorldToolKitConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(HardcoreWorldToolKitConfig.class);
	}

	@Override
	protected void startUp()
	{
		String url = serverUrl(developerMode, developerMode ? System.getProperty(SERVER_OVERRIDE_PROPERTY) : null);
		HttpUrl base = HttpUrl.get(url);
		log.debug("Using collection server {}", base);
		// Never together with the production server: trades from a normal world must not reach the real price index.
		devAnyWorld = developerMode && Boolean.getBoolean(ANY_WORLD_PROPERTY) && !PRODUCTION_SERVER_URL.equals(url);
		tracker = new OfferTracker(() -> UUID.randomUUID().toString());
		// The install id and token live in a file in the plugin's folder (never in the config, which is logged, shared
		// with other plugins and synced), one per server so a developer-mode client on a test server never touches the
		// real ones; read it now on the executor, off the client thread and the EDT.
		store.useDataDir(this::getPluginDirectory, IdentityFile.fileNameFor(base, HttpUrl.get(PRODUCTION_SERVER_URL)));
		executor.execute(store::loadIdentity);
		// Uploads carry the install token (registering first); nothing, registration included, is sent unless
		// connected and sharing.
		TradeUploader up = new TradeUploader(okHttpClient, gson, base, this::onUploaded, store, this::shareEnabled);
		uploader = up;
		// Uploads left over from the last session (RuneLite closed, server down) go back in
		// the queue; they are only sent if connected and sharing, like everything else.
		UploadSpool sp = new UploadSpool(this::getPluginDirectory, gson);
		spool = sp;
		executor.execute(() ->
		{
			List<TradeUploader.Queued> back = sp.load();
			if (!back.isEmpty())
			{
				up.restore(back);
				log.debug("Restored {} pending uploads", back.size());
			}
		});
		deletion = new DataDeletion(okHttpClient, gson, base, store, up,
			() -> configManager.setConfiguration(HardcoreWorldToolKitConfig.GROUP, HardcoreWorldToolKitConfig.KEY_CONNECTED, false));
		store.reset();
		flushTask = executor.scheduleWithFixedDelay(this::scheduledFlush, 15, 30, TimeUnit.SECONDS);
		MarketClient market = new MarketClient(okHttpClient, gson, base, HARDCORE_ECONOMY);
		geQuotes = new ItemQuoteCache(market::itemAsync);
		offerWatch = new OfferMarketWatch(market::items, System::currentTimeMillis, config::connected,
			() -> config.offerNotification().isEnabled(), this::panelShown, () -> config.staleHours() * 3600L, myTrades::snapshot,
			new OfferMarketWatch.Listener()
			{
				@Override
				public void onReport(OfferReport report)
				{
					SwingUtilities.invokeLater(() ->
					{
						HardcoreWorldToolKitPanel p = panel;
						if (p != null)
						{
							p.onOfferReport(report);
						}
					});
				}

				@Override
				public void onAlert(String message)
				{
					clientThread.invoke(() -> notifier.notify(config.offerNotification(), message));
				}
			});
		offerCheckTask = executor.scheduleWithFixedDelay(this::checkOffers, OFFER_CHECK_SECONDS, OFFER_CHECK_SECONDS,
			TimeUnit.SECONDS);
		profit = new ProfitStore(this::getPluginDirectory, gson, executor, Clock.systemDefaultZone(), this::onProfit);
		clientThread.invoke(() ->
		{
			resetTickState();
			GameState state = client.getGameState();
			// Reset during LOADING makes the next LOGGED_IN count as a login, which schedules the open offers.
			loginDetector.reset(state);
			if (state == GameState.LOGGED_IN)
			{
				// Enabled while already logged in: no login event is coming, so send the open offers soon.
				nextOpenOffersTick = client.getTickCount() + 1;
				loginCheckPending = true;
				// ... and no offer events either, for slots that haven't changed since: compare all 8 now.
				reconcileSlots();
			}
		});

		// RuneLite's config plugin answers this event by opening overlay.getPlugin()'s configuration page. The overlay
		// is never registered or drawn, so there is nothing to remove in shutDown.
		ConfigLinkOverlay overlay = new ConfigLinkOverlay(this);
		Runnable openConfig = () -> eventBus.post(new OverlayMenuClicked(
			new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, "Configure", "Hardcore World Tool Kit"), overlay));
		panel = new HardcoreWorldToolKitPanel(config, configManager, itemManager, market, myTrades::snapshot, base, openConfig,
			() ->
			{
				OfferMarketWatch w = offerWatch;
				if (w != null)
				{
					w.refresh(true);
				}
			}, this::rebuildProfit, sharedDataActions());
		navButton = NavigationButton.builder()
			.tooltip("Hardcore World Tool Kit")
			.icon(buildIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		int session = myTrades.session();
		executor.execute(() -> loadLocalLog(session));
		if (developerMode)
		{
			// ::hwtk (fresh-install preview, re-arming hints) exists only in developer mode; normal installs never register it.
			DevCommands dev = new DevCommands(devTarget(), this::localChat);
			devCommands = dev;
			eventBus.register(dev);
		}
	}

	@Override
	protected void shutDown()
	{
		DevCommands dev = devCommands;
		devCommands = null;
		if (dev != null)
		{
			eventBus.unregister(dev);
		}
		if (flushTask != null)
		{
			flushTask.cancel(false);
			flushTask = null;
		}
		ScheduledFuture<?> soon = soonFlush.getAndSet(null);
		if (soon != null)
		{
			soon.cancel(false);
		}
		if (offerCheckTask != null)
		{
			offerCheckTask.cancel(false);
			offerCheckTask = null;
		}
		offerWatch = null;
		DataDeletion del = deletion;
		deletion = null;
		if (del != null)
		{
			// A running deletion still finishes (and disconnects); it just no longer reports to the panel.
			del.cancel();
		}
		ItemQuoteCache quotes = geQuotes;
		geQuotes = null;
		if (quotes != null)
		{
			quotes.clear();
		}
		// Final profit save, after any log appends and profit updates already queued (the executor runs them in order).
		ProfitStore ps = profit;
		profit = null;
		if (ps != null)
		{
			executor.execute(ps::close);
		}
		// Take the price line out of an open GE offer setup; the game rebuilds the text without it next time anyway.
		clientThread.invoke(this::removeGeText);
		// Stop recording first, so an event handled meanwhile can't go into an uploader nobody flushes again; then send
		// what's queued and spool whatever is still waiting (the flush is asynchronous) for the next start.
		TradeUploader up = uploader;
		UploadSpool sp = spool;
		tracker = null;
		uploader = null;
		spool = null;
		if (up != null)
		{
			boolean share = shareEnabled();
			// On the executor (the flush may read the identity file and encode item names): it runs tasks in order, so
			// the spool is saved after the flush started.
			executor.execute(() ->
			{
				if (share)
				{
					up.flush();
				}
				else
				{
					up.clear();
				}
				if (sp != null)
				{
					sp.save(up.pending());
				}
			});
		}
		clientToolbar.removeNavigation(navButton);
		panel.shutDown();
		panel = null;
		navButton = null;
		tracker = null;
		store.reset();
		resetTickState();
		// Also starts a new session, so a local log load still running is dropped instead of merged.
		myTrades.clear();
	}

	private void resetTickState()
	{
		lastLoginTick = -1;
		nextOpenOffersTick = -1;
		historyReadTicks = 0;
		lastGeItem = -1;
		loginCheckPending = false;
		announcedProfile = null;
		publishedOffers = Collections.emptyList();
	}

	private boolean shareEnabled()
	{
		return config.connected() && config.shareTrades();
	}

	/** The panel's "Delete my shared data" (EDT). Results come back on the EDT. */
	private SharedDataActions sharedDataActions()
	{
		return new SharedDataActions()
		{
			@Override
			public boolean mayHaveShared()
			{
				return config.connected() || store.hasToken();
			}

			@Override
			public boolean isDeleting()
			{
				DataDeletion d = deletion;
				return d != null && d.isRunning();
			}

			@Override
			public boolean delete(Consumer<String> onDone)
			{
				DataDeletion d = deletion;
				return d != null && d.start(result ->
				{
					log.debug("Data deletion finished: {}", result);
					String message = DataDeletion.message(result);
					SwingUtilities.invokeLater(() -> onDone.accept(message));
				});
			}
		};
	}

	/* ------------------------------------------------------------ events */

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		// LOGGED_IN also follows every loading screen; only a real login / hop / reconnect replays the offers.
		if (loginDetector.update(e.getGameState()))
		{
			lastLoginTick = client.getTickCount();
			nextOpenOffersTick = lastLoginTick + LOGIN_BURST_TICKS + 1;
			loginCheckPending = true;
		}
		if (e.getGameState() == GameState.LOGIN_SCREEN)
		{
			announceAccount(null);
		}
	}

	/** The client is closing (shutDown isn't called then): save profit once the queued log writes are done. */
	@Subscribe
	public void onClientShutdown(ClientShutdown e)
	{
		ProfitStore ps = profit;
		if (ps != null)
		{
			e.waitFor(executor.submit(ps::saveNow));
		}
		e.waitFor(executor.submit(this::saveSpool));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		// Only the user-facing settings. Our own RS-profile saves (slot state on every offer update and heartbeat,
		// completed, history) carry a profile, and installId isn't a setting; reacting to those rebuilt the panel
		// and hit the server constantly.
		if (!isSettingChange(e.getGroup(), e.getProfile(), e.getKey()) || panel == null)
		{
			return;
		}
		if (HardcoreWorldToolKitConfig.KEY_CONNECTED.equals(e.getKey()) || HardcoreWorldToolKitConfig.KEY_SHARE.equals(e.getKey()))
		{
			// Send the open offers soon after sharing gets switched on. Tick state belongs to the client thread.
			clientThread.invoke(() -> nextOpenOffersTick = client.getTickCount() + 1);
		}
		OfferMarketWatch watch = offerWatch;
		if (watch != null)
		{
			if (HardcoreWorldToolKitConfig.KEY_CONNECTED.equals(e.getKey()))
			{
				// Drop what was fetched under the old setting; when now connected, compare right away.
				watch.reset();
				watch.refresh(true);
			}
			else if (HardcoreWorldToolKitConfig.KEY_STALE_HOURS.equals(e.getKey()))
			{
				watch.reevaluate();
			}
		}
		if (HardcoreWorldToolKitConfig.KEY_CONNECTED.equals(e.getKey()) && !config.connected())
		{
			clientThread.invoke(this::removeGeText);
		}
		SwingUtilities.invokeLater(() ->
		{
			HardcoreWorldToolKitPanel p = panel;
			if (p != null)
			{
				p.onConfigChanged();
			}
		});
	}

	/** True for a change to one of the settings (not an RS-profile value, the install id or the chat hint flag). */
	static boolean isSettingChange(String group, String profile, String key)
	{
		return HardcoreWorldToolKitConfig.GROUP.equals(group) && profile == null
			&& (HardcoreWorldToolKitConfig.KEY_CONNECTED.equals(key)
			|| HardcoreWorldToolKitConfig.KEY_SHARE.equals(key)
			|| HardcoreWorldToolKitConfig.KEY_LOCAL_LOG.equals(key)
			|| HardcoreWorldToolKitConfig.KEY_AUTO_OPEN.equals(key)
			|| HardcoreWorldToolKitConfig.KEY_SHOW_PROFIT.equals(key)
			|| HardcoreWorldToolKitConfig.KEY_STALE_HOURS.equals(key)
			|| HardcoreWorldToolKitConfig.KEY_OFFER_NOTIFICATION.equals(key));
	}

	/**
	 * Opening a buy/sell offer (or picking an item from the GE search) fetches the item's Hardcore prices for the offer
	 * setup text, and shows the item in the panel if enabled.
	 */
	@Subscribe
	public void onVarbitChanged(VarbitChanged e)
	{
		if (e.getVarpId() != VarPlayerID.TRADINGPOST_SEARCH || !isTrackedWorld())
		{
			return;
		}
		int itemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
		if (itemId <= 0)
		{
			lastGeItem = -1;
			geTextItem = -1;
			return;
		}
		requestGeQuote(itemManager.canonicalize(itemId));
		if (itemId != lastGeItem)
		{
			lastGeItem = itemId;
			if (config.autoOpenFromGe())
			{
				openInPanel(itemId, config.geSwitchPanel());
			}
		}
	}

	/* ------------------------------------------------- GE offer setup text */

	/**
	 * The game is about to set the GE offer setup's description text (examine text, core's GE lines, then the fee, if
	 * any). Adds the Hardcore price line from the cache; runs after core's handler (priority 0) so it never removes or
	 * repeats core's text. Without cached data nothing changes; the fetch it starts re-applies the text when done.
	 */
	@Subscribe(priority = -1)
	public void onScriptCallbackEvent(ScriptCallbackEvent e)
	{
		String name = e.getEventName();
		if (!GE_BUY_EXAMINE.equals(name) && !GE_SELL_EXAMINE.equals(name))
		{
			return;
		}
		if (!config.connected() || !isTrackedWorld())
		{
			geTextItem = -1;
			return;
		}
		int raw = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
		Object[] stack = client.getObjectStack();
		int size = client.getObjectStackSize();
		if (raw <= 0 || size < 3 || !(stack[size - 1] instanceof String))
		{
			return;
		}
		int itemId = itemManager.canonicalize(raw);
		String fee = stack[size - 2] instanceof String ? (String) stack[size - 2] : "";
		geTextItem = itemId;
		geTextFee = fee;
		MarketData.ItemDetail detail = requestGeQuote(itemId);
		if (detail != null)
		{
			stack[size - 1] = GePriceText.insert((String) stack[size - 1], fee, GePriceText.line(detail.getSummary()));
		}
	}

	/** Safety net for interface rebuilds that set the text without the callback. */
	@Subscribe
	public void onScriptPostFired(ScriptPostFired e)
	{
		if (e.getScriptId() == ScriptID.GE_OFFERS_SETUP_BUILD)
		{
			reapplyGeText();
		}
	}

	/**
	 * The cached summary for the GE price line, fetching a newer one in the background (which then re-applies the
	 * text on the client thread). Null when not connected or nothing is cached yet. Client thread.
	 */
	private MarketData.ItemDetail requestGeQuote(int itemId)
	{
		ItemQuoteCache quotes = geQuotes;
		if (quotes == null || !config.connected())
		{
			return null;
		}
		return quotes.request(itemId, System.currentTimeMillis(), () -> clientThread.invoke(this::reapplyGeText));
	}

	/**
	 * Put the price line into the offer setup's description now (data arrived after the script ran). Only when the
	 * setup still shows the same item, and only without a fee line: with one, the script places an info icon by the
	 * last line, so the text waits for the next script run instead. Idempotent. Client thread.
	 */
	private void reapplyGeText()
	{
		ItemQuoteCache quotes = geQuotes;
		if (quotes == null || geTextItem < 0 || !geTextFee.isEmpty() || !config.connected() || !isTrackedWorld())
		{
			return;
		}
		int raw = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
		if (raw <= 0 || itemManager.canonicalize(raw) != geTextItem)
		{
			return;
		}
		Widget desc = client.getWidget(InterfaceID.GeOffers.SETUP_DESC);
		MarketData.ItemDetail detail = quotes.peek(geTextItem, System.currentTimeMillis());
		if (desc == null || desc.isHidden() || detail == null)
		{
			return;
		}
		String text = desc.getText();
		if (text == null || text.isEmpty())
		{
			return;
		}
		String next = GePriceText.insert(text, "", GePriceText.line(detail.getSummary()));
		if (!next.equals(text))
		{
			desc.setText(next);
		}
	}

	/** Remove the price line from an open offer setup (disconnected, plugin stopped). Client thread. */
	private void removeGeText()
	{
		if (geTextFee.isEmpty())
		{
			Widget desc = client.getWidget(InterfaceID.GeOffers.SETUP_DESC);
			String text = desc == null ? null : desc.getText();
			if (text != null && text.contains(GePriceText.MARKER))
			{
				desc.setText(GePriceText.strip(text));
			}
		}
		geTextItem = -1;
	}

	/** What the developer-mode {@code ::hwtk} command drives. */
	private DevCommands.Target devTarget()
	{
		return new DevCommands.Target()
		{
			@Override
			public void freshPreview(boolean on)
			{
				SwingUtilities.invokeLater(() ->
				{
					HardcoreWorldToolKitPanel p = panel;
					if (p != null)
					{
						p.setFreshPreview(on);
					}
				});
			}

			@Override
			public void resetHints()
			{
				HardcoreWorldToolKitPlugin.this.resetHints();
			}
		};
	}

	/**
	 * Developer mode: forget that the one-time chat hint was shown, and show it now so it can be previewed. On a normal
	 * login it only reappears when there is still something to set up (not connected, or GE History not imported).
	 * Client thread.
	 */
	private void resetHints()
	{
		configManager.unsetConfiguration(HardcoreWorldToolKitConfig.GROUP, HardcoreWorldToolKitConfig.KEY_HINT_SHOWN);
		localChat(CHAT_HINT);
		localChat("Dev: chat hint re-armed. It shows again on your next login on a Hardcore World when there is something to set up.");
	}

	/** A line in this client's chatbox only (never sent to the game). */
	private void localChat(String text)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append(text)
				.build())
			.build());
	}

	/** Adds "HC prices" to the right-click menu of tradeable items, unless turned off in the settings. */
	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded e)
	{
		MenuEntry entry = e.getMenuEntry();
		if (!"Examine".equals(e.getOption()) || entry.getItemId() <= 0 || !isTrackedWorld() || !config.itemMenuOption())
		{
			return;
		}
		int itemId = itemManager.canonicalize(entry.getItemId());
		ItemComposition comp = itemManager.getItemComposition(itemId);
		if (!comp.isTradeable())
		{
			return;
		}
		client.getMenu().createMenuEntry(1)
			.setOption(MENU_OPTION)
			.setTarget(e.getTarget())
			.setType(MenuAction.RUNELITE)
			.onClick(me -> openInPanel(itemId, true));
	}

	/**
	 * Show an item in the side panel. Must be called on the client thread.
	 *
	 * @param switchTo switch the sidebar to this panel. When false the panel still follows (a hidden one loads the item
	 *                 when it is next shown), but whatever panel is open stays on top, e.g. another price plugin's
	 *                 following the same GE offer
	 */
	private void openInPanel(int itemId, boolean switchTo)
	{
		int id = itemManager.canonicalize(itemId);
		String name = itemManager.getItemComposition(id).getName();
		SwingUtilities.invokeLater(() ->
		{
			HardcoreWorldToolKitPanel p = panel;
			NavigationButton nav = navButton;
			if (p == null || nav == null)
			{
				return;
			}
			p.openItem(id, name);
			if (switchTo)
			{
				clientToolbar.openPanel(nav);
			}
		});
	}

	@Subscribe
	public void onGrandExchangeOfferChanged(GrandExchangeOfferChanged e)
	{
		GrandExchangeOffer offer = e.getOffer();
		// The client sends a burst of EMPTY offers before login completes; they don't mean anything.
		if (offer.getState() == GrandExchangeOfferState.EMPTY && client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		OfferTracker t = tracker;
		if (!isTrackedWorld() || !ensureProfileLoaded(t))
		{
			return;
		}

		boolean late = client.getGameState() != GameState.LOGGED_IN
			|| client.getTickCount() <= lastLoginTick + LOGIN_BURST_TICKS;
		int slot = e.getSlot();
		List<TradeEvent> events = t.update(slot, OfferTracker.Snapshot.of(offer), late, now(), HARDCORE_ECONOMY);

		store.saveSlot(t, slot);
		if (!events.isEmpty())
		{
			store.saveCompleted(t);
			record(events);
		}
		publishOffers(t);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e)
	{
		if (e.getGroupId() == InterfaceID.GE_HISTORY)
		{
			log.debug("GE history interface opened (tracked world: {})", isTrackedWorld());
			if (isTrackedWorld())
			{
				historyReadTicks = HISTORY_READ_TICKS;
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		if (loginCheckPending)
		{
			loginCheckPending = false;
			afterLogin();
		}

		if (historyReadTicks > 0)
		{
			historyReadTicks--;
			List<GeHistory.Entry> entries = readHistoryWidget();
			if (entries != null && !entries.isEmpty())
			{
				historyReadTicks = 0;
				processHistory(entries, tracker);
			}
			else if (historyReadTicks == 0)
			{
				if (entries != null)
				{
					// Still shown with no rows after the whole wait: this account has no GE History.
					markEmptyHistoryImported(tracker);
				}
				else
				{
					log.debug("GE history: gave up, no rows parsed");
				}
			}
		}

		OfferTracker t = tracker;
		if (client.getTickCount() % SEEN_INTERVAL_TICKS == 0 && client.getTickCount() > lastLoginTick + LOGIN_BURST_TICKS
			&& isTrackedWorld() && ensureProfileLoaded(t))
		{
			// Offers we're watching live haven't changed as of now. If one fills after we log out, the server knows
			// it happened after this point when pairing it with the counterparty's report.
			for (int slot : t.markSeen(now()))
			{
				store.saveSlot(t, slot);
			}
		}

		if (nextOpenOffersTick >= 0 && client.getTickCount() >= nextOpenOffersTick)
		{
			nextOpenOffersTick = client.getTickCount() + OPEN_OFFERS_INTERVAL_TICKS;
			TradeUploader up = uploader;
			if (up != null && isTrackedWorld() && ensureProfileLoaded(t) && shareEnabled())
			{
				up.setOpenOffers(HARDCORE_ECONOMY, accountId(), buildOpenOffers(t, up));
			}
		}
	}

	/**
	 * First tick after a login (or after starting while logged in): tell the panel whether this account's GE History
	 * was imported, and send the one-time chat hint. Client thread.
	 */
	private void afterLogin()
	{
		if (!isTrackedWorld() || !ensureProfileLoaded(tracker))
		{
			announceAccount(null);
			return;
		}
		// For checking what a Hardcore World looks like to RuneLite: the world flags and the profile type.
		log.debug("Logged in on world {}: types {}, RuneLite profile type {}", client.getWorld(), client.getWorldType(),
			RuneScapeProfileType.getCurrent(client));
		Boolean hintShown = configManager.getConfiguration(HardcoreWorldToolKitConfig.GROUP,
			HardcoreWorldToolKitConfig.KEY_HINT_SHOWN, Boolean.class);
		if (Boolean.TRUE.equals(hintShown))
		{
			return;
		}
		if (Onboarding.shouldShowChatHint(config.connected(), store.historyImported()))
		{
			// Local only: shown in this client's chatbox, never sent to the game.
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(new ChatMessageBuilder()
					.append(ChatColorType.HIGHLIGHT)
					.append(CHAT_HINT)
					.build())
				.build());
		}
		// Once per install, whether or not it was needed.
		configManager.setConfiguration(HardcoreWorldToolKitConfig.GROUP, HardcoreWorldToolKitConfig.KEY_HINT_SHOWN, true);
	}

	/**
	 * Send the panel the import state of the account whose profile is loaded, or null when logged out / off the Hardcore
	 * Worlds. Client thread.
	 */
	private void announceAccount(String profile)
	{
		if (profile == null)
		{
			publishOffers(null);
		}
		Boolean imported = profile == null ? null : store.historyImported();
		announcedProfile = profile;
		ProfitStore ps = profit;
		String acct = profile == null ? null : accountId();
		if (ps != null && acct != null)
		{
			// Switch to this account's profit file (one small read) and show its numbers.
			executor.execute(() -> ps.load(acct));
		}
		SwingUtilities.invokeLater(() ->
		{
			HardcoreWorldToolKitPanel p = panel;
			if (p != null)
			{
				p.setAccountState(imported);
			}
		});
	}

	/** The hashed id of the logged-in account ({@link AccountId}), or null when unknown. Client thread. */
	private String accountId()
	{
		long hash = client.getAccountHash();
		if (hash == -1)
		{
			return null;
		}
		if (hash != cachedAccountHash || cachedAccountId == null)
		{
			cachedAccountHash = hash;
			cachedAccountId = AccountId.of(hash);
		}
		return cachedAccountId;
	}

	/* ------------------------------------------------------- GE history */

	/**
	 * @return the parsed rows; an empty list when the list is shown but has no rows at all; null when the list is
	 * missing or hidden, or has rows the parser couldn't read (neither may be taken as an empty history)
	 */
	private List<GeHistory.Entry> readHistoryWidget()
	{
		Widget list = client.getWidget(InterfaceID.GeHistory.LIST);
		if (list == null || list.isHidden())
		{
			log.debug("GE history list widget {}", list == null ? "missing" : "hidden");
			return null;
		}
		Widget[] children = list.getDynamicChildren();
		if (children == null || children.length == 0)
		{
			Widget[] statics = list.getStaticChildren();
			Widget[] nested = list.getNestedChildren();
			log.debug("GE history list has no dynamic children (static: {}, nested: {})",
				statics == null ? 0 : statics.length, nested == null ? 0 : nested.length);
			return Collections.emptyList();
		}
		List<GeHistory.Cell> cells = new ArrayList<>(children.length);
		for (Widget w : children)
		{
			int itemId = w.getItemId() > 0 ? itemManager.canonicalize(w.getItemId()) : -1;
			cells.add(new GeHistory.Cell(w.getText(), itemId, w.getItemQuantity()));
		}
		List<GeHistory.Entry> entries = GeHistory.parse(cells);
		log.debug("Read {} GE history entries from {} widgets", entries.size(), children.length);
		if (entries.isEmpty())
		{
			if (log.isDebugEnabled())
			{
				// Dump the layout so the parser can be fixed if the interface differs from what we expect.
				for (int i = 0; i < Math.min(children.length, 36); i++)
				{
					Widget w = children[i];
					log.debug("  history[{}] type={} item={} qty={} text='{}'", i, w.getType(), w.getItemId(), w.getItemQuantity(), w.getText());
				}
			}
			return null;
		}
		return entries;
	}

	/**
	 * The GE History list stayed empty: there is nothing to import, but the account has been through the import, so
	 * the welcome card stops asking for it. Only the first time; a real import later still adds its rows.
	 */
	private void markEmptyHistoryImported(OfferTracker t)
	{
		if (!ensureProfileLoaded(t) || store.historyImported())
		{
			return;
		}
		store.saveHistory(Collections.emptyList());
		log.debug("GE history: empty, marked as imported");
		SwingUtilities.invokeLater(() ->
		{
			HardcoreWorldToolKitPanel p = panel;
			if (p != null)
			{
				p.onHistoryImported(0);
			}
		});
	}

	private void processHistory(List<GeHistory.Entry> entries, OfferTracker t)
	{
		if (!ensureProfileLoaded(t))
		{
			return;
		}
		List<String> current = new ArrayList<>(entries.size());
		entries.forEach(en -> current.add(en.signature()));
		List<String> previous = store.loadHistory();
		boolean wasImported = store.historyImported();
		int fresh = GeHistory.countNew(previous, current);

		long ts = now();
		List<TradeEvent> events = GeHistory.newEvents(entries, fresh, t::consumeCompleted, ts, HARDCORE_ECONOMY,
			() -> "hist:" + UUID.randomUUID());

		store.saveHistory(current);
		store.saveCompleted(t);
		log.debug("GE history: {} rows, {} new, {} not seen live", entries.size(), fresh, events.size());
		record(events);
		if (!wasImported)
		{
			int added = events.size();
			SwingUtilities.invokeLater(() ->
			{
				HardcoreWorldToolKitPanel p = panel;
				if (p != null)
				{
					p.onHistoryImported(added);
				}
			});
		}
	}

	/* ---------------------------------------------------------- output */

	/** Record events just observed (client thread): My trades, upload, local log and profit. */
	private void record(List<TradeEvent> raw)
	{
		if (raw.isEmpty())
		{
			return;
		}
		List<TradeEvent> events = new ArrayList<>(raw.size());
		for (TradeEvent ev : raw)
		{
			events.add(withName(ev));
		}
		myTrades.addNewest(events);
		notifyMyTradesChanged();
		persist(events, accountId());
	}

	/**
	 * Upload (when connected and sharing), append to the local log (when on; each event to the file of its own month)
	 * and count for profit, in that order on the executor. Client thread.
	 */
	private void persist(List<TradeEvent> events, String acct)
	{
		TradeUploader up = uploader;
		if (up != null && shareEnabled())
		{
			boolean fill = false;
			for (TradeEvent ev : events)
			{
				up.add(ev, acct);
				queueItemInfo(up, ev.getItemId());
				fill |= TradeEvent.FILL.equals(ev.getKind());
			}
			if (fill)
			{
				requestFlushSoon();
			}
		}
		Map<YearMonth, List<String>> lines = null;
		if (config.saveLocalLog())
		{
			// Live events of one update share a month.
			lines = new TreeMap<>();
			for (TradeEvent ev : events)
			{
				lines.computeIfAbsent(LocalTradeLog.monthOf(ev.getTs()), k -> new ArrayList<>())
					.add(gson.toJson(logLine(gson, ev, acct)));
			}
		}
		ProfitStore ps = profit;
		Map<YearMonth, List<String>> logLines = lines;
		executor.execute(() -> appendAndApply(ps, logLines, acct, events));
	}

	/**
	 * Executor: append the events to the local log (if it's on), then count them for profit (even when it's off). In
	 * one task, in order, so a profit rebuild reading the log never misses or double counts an event.
	 */
	private void appendAndApply(ProfitStore ps, Map<YearMonth, List<String>> lines, String acct, List<TradeEvent> events)
	{
		boolean logged = false;
		if (lines != null)
		{
			for (Map.Entry<YearMonth, List<String>> e : lines.entrySet())
			{
				boolean ok = appendLocalLog(e.getKey(), e.getValue());
				logged |= ok;
			}
		}
		if (ps == null)
		{
			return;
		}
		try
		{
			ps.apply(acct, events, logged);
		}
		catch (RuntimeException ex)
		{
			log.warn("Couldn't count profit", ex);
		}
	}

	/** Profit numbers changed (executor). */
	private void onProfit(ProfitSnapshot snapshot)
	{
		SwingUtilities.invokeLater(() ->
		{
			HardcoreWorldToolKitPanel p = panel;
			if (p != null)
			{
				p.onProfit(snapshot);
			}
		});
	}

	/** The panel's Rebuild button (EDT): recount profit from the local log in the background. */
	private void rebuildProfit()
	{
		ProfitStore ps = profit;
		if (ps != null)
		{
			executor.execute(ps::rebuild);
		}
	}

	/**
	 * One local log line: the event plus "acct", the hashed account id (when known), so per-account numbers can be
	 * rebuilt from the log. Reading a line back as a TradeEvent ignores "acct".
	 */
	static JsonObject logLine(Gson gson, TradeEvent ev, String accountId)
	{
		JsonObject o = gson.toJsonTree(ev).getAsJsonObject();
		if (accountId != null)
		{
			o.addProperty("acct", accountId);
		}
		return o;
	}

	/** Must be called on the client thread. */
	private TradeEvent withName(TradeEvent ev)
	{
		return ev.toBuilder().name(itemManager.getItemComposition(ev.getItemId()).getName()).build();
	}

	/** Older log lines have no item name. Must be called on the client thread. */
	private TradeEvent withNameIfMissing(TradeEvent ev)
	{
		return ev.getName() == null ? withName(ev) : ev;
	}

	private void notifyMyTradesChanged()
	{
		SwingUtilities.invokeLater(() ->
		{
			HardcoreWorldToolKitPanel p = panel;
			if (p != null)
			{
				p.onMyTradesChanged();
			}
		});
	}

	/** Client thread only. Captures the name now; the icon is only referenced and gets PNG-encoded during flush. */
	private void queueItemInfo(TradeUploader up, int itemId)
	{
		if (!up.needsItem(itemId))
		{
			return;
		}
		String name = itemManager.getItemComposition(itemId).getName();
		AsyncBufferedImage img = itemManager.getImage(itemId);
		up.addItem(itemId, name, img, geLimit(itemId));
		img.onLoaded(() -> up.setIcon(itemId, img));
	}

	/**
	 * The item's GE buy limit from RuneLite's item stats (an in-memory lookup, no IO), or 0 when unknown. The server
	 * uses it to reject buys over the 4-hour limit; it only accepts the value from trusted sources. Client thread.
	 */
	private int geLimit(int itemId)
	{
		ItemStats stats = itemManager.getItemStats(itemId);
		return stats != null && stats.getGeLimit() > 0 ? stats.getGeLimit() : 0;
	}

	/**
	 * Load the local trade log so "My trades" survives restarts. Runs off the client thread.
	 *
	 * @param session {@link MyTrades#session()} when the plugin started; if the plugin has been stopped since, the
	 *                result is dropped rather than merged into a later session
	 */
	private void loadLocalLog(int session)
	{
		List<TradeEvent> newest;
		try
		{
			// Only the newest shown events, read from the end of the newest files; the rest of the log isn't parsed.
			newest = LocalTradeLog.readNewest(getPluginDirectory(), gson, MyTrades.MAX);
		}
		catch (IOException ex)
		{
			log.debug("Couldn't read local trade log", ex);
			return;
		}
		if (newest.isEmpty())
		{
			return;
		}

		// Item names are filled in on the client thread. Item definitions only exist once the client has loaded the game
		// cache (login screen or later); at startup this runs earlier, so retry on each tick until then.
		clientThread.invokeLater(() ->
		{
			if (client.getGameState().getState() < GameState.LOGIN_SCREEN.getState())
			{
				return false;
			}
			if (myTrades.mergeLoaded(session, newest, this::withNameIfMissing))
			{
				notifyMyTradesChanged();
			}
			else
			{
				log.debug("Dropped a local trade log load from a previous session");
			}
			return true;
		});
	}

	private static BufferedImage buildIcon()
	{
		return SkullIcon.draw(16, SkullIcon.BONE);
	}

	/** @return true if the lines were written */
	private boolean appendLocalLog(YearMonth month, List<String> lines)
	{
		try
		{
			LocalTradeLog.append(getPluginDirectory(), month, lines);
			return true;
		}
		catch (IOException ex)
		{
			log.warn("Couldn't write local trade log", ex);
			return false;
		}
	}

	/**
	 * Your open offers, sent to the panel when they changed so the item view can show them at once. Client thread.
	 *
	 * @param t the tracker, or null when logged out / not on a Hardcore World (no offers)
	 */
	private void publishOffers(OfferTracker t)
	{
		List<ActiveOffer> offers = t == null ? Collections.emptyList()
			: ActiveOffer.fromTracker(t, id -> itemManager.getItemComposition(id).getName());
		if (offers.equals(publishedOffers))
		{
			return;
		}
		publishedOffers = offers;
		OfferMarketWatch watch = offerWatch;
		if (watch != null)
		{
			watch.setOffers(offers);
		}
		SwingUtilities.invokeLater(() ->
		{
			HardcoreWorldToolKitPanel p = panel;
			if (p != null)
			{
				p.onOffersChanged(offers);
			}
		});
	}

	/** The server accepted trades of these items (OkHttp thread): let the item view fetch the new market data. */
	private void onUploaded(Set<Integer> itemIds)
	{
		SwingUtilities.invokeLater(() ->
		{
			HardcoreWorldToolKitPanel p = panel;
			if (p != null)
			{
				p.onUploaded(itemIds);
			}
		});
	}

	/**
	 * Upload in about {@link #FLUSH_SOON_SECONDS} instead of at the next scheduled flush, so a fill shows up in the
	 * market data quickly. Requests while one is pending are merged into it.
	 */
	private void requestFlushSoon()
	{
		ScheduledFuture<?> pending = soonFlush.get();
		if (pending != null && !pending.isDone())
		{
			return;
		}
		ScheduledFuture<?> next = executor.schedule(() -> flushSoon(1), FLUSH_SOON_SECONDS, TimeUnit.SECONDS);
		if (!soonFlush.compareAndSet(pending, next))
		{
			// Another request scheduled one first.
			next.cancel(false);
		}
	}

	/** The one-shot upload; if a request is still running, try again a little later (a few times at most). */
	private void flushSoon(int attempt)
	{
		if (tracker == null)
		{
			// Stopped meanwhile; shutDown already flushed.
			return;
		}
		TradeUploader up = uploader;
		if (up != null && up.isInFlight() && attempt < FLUSH_SOON_TRIES)
		{
			soonFlush.set(executor.schedule(() -> flushSoon(attempt + 1), FLUSH_SOON_SECONDS, TimeUnit.SECONDS));
			return;
		}
		// Clear first, so a fill recorded during this upload schedules its own.
		soonFlush.set(null);
		scheduledFlush();
	}

	/** Whether the side panel is open; any thread. */
	private boolean panelShown()
	{
		HardcoreWorldToolKitPanel p = panel;
		return p != null && p.isPanelActive();
	}

	/** The periodic offer check (executor). Only starts a request when due; catches everything to keep the task alive. */
	private void checkOffers()
	{
		try
		{
			OfferMarketWatch w = offerWatch;
			if (w != null)
			{
				w.refresh(false);
			}
		}
		catch (RuntimeException ex)
		{
			warnOnce("Offer check failed", ex);
		}
	}

	/** Failures of the repeating tasks already logged as a warning; the next ones go to debug. */
	private final Set<String> warned = ConcurrentHashMap.newKeySet();

	/** A scheduled task failed: a warning the first time, debug after that (it runs every few seconds). */
	private void warnOnce(String what, RuntimeException ex)
	{
		if (warned.add(what))
		{
			log.warn(what, ex);
		}
		else
		{
			log.debug("{}: {}", what, ex.toString());
		}
	}

	/** The scheduled upload. Catches everything: an exception would cancel the fixed-delay task for the session. */
	private void scheduledFlush()
	{
		try
		{
			flush();
		}
		catch (RuntimeException ex)
		{
			warnOnce("Trade upload flush failed", ex);
		}
		saveSpool();
	}

	/** Write the uploads still waiting to the spool file (or remove it). Executor thread. */
	private void saveSpool()
	{
		UploadSpool sp = spool;
		TradeUploader up = uploader;
		if (sp != null && up != null)
		{
			try
			{
				sp.save(up.pending());
			}
			catch (RuntimeException ex)
			{
				warnOnce("Couldn't save pending uploads", ex);
			}
		}
	}

	/**
	 * Enabled while logged in on a Hardcore World: the game sends no offer events for slots that haven't changed, so compare
	 * all 8 with the saved state now. Whatever differs happened while the plugin wasn't watching, so it is recorded as
	 * late (never as a live trade at this moment). Slots the game shows as empty are cleared. Client thread.
	 */
	private void reconcileSlots()
	{
		OfferTracker t = tracker;
		if (!isTrackedWorld() || !ensureProfileLoaded(t))
		{
			return;
		}
		GrandExchangeOffer[] offers = client.getGrandExchangeOffers();
		if (offers == null)
		{
			return;
		}
		long ts = now();
		for (int slot = 0; slot < offers.length && slot < OfferTracker.SLOTS; slot++)
		{
			GrandExchangeOffer offer = offers[slot];
			if (offer == null)
			{
				continue;
			}
			List<TradeEvent> events = t.update(slot, OfferTracker.Snapshot.of(offer), true, ts, HARDCORE_ECONOMY);
			store.saveSlot(t, slot);
			if (!events.isEmpty())
			{
				store.saveCompleted(t);
				record(events);
			}
		}
		publishOffers(t);
	}

	private void flush()
	{
		TradeUploader up = uploader;
		if (up == null)
		{
			return;
		}
		if (!shareEnabled())
		{
			up.clear();
			return;
		}
		up.flush();
	}

	/** Your offers that are still open, serialized now since the slot state keeps changing. Client thread only. */
	private List<JsonObject> buildOpenOffers(OfferTracker t, TradeUploader up)
	{
		List<JsonObject> offers = new ArrayList<>();
		for (int slot = 0; slot < OfferTracker.SLOTS; slot++)
		{
			OfferTracker.SlotState s = t.getSlot(slot);
			if (s == null || s.getStatus() != OfferTracker.Status.ACTIVE || s.getSold() >= s.getTotalQty())
			{
				continue;
			}
			offers.add(TradeUploader.offerJson(slot, s));
			queueItemInfo(up, s.getItemId());
		}
		return offers;
	}

	/* ---------------------------------------------------- persistence */

	/**
	 * Load slot state for the current account's RuneLite profile.
	 *
	 * @param t the tracker, read once by the caller (null after shutDown)
	 */
	private boolean ensureProfileLoaded(OfferTracker t)
	{
		if (t == null || !store.ensureLoaded(t))
		{
			return false;
		}
		String profile = store.loadedProfile();
		if (profile != null && !profile.equals(announcedProfile))
		{
			announceAccount(profile);
			publishOffers(t);
		}
		return true;
	}

	/* ---------------------------------------------------------- helpers */

	/** Only the Hardcore Worlds, which the world list marks with {@link WorldType#HARDCORE_MAIN}. */
	private boolean isTrackedWorld()
	{
		return devAnyWorld || client.getWorldType().contains(WorldType.HARDCORE_MAIN);
	}

	private static long now()
	{
		return Instant.now().getEpochSecond();
	}
}
