package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One-time per-chunk stash/tunnel candidate extraction; render only draws cached candidates. */
public class StashAndTunnelFinder extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Integer> distance = sg.add(new IntSetting.Builder().name("distance").defaultValue(72).range(16, 160).build());
    private final Setting<Integer> maxCandidates = sg.add(new IntSetting.Builder().name("max-candidates-per-chunk").defaultValue(16).range(1, 48).build());
    private final Setting<Integer> maxTracers = sg.add(new IntSetting.Builder().name("max-tracers").description("Caps center-to-target tracer lines per frame.").defaultValue(8).range(0, 32).build());
    private final Setting<Boolean> showStashes = sg.add(new BoolSetting.Builder().name("isolated-storage").defaultValue(true).build());
    private final Setting<Boolean> showTunnels = sg.add(new BoolSetting.Builder().name("straight-tunnels").defaultValue(true).build());
    private final Setting<SettingColor> stashColor = sg.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("stash-color").defaultValue(new SettingColor(255, 75, 85, 210)).build());
    private final Setting<SettingColor> tunnelColor = sg.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("tunnel-color").defaultValue(new SettingColor(70, 190, 255, 190)).build());
    private final Map<Long, List<Candidate>> cache = new HashMap<>();

    public StashAndTunnelFinder() {
        super(AddonTemplate.VISUAL_CATEGORY, "stash-and-tunnel-finder", "Caches isolated underground storage and straight air-corridor candidates.");
    }

    @Override public void onDeactivate() { cache.clear(); }

    @EventHandler private void onChunk(ChunkDataEvent event) {
        if (event == null || event.chunk() == null || mc.level == null) return;
        LevelChunk chunk = event.chunk();
        int minY = mc.level.getMinY();
        int maxY = Math.min(45, mc.level.getMaxY() - 2);
        List<Candidate> found = new ArrayList<>();
        List<BlockPos> storage = new ArrayList<>();

        for (int y = minY; y < Math.min(30, mc.level.getMaxY()); y++) {
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                BlockPos p = new BlockPos((chunk.getPos().x << 4) + x, y, (chunk.getPos().z << 4) + z);
                if (isStorage(chunk.getBlockState(p).getBlock())) storage.add(p);
            }
        }
        if (showStashes.get()) {
            for (BlockPos p : storage) {
                boolean nearby = false;
                for (BlockPos other : storage) {
                    if (other.equals(p)) continue;
                    if (Math.abs(other.getX() - p.getX()) <= 5 && Math.abs(other.getY() - p.getY()) <= 4 && Math.abs(other.getZ() - p.getZ()) <= 5) {
                        nearby = true; break;
                    }
                }
                if (!nearby && found.size() < maxCandidates.get()) found.add(new Candidate(p.getX(), p.getY(), p.getZ(), false, 1, 1));
            }
        }

        if (showTunnels.get() && found.size() < maxCandidates.get()) {
            int bx = chunk.getPos().x << 4, bz = chunk.getPos().z << 4;
            outer: for (int y = minY; y < maxY; y++) {
                for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    if (found.size() >= maxCandidates.get()) break outer;
                    BlockPos p = new BlockPos(bx + x, y, bz + z);
                    if (!air2(chunk, p) || chunk.getBlockState(p.below()).isAir()) continue;
                    int lenX = run(chunk, p, 1, 0, 5);
                    int lenZ = run(chunk, p, 0, 1, 5);
                    if (lenX >= 5) {
                        found.add(new Candidate(p.getX(), y, p.getZ(), true, lenX, 1));
                    } else if (lenZ >= 5) {
                        found.add(new Candidate(p.getX(), y, p.getZ(), true, 1, lenZ));
                    }
                }
            }
        }
        cache.put(key(chunk.getPos().x, chunk.getPos().z), found);
    }

    @EventHandler private void onLeave(GameLeftEvent event) { cache.clear(); }

    @EventHandler private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null || cache.isEmpty()) return;
        int radius = (int) Math.ceil(distance.get() / 16.0) + 1;
        int cx = mc.player.getBlockX() >> 4, cz = mc.player.getBlockZ() >> 4;
        double maxSq = (double) distance.get() * distance.get();
        Vec3 from = mc.player.getEyePosition();
        int tracers = 0;
        for (int x = cx - radius; x <= cx + radius; x++) for (int z = cz - radius; z <= cz + radius; z++) {
            double dx = x * 16 + 8 - mc.player.getX(), dz = z * 16 + 8 - mc.player.getZ();
            if (dx * dx + dz * dz > maxSq) continue;
            List<Candidate> candidates = cache.get(key(x, z));
            if (candidates == null) continue;
            for (Candidate c : candidates) {
                SettingColor color = c.tunnel ? tunnelColor.get() : stashColor.get();
                AABB box = new AABB(c.x, c.y, c.z, c.x + c.width, c.y + 2, c.z + c.length);
                event.renderer.box(box, color, color, ShapeMode.Lines, 0);
                if (tracers < maxTracers.get()) {
                    Vec3 to = new Vec3(c.x + c.width / 2.0, c.y + 1.0, c.z + c.length / 2.0);
                    event.renderer.line(from, to, color);
                    tracers++;
                }
            }
        }
    }

    private static boolean air2(LevelChunk chunk, BlockPos p) {
        return chunk.getBlockState(p).isAir() && chunk.getBlockState(p.above()).isAir();
    }

    private static int run(LevelChunk chunk, BlockPos start, int dx, int dz, int minimum) {
        int length = 0;
        for (int i = 0; i < 16; i++) {
            BlockPos p = start.offset(dx * i, 0, dz * i);
            if (p.getX() >> 4 != start.getX() >> 4 || p.getZ() >> 4 != start.getZ() >> 4) break;
            if (!air2(chunk, p) || chunk.getBlockState(p.below()).isAir()) break;
            length++;
        }
        return length >= minimum ? length : 0;
    }

    private static boolean isStorage(Block block) {
        return block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST || block == Blocks.BARREL
            || block == Blocks.ENDER_CHEST || block == Blocks.HOPPER
            || block == Blocks.DROPPER || block == Blocks.DISPENSER;
    }

    private static long key(int x, int z) { return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32); }

    private static final class Candidate {
        final int x, y, z, width, length;
        final boolean tunnel;
        Candidate(int x, int y, int z, boolean tunnel, int width, int length) {
            this.x = x; this.y = y; this.z = z; this.tunnel = tunnel; this.width = width; this.length = length;
        }
    }
}
