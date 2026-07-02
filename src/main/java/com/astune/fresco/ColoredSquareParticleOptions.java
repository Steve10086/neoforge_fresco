package com.astune.fresco;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ColoredSquareParticleOptions(int color) implements ParticleOptions {

    static final MapCodec<ColoredSquareParticleOptions> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    Codec.INT.fieldOf("color").forGetter(ColoredSquareParticleOptions::color)
            ).apply(instance, ColoredSquareParticleOptions::new));

    static StreamCodec<RegistryFriendlyByteBuf, ColoredSquareParticleOptions> streamCodec(
            ParticleType<ColoredSquareParticleOptions> type) {
        return StreamCodec.composite(
                ByteBufCodecs.VAR_INT,
                ColoredSquareParticleOptions::color,
                ColoredSquareParticleOptions::new
        );
    }

    @Override
    public ParticleType<?> getType() {
        return Fresco.COLORED_SQUARE_PARTICLE.get();
    }
}
