package com.hardcoreworldtoolkit;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.util.Text;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/** Reports deaths: the text of each death broadcast, plus the tile of the player's own death (and its killer). */
class DeathReporter
{
	private static final long OWN_WAIT = 60;
	/** Everyone online sees each broadcast: each player sends about one in this many (always their own death). */
	static final int REPORT_ONE_IN = 15;
	private static final Callback IGNORE = new Callback()
	{
		@Override
		public void onFailure(Call call, IOException e)
		{
		}

		@Override
		public void onResponse(Call call, Response response)
		{
			response.close();
		}
	};

	private final ServerApi api;
	private final ServerIdentity identity;
	private final int oneIn;
	/** A monster that stopped attacking this many game ticks (0.6 s each) before the death still counts as the killer. */
	static final int RECENT_TICKS = 10;
	private WorldPoint ownTile;
	private String ownKiller;
	private long ownAt;

	/** oneIn: send about one in this many broadcasts of other players' deaths (1: every one). */
	DeathReporter(ServerApi api, ServerIdentity identity, int oneIn)
	{
		this.api = api;
		this.identity = identity;
		this.oneIn = Math.max(1, oneIn);
	}

	/** The player's own death: its tile, and the monster that was attacking them (null: none seen, or a player was). */
	synchronized void ownDeath(WorldPoint tile, String killer, long now)
	{
		ownTile = tile;
		ownKiller = killer;
		ownAt = now;
	}

	synchronized void broadcast(DeathBroadcast d, String me, long now)
	{
		boolean own = ownTile != null && me != null && now - ownAt <= OWN_WAIT
			&& Text.standardize(me).equals(Text.standardize(d.getVictim()));
		if (own)
		{
			send(d.getText(), ownTile, null, now); // the broadcast names the killer
			ownTile = null;
			ownKiller = null;
		}
		else if (ThreadLocalRandom.current().nextInt(oneIn) == 0)
		{
			send(d.getText(), null, null, now);
		}
	}

	/** An own death with no broadcast: sent with the tile and the monster that was attacking, if one was. */
	synchronized void tick(long now)
	{
		if (ownTile != null && now - ownAt > OWN_WAIT)
		{
			send(null, ownTile, ownKiller, ownAt);
			ownTile = null;
			ownKiller = null;
		}
	}

	private void send(String text, WorldPoint tile, String killer, long ts)
	{
		String token = identity.token();
		if (token == null)
		{
			return;
		}
		api.postDeath(token, body(identity.installId(), text, tile, killer, ts), IGNORE);
	}

	/**
	 * One report: the broadcast's text, plus the tile of the player's own death. An own death no broadcast named carries
	 * the monster that was attacking instead (a monster's name only, never a player's).
	 */
	static JsonObject body(String installId, String text, WorldPoint tile, String killer, long ts)
	{
		JsonObject b = new JsonObject();
		b.addProperty("installId", installId);
		b.addProperty("ts", ts);
		b.addProperty("text", text);
		if (tile != null)
		{
			b.addProperty("own", true);
			b.addProperty("x", tile.getX());
			b.addProperty("y", tile.getY());
			b.addProperty("plane", tile.getPlane());
			if (text == null && killer != null)
			{
				b.addProperty("killer", killer);
			}
		}
		return b;
	}

	/**
	 * What killed the player: a monster attacking them when they died (the one that last turned on them, when several
	 * were), else the last one that did within RECENT_TICKS. Null when a player was attacking them: player names come
	 * from the broadcasts only, never with a tile.
	 */
	static String killerOf(List<String> attackingNow, boolean playerAttacking, String last, int lastTick, int tick)
	{
		if (playerAttacking)
		{
			return null;
		}
		if (last != null && attackingNow.contains(last))
		{
			return last;
		}
		if (!attackingNow.isEmpty())
		{
			return attackingNow.get(0);
		}
		return last != null && tick - lastTick <= RECENT_TICKS ? last : null;
	}
}
