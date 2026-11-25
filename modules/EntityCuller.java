package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.world.entity.Entity;

/** Adds an optional distance limit before vanilla's normal frustum test. */
public class EntityCuller extends Module {
    private static volatile EntityCuller INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
        .name("distance")
        .description("Entities farther than this distance are not rendered.")
        .defaultValue(128)
        .range(16, 256)
        .build()
    );

    public EntityCuller() {
        super(AddonTemplate.FIX_LAG_CATEGORY, "entity-culler",
            "Skips rendering entities beyond a configurable distance.");
        INSTANCE = this;
    }

    public static boolean shouldCull(Entity entity, double cameraX, double cameraY, double cameraZ) {
        EntityCuller module = INSTANCE;
        if (module == null || !module.isActive() || entity == null) return false;
        if (entity.isRemoved()) return false;

        double maxDistance = module.distance.get();
        return entity.distanceToSqr(cameraX, cameraY, cameraZ) > maxDistance * maxDistance;
    }
}