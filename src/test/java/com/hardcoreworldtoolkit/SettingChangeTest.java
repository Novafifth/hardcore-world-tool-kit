package com.hardcoreworldtoolkit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Which ConfigChanged events the plugin reacts to: only the settings, never its own profile saves. */
public class SettingChangeTest
{
	private static final String G = HardcoreWorldToolKitConfig.GROUP;
	private static final String PROFILE = "rsprofile.abc";

	@Test
	public void settingsCount()
	{
		assertTrue(HardcoreWorldToolKitPlugin.isSettingChange(G, null, HardcoreWorldToolKitConfig.KEY_CONNECTED));
		assertTrue(HardcoreWorldToolKitPlugin.isSettingChange(G, null, HardcoreWorldToolKitConfig.KEY_SHARE));
		assertTrue(HardcoreWorldToolKitPlugin.isSettingChange(G, null, HardcoreWorldToolKitConfig.KEY_LOCAL_LOG));
		assertTrue(HardcoreWorldToolKitPlugin.isSettingChange(G, null, HardcoreWorldToolKitConfig.KEY_AUTO_OPEN));
		assertTrue(HardcoreWorldToolKitPlugin.isSettingChange(G, null, HardcoreWorldToolKitConfig.KEY_SHOW_PROFIT));
		assertTrue(HardcoreWorldToolKitPlugin.isSettingChange(G, null, HardcoreWorldToolKitConfig.KEY_STALE_HOURS));
		assertTrue(HardcoreWorldToolKitPlugin.isSettingChange(G, null, HardcoreWorldToolKitConfig.KEY_OFFER_NOTIFICATION));
	}

	@Test
	public void profileSavesInstallIdAndHintFlagDont()
	{
		assertFalse(HardcoreWorldToolKitPlugin.isSettingChange(G, PROFILE, "slot0"));
		assertFalse(HardcoreWorldToolKitPlugin.isSettingChange(G, PROFILE, "completed"));
		assertFalse(HardcoreWorldToolKitPlugin.isSettingChange(G, PROFILE, "history"));
		assertFalse(HardcoreWorldToolKitPlugin.isSettingChange(G, PROFILE, HardcoreWorldToolKitConfig.KEY_CONNECTED));
		assertFalse(HardcoreWorldToolKitPlugin.isSettingChange(G, null, "installId"));
		assertFalse(HardcoreWorldToolKitPlugin.isSettingChange(G, null, InstallIdentity.KEY_INSTALL_TOKEN));
		assertFalse(HardcoreWorldToolKitPlugin.isSettingChange(G, null, HardcoreWorldToolKitConfig.KEY_HINT_SHOWN));
		assertFalse(HardcoreWorldToolKitPlugin.isSettingChange("other-plugin", null, HardcoreWorldToolKitConfig.KEY_CONNECTED));
	}
}
