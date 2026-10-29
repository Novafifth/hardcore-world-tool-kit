package com.hardcoreworldtoolkit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import org.junit.Test;

/**
 * The opt-in: every request method checks the Connect setting (uploads also Share my trades) before anything else,
 * and while it is off throws without sending. No other class starts a request.
 */
public class ServerApiTest
{
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

	private final FakeServer server = new FakeServer();
	private final AtomicBoolean connected = new AtomicBoolean(true);
	private final AtomicBoolean share = new AtomicBoolean(true);
	private final ServerApi api = new ServerApi(server.client(), new Gson(), FakeServer.BASE,
		new HardcoreWorldToolKitConfig()
		{
			@Override
			public boolean connected()
			{
				return connected.get();
			}

			@Override
			public boolean shareTrades()
			{
				return share.get();
			}
		});

	private static JsonObject body()
	{
		JsonObject o = new JsonObject();
		o.addProperty("installId", "id-1");
		return o;
	}

	private static void assertNotSent(Runnable request)
	{
		try
		{
			request.run();
			fail("a request went out without the setting");
		}
		catch (ServerApi.NotConnectedException expected)
		{
			// Nothing was sent.
		}
	}

	@Test
	public void eachRequestWhileConnected() throws InterruptedException
	{
		api.postRegister(body(), IGNORE);
		FakeServer.Seen reg = server.next();
		assertEquals("POST", reg.request.method());
		assertEquals(FakeServer.REGISTER, reg.path());
		assertNull(reg.auth());
		assertEquals("id-1", reg.json().get("installId").getAsString());

		api.postSubmit("tok", body(), IGNORE);
		FakeServer.Seen sub = server.next();
		assertEquals("POST", sub.request.method());
		assertEquals(FakeServer.SUBMIT, sub.path());
		assertEquals("Bearer tok", sub.auth());

		api.deleteMe("tok", body(), IGNORE);
		FakeServer.Seen del = server.next();
		assertEquals("DELETE", del.request.method());
		assertEquals(FakeServer.ME, del.path());
		assertEquals("Bearer tok", del.auth());
		assertEquals("id-1", del.json().get("installId").getAsString());

		api.getMarketData(FakeServer.BASE.newBuilder().addPathSegments("v1/items").build(), true, IGNORE);
		FakeServer.Seen get = server.next();
		assertEquals("GET", get.request.method());
		assertEquals("/v1/items", get.path());
		assertNull("reads carry no token", get.auth());
		assertEquals("no-cache", get.request.header("Cache-Control"));

		api.getMarketData(FakeServer.BASE.newBuilder().addPathSegments("v1/recent").build(), false, IGNORE);
		assertNull(server.next().request.header("Cache-Control"));
	}

	@Test
	public void nothingIsSentWhileNotConnected() throws InterruptedException
	{
		connected.set(false);
		assertNotSent(() -> api.postRegister(body(), IGNORE));
		assertNotSent(() -> api.postSubmit("tok", body(), IGNORE));
		assertNotSent(() -> api.deleteMe("tok", body(), IGNORE));
		assertNotSent(() -> api.getMarketData(FakeServer.BASE.newBuilder().addPathSegments("v1/recent").build(), false,
			IGNORE));
		assertNull(server.poll(200));
	}

	@Test
	public void uploadsAlsoNeedShareMyTrades() throws InterruptedException
	{
		share.set(false);
		assertNotSent(() -> api.postSubmit("tok", body(), IGNORE));
		assertNull(server.poll(200));

		// Registering (also part of deleting), deleting and reading only need the connection.
		api.postRegister(body(), IGNORE);
		assertEquals(FakeServer.REGISTER, server.next().path());
		api.deleteMe("tok", body(), IGNORE);
		assertEquals(FakeServer.ME, server.next().path());
	}

	@Test
	public void noOtherClassStartsARequest() throws IOException
	{
		List<Path> sources;
		try (Stream<Path> files = Files.walk(Paths.get("src", "main", "java")))
		{
			sources = files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
		}
		List<String> offenders = new ArrayList<>();
		for (Path p : sources)
		{
			String src = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
			boolean requests = src.contains(".newCall(") || src.contains(".newWebSocket(")
				|| src.contains(".openConnection(") || src.contains(".openStream(");
			if (requests && !p.getFileName().toString().equals("ServerApi.java"))
			{
				offenders.add(p.toString());
			}
		}
		assertEquals(Collections.emptyList(), offenders);
		assertTrue("ServerApi itself is found", sources.stream()
			.anyMatch(p -> p.getFileName().toString().equals("ServerApi.java")));
	}
}
