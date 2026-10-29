package com.hardcoregetracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;

@ConfigGroup(HardcoreGeTrackerConfig.GROUP)
public interface HardcoreGeTrackerConfig extends Config
{
	String GROUP = "hardcore-ge-tracker";
	String KEY_CONNECTED = "connected";
	String KEY_SHARE = "shareTrades";
	String KEY_LOCAL_LOG = "saveLocalLog";
	String KEY_AUTO_OPEN = "autoOpenFromGe";
	String KEY_GE_SWITCH = "geSwitchPanel";
	String KEY_ITEM_MENU = "itemMenuOption";
	String KEY_SHOW_PROFIT = "showProfit";
	String KEY_STALE_HOURS = "staleHours";
	String KEY_OFFER_NOTIFICATION = "offerNotification";
	/** Not a setting: set once the one-time chat hint has been considered for this install. */
	String KEY_HINT_SHOWN = "chatHintShown";

	String THIRD_PARTY_WARNING = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers";

	@ConfigSection(
		name = "Data sharing",
		description = "Connecting to the Hardcore GETracker server and sharing your trades",
		position = 0
	)
	String SECTION_SHARING = "sharing";

	@ConfigSection(
		name = "Panel",
		description = "Side panel behaviour",
		position = 1
	)
	String SECTION_PANEL = "panel";

	@ConfigSection(
		name = "Offer alerts",
		description = "How your open Hardcore World offers are compared with the market",
		position = 2
	)
	String SECTION_ALERTS = "alerts";

	@ConfigSection(
		name = "Local",
		description = "Data kept on this computer",
		position = 3
	)
	String SECTION_LOCAL = "local";

	/**
	 * Opt-in to contacting the Hardcore GETracker server at all (viewing market data and sharing trades).
	 */
	@ConfigItem(
		keyName = KEY_CONNECTED,
		name = "Connect to Hardcore GETracker server",
		description = "Show Hardcore World market data from all plugin users and share your trades. Shared: item, price,"
			+ " quantity, time, a hashed account ID and a random install ID. Never your name. IP addresses are"
			+ " never stored.",
		warning = THIRD_PARTY_WARNING,
		position = 0,
		section = SECTION_SHARING
	)
	default boolean connected()
	{
		return false;
	}

	@ConfigItem(
		keyName = KEY_SHARE,
		name = "Share my trades",
		description = "While connected, upload your Hardcore World trades (never your name)",
		position = 1,
		section = SECTION_SHARING
	)
	default boolean shareTrades()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_AUTO_OPEN,
		name = "Open item when setting up a GE offer",
		description = "Show the item in the side panel when you set up a buy or sell offer on a Hardcore World",
		position = 0,
		section = SECTION_PANEL
	)
	default boolean autoOpenFromGe()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_GE_SWITCH,
		name = "Switch to this panel for GE offers",
		description = "Off: the item still opens here, but the sidebar stays on the panel you have open",
		position = 1,
		section = SECTION_PANEL
	)
	default boolean geSwitchPanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_ITEM_MENU,
		name = "Right-click \"HC prices\"",
		description = "Add \"HC prices\" to the right-click menu of tradeable items",
		position = 2,
		section = SECTION_PANEL
	)
	default boolean itemMenuOption()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_SHOW_PROFIT,
		name = "Show profit",
		description = "Show your profit (today, 7 days, all time) in the side panel",
		position = 3,
		section = SECTION_PANEL
	)
	default boolean showProfit()
	{
		return false;
	}

	@Range(min = 1, max = 72)
	@ConfigItem(
		keyName = KEY_STALE_HOURS,
		name = "Flag offers with no fill for (hours)",
		description = "Mark an open offer as stale when nothing of it has filled for this many hours",
		position = 0,
		section = SECTION_ALERTS
	)
	default int staleHours()
	{
		return 6;
	}

	@ConfigItem(
		keyName = KEY_OFFER_NOTIFICATION,
		name = "Notify when undercut or outbid",
		description = "While connected, notify when someone sells below your sell offer or bids above your buy offer",
		position = 1,
		section = SECTION_ALERTS
	)
	default Notification offerNotification()
	{
		return Notification.OFF;
	}

	@ConfigItem(
		keyName = KEY_LOCAL_LOG,
		name = "Save local trade log",
		description = "Keep a monthly file of your trades in this plugin's data folder (works without connecting)",
		position = 0,
		section = SECTION_LOCAL
	)
	default boolean saveLocalLog()
	{
		return true;
	}
}
