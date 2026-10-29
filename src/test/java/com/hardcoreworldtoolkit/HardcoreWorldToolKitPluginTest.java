package com.hardcoreworldtoolkit;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class HardcoreWorldToolKitPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(HardcoreWorldToolKitPlugin.class);
		RuneLite.main(args);
	}
}
