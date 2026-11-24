package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * Lightweight entity bounding-box outlines using Meteor's existing 3D renderer.
 * No hidden-entity discovery or world scanning beyond entities already exposed
 * by the client level's renderable-entity collection.
 */
public class EntityOutline extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
        .name("distance")
        .description("Maximum outline distance in blocks.")
        .defaultValue(48)
        .min(8)
        .sliderMax(128)
        .build()
    );

    private final Setting<Boolean> players = sgGeneral.add(new BoolSetting.Builder()
        .name("players")
        .description("Draw outlines around other players.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> otherEntities = sgGeneral.add(new BoolSetting.Builder()
        .name("other-entities")
        .description("Draw outlines around non-player entities.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> outlineColor = sgGeneral.add(new ColorSetting.Builder()
        .name("outline-color")
        .description("Entity outline color.")
        .defaultValue(new SettingColor(255, 105, 145, 190))
        .build()
    );

    public EntityOutline() {
        super(AddonTemplate.VISUAL_CATEGORY, "entity-outline",
            "Draws lightweight outlines around visible client-side entities.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        double maxDistanceSq = (double) distance.get() * distance.get();
        SettingColor color = outlineColor.get();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || !entity.isAlive()) continue;
            if (entity.distanceToSqr(mc.player) > maxDistanceSq) continue;

            if (entity instanceof Player) {
                if (!players.get()) continue;
            } else if (!otherEntities.get()) {
                continue;
            }

            AABB bounds = entity.getBoundingBox().inflate(0.015);
            event.renderer.box(bounds, color, color, ShapeMode.Lines, 0);
        }
    }
}
