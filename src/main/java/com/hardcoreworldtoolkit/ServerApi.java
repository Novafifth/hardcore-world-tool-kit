package com.hardcoreworldtoolkit;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import okhttp3.CacheControl;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Every HTTP request the plugin makes, one method per request. They all go to the Hardcore World Tool Kit server
 * through RuneLite's OkHttpClient; no other class calls OkHttp.
 * <p>
 * Opt-in: each method first checks the "Connect to Hardcore World Tool Kit server" setting (uploads also "Share my
 * trades") and throws {@link NotConnectedException} when it is off, so nothing is sent unless the player connected.
 * The rest of each method is only the request, enqueued on OkHttp's thread pool; the callback gets the response on an
 * OkHttp thread. Request bodies are built, and responses read, by the callers: {@link InstallAuth},
 * {@link TradeUploader}, {@link DataDeletion} and {@link MarketClient}.
 */
class ServerApi
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	/** No Cache-Control header: OkHttp's normal caching. */
	private static final CacheControl NORMAL_CACHE = new CacheControl.Builder().build();

	/** Thrown by the request methods while the setting they need is off. Nothing was sent. */
	static final class NotConnectedException extends IllegalStateException
	{
		NotConnectedException(String message)
		{
			super(message);
		}
	}

	private final OkHttpClient http;
	private final Gson gson;
	private final HardcoreWorldToolKitConfig config;
	private final HttpUrl registerUrl;
	private final HttpUrl submitUrl;
	private final HttpUrl meUrl;

	ServerApi(OkHttpClient http, Gson gson, HttpUrl base, HardcoreWorldToolKitConfig config)
	{
		this.http = http;
		this.gson = gson;
		this.config = config;
		this.registerUrl = base.newBuilder().addPathSegments("v1/register").build();
		this.submitUrl = base.newBuilder().addPathSegments("v1/submit").build();
		this.meUrl = base.newBuilder().addPathSegments("v1/me").build();
	}

	/** HTTP request: POST v1/register with the install id, for a new install token. Only while connected. */
	void postRegister(JsonObject body, Callback callback)
	{
		if (!config.connected())
		{
			throw new NotConnectedException("Not connected: registration not sent");
		}
		http.newCall(new Request.Builder()
			.url(registerUrl)
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build()).enqueue(callback);
	}

	/** HTTP request: POST v1/submit, a batch of trades with the install token. Only while connected and sharing. */
	void postSubmit(String token, JsonObject body, Callback callback)
	{
		if (!config.connected() || !config.shareTrades())
		{
			throw new NotConnectedException("Not connected or not sharing: upload not sent");
		}
		http.newCall(new Request.Builder()
			.url(submitUrl)
			.header("Authorization", "Bearer " + token)
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build()).enqueue(callback);
	}

	/**
	 * HTTP request: DELETE v1/me with the install token, deleting everything this install shared ("Delete my shared
	 * data"). Only while connected.
	 */
	void deleteMe(String token, JsonObject body, Callback callback)
	{
		if (!config.connected())
		{
			throw new NotConnectedException("Not connected: deletion not sent");
		}
		http.newCall(new Request.Builder()
			.url(meUrl)
			.header("Authorization", "Bearer " + token)
			.delete(RequestBody.create(JSON, gson.toJson(body)))
			.build()).enqueue(callback);
	}

	/**
	 * HTTP request: GET market data from the server, one of v1/recent, v1/item/{id}, v1/item/{id}/series or v1/items
	 * (the URL is built by {@link MarketClient}). No token. Only while connected.
	 *
	 * @param fresh skip any HTTP cache
	 */
	void getMarketData(HttpUrl url, boolean fresh, Callback callback)
	{
		if (!config.connected())
		{
			throw new NotConnectedException("Not connected: market request not sent");
		}
		http.newCall(new Request.Builder()
			.url(url)
			.cacheControl(fresh ? CacheControl.FORCE_NETWORK : NORMAL_CACHE)
			.get()
			.build()).enqueue(callback);
	}
}
