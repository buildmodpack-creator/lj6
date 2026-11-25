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

public class SpawnerESP extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Integer> range = sg.add(new IntSetting.Builder().name("range").defaultValue(64).range(8, 128).build());
    private final Setting<Integer> limit = sg.add(new IntSetting.Builder().name("render-limit").defaultValue(96).range(8, 384).build());
    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(255, 70, 70, 210)).build());
    public SpawnerESP() { super(AddonTemplate.VISUAL_CATEGORY, "spawner-esp", "Highlights cached mob spawners. Requires Storage Chunk Cache Scanner."); }
    @EventHandler private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        for (var b : StorageChunkCacheScanner.getCachedBlocks(mc.player.getX(), mc.player.getY(), mc.player.getZ(), range.get(), limit.get())) {
            if (!b.blockId().equals("minecraft:spawner") && !b.blockId().equals("minecraft:trial_spawner")) continue;
            event.renderer.box(new AABB(b.x(), b.y(), b.z(), b.x()+1, b.y()+1, b.z()+1).inflate(0.003), color.get(), color.get(), ShapeMode.Lines, 0);
        }
    }
}
