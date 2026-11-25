package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

public class NewOldChunkDetector extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Integer> distance = sg.add(new IntSetting.Builder().name("distance").defaultValue(96).range(16, 192).build());
    private final Setting<Integer> planeY = sg.add(new IntSetting.Builder().name("grid-y").defaultValue(64).range(-64, 320).build());
    private final Setting<Integer> sampleStep = sg.add(new IntSetting.Builder().name("vertical-sample-step").defaultValue(8).range(4, 32).build());
    private final Setting<Integer> mismatchLimit = sg.add(new IntSetting.Builder().name("edge-mismatch-limit").defaultValue(3).range(1, 64).build());
    private final Setting<SettingColor> continuousColor = sg.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("continuous").defaultValue(new SettingColor(65, 220, 110, 180)).build());
    private final Setting<SettingColor> seamColor = sg.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("edge-discontinuity").defaultValue(new SettingColor(255, 145, 55, 210)).build());
    private static volatile NewOldChunkDetector INSTANCE;
    private final Map<Long, EdgeData> cache = new HashMap<>();

    public NewOldChunkDetector() {
        super(AddonTemplate.VISUAL_CATEGORY, "new-old-chunk-detector", "Compares cached chunk-edge samples; seams are only a heuristic, not authoritative age data.");
        INSTANCE = this;
    }

    @Override public void onDeactivate() { cache.clear(); }

    @EventHandler private void onChunk(ChunkDataEvent event) {
        if (event == null || event.chunk() == null || mc.level == null) return;
        cache.put(key(event.chunk().getPos().x, event.chunk().getPos().z), sampleChunk(event.chunk()));
    }

    private EdgeData sampleChunk(LevelChunk chunk) {
        int minY = mc.level.getMinY(), maxY = mc.level.getMaxY();
        int count = Math.max(1, (maxY - minY + sampleStep.get() - 1) / sampleStep.get());
        EdgeData data = new EdgeData(count);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int bx = chunk.getPos().getMinBlockX(), bz = chunk.getPos().getMinBlockZ();
        for (int i = 0, y = minY; y < maxY && i < count; i++, y += sampleStep.get()) {
            for (int t = 0; t < 16; t++) {
                data.west[i][t] = sample(chunk, pos.set(bx, y, bz + t));
                data.east[i][t] = sample(chunk, pos.set(bx + 15, y, bz + t));
                data.north[i][t] = sample(chunk, pos.set(bx + t, y, bz));
                data.south[i][t] = sample(chunk, pos.set(bx + t, y, bz + 15));
            }
        }
        return data;
    }

    @EventHandler private void onBlock(BlockUpdateEvent event) {
        if (event == null || event.pos == null || mc.level == null) return;
        int lx = event.pos.getX() & 15, lz = event.pos.getZ() & 15;
        if (lx != 0 && lx != 15 && lz != 0 && lz != 15) return;
        EdgeData data = cache.get(key(event.pos.getX() >> 4, event.pos.getZ() >> 4));
        if (data == null) return;
        int offset = event.pos.getY() - mc.level.getMinY();
        if (offset < 0 || offset % sampleStep.get() != 0) return;
        int row = offset / sampleStep.get();
        if (row >= data.west.length) return;
        int value = sample(event.newState);
        if (lx == 0) data.west[row][lz] = value;
        if (lx == 15) data.east[row][lz] = value;
        if (lz == 0) data.north[row][lx] = value;
        if (lz == 15) data.south[row][lx] = value;
    }

    @EventHandler private void onLeave(GameLeftEvent event) { cache.clear(); }

    @EventHandler private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null || cache.isEmpty()) return;
        int radius = (int) Math.ceil(distance.get() / 16.0) + 1;
        int cx = mc.player.getBlockX() >> 4, cz = mc.player.getBlockZ() >> 4;
        double maxSq = (double) distance.get() * distance.get();
        int y = Math.max(mc.level.getMinY(), Math.min(mc.level.getMaxY() - 1, planeY.get()));
        for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) {
            double dx = x * 16 + 8 - mc.player.getX(), dz = z * 16 + 8 - mc.player.getZ();
            if (dx * dx + dz * dz > maxSq) continue;
            EdgeData current = cache.get(key(x, z));
            if (current == null) continue;
            EdgeData east = cache.get(key(x + 1, z)), south = cache.get(key(x, z + 1));
            int mismatches = compare(current.east, east == null ? null : east.west) + compare(current.south, south == null ? null : south.north);
            SettingColor color = mismatches >= mismatchLimit.get() ? seamColor.get() : continuousColor.get();
            event.renderer.box(new AABB(x * 16, y, z * 16, x * 16 + 16, y + 0.04, z * 16 + 16), color, color, ShapeMode.Lines, 0);
        }
    }

    public static void onChunkUnload(int x, int z) {
        NewOldChunkDetector module = INSTANCE;
        if (module != null && module.isActive()) module.cache.remove(key(x, z));
    }

    private static int sample(LevelChunk chunk, BlockPos pos) { return sample(chunk.getBlockState(pos)); }
    private static int sample(BlockState state) {
        return 31 * state.getBlock().hashCode() + state.getFluidState().getType().hashCode();
    }
    private static int compare(int[][] a, int[][] b) {
        if (b == null) return 0;
        int differences = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++)
            for (int j = 0; j < 16; j++) if (a[i][j] != b[i][j]) differences++;
        return differences;
    }
    private static long key(int x, int z) { return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32); }

    private static final class EdgeData {
        final int[][] west, east, north, south;
        EdgeData(int samples) {
            west = new int[samples][16]; east = new int[samples][16];
            north = new int[samples][16]; south = new int[samples][16];
        }
    }
}
