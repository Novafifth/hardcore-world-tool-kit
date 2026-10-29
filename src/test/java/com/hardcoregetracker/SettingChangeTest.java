package com.hardcoregetracker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Which ConfigChanged events the plugin reacts to: only the settings, never its own profile saves. */
public class SettingChangeTest
{
	private static final String G = HardcoreGeTrackerConfig.GROUP;
	private static final String PROFILE = "rsprofile.abc";

	@Test
	public void settingsCount()
	{
		assertTrue(HardcoreGeTrackerPlugin.isSettingChange(G, null, HardcoreGeTrackerConfig.KEY_CONNECTED));
		assertTrue(HardcoreGeTrackerPlugin.isSettingChange(G, null, HardcoreGeTrackerConfig.KEY_SHARE));
		assertTrue(HardcoreGeTrackerPlugin.isSettingChange(G, null, HardcoreGeTrackerConfig.KEY_LOCAL_LOG));
		assertTrue(HardcoreGeTrackerPlugin.isSettingChange(G, null, HardcoreGeTrackerConfig.KEY_AUTO_OPEN));
		assertTrue(HardcoreGeTrackerPlugin.isSettingChange(G, null, HardcoreGeTrackerConfig.KEY_SHOW_PROFIT));
		assertTrue(HardcoreGeTrackerPlugin.isSettingChange(G, null, HardcoreGeTrackerConfig.KEY_STALE_HOURS));
		assertTrue(HardcoreGeTrackerPlugin.isSettingChange(G, null, HardcoreGeTrackerConfig.KEY_OFFER_NOTIFICATION));
	}

	@Test
	public void profileSavesInstallIdAndHintFlagDont()
	{
		assertFalse(HardcoreGeTrackerPlugin.isSettingChange(G, PROFILE, "slot0"));
		assertFalse(HardcoreGeTrackerPlugin.isSettingChange(G, PROFILE, "completed"));
		assertFalse(HardcoreGeTrackerPlugin.isSettingChange(G, PROFILE, "history"));
		assertFalse(HardcoreGeTrackerPlugin.isSettingChange(G, PROFILE, HardcoreGeTrackerConfig.KEY_CONNECTED));
		assertFalse(HardcoreGeTrackerPlugin.isSettingChange(G, null, "installId"));
		assertFalse(HardcoreGeTrackerPlugin.isSettingChange(G, null, InstallIdentity.KEY_INSTALL_TOKEN));
		assertFalse(HardcoreGeTrackerPlugin.isSettingChange(G, null, HardcoreGeTrackerConfig.KEY_HINT_SHOWN));
		assertFalse(HardcoreGeTrackerPlugin.isSettingChange("other-plugin", null, HardcoreGeTrackerConfig.KEY_CONNECTED));
	}
}
