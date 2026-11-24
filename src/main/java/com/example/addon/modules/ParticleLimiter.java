package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.phys.Vec3;

/** Limits live client particles at ParticleEngine.add, not through vanilla graphics options. */
public class ParticleLimiter extends Module {
    private static volatile ParticleLimiter INSTANCE;
    private static volatile int trackedParticleCount;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> maxParticles = sgGeneral.add(new IntSetting.Builder()
        .name("max-particles")
        .description("Maximum number of live client particles allowed.")
        .defaultValue(900)
        .range(50, 6000)
        .build()
    );
    private final Setting<Integer> maxDistance = sgGeneral.add(new IntSetting.Builder()
        .name("max-distance")
        .description("Particles farther from the camera are discarded at spawn.")
        .defaultValue(48)
        .range(8, 128)
        .build()
    );

    public ParticleLimiter() {
        super(AddonTemplate.FIX_LAG_CATEGORY, "particle-limiter",
            "Caps live particle count and discards distant particles when they are spawned.");
        INSTANCE = this;
    }

    public static void beginTick(int currentCount) {
        trackedParticleCount = currentCount;
    }

    public static boolean allowParticle(Particle particle) {
        ParticleLimiter module = INSTANCE;
        if (module == null || !module.isActive()) return true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return true;
        if (trackedParticleCount >= module.maxParticles.get()) return false;

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 particlePos = particle.getBoundingBox().getCenter();
        double maxDistance = module.maxDistance.get();
        if (particlePos.distanceToSqr(cameraPos) > maxDistance * maxDistance) return false;

        trackedParticleCount++;
        return true;
    }
}
