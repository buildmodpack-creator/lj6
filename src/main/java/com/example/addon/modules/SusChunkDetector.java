package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SusChunkDetector extends Module {
    public static volatile String hudText = "Top Sus Chunks\nWaiting for chunk data";
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Integer> distance = sg.add(new IntSetting.Builder().name("distance").defaultValue(128).range(16, 256).build());
    private final Setting<Integer> planeY = sg.add(new IntSetting.Builder().name("outline-y").defaultValue(64).range(-64, 320).build());
    private final Setting<Integer> threshold = sg.add(new IntSetting.Builder().name("minimum-score").defaultValue(5).range(1, 100).build());
    private final Setting<Integer> topCount = sg.add(new IntSetting.Builder().name("top-chunks").defaultValue(5).range(1, 10).build());
    private final Setting<Integer> hue = sg.add(new IntSetting.Builder().name("hue").defaultValue(42).range(0, 360).build());
    private final Setting<Integer> saturation = sg.add(new IntSetting.Builder().name("saturation").defaultValue(90).range(0, 100).build());
    private final Setting<Integer> brightness = sg.add(new IntSetting.Builder().name("brightness").defaultValue(100).range(0, 100).build());
    private final Setting<Integer> alpha = sg.add(new IntSetting.Builder().name("alpha").defaultValue(190).range(0, 255).build());
    private final Setting<Boolean> chroma = sg.add(new BoolSetting.Builder().name("chroma").defaultValue(false).build());
    private final Setting<Integer> chromaSpeed = sg.add(new IntSetting.Builder().name("chroma-speed").defaultValue(3).range(1, 20).visible(chroma::get).build());
    private static volatile SusChunkDetector INSTANCE;
    private final Map<Long, Snapshot> cache = new HashMap<>();

    public SusChunkDetector() {
        super(AddonTemplate.VISUAL_CATEGORY, "sus-chunk-detector", "Ranks cached chunks by storage and artificial block evidence.");
        INSTANCE = this;
    }

    @Override public void onDeactivate() {
        cache.clear();
        hudText = "Top Sus Chunks\nDetector off";
    }

    @EventHandler private void onChunk(ChunkDataEvent event) {
        if (event == null || event.chunk() == null || mc.level == null) return;
        LevelChunk chunk = event.chunk();
        Snapshot snapshot = new Snapshot();
        LevelChunkSection[] sections = chunk.getSections();
        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection section = sections[si];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> isInteresting(state.getBlock()))) continue;
            int baseY = mc.level.getMinY() + (si << 4);
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                BlockState state = section.getBlockState(x, y, z);
                int weight = weight(state.getBlock(), baseY + y);
                if (weight == 0) continue;
                int wx = (chunk.getPos().x << 4) + x, wy = baseY + y, wz = (chunk.getPos().z << 4) + z;
                snapshot.put(BlockPos.asLong(wx, wy, wz), weight);
            }
        }
        cache.put(key(chunk.getPos().x, chunk.getPos().z), snapshot);
    }

    @EventHandler private void onBlock(BlockUpdateEvent event) {
        if (event == null || event.pos == null || event.newState == null || mc.level == null) return;
        Snapshot snapshot = cache.get(key(event.pos.getX() >> 4, event.pos.getZ() >> 4));
        if (snapshot == null) return;
        int weight = weight(event.newState.getBlock(), event.pos.getY());
        if (weight == 0) snapshot.remove(event.pos.asLong());
        else snapshot.put(event.pos.asLong(), weight);
    }

    @EventHandler private void onLeave(GameLeftEvent event) {
        cache.clear();
        hudText = "Top Sus Chunks\nWorld unloaded";
    }

    @EventHandler private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null || cache.isEmpty()) {
            hudText = "Top Sus Chunks\nWaiting for cached chunks";
            return;
        }

        int radius = (int) Math.ceil(distance.get() / 16.0) + 1;
        int cx = mc.player.getBlockX() >> 4, cz = mc.player.getBlockZ() >> 4;
        double maxSq = (double) distance.get() * distance.get();
        List<Entry> ranked = new ArrayList<>();
        for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) {
            Snapshot snapshot = cache.get(key(x, z));
            if (snapshot == null) continue;
            double px = x * 16 + 8 - mc.player.getX();
            double pz = z * 16 + 8 - mc.player.getZ();
            double distanceSq = px * px + pz * pz;
            if (distanceSq > maxSq) continue;
            int score = snapshot.score();
            if (score >= threshold.get()) ranked.add(new Entry(x, z, score, Math.sqrt(distanceSq)));
        }
        ranked.sort(Comparator.comparingInt((Entry e) -> e.score).reversed().thenComparingDouble(e -> e.distance));

        SettingColor color = color();
        int y = Math.max(mc.level.getMinY(), Math.min(mc.level.getMaxY() - 1, planeY.get()));
        StringBuilder text = new StringBuilder("Top Sus Chunks");
        int shown = Math.min(topCount.get(), ranked.size());
        for (int i = 0; i < shown; i++) {
            Entry e = ranked.get(i);
            AABB box = new AABB(e.x * 16, y, e.z * 16, e.x * 16 + 16, y + 0.08, e.z * 16 + 16);
            event.renderer.box(box, color, color, ShapeMode.Lines, 0);
            text.append('\n').append(i + 1).append(". ").append(e.x).append(", ").append(e.z)
                .append(" | ").append(e.score).append(" pts | ")
                .append((int) e.distance).append("m | ").append(direction(e.x * 16 + 8 - mc.player.getX(), e.z * 16 + 8 - mc.player.getZ()));
        }
        if (shown == 0) text.append("\nNo candidates in range");
        hudText = text.toString();
    }

    private String direction(double dx, double dz) {
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double relative = targetYaw - mc.player.getYRot();
        while (relative <= -180) relative += 360;
        while (relative > 180) relative -= 360;
        String[] dirs = {"F", "FR", "R", "BR", "B", "BL", "L", "FL"};
        int sector = Math.floorMod((int) Math.round(relative / 45.0), 8);
        return dirs[sector];
    }

    private SettingColor color() {
        float h = chroma.get() ? ((System.currentTimeMillis() * chromaSpeed.get()) % 12000L) / 12000f : hue.get() / 360f;
        int rgb = Color.HSBtoRGB(h, saturation.get() / 100f, brightness.get() / 100f);
        return new SettingColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha.get());
    }

    private static boolean isInteresting(Block block) {
        String n = BuiltInRegistries.BLOCK.getKey(block).getPath();
        return n.equals("chest") || n.equals("trapped_chest") || n.equals("barrel") || n.contains("shulker_box")
            || n.equals("ender_chest") || n.equals("hopper") || n.equals("furnace") || n.equals("blast_furnace")
            || n.equals("smoker") || n.equals("enchanting_table") || n.equals("spawner") || n.equals("trial_spawner")
            || n.equals("nether_portal") || n.contains("piston") || n.endsWith("_bed") || n.contains("torch")
            || n.contains("glass") || n.endsWith("_sign") || n.contains("planks") || n.contains("stairs")
            || n.contains("slab") || n.contains("fence") || n.contains("wall");
    }

    private static int weight(Block block, int y) {
        String n = BuiltInRegistries.BLOCK.getKey(block).getPath();
        int value = 0;
        if (n.equals("chest") || n.equals("trapped_chest") || n.equals("barrel") || n.contains("shulker_box")) value = 5;
        else if (n.equals("spawner") || n.equals("trial_spawner")) value = 7;
        else if (n.equals("hopper") || n.equals("furnace") || n.equals("blast_furnace") || n.equals("smoker")
            || n.equals("enchanting_table")) value = 4;
        else if (n.equals("nether_portal") || n.contains("piston") || n.endsWith("_bed")) value = 3;
        else if (n.contains("torch") || n.contains("glass") || n.endsWith("_sign") || n.endsWith("_wall_sign")) value = 2;
        else if (n.contains("planks") || n.contains("stairs") || n.contains("slab") || n.contains("fence") || n.contains("wall")) value = 1;
        if (y < 50 && value > 0) value++;
        return value;
    }

    public static void onChunkUnload(int x, int z) {
        SusChunkDetector module = INSTANCE;
        if (module != null && module.isActive()) module.cache.remove(key(x, z));
    }

    private static long key(int x, int z) { return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32); }

    private static final class Snapshot {
        private final Map<Long, Integer> blocks = new HashMap<>();
        private int total;
        void put(long pos, int weight) { Integer old = blocks.put(pos, weight); if (old != null) total -= old; total += weight; }
        void remove(long pos) { Integer old = blocks.remove(pos); if (old != null) total -= old; }
        int score() { return Math.min(100, total); }
    }

    private static final class Entry {
        final int x, z, score;
        final double distance;
        Entry(int x, int z, int score, double distance) { this.x = x; this.z = z; this.score = score; this.distance = distance; }
    }
}
