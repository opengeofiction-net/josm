// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.draw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.gui.draw.BlendComposite.Mode;

/**
 * Unit tests of {@link BlendComposite}.
 */
class BlendCompositeTest {

    private static int paint(int imageType, Color background, Color paint, Mode mode) {
        BufferedImage img = new BufferedImage(4, 4, imageType);
        Graphics2D g = img.createGraphics();
        g.setColor(background);
        g.fillRect(0, 0, 4, 4);
        g.setComposite(BlendComposite.getInstance(mode));
        g.setColor(paint);
        g.fillRect(1, 1, 2, 2);
        g.dispose();
        // the untouched pixel keeps the background
        assertEquals(background.getRGB() & 0xffffff, img.getRGB(0, 0) & 0xffffff);
        return img.getRGB(1, 1) & 0xffffff;
    }

    /**
     * Blend modes on an opaque source, on both an image without and with alpha channel.
     * @param imageType image type
     */
    @ParameterizedTest
    @ValueSource(ints = {BufferedImage.TYPE_3BYTE_BGR, BufferedImage.TYPE_INT_RGB, BufferedImage.TYPE_INT_ARGB})
    void testModes(int imageType) {
        Color bg = new Color(200, 100, 50);
        Color fg = new Color(128, 255, 0);
        assertEquals(0x80ff00, paint(imageType, bg, fg, Mode.NORMAL));
        assertEquals(new Color(200 * 128 / 255, 100, 0).getRGB() & 0xffffff, paint(imageType, bg, fg, Mode.MULTIPLY));
        assertEquals(new Color(146, 100, 0).getRGB() & 0xffffff, paint(imageType, bg, fg, Mode.BURN));
        assertEquals(new Color(201, 255, 0).getRGB() & 0xffffff, paint(imageType, bg, fg, Mode.HARD_LIGHT));
        assertEquals(new Color(72, 155, 50).getRGB() & 0xffffff, paint(imageType, bg, fg, Mode.DIFFERENCE));
        assertEquals(new Color(255, 100, 255).getRGB() & 0xffffff, paint(imageType, bg, fg, Mode.DIVIDE));
    }

    /**
     * A translucent source only partially applies the blended color.
     */
    @Test
    void testAlpha() {
        Color bg = new Color(200, 200, 200);
        // half transparent black, multiply: 200 -> 0 at full alpha -> 100 at half alpha
        int rgb = paint(BufferedImage.TYPE_INT_RGB, bg, new Color(0, 0, 0, 128), Mode.MULTIPLY);
        assertEquals(100, (rgb >> 16) & 0xff, 1);
        assertEquals(100, rgb & 0xff, 1);
        // fully transparent: nothing changes
        assertEquals(bg.getRGB() & 0xffffff, paint(BufferedImage.TYPE_INT_RGB, bg, new Color(0, 0, 0, 0), Mode.DIFFERENCE));
    }

    /**
     * White is neutral for multiply, burn and divide; black is neutral for difference
     */
    @Test
    void testNeutralColors() {
        Color bg = new Color(12, 34, 56);
        assertEquals(bg.getRGB() & 0xffffff, paint(BufferedImage.TYPE_INT_RGB, bg, Color.WHITE, Mode.MULTIPLY));
        assertEquals(bg.getRGB() & 0xffffff, paint(BufferedImage.TYPE_INT_RGB, bg, Color.WHITE, Mode.BURN));
        assertEquals(bg.getRGB() & 0xffffff, paint(BufferedImage.TYPE_INT_RGB, bg, Color.WHITE, Mode.DIVIDE));
        assertEquals(bg.getRGB() & 0xffffff, paint(BufferedImage.TYPE_INT_RGB, bg, Color.BLACK, Mode.DIFFERENCE));
        // black burns everything to black, white hard light gives white
        assertEquals(0, paint(BufferedImage.TYPE_INT_RGB, bg, Color.BLACK, Mode.BURN));
        assertEquals(0xffffff, paint(BufferedImage.TYPE_INT_RGB, bg, Color.WHITE, Mode.HARD_LIGHT));
    }

    /**
     * Instances are shared per mode
     */
    @Test
    void testInstances() {
        assertSame(BlendComposite.getInstance(Mode.MULTIPLY), BlendComposite.getInstance(Mode.MULTIPLY));
        assertEquals(Mode.DIVIDE, BlendComposite.getInstance(Mode.DIVIDE).getMode());
    }
}
