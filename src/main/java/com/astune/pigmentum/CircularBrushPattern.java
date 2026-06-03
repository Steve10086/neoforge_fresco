package com.astune.pigmentum;

import com.astune.painter.api.BlendMode;
import com.astune.painter.api.PaintPattern;
import com.astune.painter.api.PixelProvider;

/**
 * 圆形画笔图案 — 从画笔参数动态生成 PaintPattern。
 * <p>
 * 与具体画笔解耦，任何 IPaintProvider 都可以通过此类构造圆形 pattern。
 */
public class CircularBrushPattern {

    /** Feather 固定值：过渡区起始比例 (0~1) */
    public static final double FEATHER_INNER = 0.9;

    private CircularBrushPattern() {}

    /**
     * 根据参数创建一个圆形 PaintPattern。
     *
     * @param size      画笔尺寸（半径，物品组件 BRUSH_SIZE）
     * @param opacity   不透明度（0~1，物品组件 OPACITY）
     * @param blendMode 像素混合模式
     * @param color     ARGB 绘制颜色
     * @return 圆形 pattern，或 size≤0 时返回 null
     */
    public static PaintPattern create(double size, float opacity, BlendMode blendMode, int color) {
        if (size <= 0) return null;

        double diameter = size * 2;
        return new PaintPattern(diameter, diameter, new PixelProvider() {
            @Override
            public BlendMode getBlendMode() {
                return blendMode;
            }

            @Override
            public Integer getPixel(double dx, double dy) {
                // 将 dx,dy 从 [0, diameter] 映射到以圆心为原点的 [-size, size]
                double cx = dx - size;
                double cy = dy - size;
                double dist = Math.sqrt(cx * cx + cy * cy) / size;

                // 圆形外不绘制
                if (dist > 1.0) return null;

                // 计算 alpha
                int alpha;
                if (dist <= FEATHER_INNER) {
                    alpha = 255;
                } else {
                    // 过渡区：线性衰减
                    double fade = 1.0 - (dist - FEATHER_INNER) / (1.0 - FEATHER_INNER);
                    alpha = (int) (255 * fade);
                }

                alpha = (int) (alpha * opacity);
                if (alpha <= 0) return null;

                int r = (color >> 16) & 0xFF;
                int g = (color >> 8) & 0xFF;
                int b = color & 0xFF;
                return (alpha << 24) | (r << 16) | (g << 8) | b;
            }
        });
    }
}
