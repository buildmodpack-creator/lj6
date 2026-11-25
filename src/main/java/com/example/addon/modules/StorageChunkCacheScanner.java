package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * Event-driven scanner. Chunk sections are inspected only when chunk data arrives;
 * block updates patch one cached position. The render handler only reads cached data.
 */
public class StorageChunkCacheScanner extends Module {
    private static final Map<Block, Integer> BLOCK_WEIGHTS = createBlockWeights();
    private static volatile StorageChunkCacheScanner INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> distance = sgGeneral.add(new IntSetting.Builder()
        .name("render-distance")
        .description("Maximum distance for cached chunk outlines and block markers.")
        .defaultValue(96)
        .range(16, 256)
        .build()
    );

    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder()
        .name("min-y")
        .description("Lowest absolute Y level to display.")
        .defaultValue(-64)
        .range(-64, 320)
        .build()
    );

    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder()
        .name("max-y")
        .description("Highest absolute Y level to display.")
        .defaultValue(320)
        .range(-64, 320)
        .build()
    );

    private final Setting<Integer> minScore = sgGeneral.add(new IntSetting.Builder()
        .name("minimum-chunk-score")
        .description("Only mark chunks whose cached storage/special-block score reaches this value.")
        .defaultValue(28)
        .range(1, 100)
        .build()
    );

    private final Setting<Boolean> showMarkers = sgGeneral.add(new BoolSetting.Builder()
        .name("show-block-markers")
        .description("Draw cached boxes around recognized storage and special blocks.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxMarkersPerFrame = sgGeneral.add(new IntSetting.Builder()
        .name("marker-render-limit")
        .description("Maximum cached block markers drawn in one frame.")
        .defaultValue(128)
        .range(8, 512)
        .visible(showMarkers::get)
        .build()
    );

    private final Setting<Boolean> chroma = sgGeneral.add(new BoolSetting.Builder()
        .name("chroma")
        .description("Cycles the hue over time.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> hue = sgGeneral.add(new IntSetting.Builder()
        .name("hue")
        .description("Base HSB hue, from 0 to 360 degrees.")
        .defaultValue(195)
        .range(0, 360)
        .visible(() -> !chroma.get())
        .build()
    );

    private final Setting<Integer> saturation = sgGeneral.add(new IntSetting.Builder()
        .name("saturation")
        .description("HSB saturation percentage.")
        .defaultValue(85)
        .range(0, 100)
        .build()
    );

    private final Setting<Integer> brightness = sgGeneral.add(new IntSetting.Builder()
        .name("brightness")
        .description("HSB brightness percentage.")
        .defaultValue(100)
        .range(0, 100)
        .build()
    );

    private final Setting<Integer> alpha = sgGeneral.add(new IntSetting.Builder()
        .name("alpha")
        .description("Overlay opacity.")
        .defaultValue(180)
        .range(0, 255)
        .build()
    );

    private final Setting<Integer> chromaSpeed = sgGeneral.add(new IntSetting.Builder()
        .name("chroma-speed")
        .description("Chroma cycle speed.")
        .defaultValue(3)
        .range(1, 20)
        .visible(chroma::get)
        .build()
    );

    private final Map<Long, ChunkSnapshot> cache = new HashMap<>();

    public StorageChunkCacheScanner() {
        super(AddonTemplate.VISUAL_CATEGORY, "storage-chunk-cache-scanner",
            "Caches storage and special blocks on chunk data and block updates, then scores unusual chunks.");
        INSTANCE = this;
    }

    @Override
    public void onDeactivate() {
        cache.clear();
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        if (event == null || event.chunk() == null) return;
        cacheChunk(event.chunk());
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (event == null || event.pos == null || event.newState == null) return;

        long key = chunkKey(event.pos.getX() >> 4, event.pos.getZ() >> 4);
        ChunkSnapshot snapshot = cache.get(key);
        if (snapshot == null) return;

        long posKey = event.pos.asLong();
        int weight = classify(event.newState);
        if (weight == 0) snapshot.remove(posKey);
        else snapshot.put(posKey, event.pos.getX(), event.pos.getY(), event.pos.getZ(), weight, BuiltInRegistries.BLOCK.getKey(event.newState.getBlock()).toString());
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        cache.clear();
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null || cache.isEmpty()) return;

        int lowY = Math.max(mc.level.getMinY(), Math.min(minY.get(), maxY.get()));
        int highY = Math.min(mc.level.getMaxY(), Math.max(minY.get(), maxY.get()));
        if (lowY > highY) return;

        double maxDistance = distance.get();
        double maxDistanceSq = maxDistance * maxDistance;
        double playerX = mc.player.getX();
        double playerY = mc.player.getY();
        double playerZ = mc.player.getZ();
        double chunkRenderRadius = maxDistance + 12.0;
        double chunkRenderRadiusSq = chunkRenderRadius * chunkRenderRadius;

        // Probe only chunk keys inside the render radius instead of iterating the
        // entire cache every frame. No block states are read in this render path.
        int playerChunkX = Math.floorDiv(mc.player.getBlockX(), 16);
        int playerChunkZ = Math.floorDiv(mc.player.getBlockZ(), 16);
        int chunkRadius = (int) Math.ceil(chunkRenderRadius / 16.0);
        int requiredScore = minScore.get();
        SettingColor color = getRenderColor();
        int renderedMarkers = 0;
        int markerLimit = maxMarkersPerFrame.get();
        boolean drawMarkers = showMarkers.get();

        for (int chunkX = playerChunkX - chunkRadius; chunkX <= playerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = playerChunkZ - chunkRadius; chunkZ <= playerChunkZ + chunkRadius; chunkZ++) {
                double dx = chunkX * 16.0 + 8.0 - playerX;
                double dz = chunkZ * 16.0 + 8.0 - playerZ;
                if (dx * dx + dz * dz > chunkRenderRadiusSq) continue;

                ChunkSnapshot snapshot = cache.get(chunkKey(chunkX, chunkZ));
                if (snapshot == null || snapshot.score < requiredScore) continue;

                AABB chunkBounds = new AABB(
                    chunkX * 16.0, lowY, chunkZ * 16.0,
                    chunkX * 16.0 + 16.0, highY + 1.0, chunkZ * 16.0 + 16.0
                );
                event.renderer.box(chunkBounds, color, color, ShapeMode.Lines, 0);

                if (!drawMarkers || renderedMarkers >= markerLimit) continue;
                for (Marker marker : snapshot.markers.values()) {
                    if (renderedMarkers >= markerLimit) break;
                    if (marker.y < lowY || marker.y > highY) continue;

                    double bx = marker.x + 0.5 - playerX;
                    double by = marker.y + 0.5 - playerY;
                    double bz = marker.z + 0.5 - playerZ;
                    if (bx * bx + by * by + bz * bz > maxDistanceSq) continue;

                    AABB bounds = new AABB(
                        marker.x, marker.y, marker.z,
                        marker.x + 1.0, marker.y + 1.0, marker.z + 1.0
                    ).inflate(0.002);
                    event.renderer.box(bounds, color, color, ShapeMode.Lines, 0);
                    renderedMarkers++;
                }
            }
        }
    }

    private void cacheChunk(LevelChunk chunk) {
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        ChunkSnapshot snapshot = new ChunkSnapshot();
        LevelChunkSection[] sections = chunk.getSections();

        // Palette pre-check avoids scanning empty/unrelated sections. Only sections
        // whose palette contains a target block are inspected (once, on chunk data).
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (section == null || !section.maybeHas(StorageChunkCacheScanner::isInteresting)) continue;

            int baseY = mc.level.getMinY() + (sectionIndex << 4);
            for (int localY = 0; localY < 16; localY++) {
                int y = baseY + localY;
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        BlockState state = section.getBlockState(localX, localY, localZ);
                        int weight = classify(state);
                        if (weight == 0) continue;

                        int x = (chunkX << 4) + localX;
                        int z = (chunkZ << 4) + localZ;
                        long posKey = BlockPos.asLong(x, y, z);
                        snapshot.put(posKey, x, y, z, weight, BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                    }
                }
            }
        }

        cache.put(chunkKey(chunkX, chunkZ), snapshot);
    }

    private SettingColor getRenderColor() {
        float h = chroma.get()
            ? ((System.currentTimeMillis() * chromaSpeed.get()) % 12000L) / 12000.0f
            : hue.get() / 360.0f;
        int rgb = Color.HSBtoRGB(h, saturation.get() / 100.0f, brightness.get() / 100.0f);
        return new SettingColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha.get());
    }

    private static boolean isInteresting(BlockState state) {
        return classify(state) > 0;
    }

    private static int classify(BlockState state) {
        Integer weight = BLOCK_WEIGHTS.get(state.getBlock());
        if (weight != null) return weight;
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (id.endsWith("_bed")) return 2;
        if (id.equals("nether_portal") || id.contains("piston")) return 3;
        if (id.contains("torch") || id.contains("glass") || id.endsWith("_sign")) return 1;
        if (id.equals("enchanting_table")) return 4;
        if (id.contains("planks") || id.contains("stairs") || id.contains("slab")
            || id.contains("fence") || id.contains("wall")) return 1;
        return 0;
    }

    private static Map<Block, Integer> createBlockWeights() {
        Map<Block, Integer> weights = new HashMap<>();
        add(weights, 5, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.BARREL, Blocks.ENDER_CHEST);
        add(weights, 6,
            Blocks.WHITE_SHULKER_BOX, Blocks.ORANGE_SHULKER_BOX, Blocks.MAGENTA_SHULKER_BOX,
            Blocks.LIGHT_BLUE_SHULKER_BOX, Blocks.YELLOW_SHULKER_BOX, Blocks.LIME_SHULKER_BOX,
            Blocks.PINK_SHULKER_BOX, Blocks.GRAY_SHULKER_BOX, Blocks.LIGHT_GRAY_SHULKER_BOX,
            Blocks.CYAN_SHULKER_BOX, Blocks.PURPLE_SHULKER_BOX, Blocks.BLUE_SHULKER_BOX,
            Blocks.BROWN_SHULKER_BOX, Blocks.GREEN_SHULKER_BOX, Blocks.RED_SHULKER_BOX,
            Blocks.BLACK_SHULKER_BOX
        );
        add(weights, 4, Blocks.HOPPER, Blocks.DISPENSER, Blocks.DROPPER, Blocks.CRAFTER, Blocks.ENCHANTING_TABLE);
        add(weights, 2, Blocks.FURNACE, Blocks.SMOKER, Blocks.BLAST_FURNACE, Blocks.BREWING_STAND, Blocks.LECTERN);
        add(weights, 8,
            Blocks.SPAWNER, Blocks.TRIAL_SPAWNER, Blocks.VAULT, Blocks.BEACON, Blocks.CONDUIT,
            Blocks.COMMAND_BLOCK, Blocks.CHAIN_COMMAND_BLOCK, Blocks.REPEATING_COMMAND_BLOCK,
            Blocks.STRUCTURE_BLOCK, Blocks.JIGSAW
        );
        return Map.copyOf(weights);
    }

    private static void add(Map<Block, Integer> weights, int weight, Block... blocks) {
        for (Block block : blocks) weights.put(block, weight);
    }

    private static long chunkKey(int x, int z) {
        return (x & 0xffffffffL) | ((z & 0xffffffffL) << 32);
    }

    public static void onChunkUnload(int chunkX, int chunkZ) {
        StorageChunkCacheScanner module = INSTANCE;
        if (module != null && module.isActive()) {
            module.cache.remove(chunkKey(chunkX, chunkZ));
        }
    }


    /** Immutable client-loaded block snapshot shared by ESP modules. */
    public record CachedBlock(int x, int y, int z, String blockId, int weight) {}

    /** Reads only cached entries; it never scans chunk sections or queries world block states. */
    public static java.util.List<CachedBlock> getCachedBlocks(double x, double y, double z, double range, int limit) {
        StorageChunkCacheScanner module = INSTANCE;
        if (module == null || !module.isActive() || limit <= 0) return java.util.List.of();
        java.util.ArrayList<CachedBlock> result = new java.util.ArrayList<>();
        int cx = ((int) Math.floor(x)) >> 4;
        int cz = ((int) Math.floor(z)) >> 4;
        int radius = (int) Math.ceil(range / 16.0) + 1;
        double rangeSq = range * range;
        for (int dx = -radius; dx <= radius && result.size() < limit; dx++) {
            for (int dz = -radius; dz <= radius && result.size() < limit; dz++) {
                ChunkSnapshot snapshot = module.cache.get(chunkKey(cx + dx, cz + dz));
                if (snapshot == null) continue;
                for (Marker marker : snapshot.markers.values()) {
                    double bx = marker.x + 0.5 - x, by = marker.y + 0.5 - y, bz = marker.z + 0.5 - z;
                    if (bx * bx + by * by + bz * bz > rangeSq) continue;
                    result.add(new CachedBlock(marker.x, marker.y, marker.z, marker.blockId, marker.weight));
                    if (result.size() >= limit) break;
                }
            }
        }
        return java.util.List.copyOf(result);
    }

    private static final class Marker {
        private final int x;
        private final int y;
        private final int z;
        private final int weight;
        private final String blockId;

        private Marker(int x, int y, int z, int weight, String blockId) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.weight = weight;
            this.blockId = blockId;
        }
    }

    private static final class ChunkSnapshot {
        private final Map<Long, Marker> markers = new HashMap<>();
        private int totalWeight;
        private int score;

        private void put(long key, int x, int y, int z, int weight, String blockId) {
            Marker previous = markers.put(key, new Marker(x, y, z, weight, blockId));
            if (previous != null) totalWeight -= previous.weight;
            totalWeight += weight;
            updateScore();
        }

        private void remove(long key) {
            Marker previous = markers.remove(key);
            if (previous == null) return;
            totalWeight -= previous.weight;
            updateScore();
        }

        private void updateScore() {
            score = Math.min(100, totalWeight * 4 + Math.max(0, markers.size() - 2) * 3);
        }
    }
}