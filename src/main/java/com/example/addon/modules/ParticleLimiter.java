package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.phys.Vec3;

/** Limits incoming particle spawns without scanning every live particle each tick. */
public class ParticleLimiter extends Module {
    private static volatile ParticleLimiter INSTANCE;
    private static int acceptedThisTick;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> maxSpawnsPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("max-spawns-per-tick")
        .description("Maximum new particles accepted during one client tick.")
        .defaultValue(80)
        .range(10, 1000)
        .build()
    );
    private final Setting<Integer> maxDistance = sgGeneral.add(new IntSetting.Builder()
        .name("max-distance")
        .description("Particles farther from the camera are discarded when spawned.")
        .defaultValue(48)
        .range(8, 128)
        .build()
    );

    public ParticleLimiter() {
        super(AddonTemplate.FIX_LAG_CATEGORY, "particle-limiter",
            "Limits new particle spawns per tick and discards distant particles.");
        INSTANCE = this;
    }

    public static void beginTick() {
        acceptedThisTick = 0;
    }

    public static boolean allowParticle(Particle particle) {
        ParticleLimiter module = INSTANCE;
        if (module == null || !module.isActive()) return true;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return true;
        if (acceptedThisTick >= module.maxSpawnsPerTick.get()) return false;

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 particlePos = particle.getBoundingBox().getCenter();
        double maxDistance = module.maxDistance.get();
        if (particlePos.distanceToSqr(cameraPos) > maxDistance * maxDistance) return false;

        acceptedThisTick++;
        return true;
    }
}