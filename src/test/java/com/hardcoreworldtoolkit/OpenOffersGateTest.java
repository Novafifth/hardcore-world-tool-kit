package com.hardcoreworldtoolkit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class OpenOffersGateTest
{
	private static final long MIN = 60_000;
	private final OpenOffersGate gate = new OpenOffersGate();

	private static List<JsonObject> offers(int filled)
	{
		JsonObject o = new JsonObject();
		o.addProperty("slot", 0);
		o.addProperty("filledQty", filled);
		return Arrays.asList(o);
	}

	@Test
	public void firstListIsSentEvenWhenEmpty()
	{
		assertTrue(gate.shouldSend("a", Collections.emptyList(), 0));
	}

	@Test
	public void unchangedEmptyListIsNeverSentAgain()
	{
		gate.shouldSend("a", Collections.emptyList(), 0);
		assertFalse(gate.shouldSend("a", Collections.emptyList(), MIN));
		assertFalse(gate.shouldSend("a", Collections.emptyList(), 24 * 60 * MIN));
	}

	@Test
	public void unchangedOffersWaitForTheKeepAlive()
	{
		assertTrue(gate.shouldSend("a", offers(0), 0));
		assertFalse(gate.shouldSend("a", offers(0), MIN));
		assertFalse(gate.shouldSend("a", offers(0), 29 * MIN));
		assertTrue(gate.shouldSend("a", offers(0), 30 * MIN));
		assertFalse(gate.shouldSend("a", offers(0), 31 * MIN));
	}

	@Test
	public void aChangeIsSentAtOnce()
	{
		gate.shouldSend("a", offers(0), 0);
		// Changed, but sent only 1 minute ago: waits for the minimum gap, then goes out.
		assertFalse(gate.shouldSend("a", offers(5), MIN));
		assertTrue(gate.shouldSend("a", offers(5), 6 * MIN));
		// Offers cleared: the server hears about it once.
		assertTrue(gate.shouldSend("a", Collections.emptyList(), 12 * MIN));
		assertFalse(gate.shouldSend("a", Collections.emptyList(), 13 * MIN));
	}

	@Test
	public void aFastFillingOfferSendsNoMoreOftenThanEverySixMinutes()
	{
		int sends = 0;
		for (int minute = 0; minute < 60; minute++)
		{
			if (gate.shouldSend("a", offers(minute), minute * MIN))
			{
				sends++;
			}
		}
		assertTrue("sent " + sends, sends <= 10);
	}

	@Test
	public void anotherAccountIsSentAtOnce()
	{
		gate.shouldSend("a", offers(0), 0);
		assertTrue(gate.shouldSend("b", offers(0), MIN));
	}

	@Test
	public void resetSendsTheNextOne()
	{
		gate.shouldSend("a", Collections.emptyList(), 0);
		gate.reset();
		assertTrue(gate.shouldSend("a", Collections.emptyList(), MIN));
	}
}
