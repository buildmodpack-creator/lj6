package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.AABB;

/**
 * Visualizes the borders of the player's current loaded chunk.
 * It does not inspect hidden blocks or search for bases or stashes.
 */
public class ChunkVisual extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> verticalRange = sgGeneral.add(new IntSetting.Builder()
        .name("vertical-range")
        .description("How far above and below the player to draw the current chunk outline.")
        .defaultValue(12)
        .min(2)
        .sliderMax(64)
        .build()
    );

    private final Setting<SettingColor> chunkColor = sgGeneral.add(new ColorSetting.Builder()
        .name("chunk-color")
        .description("Color of the current chunk outline.")
        .defaultValue(new SettingColor(80, 190, 255, 180))
        .build()
    );

    public ChunkVisual() {
        super(AddonTemplate.VISUAL_CATEGORY, "chunk-visual",
            "Draws the borders of your current chunk using already loaded world coordinates.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        int chunkX = Math.floorDiv(mc.player.getBlockX(), 16) * 16;
        int chunkZ = Math.floorDiv(mc.player.getBlockZ(), 16) * 16;
        int range = verticalRange.get();
        double minY = Math.max(mc.level.getMinY(), mc.player.getY() - range);
        double maxY = Math.min(mc.level.getMaxY(), mc.player.getY() + range);

        AABB bounds = new AABB(chunkX, minY, chunkZ, chunkX + 16, maxY, chunkZ + 16);
        SettingColor color = chunkColor.get();
        event.renderer.box(bounds, color, color, ShapeMode.Lines, 0);
    }
}
