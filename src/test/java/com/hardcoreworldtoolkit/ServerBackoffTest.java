package com.hardcoreworldtoolkit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Random;
import org.junit.Test;

public class ServerBackoffTest
{
	private long now = 1_000_000L;
	private final ServerBackoff backoff = new ServerBackoff(() -> now, new Random(1));

	@Test
	public void waitsLongerEachFailureUpToTheLimit()
	{
		assertFalse(backoff.waiting());
		backoff.failed(0);
		assertTrue(backoff.waiting());
		now += 29_000L;
		assertTrue(backoff.waiting());
		now += 8_000L; // 30 s plus at most 20%
		assertFalse(backoff.waiting());

		backoff.failed(0); // 60 s
		now += 59_000L;
		assertTrue(backoff.waiting());
		for (int i = 0; i < 10; i++)
		{
			backoff.failed(0);
		}
		now += 299_000L; // 5 minutes at most, plus up to 20%
		assertTrue(backoff.waiting());
		now += 62_000L;
		assertFalse(backoff.waiting());
	}

	@Test
	public void honoursRetryAfterAndClearsOnSuccess()
	{
		backoff.failed(120);
		now += 119_000L;
		assertTrue(backoff.waiting());
		backoff.succeeded();
		assertFalse(backoff.waiting());
	}
}
