package com.astune.fresco.item;

import com.astune.painter.api.BlendMode;
import com.astune.painter.api.PaintPattern;
import com.astune.painter.api.PixelProvider;

import java.util.Random;

public final class CircularBrushPattern {

    public static final double DEFAULT_BLUR = 0.1;

    private CircularBrushPattern() {}

    public static PaintPattern create(double size, float opacity, BlendMode blendMode, int color) {
        return create(size, opacity, blendMode, color, DEFAULT_BLUR);
    }

    public static PaintPattern create(double size, float opacity, BlendMode blendMode, int color, double blur) {
        return create(size, opacity, blendMode, color, blur, 1.0);
    }

    /** 带密度的圆形 pattern（无 tint）。 */
    public static PaintPattern create(double size, float opacity, BlendMode blendMode,
                                       int color, double blur, double density) {
        return create(size, opacity, blendMode, color, blur, density, 0);
    }

    /**
     * @param tint 底色 (ARGB)，0=密度跳过返回 null；非0=密度跳过返回带明暗变化的底色
     */
    public static PaintPattern create(double size, float opacity, BlendMode blendMode,
                                       int color, double blur, double density, int tint) {
        if (size <= 0) return null;

        double inner = 1.0 - blur;
        double diameter = size * 2;
        Random rng = new Random();

        return new PaintPattern(diameter, diameter, new PixelProvider() {
            @Override
            public BlendMode getBlendMode() { return blendMode; }

            @Override
            public Integer getPixel(double dx, double dy) {
                double cx = dx - size;
                double cy = dy - size;
                double dist = Math.sqrt(cx * cx + cy * cy) / size;

                if (dist > 1.0) return null;

                // 核心着色
                int alpha;
                if (dist <= inner) {
                    alpha = 255;
                } else {
                    double fade = 1.0 - (dist - inner) / (1.0 - inner);
                    alpha = (int) (255 * fade);
                }

                alpha = (int) (alpha * opacity);
                if (alpha <= 0) return null;

                // 密度跳过
                if (density < 1.0 && rng.nextFloat() > density) {
                    if (tint == 0) return null;
                    // 同系数亮度变化，保持色调
                    float factor = 0.7f + rng.nextFloat() * 0.6f;
                    int r = (int) (((tint >> 16) & 0xFF) * factor);
                    int g = (int) (((tint >> 8) & 0xFF) * factor);
                    int b = (int) ((tint & 0xFF) * factor);
                    if (r > 255) r = 255;
                    if (g > 255) g = 255;
                    if (b > 255) b = 255;
                    return (alpha << 24) | (r << 16) | (g << 8) | b;
                }

                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                return (alpha << 24) | (r << 16) | (g << 8) | b;
            }
        });
    }

}
