package com.hardcoreworldtoolkit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class DeathBroadcastTest
{
	@Test
	public void levelIsTheNumberAfterTheVictim()
	{
		assertEquals(1306, DeathBroadcast.parse("Made Up2(1306) was killed by a 12 damage hit from a Hill Giant.").level());
		assertEquals(70, DeathBroadcast.parse("Pk tester(120) killed Seed Player0(70) with a 25 damage hit in the Wilderness.").level());
		assertEquals(42, DeathBroadcast.parse("<col=ef1020>Low Lvl</col>(42) was killed by a 3 damage hit from a Goblin.").level());
	}

	@Test
	public void otherMessagesAreNotDeaths()
	{
		assertNull(DeathBroadcast.parse("Welcome to Old School RuneScape."));
	}
}
