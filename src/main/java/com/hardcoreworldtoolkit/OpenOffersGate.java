package com.hardcoreworldtoolkit;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Objects;

/**
 * Decides when the list of your open offers is worth sending. It used to go out every six minutes for every sharing
 * player, even with no offers and nothing changed, each one a database write on the server. Now it goes out
 * when it differs from what was last sent (at most once per {@link #MIN_GAP_MS}), and as a keep-alive every {@link #KEEP_ALIVE_MS} while offers stay open
 * (the server ignores offers not refreshed for two weeks). An empty list is sent once (it clears what the server has),
 * then not again until something changes. {@link #reset} makes the next one go out (login, sharing switched on).
 * Thread safe.
 */
class OpenOffersGate
{
	static final long KEEP_ALIVE_MS = 30 * 60_000L;
	/** A change is sent at most this often (the old fixed interval), so a fast-filling offer can't send more than before. */
	static final long MIN_GAP_MS = 6 * 60_000L;

	private String lastAccount;
	private String lastSignature;
	private long lastSentAt;

	/** @return true when {@code offers} should be sent now; the call counts as the send, so call it once per decision */
	synchronized boolean shouldSend(String accountId, List<JsonObject> offers, long nowMs)
	{
		String signature = String.valueOf(offers);
		// Another account (or the first call) is sent at once; a change in the offers waits for the minimum gap.
		boolean changed = lastSignature == null || !Objects.equals(accountId, lastAccount)
			|| (!signature.equals(lastSignature) && nowMs - lastSentAt >= MIN_GAP_MS);
		boolean keepAlive = !offers.isEmpty() && nowMs - lastSentAt >= KEEP_ALIVE_MS;
		if (!changed && !keepAlive)
		{
			return false;
		}
		lastAccount = accountId;
		lastSignature = signature;
		lastSentAt = nowMs;
		return true;
	}

	/** Forget what was sent: the next call to {@link #shouldSend} says yes. */
	synchronized void reset()
	{
		lastAccount = null;
		lastSignature = null;
		lastSentAt = 0;
	}
}
