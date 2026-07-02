package com.astune.fresco.client;

import com.astune.fresco.ColoredSquareParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

public class ColoredSquareParticle extends TextureSheetParticle {

    public ColoredSquareParticle(ClientLevel level, double x, double y, double z,
                                  ColoredSquareParticleOptions options, SpriteSet sprites) {
        super(level, x, y, z, 0, 0, 0);
        this.gravity = 1.0f;
        this.friction = 1.0f;
        this.lifetime = 5;
        this.hasPhysics = true;
        int color = options.color();
        this.rCol = ((color >> 16) & 0xFF) / 255f;
        this.gCol = ((color >> 8) & 0xFF) / 255f;
        this.bCol = (color & 0xFF) / 255f;
        this.alpha = ((color >> 24) & 0xFF) / 255f;
        this.quadSize = 0.03f;
        this.setSize(0.03f, 0.03f);
        pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }
}
