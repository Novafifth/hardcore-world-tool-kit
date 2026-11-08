package com.hardcoreworldtoolkit;

import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class DeathReporterTest
{
	@Test
	public void killerIsTheMonsterAttackingNeverAPlayer()
	{
		// The one attacking at the death; of several, the one that last turned on the player.
		assertEquals("Goblin", DeathReporter.killerOf(Collections.singletonList("Goblin"), false, null, 0, 100));
		assertEquals("Hill Giant", DeathReporter.killerOf(Arrays.asList("Goblin", "Hill Giant"), false, "Hill Giant", 95, 100));
		assertEquals("Goblin", DeathReporter.killerOf(Arrays.asList("Goblin", "Hill Giant"), false, "Guard", 95, 100));
		// None attacking now: the last one, if recent.
		assertEquals("Guard", DeathReporter.killerOf(Collections.emptyList(), false, "Guard", 100 - DeathReporter.RECENT_TICKS, 100));
		assertNull(DeathReporter.killerOf(Collections.emptyList(), false, "Guard", 100 - DeathReporter.RECENT_TICKS - 1, 100));
		// A player attacking: no name at all (player names only come from the broadcasts).
		assertNull(DeathReporter.killerOf(Collections.singletonList("Goblin"), true, "Goblin", 99, 100));
	}

	@Test
	public void ownDeathWithoutBroadcastCarriesTheKiller()
	{
		WorldPoint tile = new WorldPoint(3200, 3200, 0);
		JsonObject own = DeathReporter.body("id", null, tile, "Goblin", 5);
		assertEquals("Goblin", own.get("killer").getAsString());
		assertTrue(own.get("own").getAsBoolean());
		// A broadcast names the killer itself; a broadcast of someone else's death has no tile and no killer.
		assertFalse(DeathReporter.body("id", "Goblin killed Bob(3) with a 1 damage hit in Misthalin.", tile, "Goblin", 5).has("killer"));
		assertFalse(DeathReporter.body("id", "Goblin killed Bob(3) with a 1 damage hit in Misthalin.", null, "Goblin", 5).has("killer"));
		assertFalse(DeathReporter.body("id", null, tile, null, 5).has("killer"));
	}
}
