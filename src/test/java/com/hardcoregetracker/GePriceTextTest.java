package com.hardcoregetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import net.runelite.client.util.Text;
import org.junit.Test;

public class GePriceTextTest
{
	private static final String M = GePriceText.MARKER;
	private static final String LINE = M + " last 1.25M/1.24M";

	@Test
	public void emptyFeeAppends()
	{
		assertEquals("Examine<br>Buy limit: 100<br>" + LINE, GePriceText.insert("Examine<br>Buy limit: 100", "", LINE));
		assertEquals(LINE, GePriceText.insert("", "", LINE));
	}

	@Test
	public void lineGoesBeforeTheFee()
	{
		String core = "Examine<br>Buy limit: 100<br>Fee text";
		assertEquals("Examine<br>Buy limit: 100<br>" + LINE + "<br>Fee text", GePriceText.insert(core, "Fee text", LINE));
		// The script's own "<br><br>" before the fee survives.
		assertEquals("Examine<br><br>" + LINE + "<br>Fee", GePriceText.insert("Examine<br><br>Fee", "Fee", LINE));
	}

	@Test
	public void feeNotAtTheEndAppends()
	{
		assertEquals("Examine<br>" + LINE, GePriceText.insert("Examine", "Fee", LINE));
	}

	@Test
	public void insertIsIdempotentAndReplaces()
	{
		String once = GePriceText.insert("Examine<br>Limit", "", LINE);
		assertEquals(once, GePriceText.insert(once, "", LINE));
		String other = M + " no trades yet";
		assertEquals("Examine<br>Limit<br>" + other, GePriceText.insert(once, "", other));

		String withFee = GePriceText.insert("Examine<br>Fee", "Fee", LINE);
		assertEquals(withFee, GePriceText.insert(withFee, "Fee", LINE));
	}

	@Test
	public void stripOnlyRemovesOurLine()
	{
		assertEquals("Examine<br><col=ff0000>Other</col><br>Fee",
			GePriceText.strip("Examine<br><col=ff0000>Other</col><br>" + LINE + "<br>Fee"));
		assertEquals("Examine", GePriceText.strip("Examine<br>" + LINE));
		assertEquals("Rest", GePriceText.strip(LINE + "<br>Rest"));
		assertEquals("", GePriceText.strip(LINE));
		assertEquals("No marker here", GePriceText.strip("No marker here"));
	}

	@Test
	public void lineWithMissingValues()
	{
		String l = GePriceText.line(1_250_000L, null, null, 1_260_000L, 523);
		assertTrue(l.startsWith(M + " "));
		assertEquals("HC last 1.25M/- · bid - ask 1.26M · 523/24h", Text.removeTags(l));
	}

	@Test
	public void noDataAtAll()
	{
		assertEquals(M + " no trades yet", GePriceText.line(null, null, null, null, 0));
		assertEquals(M + " no trades yet", GePriceText.line((MarketData.Summary) null));
	}

	@Test
	public void billionsAreCompact()
	{
		String l = Text.removeTags(GePriceText.line(2_147_000_000L, 2_100_000_000L, 2_000_000_000L, 2_140_000_000L, 12));
		assertTrue(l, l.contains("2.14B/2.1B"));
		assertTrue(l.length() <= GePriceText.MAX_VISIBLE);
	}

	@Test
	public void worstCaseStillFitsWithBidAsk()
	{
		// The widest values each format to five characters ("9,999", "99.9K", "2.14B").
		String l = Text.removeTags(GePriceText.line(9_999L, 99_999L, 2_147_000_000L, 9_998L, 2_147_000_000L));
		assertTrue(l, l.length() <= GePriceText.MAX_VISIBLE);
		assertTrue(l, l.contains(" bid 2.14B ask 9,998"));
	}

	@Test
	public void fromSummary()
	{
		MarketData.Summary s = new Gson().fromJson("{\"lastBuy\":1250000,\"lastSell\":1240000,\"bestBid\":1230000,"
			+ "\"bestAsk\":1260000,\"vol24\":523}", MarketData.Summary.class);
		assertEquals("HC last 1.25M/1.24M · bid 1.23M ask 1.26M · 523/24h", Text.removeTags(GePriceText.line(s)));
		assertFalse(GePriceText.line(s).contains("<br>"));
	}
}
