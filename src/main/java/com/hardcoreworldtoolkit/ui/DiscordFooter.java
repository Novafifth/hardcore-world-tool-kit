package com.hardcoreworldtoolkit.ui;

import java.awt.Cursor;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * The strip pinned under the panel: a thin line and the Market Dashboard button. EDT only.
 */
final class DiscordFooter
{
	static final String INVITE_URL = "https://discord.com/invite/a2dfkSYw2j";

	private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));

	DiscordFooter(JButton button)
	{
		panel.setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, ColorScheme.DARKER_GRAY_COLOR),
			new EmptyBorder(6, 0, 6, 0)));
		button.setIconTextGap(6);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		panel.add(button);
	}

	JComponent getComponent()
	{
		return panel;
	}
}
