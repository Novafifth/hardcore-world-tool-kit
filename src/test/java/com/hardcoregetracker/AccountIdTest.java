package com.hardcoregetracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class AccountIdTest
{
	@Test
	public void knownVectors()
	{
		// sha256("hardcore-ge-tracker:123456789")
		assertEquals("16382665778bbd2d98f59833f90520b9e9c6c78abc51ff4a48dd264d2f70c2bb", AccountId.of(123456789L));
		// sha256("hardcore-ge-tracker:-42")
		assertEquals("18ba82bab4048cea02208f392ce6e279ee086ddea37eea5748d7cbed5fe6d8a0", AccountId.of(-42L));
	}

	@Test
	public void saltedLowercaseHex()
	{
		String id = AccountId.of(Long.MAX_VALUE);
		assertEquals(64, id.length());
		assertTrue(id.matches("[0-9a-f]{64}"));
		// Not the plain hash of the account hash: the prefix is included.
		assertNotEquals("15e2b0d3c33891ebb0f1ef609ec419420c20e320ce94c65fbc8c3312448eb225", AccountId.of(123456789L));
		assertNotEquals(AccountId.of(1L), AccountId.of(2L));
		assertEquals(AccountId.of(7L), AccountId.of(7L));
	}
}
