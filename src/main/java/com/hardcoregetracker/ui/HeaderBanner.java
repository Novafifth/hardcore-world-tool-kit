package com.hardcoregetracker.ui;

import static com.hardcoregetracker.ui.PanelComponents.HC_DARK;
import static com.hardcoregetracker.ui.PanelComponents.HC_RED;
import static com.hardcoregetracker.ui.PanelComponents.HC_TEXT;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.FontManager;

/**
 * The strip at the top of the panel: the skull and the plugin's name on a dark red band. EDT only.
 */
final class HeaderBanner
{
	private static final Color EDGE = new Color(0x4a2420);

	private final JPanel panel = new JPanel(new BorderLayout(7, 0));

	HeaderBanner()
	{
		panel.setBackground(HC_DARK);
		panel.setBorder(new CompoundBorder(new LineBorder(EDGE, 1),
			new CompoundBorder(new MatteBorder(0, 3, 0, 0, HC_RED), new EmptyBorder(4, 6, 4, 8))));
		panel.add(new JLabel(Icons.skull()), BorderLayout.WEST);

		JPanel title = new JPanel();
		title.setLayout(new BoxLayout(title, BoxLayout.X_AXIS));
		title.setOpaque(false);
		title.add(word("HARDCORE", HC_TEXT));
		title.add(Box.createHorizontalStrut(4));
		title.add(word("GETracker", Color.WHITE));
		panel.add(title, BorderLayout.CENTER);

		panel.setToolTipText("Hardcore GETracker: Grand Exchange prices for the Hardcore Worlds");
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
	}

	JComponent getComponent()
	{
		return panel;
	}

	private static JLabel word(String text, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(color);
		return l;
	}
}
