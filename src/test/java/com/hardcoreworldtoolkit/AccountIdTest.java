package com.hardcoreworldtoolkit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class AccountIdTest
{
	@Test
	public void knownVectors()
	{
		// sha256("hardcore-world-tool-kit:123456789")
		assertEquals("7b1a2f8f6f61ed6066424c5038c1ef0aee11a83c2fe561fd8ff8a0abac8fe11a", AccountId.of(123456789L));
		// sha256("hardcore-world-tool-kit:-42")
		assertEquals("0aa4d618b4866a23a3e47e433dae25225d4aba2a2def1dab0c5a6119b6ff5cdf", AccountId.of(-42L));
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
