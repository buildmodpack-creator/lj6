package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Limits incoming particle spawns without scanning every live particle each tick. */
public class ParticleLimiter extends Module {
    private static volatile ParticleLimiter INSTANCE;

    // Tick-local cache: avoid repeatedly reading module state/settings for every particle.
    private static int acceptedThisTick;
    private static int cachedMaxSpawns;
    private static double cachedMaxDistanceSquared;
    private static double cameraX, cameraY, cameraZ;
    private static boolean cameraPositionValid;
    private static boolean limiterEnabled;

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
        limiterEnabled = false;
        cameraPositionValid = false;

        ParticleLimiter module = INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        if (module == null || mc == null || mc.player == null || mc.level == null || mc.gameRenderer == null) return;
        if (!module.isActive()) return;

        cachedMaxSpawns = module.maxSpawnsPerTick.get();
        double distance = module.maxDistance.get();
        cachedMaxDistanceSquared = distance * distance;
        limiterEnabled = true;

        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        if (camera == null) {
            limiterEnabled = false;
            return;
        }

        cameraX = camera.x;
        cameraY = camera.y;
        cameraZ = camera.z;
        cameraPositionValid = true;
    }

    public static boolean allowParticle(Particle particle) {
        if (!limiterEnabled || !cameraPositionValid || particle == null) return true;
        if (acceptedThisTick >= cachedMaxSpawns) return false;

        AABB bounds = particle.getBoundingBox();
        if (bounds == null) return true;
        double dx = (bounds.minX + bounds.maxX) * 0.5 - cameraX;
        double dy = (bounds.minY + bounds.maxY) * 0.5 - cameraY;
        double dz = (bounds.minZ + bounds.maxZ) * 0.5 - cameraZ;
        if (dx * dx + dy * dy + dz * dz > cachedMaxDistanceSquared) return false;

        acceptedThisTick++;
        return true;
    }
}
