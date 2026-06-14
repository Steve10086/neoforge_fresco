package com.astune.fresco.item;

import com.astune.fresco.glow.GlowImageProvider;
import com.astune.painter.api.blend.BlendContext;
import com.astune.painter.api.blend.BlendFunction;
import com.astune.painter.api.BlendMode;
import com.astune.painter.registry.ModDataComponents;

/**
 * 夜光画笔 — 继承 CustomPaintbrush，额外向每个绘制像素写入 glow 效果层。
 * <p>
 * 效果层 key "glow" 会被 GlowImageProvider + GlowPixelRenderer 链路消费，
 * 最终以 full-bright 渲染出夜光效果。
 */
public class GlowPaintbrush extends CustomPaintbrush {

    public GlowPaintbrush() {
        super(); // 通过 CustomPaintbrush 构造器注册 IPaintProvider
    }

    /**
     * 返回自定义 BlendFunction：
     * <ol>
     *   <li>先委托当前混合模式的默认函数处理颜色</li>
     *   <li>再写入 glow 效果值 (255 = 最大亮度)</li>
     * </ol>
     */
    @Override
    public BlendFunction getCustomBlendFunction(net.minecraft.world.item.ItemStack brushStack) {
        // 从物品组件读取当前混合模式
        String modeStr = brushStack.getOrDefault(ModDataComponents.BLEND_MODE.get(), BlendMode.OVERWRITE.name());
        BlendMode mode = safeBlendMode(modeStr);

        return new BlendFunction() {
            @Override
            public boolean apply(BlendContext ctx) {
                // 1. 先执行默认混合
                boolean changed = mode.getDefaultFunction().apply(ctx);

                // 2. 写入 glow 效果层（固定值 255 = 全亮）
                ctx.setEffect("fresco/glow", 255);
                //System.out.println(ctx);

                return changed;
            }
        };
    }
}
