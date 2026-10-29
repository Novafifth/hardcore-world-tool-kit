package com.hardcoregetracker.ui;

import static org.junit.Assert.assertEquals;
import java.awt.image.BufferedImage;
import org.junit.Test;

public class SkullIconTest
{
	@Test
	public void bonyWithSeeThroughEyes()
	{
		BufferedImage img = SkullIcon.draw(32, SkullIcon.BONE);
		assertEquals(32, img.getWidth());
		assertEquals(32, img.getHeight());
		// The forehead is bone; an eye socket and the corner of the image are see-through.
		assertEquals(SkullIcon.BONE.getRGB(), img.getRGB(16, 8));
		assertEquals(0, img.getRGB(10, 14) >>> 24);
		assertEquals(0, img.getRGB(0, 0) >>> 24);
	}

	@Test
	public void drawsAtSidebarSize()
	{
		BufferedImage img = SkullIcon.draw(16, SkullIcon.BONE);
		int opaque = 0;
		for (int y = 0; y < 16; y++)
		{
			for (int x = 0; x < 16; x++)
			{
				if ((img.getRGB(x, y) >>> 24) > 0)
				{
					opaque++;
				}
			}
		}
		// Neither blank nor a filled square.
		assertEquals(true, opaque > 60 && opaque < 200);
	}
}
