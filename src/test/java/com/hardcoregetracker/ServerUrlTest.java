package com.hardcoregetracker;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ServerUrlTest
{
	@Test
	public void normalInstallsAlwaysUseProduction()
	{
		assertEquals(HardcoreGeTrackerPlugin.PRODUCTION_SERVER_URL, HardcoreGeTrackerPlugin.serverUrl(false, null));
		assertEquals(HardcoreGeTrackerPlugin.PRODUCTION_SERVER_URL, HardcoreGeTrackerPlugin.serverUrl(false, "http://evil.example"));
	}

	@Test
	public void developerModeUsesLocalServerByDefault()
	{
		assertEquals(HardcoreGeTrackerPlugin.LOCAL_SERVER_URL, HardcoreGeTrackerPlugin.serverUrl(true, null));
		assertEquals(HardcoreGeTrackerPlugin.LOCAL_SERVER_URL, HardcoreGeTrackerPlugin.serverUrl(true, "  "));
		assertEquals(HardcoreGeTrackerPlugin.LOCAL_SERVER_URL, HardcoreGeTrackerPlugin.serverUrl(true, "not a url"));
	}

	@Test
	public void developerModeOverride()
	{
		assertEquals("https://staging.example", HardcoreGeTrackerPlugin.serverUrl(true, " https://staging.example "));
	}
}
