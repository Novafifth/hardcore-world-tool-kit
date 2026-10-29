package com.hardcoreworldtoolkit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import net.runelite.client.config.Notification;
import org.junit.Test;

/** Defaults the Plugin Hub cares about: connecting to the server is opt-in; everything local is on. */
public class ConfigDefaultsTest
{
	private final HardcoreWorldToolKitConfig config = new HardcoreWorldToolKitConfig()
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
		assertEquals("hardcore-world-tool-kit", HardcoreWorldToolKitConfig.GROUP);
		assertEquals("connected", HardcoreWorldToolKitConfig.KEY_CONNECTED);
		assertEquals("shareTrades", HardcoreWorldToolKitConfig.KEY_SHARE);
		assertEquals("saveLocalLog", HardcoreWorldToolKitConfig.KEY_LOCAL_LOG);
		assertEquals("autoOpenFromGe", HardcoreWorldToolKitConfig.KEY_AUTO_OPEN);
		assertEquals("geSwitchPanel", HardcoreWorldToolKitConfig.KEY_GE_SWITCH);
		assertEquals("itemMenuOption", HardcoreWorldToolKitConfig.KEY_ITEM_MENU);
		assertEquals("showProfit", HardcoreWorldToolKitConfig.KEY_SHOW_PROFIT);
		assertEquals("chatHintShown", HardcoreWorldToolKitConfig.KEY_HINT_SHOWN);
		assertEquals("staleHours", HardcoreWorldToolKitConfig.KEY_STALE_HOURS);
		assertEquals("offerNotification", HardcoreWorldToolKitConfig.KEY_OFFER_NOTIFICATION);
		assertEquals("This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
			HardcoreWorldToolKitConfig.THIRD_PARTY_WARNING);
	}
}
