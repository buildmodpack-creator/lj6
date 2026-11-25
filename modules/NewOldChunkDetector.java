package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

/**
 * Edge-continuity heuristic. A client cannot know a chunk's generation history
 * from block states alone; this marks edge discontinuities, not authoritative age.
 */
public class NewOldChunkDetector extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Integer> distance = sg.add(new IntSetting.Builder().name("distance").defaultValue(96).range(16, 192).build());
    private final Setting<Integer> planeY = sg.add(new IntSetting.Builder().name("grid-y").defaultValue(64).range(-64, 320).build());
    private final Setting<Integer> sampleStep = sg.add(new IntSetting.Builder().name("vertical-sample-step").description("Vertical spacing for cached border block/fluid samples.").defaultValue(8).range(4, 32).build());
    private final Setting<Integer> mismatchLimit = sg.add(new IntSetting.Builder().name("edge-mismatch-limit").description("Minimum differing border samples to mark a seam.").defaultValue(3).range(1, 64).build());
    private final SettingGroup sgColors = settings.createGroup("Colors");
    private final Setting<SettingColor> continuousColor = sgColors.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("continuous").defaultValue(new SettingColor(65, 220, 110, 180)).build());
    private final Setting<SettingColor> seamColor = sgColors.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("edge-discontinuity").defaultValue(new SettingColor(255, 145, 55, 210)).build());
    private final Map<Long, EdgeData> cache = new HashMap<>();

    public NewOldChunkDetector() {
        super(AddonTemplate.VISUAL_CATEGORY, "new-old-chunk-detector", "Compares cached block/fluid samples along neighboring chunk borders.");
    }

    @Override public void onDeactivate() { cache.clear(); }

    @EventHandler private void onChunk(ChunkDataEvent event) {
        if (event == null || event.chunk() == null || mc.level == null) return;
        LevelChunk chunk = event.chunk();
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
        cache.put(key(chunk.getPos().x, chunk.getPos().z), data);
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
            int mismatches = compare(current.east, cache.get(key(x + 1, z)) == null ? null : cache.get(key(x + 1, z)).west);
            mismatches += compare(current.south, cache.get(key(x, z + 1)) == null ? null : cache.get(key(x, z + 1)).north);
            SettingColor color = mismatches >= mismatchLimit.get() ? seamColor.get() : continuousColor.get();
            AABB grid = new AABB(x * 16, y, z * 16, x * 16 + 16, y + 0.04, z * 16 + 16);
            event.renderer.box(grid, color, color, ShapeMode.Lines, 0);
        }
    }

    private static int sample(LevelChunk chunk, BlockPos pos) {
        BlockState state = chunk.getBlockState(pos);
        int block = state.getBlock().hashCode();
        int fluid = state.getFluidState().getType().hashCode();
        return 31 * block + fluid;
    }

    private static int compare(int[][] a, int[][] b) {
        if (b == null) return 0;
        int differences = 0;
        int rows = Math.min(a.length, b.length);
        for (int i = 0; i < rows; i++) for (int j = 0; j < 16; j++) if (a[i][j] != b[i][j]) differences++;
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
