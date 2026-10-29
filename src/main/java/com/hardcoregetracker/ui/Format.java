package com.hardcoregetracker.ui;

import com.hardcoregetracker.TradeEvent;
import java.time.Instant;
import java.util.Locale;
import net.runelite.client.util.QuantityFormatter;

/**
 * Display text for trades, prices and times.
 */
final class Format
{
	/** Shown instead of a time for trades read from the GE History tab, which has no timestamps. */
	static final String HISTORY_MARK = "history";

	private Format()
	{
	}

	static String describe(String kind, String side, int qty, long price)
	{
		String q = QuantityFormatter.formatNumber(qty), p = QuantityFormatter.formatNumber(price);
		boolean buy = TradeEvent.BUY.equals(side);
		String verb = buy ? "Bought" : "Sold";
		switch (kind)
		{
			case TradeEvent.PLACED:
				return (buy ? "Buy" : "Sell") + " offer " + q + " × " + p;
			case TradeEvent.CANCELLED:
				return "Cancelled " + (buy ? "buy" : "sell") + ", " + q + " left";
			default:
				return verb + " " + q + " × " + p;
		}
	}

	/** One trade both sides of which were reported (two plugin users traded with each other). */
	static String traded(int qty, long price)
	{
		return "Traded " + QuantityFormatter.formatNumber(qty) + " × " + QuantityFormatter.formatNumber(price);
	}

	/** Exact below 10m gp, stack format (e.g. 12.5M) above. "-" when unknown. */
	static String gp(Long v)
	{
		if (v == null)
		{
			return "-";
		}
		return v < 10_000_000 ? QuantityFormatter.formatNumber(v) : QuantityFormatter.quantityToStackSize(v);
	}

	/** A compact gp amount with its sign, e.g. "+1.25M", "-350K", "0". */
	static String signedGp(long v)
	{
		if (v == 0)
		{
			return "0";
		}
		if (v == Long.MIN_VALUE)
		{
			v++;
		}
		return (v > 0 ? "+" : "-") + QuantityFormatter.quantityToStackSize(Math.abs(v));
	}

	/** A compact unsigned gp amount, e.g. "1.25M", "950K", "1,500". */
	static String shortGp(long v)
	{
		return QuantityFormatter.quantityToStackSize(v);
	}

	/** How long ago {@code ts} (unix s) was, e.g. "now", "5m", "3h", "2d". Empty when unknown. */
	static String ago(Long ts)
	{
		return ago(ts, Instant.now().getEpochSecond());
	}

	static String ago(Long ts, long now)
	{
		if (ts == null || ts == 0)
		{
			return "";
		}
		long s = Math.max(0, now - ts);
		if (s < 60)
		{
			return "now";
		}
		if (s < 3600)
		{
			return (s / 60) + "m";
		}
		if (s < 86400)
		{
			return (s / 3600) + "h";
		}
		return (s / 86400) + "d";
	}

	/** The time column for a trade: {@link #HISTORY_MARK} for GE History rows, otherwise {@link #ago}. */
	static String when(String kind, Long ts)
	{
		return TradeEvent.HISTORY.equals(kind) ? HISTORY_MARK : ago(ts);
	}

	private static final long[] UNITS = {1_000_000_000L, 1_000_000L, 1_000L};
	private static final String[] UNIT_SUFFIX = {"B", "M", "K"};

	/**
	 * A compact gp amount for chart axes: the value's own unit (K/M/B) with as many decimals (up to 3, 2 for K) as
	 * {@code step} needs, so labels a step apart never look the same (e.g. 1.25M and 1.30M; 2.1B; 950K). When that
	 * isn't enough (nearly flat data at a high price) it is the exact number with separators, never a smaller unit
	 * (which read as "1000.0K" or "2,100,002K").
	 *
	 * @param step the difference between neighbouring labels (the tick step)
	 */
	static String compactGp(long v, long step)
	{
		long s = Math.max(1, Math.abs(step));
		long a = Math.abs(v);
		for (int i = 0; i < UNITS.length; i++)
		{
			long unit = UNITS[i];
			if (a < unit)
			{
				continue;
			}
			// Decimals needed so one step is visible: the largest power of ten (unit / 10^d) no bigger than the step.
			int d = 0;
			long res = unit;
			while (res > s && d < 4)
			{
				res /= 10;
				d++;
			}
			// K never needs 3 decimals: "1,002" is as short as "1.002K".
			if (d > (unit == 1_000L ? 2 : 3))
			{
				break;
			}
			String num = d == 0 ? QuantityFormatter.formatNumber(Math.round((double) a / unit))
				: String.format(Locale.ROOT, "%." + d + "f", (double) a / unit);
			return (v < 0 ? "-" : "") + num + UNIT_SUFFIX[i];
		}
		return QuantityFormatter.formatNumber(v);
	}

	/** A short amount for a single value (not an axis), e.g. the chart's volume scale: "1M", "2.1B", "523". */
	static String compactAmount(long v)
	{
		return QuantityFormatter.quantityToStackSize(v);
	}

	/** "just now", "12s ago", "3m ago", "2h ago", "1d ago" for an epoch-ms time; empty when never (0). */
	static String updatedAgo(long ms, long nowMs)
	{
		if (ms <= 0)
		{
			return "";
		}
		long s = Math.max(0, nowMs - ms) / 1000;
		if (s < 5)
		{
			return "just now";
		}
		if (s < 60)
		{
			return s + "s ago";
		}
		if (s < 3600)
		{
			return (s / 60) + "m ago";
		}
		if (s < 86400)
		{
			return (s / 3600) + "h ago";
		}
		return (s / 86400) + "d ago";
	}
}
