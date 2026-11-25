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
import net.minecraft.world.phys.AABB;
import java.util.List;

public class StorageESP extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Integer> range = sg.add(new IntSetting.Builder().name("range").defaultValue(64).range(8, 128).build());
    private final Setting<Integer> renderLimit = sg.add(new IntSetting.Builder().name("render-limit")
        .description("Maximum cached storage markers drawn per frame; lower values are smoother on mobile.")
        .defaultValue(192).range(16, 384).build());
    private final Setting<Boolean> chest = bool("chest", true);
    private final Setting<Boolean> shulker = bool("shulker-box", true);
    private final Setting<Boolean> barrel = bool("barrel", true);
    private final Setting<Boolean> ender = bool("ender-chest", true);
    private final Setting<Boolean> hopper = bool("hopper", true);
    private final Setting<Boolean> furnace = bool("furnace", true);
    private final Setting<SettingColor> chestColor = color("chest-color", 255, 190, 70);
    private final Setting<SettingColor> shulkerColor = color("shulker-color", 190, 100, 255);
    private final Setting<SettingColor> barrelColor = color("barrel-color", 190, 130, 75);
    private final Setting<SettingColor> enderColor = color("ender-chest-color", 70, 220, 190);
    private final Setting<SettingColor> hopperColor = color("hopper-color", 150, 150, 160);
    private final Setting<SettingColor> furnaceColor = color("furnace-color", 255, 110, 75);

    public StorageESP() {
        // Keep a unique Meteor module ID: "storage-esp" belongs to Meteor itself.
        // Modules.add() replaces modules by name, so reusing it removes Meteor's
        // built-in StorageESP and leaves StorageOutlineShader without its dependency.
        super(AddonTemplate.VISUAL_CATEGORY, "ljct-storage-esp",
            "Draws cached storage boxes with the normal 3D shape renderer; does not use Meteor's post-process outline shader.");
    }
    private Setting<Boolean> bool(String name, boolean value) {
        return sg.add(new BoolSetting.Builder().name(name).defaultValue(value).build());
    }
    private Setting<SettingColor> color(String name, int r, int g, int b) {
        return sg.add(new ColorSetting.Builder().name(name).defaultValue(new SettingColor(r, g, b, 190)).build());
    }
    @EventHandler private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null || !StorageChunkCacheScanner.isAvailable()) return;
        List<StorageChunkCacheScanner.CachedBlock> blocks = StorageChunkCacheScanner.getCachedBlocks(
            mc.player.getX(), mc.player.getY(), mc.player.getZ(), range.get(), renderLimit.get());
        for (var block : blocks) {
            SettingColor c = colorFor(block.blockId());
            if (c == null) continue;
            event.renderer.box(new AABB(block.x(), block.y(), block.z(), block.x()+1, block.y()+1, block.z()+1).inflate(0.002), c, c, ShapeMode.Lines, 0);
        }
    }
    private SettingColor colorFor(String id) {
        if (id.equals("minecraft:chest") || id.equals("minecraft:trapped_chest")) return chest.get() ? chestColor.get() : null;
        if (id.endsWith("_shulker_box")) return shulker.get() ? shulkerColor.get() : null;
        if (id.equals("minecraft:barrel")) return barrel.get() ? barrelColor.get() : null;
        if (id.equals("minecraft:ender_chest")) return ender.get() ? enderColor.get() : null;
        if (id.equals("minecraft:hopper")) return hopper.get() ? hopperColor.get() : null;
        if (id.equals("minecraft:furnace") || id.equals("minecraft:smoker") || id.equals("minecraft:blast_furnace")) return furnace.get() ? furnaceColor.get() : null;
        return null;
    }
}
