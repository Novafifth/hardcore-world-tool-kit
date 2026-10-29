package com.hardcoregetracker.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * The plugin's skull, drawn in code so it scales to any size and the plugin ships no image for it. The eye sockets, the
 * nose and the gaps between the teeth are see-through, so it works on any background.
 */
public final class SkullIcon
{
	/** Bone white. */
	public static final Color BONE = new Color(0xe9e2d0);

	private SkullIcon()
	{
	}

	/**
	 * @param size  width and height in px
	 * @param bone  the skull's colour
	 */
	public static BufferedImage draw(int size, Color bone)
	{
		BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		double s = size;
		// Cranium and jaw.
		Area skull = new Area(new Ellipse2D.Double(0.09 * s, 0.03 * s, 0.82 * s, 0.72 * s));
		skull.add(new Area(new RoundRectangle2D.Double(0.27 * s, 0.50 * s, 0.46 * s, 0.46 * s, 0.14 * s, 0.14 * s)));
		// Eye sockets.
		skull.subtract(new Area(new Ellipse2D.Double(0.20 * s, 0.30 * s, 0.24 * s, 0.26 * s)));
		skull.subtract(new Area(new Ellipse2D.Double(0.56 * s, 0.30 * s, 0.24 * s, 0.26 * s)));
		// Nose.
		Path2D nose = new Path2D.Double();
		nose.moveTo(0.50 * s, 0.57 * s);
		nose.lineTo(0.44 * s, 0.70 * s);
		nose.lineTo(0.56 * s, 0.70 * s);
		nose.closePath();
		skull.subtract(new Area(nose));
		// Teeth: gaps cut up from the bottom of the jaw.
		double gap = Math.max(1.0, 0.05 * s);
		for (double x : new double[] {0.395, 0.5, 0.605})
		{
			skull.subtract(new Area(new Rectangle2D.Double(x * s - gap / 2, 0.80 * s, gap, 0.20 * s)));
		}
		g.setColor(bone);
		g.fill(skull);
		g.dispose();
		return img;
	}
}
