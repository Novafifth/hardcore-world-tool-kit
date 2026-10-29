package com.hardcoregetracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class HardcoreGeTrackerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(HardcoreGeTrackerPlugin.class);
		RuneLite.main(args);
	}
}
