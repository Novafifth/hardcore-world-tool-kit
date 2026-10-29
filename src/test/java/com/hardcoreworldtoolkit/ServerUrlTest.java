package com.hardcoreworldtoolkit;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ServerUrlTest
{
	@Test
	public void normalInstallsAlwaysUseProduction()
	{
		assertEquals(HardcoreWorldToolKitPlugin.PRODUCTION_SERVER_URL, HardcoreWorldToolKitPlugin.serverUrl(false, null));
		assertEquals(HardcoreWorldToolKitPlugin.PRODUCTION_SERVER_URL, HardcoreWorldToolKitPlugin.serverUrl(false, "http://evil.example"));
	}

	@Test
	public void developerModeUsesLocalServerByDefault()
	{
		assertEquals(HardcoreWorldToolKitPlugin.LOCAL_SERVER_URL, HardcoreWorldToolKitPlugin.serverUrl(true, null));
		assertEquals(HardcoreWorldToolKitPlugin.LOCAL_SERVER_URL, HardcoreWorldToolKitPlugin.serverUrl(true, "  "));
		assertEquals(HardcoreWorldToolKitPlugin.LOCAL_SERVER_URL, HardcoreWorldToolKitPlugin.serverUrl(true, "not a url"));
	}

	@Test
	public void developerModeOverride()
	{
		assertEquals("https://staging.example", HardcoreWorldToolKitPlugin.serverUrl(true, " https://staging.example "));
	}
}
