package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Iterator;

public class StashAndTunnelFinder extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Integer> distance = sg.add(new IntSetting.Builder().name("distance").defaultValue(96).range(16, 192).build());
    private final Setting<Integer> maxCandidates = sg.add(new IntSetting.Builder().name("max-candidates-per-chunk").defaultValue(24).range(1, 64).build());
    private final Setting<Integer> maxTracers = sg.add(new IntSetting.Builder().name("max-tracers").defaultValue(24).range(0, 64).build());
    private final Setting<Boolean> showStashes = sg.add(new BoolSetting.Builder().name("container-clusters").defaultValue(true).build());
    private final Setting<Boolean> showTunnels = sg.add(new BoolSetting.Builder().name("straight-tunnels").defaultValue(true).build());
    private final Setting<SettingColor> stashColor = sg.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("stash-color").defaultValue(new SettingColor(255, 75, 85, 210)).build());
    private final Setting<SettingColor> tunnelColor = sg.add(new meteordevelopment.meteorclient.settings.ColorSetting.Builder().name("tunnel-color").defaultValue(new SettingColor(70, 190, 255, 190)).build());
    private static volatile StashAndTunnelFinder INSTANCE;
    private final Map<Long, List<Candidate>> cache = new HashMap<>();
    private final Set<Long> dirtyChunks = new HashSet<>();

    public StashAndTunnelFinder() {
        super(AddonTemplate.VISUAL_CATEGORY, "stash-and-tunnel-finder", "Caches nearby container clusters and straight underground corridors.");
        INSTANCE = this;
    }

    @Override public void onDeactivate() { cache.clear(); dirtyChunks.clear(); }

    @EventHandler private void onChunk(ChunkDataEvent event) {
        if (event == null || event.chunk() == null || mc.level == null) return;
        long key = key(event.chunk().getPos().x, event.chunk().getPos().z);
        dirtyChunks.remove(key);
        rebuild(event.chunk());
    }

    @EventHandler private void onBlock(BlockUpdateEvent event) {
        if (event == null || event.pos == null || mc.level == null) return;
        dirtyChunks.add(key(event.pos.getX() >> 4, event.pos.getZ() >> 4));
    }

    @EventHandler private void onTick(TickEvent.Pre event) {
        if (mc.level == null || dirtyChunks.isEmpty()) return;
        Iterator<Long> iterator = dirtyChunks.iterator();
        long chunkKey = iterator.next();
        iterator.remove();
        int x = (int) chunkKey;
        int z = (int) (chunkKey >> 32);
        LevelChunk chunk = mc.level.getChunk(x, z);
        if (chunk != null) rebuild(chunk);
    }

    private void rebuild(LevelChunk chunk) {
        int minY = mc.level.getMinY();
        int maxY = mc.level.getMaxY() - 3;
        int bx = chunk.getPos().x << 4, bz = chunk.getPos().z << 4;
        List<Candidate> found = new ArrayList<>();
        Set<Long> storage = new HashSet<>();

        // Scan only sections that contain at least one relevant storage/bed block.
        net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
        for (int si = 0; si < sections.length; si++) {
            net.minecraft.world.level.chunk.LevelChunkSection section = sections[si];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> isStorage(state.getBlock()))) continue;
            int baseY = minY + (si << 4);
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                if (isStorage(section.getBlockState(x, y, z).getBlock())) {
                    storage.add(net.minecraft.core.BlockPos.asLong(bx + x, baseY + y, bz + z));
                }
            }
        }

        // A single container or bed is enough to create a visible box and tracer.
        if (showStashes.get()) {
            for (long packed : storage) {
                BlockPos p = BlockPos.of(packed);
                found.add(new Candidate(p.getX(), p.getY(), p.getZ(), 1, 1, 1, false));
                if (found.size() >= maxCandidates.get()) break;
            }
        }

        if (showTunnels.get() && found.size() < maxCandidates.get()) {
            outer: for (int y = minY; y <= maxY; y++) {
                for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    BlockPos p = new BlockPos(bx + x, y, bz + z);
                    for (int width = 1; width <= 3; width++) {
                        int lenX = tunnelRun(chunk, p, 1, 0, width);
                        if (lenX >= 6) {
                            found.add(new Candidate(p.getX(), y, p.getZ(), lenX, 2, width, true));
                            if (found.size() >= maxCandidates.get()) break outer;
                        }
                        int lenZ = tunnelRun(chunk, p, 0, 1, width);
                        if (lenZ >= 6) {
                            found.add(new Candidate(p.getX(), y, p.getZ(), width, 2, lenZ, true));
                            if (found.size() >= maxCandidates.get()) break outer;
                        }
                    }
                }
            }
        }
        cache.put(key(chunk.getPos().x, chunk.getPos().z), found);
    }

    @EventHandler private void onLeave(GameLeftEvent event) { cache.clear(); dirtyChunks.clear(); }

    public static void onChunkUnload(int x, int z) {
        StashAndTunnelFinder module = INSTANCE;
        if (module != null && module.isActive()) {
            long key = key(x, z);
            module.cache.remove(key);
            module.dirtyChunks.remove(key);
        }
    }

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
                AABB box = new AABB(c.x, c.y, c.z, c.x + c.width, c.y + c.height, c.z + c.length);
                event.renderer.box(box, color, color, ShapeMode.Lines, 0);
                if (tracers < maxTracers.get()) {
                    Vec3 to = new Vec3(c.x + c.width / 2.0, c.y + c.height / 2.0, c.z + c.length / 2.0);
                    event.renderer.line(from.x, from.y, from.z, to.x, to.y, to.z, color);
                    tracers++;
                }
            }
        }
    }

    private static int tunnelRun(LevelChunk chunk, BlockPos start, int dx, int dz, int width) {
        int length = 0;
        for (int i = 0; i < 16; i++) {
            boolean sectionValid = true;
            int solidSides = 0;
            for (int w = 0; w < width; w++) {
                BlockPos floor = start.offset(dx * i + (dz != 0 ? w : 0), 0, dz * i + (dx != 0 ? w : 0));
                if ((floor.getX() >> 4) != (start.getX() >> 4) || (floor.getZ() >> 4) != (start.getZ() >> 4)) return length >= 6 ? length : 0;
                if (!chunk.getBlockState(floor).isAir() || !chunk.getBlockState(floor.above()).isAir() || chunk.getBlockState(floor.below()).isAir()) { sectionValid = false; break; }
                if (!chunk.getBlockState(floor.offset(dz, 0, -dx)).isAir()) solidSides++;
                if (!chunk.getBlockState(floor.offset(-dz, 0, dx)).isAir()) solidSides++;
            }
            if (!sectionValid || solidSides == 0) break;
            length++;
        }
        return length >= 6 ? length : 0;
    }

    private static boolean isStorage(Block block) {
        String n = block.builtInRegistryHolder().key().location().getPath();
        return n.equals("chest") || n.equals("trapped_chest") || n.equals("barrel") || n.endsWith("shulker_box")
            || n.equals("hopper") || n.equals("furnace") || n.equals("blast_furnace") || n.equals("smoker")
            || n.equals("ender_chest") || n.endsWith("_bed");
    }

    private static long key(int x, int z) { return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32); }

    private static final class Candidate {
        final int x, y, z, width, height, length;
        final boolean tunnel;
        Candidate(int x, int y, int z, int width, int height, int length, boolean tunnel) {
            this.x = x; this.y = y; this.z = z; this.width = width; this.height = height; this.length = length; this.tunnel = tunnel;
        }
    }
}
