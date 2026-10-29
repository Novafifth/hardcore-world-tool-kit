package com.hardcoregetracker.ui;

import static com.hardcoregetracker.ui.PanelComponents.column;
import static com.hardcoregetracker.ui.PanelComponents.linkLabel;
import static com.hardcoregetracker.ui.PanelComponents.primaryButton;
import static com.hardcoregetracker.ui.PanelComponents.smallLabel;
import static com.hardcoregetracker.ui.PanelComponents.wrapText;
import com.hardcoregetracker.HardcoreGeTrackerConfig;
import com.hardcoregetracker.Onboarding;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.Objects;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * "Get started" card at the top of the home view: connect (what's shared; RuneLite's third-party warning is shown as a
 * confirmation when Connect is clicked) and the GE History import steps, then a one-time "Imported N trades" line.
 * EDT only.
 */
class WelcomeCard
{
	static final String SHARED_TEXT = "Shared: item, price, quantity, time, a hashed account ID and a random install ID."
		+ " Never your name. IP addresses are never stored.";

	/** Asks before connecting; returns true to go ahead. Tests replace the dialog. */
	interface ConnectConfirm
	{
		boolean confirm(Component parent);
	}

	/**
	 * RuneLite's required third-party warning, shown when Connect is clicked. The settings page shows the same text
	 * through the config item's warning; the card's Connect button doesn't go through that dialog, so it asks here.
	 */
	static final ConnectConfirm WARNING_DIALOG = parent -> JOptionPane.showConfirmDialog(parent,
		HardcoreGeTrackerConfig.THIRD_PARTY_WARNING + ".\n\nThe Hardcore GETracker server never stores your IP address."
			+ "\n\nConnect now?",
		"Hardcore GETracker", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;

	private final Runnable onConnect;
	private final ConnectConfirm confirmConnect;
	private final Runnable onPrivacy;
	private final Runnable onDismissNotice;
	/** Outer wrapper: the card plus the gap below it, so a hidden card leaves no gap. */
	private final JPanel wrapper = column();
	private final JPanel card = column();

	private Onboarding.Step shownStep;
	private boolean shownNeedsLogin;
	private Integer shownNotice;

	/**
	 * @param onConnect       sets the connected setting
	 * @param onPrivacy       opens the privacy notice
	 * @param onDismissNotice forgets the import notice (the "x" button)
	 */
	WelcomeCard(Runnable onConnect, Runnable onPrivacy, Runnable onDismissNotice)
	{
		this(onConnect, onPrivacy, onDismissNotice, WARNING_DIALOG);
	}

	/** @param confirmConnect asks before connecting (RuneLite's third-party warning) */
	WelcomeCard(Runnable onConnect, Runnable onPrivacy, Runnable onDismissNotice, ConnectConfirm confirmConnect)
	{
		this.onConnect = onConnect;
		this.confirmConnect = confirmConnect;
		this.onPrivacy = onPrivacy;
		this.onDismissNotice = onDismissNotice;
		card.setOpaque(true);
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new CompoundBorder(new MatteBorder(0, 3, 0, 0, PanelComponents.HC_RED), new EmptyBorder(8, 8, 8, 8)));
		wrapper.add(card);
		wrapper.add(Box.createVerticalStrut(8));
		wrapper.setVisible(false);
	}

	JComponent getComponent()
	{
		return wrapper;
	}

	/** Show the given state. Rebuilds only when something changed. */
	void update(Onboarding.Step step, boolean needsLogin, Integer notice)
	{
		if (step == shownStep && needsLogin == shownNeedsLogin && Objects.equals(notice, shownNotice))
		{
			return;
		}
		shownStep = step;
		shownNeedsLogin = needsLogin;
		shownNotice = notice;

		card.removeAll();
		boolean connect = step == Onboarding.Step.CONNECT || step == Onboarding.Step.CONNECT_AND_IMPORT;
		boolean importSteps = step == Onboarding.Step.IMPORT || step == Onboarding.Step.CONNECT_AND_IMPORT;
		boolean showNotice = notice != null && step != Onboarding.Step.HIDDEN;
		if (connect || importSteps)
		{
			JLabel title = new JLabel("Get started");
			title.setFont(FontManager.getRunescapeBoldFont());
			title.setForeground(Color.WHITE);
			title.setAlignmentX(Component.LEFT_ALIGNMENT);
			card.add(title);
			card.add(Box.createVerticalStrut(4));
		}
		if (showNotice)
		{
			card.add(noticeRow(notice));
			if (connect || importSteps)
			{
				card.add(Box.createVerticalStrut(6));
			}
		}
		if (connect)
		{
			addConnectBlock();
		}
		if (importSteps)
		{
			if (connect)
			{
				card.add(Box.createVerticalStrut(8));
				card.add(divider());
				card.add(Box.createVerticalStrut(8));
			}
			addImportBlock(needsLogin);
		}
		wrapper.setVisible(connect || importSteps || showNotice);
		wrapper.revalidate();
		wrapper.repaint();
	}

	private void addConnectBlock()
	{
		card.add(wrapText("See Hardcore World prices from every plugin user and share your trades.", ColorScheme.TEXT_COLOR));
		card.add(Box.createVerticalStrut(4));
		card.add(wrapText(SHARED_TEXT, ColorScheme.TEXT_COLOR));
		card.add(Box.createVerticalStrut(6));
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);
		JButton connect = primaryButton("Connect");
		connect.addActionListener(e ->
		{
			if (confirmConnect.confirm(card))
			{
				onConnect.run();
			}
		});
		row.add(connect, BorderLayout.WEST);
		JLabel privacy = linkLabel("Privacy", onPrivacy);
		row.add(privacy, BorderLayout.CENTER);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		card.add(row);
	}

	private void addImportBlock(boolean needsLogin)
	{
		card.add(line("Import your GE History:", Color.WHITE));
		// One component type for all steps, so they line up.
		card.add(wrapText("1. Open the Grand Exchange.", ColorScheme.TEXT_COLOR));
		card.add(wrapText("2. Click the History button.", ColorScheme.TEXT_COLOR));
		card.add(wrapText("3. Done. Your past trades are added.", ColorScheme.TEXT_COLOR));
		if (needsLogin)
		{
			card.add(Box.createVerticalStrut(2));
			card.add(line("Log in on a Hardcore World first.", ColorScheme.LIGHT_GRAY_COLOR));
		}
	}

	private JComponent noticeRow(int count)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setOpaque(false);
		row.add(smallLabel(noticeText(count), PanelComponents.GOOD), BorderLayout.CENTER);
		JButton close = new JButton("x");
		close.setFont(FontManager.getRunescapeSmallFont());
		close.setMargin(new Insets(0, 4, 0, 4));
		close.setFocusPainted(false);
		close.setToolTipText("Dismiss");
		close.addActionListener(e -> onDismissNotice.run());
		row.add(close, BorderLayout.EAST);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		return row;
	}

	/** The one-time line after the first GE History import; an empty history still counts as imported. */
	static String noticeText(int count)
	{
		return count == 0 ? "GE History read: no trades to import"
			: "Imported " + count + " trade" + (count == 1 ? "" : "s") + " from GE History";
	}

	/** A thin line between the card's two parts. */
	private static JComponent divider()
	{
		JPanel line = new JPanel();
		line.setBackground(ColorScheme.MEDIUM_GRAY_COLOR);
		line.setPreferredSize(new Dimension(1, 1));
		line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		line.setAlignmentX(Component.LEFT_ALIGNMENT);
		return line;
	}

	private static JComponent line(String text, Color color)
	{
		JLabel l = smallLabel(text, color);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}
}
