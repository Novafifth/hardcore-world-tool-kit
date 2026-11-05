package com.hardcoreworldtoolkit;

import java.util.Random;
import java.util.function.LongSupplier;

/**
 * Waits before asking again when the server says it is overloaded (503 or 429) or can't be reached: at least its
 * Retry-After, otherwise 30 s, doubling up to 5 minutes, with some random spread so players don't all retry together.
 * One success ends it. Thread safe: requests start on several threads, responses arrive on OkHttp's.
 */
final class ServerBackoff
{
	private static final long BASE_MS = 30_000L;
	private static final long MAX_MS = 300_000L;
	private static final double SPREAD = 0.2;

	private final LongSupplier clock;
	private final Random random;
	private long until;
	private int failures;

	ServerBackoff(LongSupplier clock, Random random)
	{
		this.clock = clock;
		this.random = random;
	}

	/** True while requests should wait. */
	synchronized boolean waiting()
	{
		return clock.getAsLong() < until;
	}

	/** @param retryAfterSeconds the server's Retry-After, or 0 if it gave none */
	synchronized void failed(long retryAfterSeconds)
	{
		long wait = BASE_MS << Math.min(failures, 4);
		failures++;
		wait = Math.max(Math.min(wait, MAX_MS), Math.min(retryAfterSeconds * 1000L, MAX_MS));
		long spread = (long) (wait * SPREAD * random.nextDouble());
		until = clock.getAsLong() + wait + spread;
	}

	synchronized void succeeded()
	{
		failures = 0;
		until = 0;
	}

	/** Records a response: 503 / 429 start or lengthen the wait, any other answer below 500 ends it. */
	void observe(int code, String retryAfter)
	{
		if (code == 503 || code == 429)
		{
			failed(parseSeconds(retryAfter));
		}
		else if (code < 500)
		{
			succeeded();
		}
	}

	static long parseSeconds(String value)
	{
		try
		{
			return value == null ? 0 : Math.max(0, Long.parseLong(value.trim()));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}
}
