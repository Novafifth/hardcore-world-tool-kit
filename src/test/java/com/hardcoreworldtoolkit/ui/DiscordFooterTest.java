package com.hardcoreworldtoolkit.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import java.awt.Container;
import java.awt.image.BufferedImage;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class DiscordFooterTest
{
	@Test
	public void buttonOpensTheInvite() throws Exception
	{
		int[] opened = {0};
		SwingUtilities.invokeAndWait(() ->
		{
			DiscordFooter footer = new DiscordFooter(() -> opened[0]++);
			JButton button = (JButton) ((Container) footer.getComponent()).getComponent(0);
			assertEquals(DiscordFooter.BUTTON_TEXT, button.getText());
			assertNotNull(button.getIcon());
			button.doClick();
		});
		assertEquals(1, opened[0]);
		assertTrue(DiscordFooter.INVITE_URL, DiscordFooter.INVITE_URL.startsWith("https://discord.com/invite/"));
	}

	@Test
	public void chatBubbleWithSeeThroughDots()
	{
		BufferedImage img = Icons.drawChat(28, PanelComponents.DISCORD);
		// The bubble is blurple; its middle dot, the corner and the space right of the tail are see-through.
		assertEquals(PanelComponents.DISCORD.getRGB(), img.getRGB(14, 5));
		assertEquals(0, img.getRGB(14, 11) >>> 24);
		assertEquals(0, img.getRGB(0, 0) >>> 24);
		assertEquals(0, img.getRGB(24, 26) >>> 24);
	}
}
