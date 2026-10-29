package com.hardcoregetracker;

import static org.junit.Assert.assertEquals;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class LogFilesTest
{
	@Test
	public void newestFirstSortsAcrossYears()
	{
		assertEquals(Arrays.asList("trades-2026-01.jsonl", "trades-2025-12.jsonl", "trades-2025-02.jsonl"),
			LogFiles.newestFirst(Arrays.asList("trades-2025-02.jsonl", "trades-2026-01.jsonl", "trades-2025-12.jsonl")));
	}

	@Test
	public void unrelatedFilesAreIgnored()
	{
		assertEquals(Collections.singletonList("trades-2026-10.jsonl"),
			LogFiles.newestFirst(Arrays.asList("profit-abc.json", "trades-2026-10.jsonl", "trades-26-1.jsonl",
				"trades-2026-10.jsonl.bak", "Trades.jsonl", "trades-2026-1.jsonl", "notes.txt", "trades.jsonl")));
	}

	@Test
	public void oldestFirstIsTheReverse()
	{
		assertEquals(Arrays.asList("trades-2025-12.jsonl", "trades-2026-01.jsonl"),
			LogFiles.oldestFirst(Arrays.asList("trades-2026-01.jsonl", "trades-2025-12.jsonl")));
		assertEquals(Collections.emptyList(), LogFiles.newestFirst(Collections.emptyList()));
	}

	@Test
	public void monthsOfFiles()
	{
		assertEquals(YearMonth.of(2026, 7), LogFiles.monthOf("trades-2026-07.jsonl"));
		assertEquals(null, LogFiles.monthOf("trades.jsonl"));
		assertEquals(null, LogFiles.monthOf("trades-2026-13.jsonl"));
		assertEquals(null, LogFiles.monthOf(null));
	}
}
