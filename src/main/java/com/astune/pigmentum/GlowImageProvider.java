package com.astune.pigmentum;

import com.astune.painter.api.CanvasFace;
import com.astune.painter.api.PixelMatrix;
import com.astune.painter.api.imageProvider.CanvasImageProvider;
import com.astune.painter.api.imageProvider.ImageProviderContext;
import com.mojang.blaze3d.platform.NativeImage;

/**
 * 夜光纹理生成器 — 从 "glow" 效果层读取数据，生成仅含发光信息的 NativeImage。
 * <p>
 * 返回图像：每个像素的 alpha = glow 值, RGB = 白色。
 * 若 CanvasFace 无 "glow" 层则返回 null 跳过纹理生成。
 */
public class GlowImageProvider implements CanvasImageProvider {

    @Override
    public String name() {
        return "glow";
    }

    @Override
    public boolean canProvide(ImageProviderContext context) {
        return context.face != null && context.face.getEffectLayer("glow") != null;
    }

    @Override
    public NativeImage createImage(CanvasFace face) {
        //System.out.println("glow image");
        byte[] glowLayer = face.getEffectLayer("glow");
        if (glowLayer == null) return null;

        PixelMatrix matrix = face.pixels();
        if (matrix == null || matrix.getWidth() <= 0 || matrix.getHeight() <= 0) return null;
        int w = matrix.getWidth(), h = matrix.getHeight();
        NativeImage image = null;
        try {
            image = new NativeImage(w, h, true);
            image.getPixelRGBA(0, 0); // 触发分配检查
        } catch (Exception e) {
            if (image != null) image.close();
            return null;
        }

        try {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (glowLayer[y * w + x] == (byte)255){
                        int argb = matrix.getPixel(x, y);
                        int a = (argb >> 24) & 0xFF;
                        int r = (argb >> 16) & 0xFF;
                        int g = (argb >> 8) & 0xFF;
                        int b = argb & 0xFF;

                        int bgr = (b << 16) | (g << 8) | r;
                        int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                        if (bgr != 0 && a == 0) {
                            abgr = 255 << 24 | bgr;
                        }
                        image.setPixelRGBA(x, y, abgr);
                    }
                }
            }
        } catch (Exception e) {
            image.close();
            return null;
        }
        return image;
    }
}
