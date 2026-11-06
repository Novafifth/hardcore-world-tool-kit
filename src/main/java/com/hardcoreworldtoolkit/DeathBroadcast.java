package com.hardcoreworldtoolkit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import net.runelite.client.util.Text;

/** A Hardcore death broadcast: its text (read by the server) and the victim (to spot the player's own death). */
@Value
class DeathBroadcast
{
	private static final Pattern KILLED = Pattern.compile(".+? killed (.+?)\\(\\d+\\) with an? \\d+ damage hit in .+", Pattern.CASE_INSENSITIVE);
	private static final Pattern DIED = Pattern.compile("(.+?)\\(\\d+\\) was killed by an? \\d+ damage hit in .+", Pattern.CASE_INSENSITIVE);

	String text;
	String victim;

	static DeathBroadcast parse(String message)
	{
		String m = Text.removeTags(message).replace('\u00a0', ' ').trim();
		Matcher k = KILLED.matcher(m);
		Matcher d = DIED.matcher(m);
		return k.matches() ? new DeathBroadcast(m, k.group(1)) : d.matches() ? new DeathBroadcast(m, d.group(1)) : null;
	}
}
