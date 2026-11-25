package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.AABB;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class CustomBlockESP extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<String> blockIds = sg.add(new StringSetting.Builder().name("block-ids")
        .description("Comma-separated registry IDs, e.g. minecraft:beacon,minecraft:obsidian,minecraft:anvil")
        .defaultValue("minecraft:enchanting_table,minecraft:anvil,minecraft:beacon,minecraft:bed,minecraft:netherite_block,minecraft:obsidian,minecraft:amethyst_block")
        .build());
    private final Setting<Integer> range = sg.add(new IntSetting.Builder().name("range").defaultValue(64).range(8, 128).build());
    private final Setting<Integer> limit = sg.add(new IntSetting.Builder().name("render-limit").defaultValue(160).range(8, 512).build());
    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(100, 210, 255, 200)).build());
    public CustomBlockESP() { super(AddonTemplate.VISUAL_CATEGORY, "custom-block-esp", "Highlights configured block IDs from the shared chunk cache."); }
    @EventHandler private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        Set<String> wanted = new HashSet<>();
        for (String raw : blockIds.get().split(",")) {
            String id = raw.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty()) wanted.add(id.contains(":") ? id : "minecraft:" + id);
        }
        if (wanted.isEmpty()) return;
        for (var b : StorageChunkCacheScanner.getCachedBlocks(mc.player.getX(), mc.player.getY(), mc.player.getZ(), range.get(), limit.get())) {
            if (!wanted.contains(b.blockId())) continue;
            event.renderer.box(new AABB(b.x(), b.y(), b.z(), b.x()+1, b.y()+1, b.z()+1).inflate(0.003), color.get(), color.get(), ShapeMode.Lines, 0);
        }
    }
}
