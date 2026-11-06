package com.hardcoreworldtoolkit;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.client.util.Text;

/**
 * A Hardcore death broadcast. The victim is only compared with the local player's name and never sent; in the
 * Wilderness the killer may be a player, so it is dropped. The key (to merge reports of one death) leaves out the name.
 */
@Value
class DeathBroadcast
{
	private static final Pattern KILLED = Pattern.compile("(.+?) killed (.+?)\\((\\d+)\\) with an? (\\d+) damage hit in (?:the )?(.+?)\\.?", Pattern.CASE_INSENSITIVE);
	private static final Pattern DIED = Pattern.compile("(.+?)\\((\\d+)\\) was killed by an? (\\d+) damage hit in (?:the )?(.+?)\\.?", Pattern.CASE_INSENSITIVE);

	String victim;
	String killer;
	String region;
	String key;

	static DeathBroadcast parse(String message)
	{
		String m = Text.removeTags(message).replace('\u00a0', ' ').trim();
		Matcher k = KILLED.matcher(m);
		if (k.matches())
		{
			String region = k.group(5);
			String killer = region.toLowerCase().contains("wilderness") ? null : k.group(1);
			return new DeathBroadcast(k.group(2), killer, region, key(killer == null ? "" : killer, k.group(3), k.group(4), region));
		}
		Matcher d = DIED.matcher(m);
		return d.matches() ? new DeathBroadcast(d.group(1), null, d.group(4), key("", d.group(2), d.group(3), d.group(4))) : null;
	}

	private static String key(String killer, String level, String damage, String region)
	{
		return Hashing.sha256().hashString(killer + "|" + level + "|" + damage + "|" + region, StandardCharsets.UTF_8).toString();
	}
}
