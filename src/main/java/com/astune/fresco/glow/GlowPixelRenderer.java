package com.astune.fresco.glow;

import com.astune.painter.api.CanvasFace;
import com.astune.painter.api.render.CanvasPixelRenderer;
import com.astune.painter.api.render.RenderContext;
import com.astune.fresco.Fresco;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequence;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 夜光像素渲染器 — 匹配含 "_glow_" 的纹理，以 full-bright 渲染。
 * <p>
 * 检查 context.texture 路径中是否包含 "_glow_"，
 * 匹配时取 max(packedLight, 自身随时间变化的亮度) 绘制 entityTranslucent quad。
 * 返回 true 停止渲染链。
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Fresco.MODID, value = Dist.CLIENT)
public class GlowPixelRenderer implements CanvasPixelRenderer {

    private static final Map<Pair<BlockPos, Vec3>, Integer> LIGHT_MAP = new HashMap<>();
    private static int globalTick = 0;

    @Override
    public boolean canRender(RenderContext context) {
        ResourceLocation tex = context.texture;
        return tex != null && tex.getPath().contains("_fresco/glow_");
    }

    @Override
    public boolean renderFace(RenderContext context) {
        CanvasFace face = context.face;
        ResourceLocation texture = context.texture;
        if (texture == null) return false;
        Pair<BlockPos, Vec3> key = new Pair<>(context.pos, face.corner0());
        if (!LIGHT_MAP.containsKey(key)) LIGHT_MAP.put(key, RandomSource.create().nextInt());

        Vec3[] corners = face.cornerWithOffset(context.offset);

        VertexConsumer vc = context.bufferSource.getBuffer(RenderType.entityTranslucent(texture));
        var last = context.poseStack.last();
        Vec3i normal = face.primaryFace().getNormal();
        float nx = normal.getX();
        float ny = normal.getY();
        float nz = normal.getZ();

        // 自身亮度：用 cos 在 0~2 之间振荡，然后转为 0~1 factor
        int faceTick = LIGHT_MAP.get(key);
        float factor = (float) (1.0 + Math.cos((faceTick + globalTick) * Math.PI / 50)) / 2.0f;
        int selfBlock = Math.round(15 * factor);
        int selfSky   = Math.round(15 * factor);
        int selfBrightness = LightTexture.pack(selfBlock, selfSky);

        // 取 packedLight 和自身亮度中每个分量更大的值
        int envLight = context.packedLight;
        int light = maxLight(envLight, selfBrightness);

        add(vc, last, corners[0], 0, 0, nx, ny, nz, light, context.packedOverlay);
        add(vc, last, corners[1], 1, 0, nx, ny, nz, light, context.packedOverlay);
        add(vc, last, corners[2], 1, 1, nx, ny, nz, light, context.packedOverlay);
        add(vc, last, corners[3], 0, 1, nx, ny, nz, light, context.packedOverlay);

        return true; // 停止渲染链
    }

    /** 两个 packed light 逐分量取 max */
    private static int maxLight(int a, int b) {
        int aBlock = LightTexture.block(a);
        int aSky   = LightTexture.sky(a);
        int bBlock = LightTexture.block(b);
        int bSky   = LightTexture.sky(b);
        return LightTexture.pack(Math.max(aBlock, bBlock), Math.max(aSky, bSky));
    }

    private static void add(VertexConsumer vc, PoseStack.Pose pose, Vec3 pos, float u, float v,
                            float nx, float ny, float nz, int light, int overlay) {
        vc.addVertex(pose, (float) pos.x, (float) pos.y, (float) pos.z)
                .setColor(255,255,255,255)
                .setUv(u,v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        globalTick = (globalTick + 1) % 100;
        LIGHT_MAP.replaceAll((f, v) -> (v + 1) % 100);
    }
}
