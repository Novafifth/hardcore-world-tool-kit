package com.hardcoreworldtoolkit;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.util.Text;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/** Reports deaths: the text of each death broadcast, plus the tile of the player's own death. */
class DeathReporter
{
	private static final long OWN_WAIT = 60;
	/** Everyone online sees each broadcast: each player sends about one in this many (always their own death). */
	private static final int REPORT_ONE_IN = 5;
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
	private WorldPoint ownTile;
	private long ownAt;

	DeathReporter(ServerApi api, ServerIdentity identity)
	{
		this.api = api;
		this.identity = identity;
	}

	synchronized void ownDeath(WorldPoint tile, long now)
	{
		ownTile = tile;
		ownAt = now;
	}

	synchronized void broadcast(DeathBroadcast d, String me, long now)
	{
		boolean own = ownTile != null && me != null && now - ownAt <= OWN_WAIT
			&& Text.standardize(me).equals(Text.standardize(d.getVictim()));
		if (own)
		{
			send(d.getText(), ownTile, now);
			ownTile = null;
		}
		else if (ThreadLocalRandom.current().nextInt(REPORT_ONE_IN) == 0)
		{
			send(d.getText(), null, now);
		}
	}

	/** An own death with no broadcast: sent with the tile only. */
	synchronized void tick(long now)
	{
		if (ownTile != null && now - ownAt > OWN_WAIT)
		{
			send(null, ownTile, ownAt);
			ownTile = null;
		}
	}

	private void send(String text, WorldPoint tile, long ts)
	{
		String token = identity.token();
		if (token == null)
		{
			return;
		}
		JsonObject b = new JsonObject();
		b.addProperty("installId", identity.installId());
		b.addProperty("ts", ts);
		b.addProperty("text", text);
		if (tile != null)
		{
			b.addProperty("own", true);
			b.addProperty("x", tile.getX());
			b.addProperty("y", tile.getY());
			b.addProperty("plane", tile.getPlane());
		}
		api.postDeath(token, b, IGNORE);
	}
}
