package com.hardcoreworldtoolkit.ui;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.ColorScheme;

/**
 * The strip pinned under the panel: a thin line, a button that opens the Hardcore World Tool Kit Discord and a heart that
 * opens the support and links page. EDT only.
 */
final class DiscordFooter
{
	static final String INVITE_URL = "https://discord.com/invite/a2dfkSYw2j";
	static final String BUTTON_TEXT = "Join the Discord";
	static final String SUPPORT_URL = "https://hardcore.malkana.dev/links?from=plugin";

	private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));

	/**
	 * @param open    opens {@link #INVITE_URL} in the browser
	 * @param support opens {@link #SUPPORT_URL} in the browser
	 */
	DiscordFooter(Runnable open, Runnable support)
	{
		panel.setBorder(new CompoundBorder(new MatteBorder(1, 0, 0, 0, ColorScheme.DARKER_GRAY_COLOR),
			new EmptyBorder(6, 0, 6, 0)));
		JButton button = new JButton(BUTTON_TEXT, Icons.chat());
		button.setIconTextGap(6);
		button.setFocusPainted(false);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.setToolTipText("Opens the Hardcore World Tool Kit Discord in your browser");
		button.addActionListener(e -> open.run());
		panel.add(button);

		JButton heart = new JButton(Icons.heart());
		heart.setFocusPainted(false);
		heart.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		heart.setToolTipText("Support the plugin");
		heart.setPreferredSize(new Dimension(button.getPreferredSize().height, button.getPreferredSize().height));
		heart.addActionListener(e -> support.run());
		panel.add(heart);
	}

	JComponent getComponent()
	{
		return panel;
	}
}
