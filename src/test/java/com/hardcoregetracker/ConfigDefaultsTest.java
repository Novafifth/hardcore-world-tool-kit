package com.hardcoregetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import net.runelite.client.config.Notification;
import org.junit.Test;

/** Defaults the Plugin Hub cares about: connecting to the server is opt-in; everything local is on. */
public class ConfigDefaultsTest
{
	private final HardcoreGeTrackerConfig config = new HardcoreGeTrackerConfig()
	{
	};

	@Test
	public void defaults()
	{
		assertFalse(config.connected());
		assertTrue(config.shareTrades());
		assertTrue(config.saveLocalLog());
		assertTrue(config.autoOpenFromGe());
		assertTrue(config.geSwitchPanel());
		assertTrue(config.itemMenuOption());
		assertFalse(config.showProfit());
		assertEquals(6, config.staleHours());
		assertSame(Notification.OFF, config.offerNotification());
	}

	@Test
	public void keysNeverChange()
	{
		assertEquals("hardcore-ge-tracker", HardcoreGeTrackerConfig.GROUP);
		assertEquals("connected", HardcoreGeTrackerConfig.KEY_CONNECTED);
		assertEquals("shareTrades", HardcoreGeTrackerConfig.KEY_SHARE);
		assertEquals("saveLocalLog", HardcoreGeTrackerConfig.KEY_LOCAL_LOG);
		assertEquals("autoOpenFromGe", HardcoreGeTrackerConfig.KEY_AUTO_OPEN);
		assertEquals("geSwitchPanel", HardcoreGeTrackerConfig.KEY_GE_SWITCH);
		assertEquals("itemMenuOption", HardcoreGeTrackerConfig.KEY_ITEM_MENU);
		assertEquals("showProfit", HardcoreGeTrackerConfig.KEY_SHOW_PROFIT);
		assertEquals("chatHintShown", HardcoreGeTrackerConfig.KEY_HINT_SHOWN);
		assertEquals("staleHours", HardcoreGeTrackerConfig.KEY_STALE_HOURS);
		assertEquals("offerNotification", HardcoreGeTrackerConfig.KEY_OFFER_NOTIFICATION);
		assertEquals("This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
			HardcoreGeTrackerConfig.THIRD_PARTY_WARNING);
	}
}
