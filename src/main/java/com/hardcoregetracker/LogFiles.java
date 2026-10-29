package com.hardcoregetracker;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which files in the plugin folder make up the local trade log, and in what order. Pure.
 * <p>
 * The log is one file per month, {@code trades-YYYY-MM.jsonl}.
 */
final class LogFiles
{
	private static final Pattern MONTHLY = Pattern.compile("^trades-([0-9]{4})-([0-9]{2})[.]jsonl$");

	private LogFiles()
	{
	}

	static boolean isMonthly(String name)
	{
		return name != null && MONTHLY.matcher(name).matches();
	}

	/** The month of a monthly log file's name, or null for any other name. */
	static YearMonth monthOf(String name)
	{
		Matcher m = name == null ? null : MONTHLY.matcher(name);
		if (m == null || !m.matches())
		{
			return null;
		}
		int month = Integer.parseInt(m.group(2));
		return month >= 1 && month <= 12 ? YearMonth.of(Integer.parseInt(m.group(1)), month) : null;
	}

	/** Monthly log files, newest month first. Other names are ignored. */
	static List<String> newestFirst(List<String> names)
	{
		List<String> monthly = new ArrayList<>();
		for (String n : names)
		{
			if (isMonthly(n))
			{
				monthly.add(n);
			}
		}
		// Zero-padded year and month, so name order is time order.
		monthly.sort(Collections.reverseOrder());
		return monthly;
	}

	/** The reverse of {@link #newestFirst}: each month oldest first. */
	static List<String> oldestFirst(List<String> names)
	{
		List<String> list = newestFirst(names);
		Collections.reverse(list);
		return list;
	}
}
