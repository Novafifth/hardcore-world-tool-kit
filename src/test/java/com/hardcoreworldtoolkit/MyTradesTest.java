package com.hardcoreworldtoolkit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.junit.Test;

public class MyTradesTest
{
	private static TradeEvent ev(String id, String kind, long ts, boolean late)
	{
		return TradeEvent.builder().id(id).kind(kind).side(TradeEvent.BUY).itemId(4151).qty(1).price(1).ts(ts).late(late).world(9000).build();
	}

	private static List<String> ids(List<TradeEvent> events)
	{
		return events.stream().map(TradeEvent::getId).collect(Collectors.toList());
	}

	@Test
	public void hidesPlacements()
	{
		// Offer placements aren't trades; fills, GE History rows and cancellations are listed.
		assertFalse(MyTrades.isShown(ev("a", TradeEvent.PLACED, 1, false)));
		assertFalse(MyTrades.isShown(ev("a", TradeEvent.PLACED, 1, true)));
		assertTrue(MyTrades.isShown(ev("a", TradeEvent.FILL, 1, false)));
		assertTrue(MyTrades.isShown(ev("a", TradeEvent.FILL, 1, true)));
		assertTrue(MyTrades.isShown(ev("a", TradeEvent.CANCELLED, 1, true)));
		assertTrue(MyTrades.isShown(ev("a", TradeEvent.HISTORY, 1, true)));
	}

	@Test
	public void addNewestPutsLatestFirstAndCaps()
	{
		MyTrades trades = new MyTrades();
		trades.addNewest(Arrays.asList(ev("a", TradeEvent.FILL, 1, false), ev("b", TradeEvent.PLACED, 2, false),
			ev("c", TradeEvent.CANCELLED, 3, false)));
		assertEquals(Arrays.asList("c", "a"), ids(trades.snapshot()));

		List<TradeEvent> many = new ArrayList<>();
		for (int i = 0; i < MyTrades.MAX + 5; i++)
		{
			many.add(ev("n" + i, TradeEvent.FILL, 10 + i, false));
		}
		trades.addNewest(many);
		List<TradeEvent> snap = trades.snapshot();
		assertEquals(MyTrades.MAX, snap.size());
		assertEquals("n" + (MyTrades.MAX + 4), snap.get(0).getId());
		assertEquals("n5", snap.get(MyTrades.MAX - 1).getId());

		trades.clear();
		assertTrue(trades.snapshot().isEmpty());
	}

	@Test
	public void mergeLoadedFillsNamesAndSortsNewestFirst()
	{
		MyTrades trades = new MyTrades();
		trades.addNewest(Arrays.asList(ev("live5", TradeEvent.FILL, 5, false), ev("live7", TradeEvent.FILL, 7, false)));
		trades.mergeLoaded(trades.session(), Arrays.asList(ev("log3", TradeEvent.FILL, 3, false), ev("log5", TradeEvent.FILL, 5, false),
			ev("log9", TradeEvent.FILL, 9, false)), e -> e.toBuilder().name("named " + e.getId()).build());

		List<TradeEvent> snap = trades.snapshot();
		// Equal times keep live trades ahead of loaded ones.
		assertEquals(Arrays.asList("log9", "live7", "live5", "log5", "log3"), ids(snap));
		assertEquals("named log9", snap.get(0).getName());
		assertNull(snap.get(1).getName());
	}

	@Test
	public void mergeLoadedCapsTotal()
	{
		MyTrades trades = new MyTrades();
		trades.addNewest(Collections.singletonList(ev("live", TradeEvent.FILL, 0, false)));
		List<TradeEvent> loaded = new ArrayList<>();
		for (int i = 0; i < MyTrades.MAX; i++)
		{
			loaded.add(ev("f" + i, TradeEvent.FILL, 100 + i, false));
		}
		trades.mergeLoaded(trades.session(), loaded, UnaryOperator.identity());
		List<TradeEvent> snap = trades.snapshot();
		assertEquals(MyTrades.MAX, snap.size());
		assertEquals("f0", snap.get(MyTrades.MAX - 1).getId());
	}

	@Test
	public void loadFromAnEarlierSessionIsDropped()
	{
		MyTrades trades = new MyTrades();
		int oldSession = trades.session();
		// Plugin stopped and started again while the first log read was still running.
		trades.clear();
		int newSession = trades.session();
		List<TradeEvent> log = Collections.singletonList(ev("logged", TradeEvent.FILL, 1, false));

		assertFalse(trades.mergeLoaded(oldSession, log, UnaryOperator.identity()));
		assertTrue(trades.snapshot().isEmpty());

		assertTrue(trades.mergeLoaded(newSession, log, UnaryOperator.identity()));
		assertEquals(Collections.singletonList("logged"), ids(trades.snapshot()));
	}

}
