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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Event-driven construction-density scoring. Render reads cached scores only. */
public class SusChunkDetector extends Module {
    public static volatile String hudText = "Sus chunks: waiting for chunk data";
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Integer> distance = sg.add(new IntSetting.Builder().name("distance").defaultValue(96).range(16, 192).build());
    private final Setting<Integer> planeY = sg.add(new IntSetting.Builder().name("outline-y").description("Y plane used for the chunk outline.").defaultValue(64).range(-64, 320).build());
    private final Setting<Integer> threshold = sg.add(new IntSetting.Builder().name("minimum-score").defaultValue(18).range(1, 100).build());
    private final Setting<Integer> hue = sg.add(new IntSetting.Builder().name("hue").defaultValue(42).range(0, 360).build());
    private final Setting<Integer> saturation = sg.add(new IntSetting.Builder().name("saturation").defaultValue(90).range(0, 100).build());
    private final Setting<Integer> brightness = sg.add(new IntSetting.Builder().name("brightness").defaultValue(100).range(0, 100).build());
    private final Setting<Integer> alpha = sg.add(new IntSetting.Builder().name("alpha").defaultValue(190).range(0, 255).build());
    private final Setting<Boolean> chroma = sg.add(new BoolSetting.Builder().name("chroma").defaultValue(false).build());
    private final Setting<Integer> chromaSpeed = sg.add(new IntSetting.Builder().name("chroma-speed").defaultValue(3).range(1, 20).visible(chroma::get).build());
    private final Map<Long, Snapshot> cache = new HashMap<>();

    public SusChunkDetector() {
        super(AddonTemplate.VISUAL_CATEGORY, "sus-chunk-detector", "Scores cached chunks by artificial/building block density.");
    }

    @Override public void onDeactivate() { cache.clear(); hudText = "Sus Chunk Detector: off"; }

    @EventHandler private void onChunk(ChunkDataEvent event) {
        if (event == null || event.chunk() == null || mc.level == null) return;
        LevelChunk chunk = event.chunk();
        Snapshot snapshot = new Snapshot();
        LevelChunkSection[] sections = chunk.getSections();
        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection section = sections[si];
            if (section == null || section.hasOnlyAir()) continue;
            int baseY = mc.level.getMinY() + (si << 4);
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                BlockState state = section.getBlockState(x, y, z);
                int weight = weight(state.getBlock());
                if (weight == 0) continue;
                int wx = (chunk.getPos().x << 4) + x, wy = baseY + y, wz = (chunk.getPos().z << 4) + z;
                snapshot.put(BlockPos.asLong(wx, wy, wz), weight);
            }
        }
        cache.put(key(chunk.getPos().x, chunk.getPos().z), snapshot);
    }

    @EventHandler private void onBlock(BlockUpdateEvent event) {
        if (event == null || event.pos == null || event.newState == null) return;
        Snapshot s = cache.get(key(event.pos.getX() >> 4, event.pos.getZ() >> 4));
        if (s == null) return;
        int w = weight(event.newState.getBlock());
        long p = event.pos.asLong();
        if (w == 0) s.remove(p); else s.put(p, w);
    }

    @EventHandler private void onLeave(GameLeftEvent event) { cache.clear(); hudText = "Sus chunks: world unloaded"; }

    @EventHandler private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null || cache.isEmpty()) {
            hudText = "Sus chunks: waiting for cached chunks";
            return;
        }
        int radius = (int) Math.ceil(distance.get() / 16.0) + 1;
        int cx = mc.player.getBlockX() >> 4, cz = mc.player.getBlockZ() >> 4;
        double maxSq = (double) distance.get() * distance.get();
        SettingColor color = color();
        StringBuilder labels = new StringBuilder("Sus chunks");
        int labelCount = 0;
        for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) {
            double dx = x * 16 + 8 - mc.player.getX(), dz = z * 16 + 8 - mc.player.getZ();
            if (dx * dx + dz * dz > maxSq) continue;
            Snapshot s = cache.get(key(x, z));
            if (s == null || s.score() < threshold.get()) continue;
            double y = Math.max(mc.level.getMinY(), Math.min(mc.level.getMaxY() - 1, planeY.get()));
            AABB box = new AABB(x * 16, y, z * 16, x * 16 + 16, y + 0.08, z * 16 + 16);
            event.renderer.box(box, color, color, ShapeMode.Lines, 0);
            if (labelCount < 6) {
                labels.append((char) 10).append("Chunk ").append(x).append(',').append(z).append(" score ").append(s.score());
                labelCount++;
            }
        }
        if (labelCount == 0) labels.append(": no matches");
        hudText = labels.toString();
    }

    private SettingColor color() {
        float h = chroma.get() ? ((System.currentTimeMillis() * chromaSpeed.get()) % 12000L) / 12000f : hue.get() / 360f;
        int rgb = Color.HSBtoRGB(h, saturation.get() / 100f, brightness.get() / 100f);
        return new SettingColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha.get());
    }

    private static int weight(Block b) {
        String n = BuiltInRegistries.BLOCK.getKey(b).getPath();
        if (n.contains("kelp") || n.contains("amethyst") || n.contains("bone_block")) return 3;
        if (n.contains("obsidian") || n.contains("quartz") || n.contains("glass") || n.contains("torch")) return 2;
        if (n.contains("planks") || n.contains("stairs") || n.contains("slab") || n.contains("fence") || n.contains("wall")) return 1;
        return 0;
    }

    private static long key(int x, int z) { return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32); }

    private static final class Snapshot {
        private final Map<Long, Integer> blocks = new HashMap<>();
        private int total;
        void put(long pos, int weight) { Integer old = blocks.put(pos, weight); if (old != null) total -= old; total += weight; }
        void remove(long pos) { Integer old = blocks.remove(pos); if (old != null) total -= old; }
        int score() { return Math.min(100, total / 3 + blocks.size() / 12); }
    }
}
