package com.hardcoreworldtoolkit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.junit.Assume;
import org.junit.Test;

/**
 * Opt-in contract check against a real collection server (skipped unless the environment variable
 * HWTK_CONTRACT_SERVER holds its base URL, e.g. a local server from server/ on an ephemeral port). Exercises
 * registration, authorised uploads, the 401 re-registration path, the item limit field, include=trust,fill and
 * DELETE v1/me with the plugin's own classes.
 */
public class ServerContractTest
{
	private final Gson gson = new Gson();
	private final OkHttpClient http = new OkHttpClient();

	@Test
	public void registerSubmitDelete() throws InterruptedException
	{
		String url = System.getenv("HWTK_CONTRACT_SERVER");
		Assume.assumeTrue("set HWTK_CONTRACT_SERVER to run", url != null && !url.isEmpty());
		HttpUrl base = HttpUrl.get(url);

		Map<String, String> config = new ConcurrentHashMap<>();
		InstallIdentity identity = FakeServer.identity(config);
		AtomicBoolean connected = new AtomicBoolean(true);
		BlockingQueue<java.util.Set<Integer>> sent = new LinkedBlockingQueue<>();
		TradeUploader up = new TradeUploader(http, gson, base, sent::add, identity, connected::get);
		String acct = AccountId.of(123456789L);
		long now = System.currentTimeMillis() / 1000;

		// 1. First upload: registers, then submits with the token (a placed offer, a fill, item info with a limit).
		TradeEvent placed = TradeEvent.builder().id("ck1:placed").kind(TradeEvent.PLACED).side(TradeEvent.BUY)
			.itemId(4151).qty(10).price(1_500_000).slot(0).offerKey("ck1").ts(now - 60).late(false).world(9000).build();
		TradeEvent fill = TradeEvent.builder().id("ck1:fill:2").kind(TradeEvent.FILL).side(TradeEvent.BUY).itemId(4151)
			.qty(2).price(1_490_000).total(2_980_000L).slot(0).offerKey("ck1").ts(now - 30).late(false).filled(2)
			.world(9000).build();
		up.add(placed, acct);
		up.add(fill, acct);
		up.addItem(4151, "Abyssal whip", null, 70);
		up.flush();
		assertEquals(Collections.singleton(4151), sent.poll(10, TimeUnit.SECONDS));
		FakeServer.awaitIdle(up);
		assertFalse(up.hasPending());
		String firstId = identity.installId();
		String firstToken = identity.token();
		assertNotNull("registered", firstToken);

		BlockingQueue<Object> imp = new LinkedBlockingQueue<>();
		new MarketClient(http, gson, base, 9000).itemAsync(4151, imp::add, imp::add);
		Object d = imp.poll(10, TimeUnit.SECONDS);
		assertTrue(String.valueOf(d), d instanceof MarketData.ItemDetail);
		// The last price is the fill's, and vol24 counts it.
		assertEquals(Long.valueOf(1_490_000), ((MarketData.ItemDetail) d).getSummary().getLastBuy());
		assertEquals(2, ((MarketData.ItemDetail) d).getSummary().getVol24());

		// 2. A wrong token: 401, re-register gets 409 (the id has a token), so the uploader rotates and resends.
		identity.setToken(firstId, FakeServer.tok('x'));
		up.add(fill.toBuilder().id("ck1:fill:3").qty(1).total(1_490_000L).filled(3).build(), acct);
		up.flush();
		assertNotNull(sent.poll(10, TimeUnit.SECONDS));
		FakeServer.awaitIdle(up);
		assertNotEquals("rotated after 409", firstId, identity.installId());
		assertNotNull(identity.token());

		// 3. Reads with include=trust,fill carry the new fields.
		MarketClient market = new MarketClient(http, gson, base, 9000);
		BlockingQueue<Object> got = new LinkedBlockingQueue<>();
		market.itemAsync(4151, got::add, got::add);
		Object detail = got.poll(10, TimeUnit.SECONDS);
		assertTrue(String.valueOf(detail), detail instanceof MarketData.ItemDetail);
		MarketData.Summary s = ((MarketData.ItemDetail) detail).getSummary();
		assertNotNull("confVol24 (include=trust)", s.getConfVol24());
		assertEquals(Long.valueOf(1_490_000), s.getLastFillBuy());
		market.items(Collections.singletonList(4151), false, got::add, got::add);
		Object items = got.poll(10, TimeUnit.SECONDS);
		assertTrue(String.valueOf(items), items instanceof MarketData.ItemsResponse);
		assertEquals(1, ((MarketData.ItemsResponse) items).getItems().size());
		assertNotNull(((MarketData.ItemsResponse) items).getItems().get(0).getConfVol24());

		// 4. Delete my shared data: DELETE v1/me with the token, then disconnected with a new id and no token.
		String beforeDelete = identity.installId();
		BlockingQueue<DataDeletion.Result> results = new LinkedBlockingQueue<>();
		DataDeletion deletion = new DataDeletion(http, gson, base, identity, up, () -> connected.set(false));
		assertTrue(deletion.start(results::add));
		DataDeletion.Result r = results.poll(20, TimeUnit.SECONDS);
		assertNotNull(r);
		assertTrue(r.toString(), r.outcome == DataDeletion.Outcome.DELETED || r.outcome == DataDeletion.Outcome.PENDING);
		assertFalse(connected.get());
		assertNull(identity.token());
		assertNotEquals(beforeDelete, identity.installId());

		// 5. The deleted install's id can't be registered again (tombstone).
		Map<String, String> ghostConfig = new ConcurrentHashMap<>();
		ghostConfig.put(InstallIdentity.KEY_INSTALL_ID, beforeDelete);
		InstallIdentity old = FakeServer.identity(ghostConfig);
		BlockingQueue<InstallAuth.Result> reg = new LinkedBlockingQueue<>();
		new InstallAuth(http, gson, base, old).register(false, reg::add);
		InstallAuth.Result again = reg.poll(10, TimeUnit.SECONDS);
		assertNotNull(again);
		assertEquals("tombstoned id", InstallAuth.Status.DELETED, again.getStatus());

		// 6. Deleting again from the new (never used) id: registers first, then deletes nothing.
		connected.set(true);
		assertTrue(deletion.start(results::add));
		DataDeletion.Result second = results.poll(20, TimeUnit.SECONDS);
		assertNotNull(second);
		assertEquals(second.toString(), DataDeletion.Outcome.DELETED, second.outcome);
	}
}
