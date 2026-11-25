package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;

public class HoleESP extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Integer> range = sg.add(new IntSetting.Builder().name("range").defaultValue(16).range(4, 48).build());
    private final Setting<Integer> limit = sg.add(new IntSetting.Builder().name("candidate-limit").defaultValue(160).range(16, 512).build());
    private final Setting<Boolean> obsidian = sg.add(new BoolSetting.Builder().name("obsidian-holes").defaultValue(true).build());
    private final Setting<Boolean> bedrock = sg.add(new BoolSetting.Builder().name("bedrock-holes").defaultValue(true).build());
    private final Setting<SettingColor> obsidianColor = sg.add(new ColorSetting.Builder().name("obsidian-color").defaultValue(new SettingColor(150, 70, 255, 190)).build());
    private final Setting<SettingColor> bedrockColor = sg.add(new ColorSetting.Builder().name("bedrock-color").defaultValue(new SettingColor(80, 220, 255, 190)).build());
    private final List<Hole> holes = new ArrayList<>();
    public HoleESP() {
        // "hole-esp" is already registered by Meteor Client.
        super(AddonTemplate.VISUAL_CATEGORY, "ljct-hole-esp",
            "Finds nearby 1x2 holes using cached obsidian/bedrock candidates; checks are throttled to every 5 ticks.");
    }
    @Override public void onDeactivate() { holes.clear(); }
    @EventHandler private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.player.tickCount % 5 != 0) return;
        holes.clear();
        for (var b : StorageChunkCacheScanner.getCachedBlocks(mc.player.getX(), mc.player.getY(), mc.player.getZ(), range.get(), limit.get())) {
            boolean floor = (obsidian.get() && b.blockId().equals("minecraft:obsidian")) || (bedrock.get() && b.blockId().equals("minecraft:bedrock"));
            if (!floor) continue;
            BlockPos p = new BlockPos(b.x(), b.y(), b.z());
            if (!mc.level.getBlockState(p.above()).isAir() || !mc.level.getBlockState(p.above(2)).isAir()) continue;
            if (!isWall(p.north().above()) || !isWall(p.south().above())
                || !isWall(p.east().above()) || !isWall(p.west().above())) continue;
            holes.add(new Hole(p, b.blockId().equals("minecraft:bedrock")));
        }
    }
    @EventHandler private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        for (Hole h : holes) {
            SettingColor c = h.bedrock ? bedrockColor.get() : obsidianColor.get();
            event.renderer.box(new AABB(h.pos.getX(), h.pos.getY()+1, h.pos.getZ(), h.pos.getX()+1, h.pos.getY()+3, h.pos.getZ()+1), c, c, ShapeMode.Lines, 0);
        }
    }
    private boolean isWall(BlockPos pos) { return mc.level != null && !mc.level.getBlockState(pos).isAir(); }
    private record Hole(BlockPos pos, boolean bedrock) {}
}
