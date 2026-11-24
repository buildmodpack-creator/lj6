package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * First LJCT Builder milestone: highlights the block currently targeted by the crosshair.
 * This is a visual placement aid; it does not send automated placement or movement packets.
 */
public class BuildAssist extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<SettingColor> markerColor = sgGeneral.add(new ColorSetting.Builder()
        .name("marker-color")
        .description("Color of the block placement guide.")
        .defaultValue(new SettingColor(235, 120, 255, 80))
        .build()
    );

    public BuildAssist() {
        super(AddonTemplate.CATEGORY, "build-assist", "Highlights the block you're looking at to help align manual building.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;
        if (!(mc.hitResult instanceof BlockHitResult hit)) return;

        AABB box = new AABB(hit.getBlockPos()).inflate(0.002);
        SettingColor color = markerColor.get();
        event.renderer.box(box, color, color, ShapeMode.Both, 0);
    }
}
