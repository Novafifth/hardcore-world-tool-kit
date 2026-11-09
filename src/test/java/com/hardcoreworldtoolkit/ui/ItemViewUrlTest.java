package com.hardcoreworldtoolkit.ui;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ItemViewUrlTest
{
	@Test
	public void marketUrlOpensTheItemPage()
	{
		assertEquals("https://hcwtoolkit.com/tools?from=plugin&utm_source=plugin&utm_medium=item_button#item=1993",
			ItemView.marketUrl(1993));
	}
}
