package com.hardcoreworldtoolkit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.client.util.Text;

/** A Hardcore death broadcast: its text (read by the server) and the victim (to spot the player's own death). */
@Value
class DeathBroadcast
{
	/** The total level: 1000 and up shows as "1K" (or "1.2K"). */
	private static final String LEVEL = "\\((\\d+(?:\\.\\d+)?k?)\\)";
	private static final Pattern KILLED = Pattern.compile(".+? killed (.+?)" + LEVEL + " with an? \\d+ damage hit.*", Pattern.CASE_INSENSITIVE);
	private static final Pattern DIED = Pattern.compile("(.+?)" + LEVEL + " was killed by an? \\d+ damage hit.*", Pattern.CASE_INSENSITIVE);

	String text;
	String victim;

	static DeathBroadcast parse(String message)
	{
		String m = Text.removeTags(message).replace('\u00a0', ' ').trim();
		Matcher k = KILLED.matcher(m);
		Matcher d = DIED.matcher(m);
		if (!k.matches() && !d.matches())
		{
			return null;
		}
		return new DeathBroadcast(m, (k.matches() ? k.group(1) : d.group(1)).replaceAll("@[a-z_]+@", ""));
	}

	int level()
	{
		Matcher m = Pattern.compile(Pattern.quote(victim) + LEVEL, Pattern.CASE_INSENSITIVE).matcher(text);
		if (!m.find())
		{
			return -1;
		}
		String v = m.group(1).toLowerCase();
		return v.endsWith("k") ? (int) (Double.parseDouble(v.substring(0, v.length() - 1)) * 1000) : Integer.parseInt(v);
	}
}
