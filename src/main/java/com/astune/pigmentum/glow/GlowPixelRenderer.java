package com.astune.pigmentum.glow;

import com.astune.painter.api.CanvasFace;
import com.astune.painter.api.render.CanvasPixelRenderer;
import com.astune.painter.api.render.RenderContext;
import com.astune.painter.client.CanvasBlockEntityRenderer;
import com.astune.painter.client.PaintInputHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * 夜光像素渲染器 — 匹配含 "_glow_" 的纹理，以 full-bright 渲染。
 * <p>
 * 检查 context.texture 路径中是否包含 "_glow_"，
 * 匹配时使用 packedLight = 0x00F000F0（全亮度）绘制 entityTranslucent quad。
 * 返回 true 停止渲染链。
 */
public class GlowPixelRenderer implements CanvasPixelRenderer {
    @Override
    public boolean canRender(RenderContext context) {
        ResourceLocation tex = context.texture;
        return tex != null && tex.getPath().contains("_glow_");
    }

    @Override
    public boolean renderFace(RenderContext context) {
        //System.out.println("render glow");
        CanvasFace face = context.face;
        ResourceLocation texture = context.texture;
        if (texture == null) return false;

        Vec3[] corners = face.cornerWithOffset();

        VertexConsumer vc = context.bufferSource.getBuffer(RenderType.entityTranslucent(texture));
        var last = context.poseStack.last();
        Vec3i normal = face.primaryFace().getNormal();
        float nx = normal.getX();
        float ny = normal.getY();
        float nz = normal.getZ();

        // Full brightness — makes the glow emissive
        int light = 0x00F000F0;

        add(vc, last, corners[0], 0, 0, nx, ny, nz, light, context.packedOverlay);
        add(vc, last, corners[1], 1, 0, nx, ny, nz, light, context.packedOverlay);
        add(vc, last, corners[2], 1, 1, nx, ny, nz, light, context.packedOverlay);
        add(vc, last, corners[3], 0, 1, nx, ny, nz, light, context.packedOverlay);

        return true; // 停止渲染链
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
}
