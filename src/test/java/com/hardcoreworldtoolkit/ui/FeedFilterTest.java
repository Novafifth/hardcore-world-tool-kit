package com.hardcoreworldtoolkit.ui;

import static org.junit.Assert.assertEquals;
import com.hardcoreworldtoolkit.MarketData;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/** The Market feed lists live fills only; an item's recent trades never list offer placements. */
public class FeedFilterTest
{
	private final Gson gson = new Gson();

	@Test
	public void marketShowsLiveFillsOnly()
	{
		MarketData.Recent r = gson.fromJson("{\"events\":[{\"id\":1,\"kind\":\"placed\"},{\"id\":2,\"kind\":\"fill\"},"
			+ "{\"id\":3,\"kind\":\"cancelled\"},{\"id\":4,\"kind\":\"history\"},{\"id\":6}]}",
			MarketData.Recent.class);
		List<Long> ids = FeedMerge.liveFills(r.getEvents()).stream().map(MarketData.FeedEvent::getId)
			.collect(Collectors.toList());
		assertEquals(Arrays.asList(2L), ids);
	}

	@Test
	public void itemRecentTradesDropPlacements()
	{
		MarketData.ItemDetail d = gson.fromJson("{\"trades\":[{\"kind\":\"placed\",\"qty\":1},{\"kind\":\"fill\",\"qty\":2},"
			+ "{\"kind\":\"cancelled\",\"qty\":3},{\"kind\":\"history\",\"qty\":4}]}", MarketData.ItemDetail.class);
		List<Integer> qtys = ItemView.recentTrades(d.getTrades()).stream().map(MarketData.Trade::getQty)
			.collect(Collectors.toList());
		assertEquals(Arrays.asList(2, 3, 4), qtys);
	}

}
