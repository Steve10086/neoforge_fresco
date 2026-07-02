package com.astune.fresco.client;

import com.astune.fresco.ColoredSquareParticleOptions;
import com.astune.fresco.Fresco;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** ponytail: custom colored square particle, gravity-driven. */
public final class BrushParticleSpawner {

    private BrushParticleSpawner() {}

    /** Spawn a particle with probability {@code size * 0.5}. */
    public static void trySpawn(Level level, Vec3 pos, int color, double size, RandomSource random) {
        if (random.nextDouble() >= size) return;
        spawn(level, pos, color);
    }

    /** Spawn one colored particle at pos. */
    public static void spawn(Level level, Vec3 pos, int color) {
        level.addParticle(
                new ColoredSquareParticleOptions(color),
                pos.x, pos.y, pos.z,
                0.0, 0.0, 0.0
        );
    }
}
