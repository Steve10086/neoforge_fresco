package com.astune.fresco.screen.widget;

import java.util.Locale;

/** Small HSV helpers shared by the palette widgets and screen. */
public final class ColorPickerMath {
    private ColorPickerMath() {
    }

    public static int fromHsv(double hue, double saturation, double value) {
        hue = wrap(hue);
        saturation = clamp01(saturation);
        value = clamp01(value);

        double scaledHue = hue * 6.0;
        int sector = (int) Math.floor(scaledHue) % 6;
        double fraction = scaledHue - Math.floor(scaledHue);
        double p = value * (1.0 - saturation);
        double q = value * (1.0 - fraction * saturation);
        double t = value * (1.0 - (1.0 - fraction) * saturation);

        double red;
        double green;
        double blue;
        switch (sector) {
            case 0 -> { red = value; green = t; blue = p; }
            case 1 -> { red = q; green = value; blue = p; }
            case 2 -> { red = p; green = value; blue = t; }
            case 3 -> { red = p; green = q; blue = value; }
            case 4 -> { red = t; green = p; blue = value; }
            default -> { red = value; green = p; blue = q; }
        }

        return 0xFF000000
                | ((int) Math.round(red * 255.0) << 16)
                | ((int) Math.round(green * 255.0) << 8)
                | (int) Math.round(blue * 255.0);
    }

    /** Returns hue, saturation and value in the inclusive range 0..1. */
    public static double[] toHsv(int argb) {
        double red = ((argb >> 16) & 0xFF) / 255.0;
        double green = ((argb >> 8) & 0xFF) / 255.0;
        double blue = (argb & 0xFF) / 255.0;

        double max = Math.max(red, Math.max(green, blue));
        double min = Math.min(red, Math.min(green, blue));
        double delta = max - min;

        double hue = 0.0;
        if (delta > 0.0) {
            if (max == red) {
                hue = ((green - blue) / delta) % 6.0;
            } else if (max == green) {
                hue = (blue - red) / delta + 2.0;
            } else {
                hue = (red - green) / delta + 4.0;
            }
            hue /= 6.0;
            if (hue < 0.0) hue += 1.0;
        }

        double saturation = max == 0.0 ? 0.0 : delta / max;
        return new double[] { hue, saturation, max };
    }

    public static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static String toHex(int argb) {
        return String.format(Locale.ROOT, "#%06X", argb & 0x00FFFFFF);
    }

    private static double wrap(double value) {
        value %= 1.0;
        return value < 0.0 ? value + 1.0 : value;
    }
}
