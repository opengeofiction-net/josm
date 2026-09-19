// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.draw;

import java.awt.Composite;
import java.awt.CompositeContext;
import java.awt.RenderingHints;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.EnumMap;
import java.util.Map;

/**
 * A {@link Composite} implementing the common "blend modes" of image editors (multiply, burn, hard light,
 * difference, divide), which the standard {@link java.awt.AlphaComposite} does not provide.
 * <p>
 * The source alpha (including the coverage produced by antialiasing) controls how strongly the blended color
 * replaces the destination. It works on any color model, but is implemented per pixel and thus only meant for
 * drawing thin shapes such as grid lines. It must be used on {@link java.awt.image.BufferedImage} backed graphics,
 * since hardware accelerated pipelines do not support custom composites.
 * @since xxx
 */
public final class BlendComposite implements Composite {

    /**
     * The supported blend modes.
     */
    public enum Mode {
        /** Normal alpha blending (like {@link java.awt.AlphaComposite#SrcOver}) */
        NORMAL,
        /** Multiplies source and destination: darkens, white is neutral */
        MULTIPLY,
        /** Color burn: darkens and increases contrast, white is neutral */
        BURN,
        /** Hard light: multiplies for dark sources, screens for light sources; strong contrast */
        HARD_LIGHT,
        /** Absolute difference between source and destination: always visible on any background */
        DIFFERENCE,
        /** Divides destination by source: lightens, white is neutral, dark sources give bright lines */
        DIVIDE
    }

    private static final Map<Mode, BlendComposite> INSTANCES = new EnumMap<>(Mode.class);

    private final Mode mode;

    private BlendComposite(Mode mode) {
        this.mode = mode;
    }

    /**
     * Returns the composite for the given mode.
     * @param mode the blend mode
     * @return the composite
     */
    public static synchronized BlendComposite getInstance(Mode mode) {
        return INSTANCES.computeIfAbsent(mode, BlendComposite::new);
    }

    /**
     * Returns the blend mode of this composite.
     * @return the blend mode
     */
    public Mode getMode() {
        return mode;
    }

    @Override
    public CompositeContext createContext(ColorModel srcColorModel, ColorModel dstColorModel, RenderingHints hints) {
        return new BlendContext(mode, srcColorModel, dstColorModel);
    }

    /**
     * Blends one color channel.
     * @param mode blend mode
     * @param s source channel value (0-255)
     * @param d destination channel value (0-255)
     * @return blended value (0-255)
     */
    static int blend(Mode mode, int s, int d) {
        switch (mode) {
        case MULTIPLY:
            return s * d / 255;
        case BURN:
            return s == 0 ? 0 : 255 - Math.min(255, (255 - d) * 255 / s);
        case HARD_LIGHT:
            return s < 128 ? 2 * s * d / 255 : 255 - (255 - d) * (510 - 2 * s) / 255;
        case DIFFERENCE:
            return Math.abs(s - d);
        case DIVIDE:
            return s == 0 ? 255 : Math.min(255, d * 255 / s);
        case NORMAL:
        default:
            return s;
        }
    }

    private static final class BlendContext implements CompositeContext {
        private final Mode mode;
        private final ColorModel srcColorModel;
        private final ColorModel dstColorModel;

        BlendContext(Mode mode, ColorModel srcColorModel, ColorModel dstColorModel) {
            this.mode = mode;
            this.srcColorModel = srcColorModel;
            this.dstColorModel = dstColorModel;
        }

        @Override
        public void compose(Raster src, Raster dstIn, WritableRaster dstOut) {
            int w = Math.min(src.getWidth(), dstIn.getWidth());
            int h = Math.min(src.getHeight(), dstIn.getHeight());
            Object srcPixel = null;
            Object dstPixel = null;
            Object outPixel = null;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    srcPixel = src.getDataElements(x, y, srcPixel);
                    dstPixel = dstIn.getDataElements(x, y, dstPixel);
                    int s = srcColorModel.getRGB(srcPixel);
                    int d = dstColorModel.getRGB(dstPixel);
                    int sa = s >>> 24;
                    int result;
                    if (sa == 0) {
                        result = d;
                    } else {
                        int da = d >>> 24;
                        int r = mix(blend(mode, (s >> 16) & 0xff, (d >> 16) & 0xff), (d >> 16) & 0xff, sa);
                        int g = mix(blend(mode, (s >> 8) & 0xff, (d >> 8) & 0xff), (d >> 8) & 0xff, sa);
                        int b = mix(blend(mode, s & 0xff, d & 0xff), d & 0xff, sa);
                        int a = Math.max(sa, da);
                        result = (a << 24) | (r << 16) | (g << 8) | b;
                    }
                    outPixel = dstColorModel.getDataElements(result, outPixel);
                    dstOut.setDataElements(x, y, outPixel);
                }
            }
        }

        /** linear interpolation between d (alpha 0) and s (alpha 255) */
        private static int mix(int s, int d, int alpha) {
            return d + (s - d) * alpha / 255;
        }

        @Override
        public void dispose() {
            // nothing to dispose
        }
    }
}
