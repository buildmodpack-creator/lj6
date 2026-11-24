package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Skips rendering distant entities and entities outside the configured camera cone. */
public class EntityCuller extends Module {
    private static volatile EntityCuller INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
        .name("distance")
        .description("Entities farther than this distance are not rendered.")
        .defaultValue(80)
        .range(8, 256)
        .build()
    );
    private final Setting<Integer> viewAngle = sgGeneral.add(new IntSetting.Builder()
        .name("view-angle")
        .description("Camera cone in degrees; entities outside it are not rendered.")
        .defaultValue(170)
        .range(60, 360)
        .build()
    );

    public EntityCuller() {
        super(AddonTemplate.FIX_LAG_CATEGORY, "entity-culler",
            "Skips rendering entities beyond a distance or outside the camera view cone.");
        INSTANCE = this;
    }

    public static boolean shouldCull(Entity entity) {
        EntityCuller module = INSTANCE;
        if (module == null || !module.isActive()) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || entity == mc.player || entity.isRemoved()) return false;

        var camera = mc.gameRenderer.getMainCamera();
        Vec3 cameraPos = camera.getPosition();
        Vec3 toEntity = entity.getBoundingBox().getCenter().subtract(cameraPos);
        double maxDistance = module.distance.get();
        double lengthSquared = toEntity.lengthSqr();
        if (lengthSquared > maxDistance * maxDistance) return true;
        if (lengthSquared < 1.0E-6 || module.viewAngle.get() >= 360) return false;

        double dot = toEntity.normalize().dot(camera.getLookVector());
        double threshold = Math.cos(Math.toRadians(module.viewAngle.get() * 0.5));
        return dot < threshold;
    }
}
