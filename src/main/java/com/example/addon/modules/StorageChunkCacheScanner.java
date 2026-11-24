package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.awt.Color;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Event-driven scanner. Chunk contents are inspected only when chunk data arrives;
 * later block changes update one cached position. Rendering reads the cache only.
 */
public class StorageChunkCacheScanner extends Module {
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

    private final Setting<Boolean> chroma = sgGeneral.add(new BoolSetting.Builder()
        .name("chroma")
        .description("Cycles the hue over time.")
        .defaultValue(false)
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
            "Caches storage/special blocks on chunk data and block updates, then scores unusual chunks.");
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

        long chunkKey = chunkKey(event.pos.getX() >> 4, event.pos.getZ() >> 4);
        ChunkSnapshot snapshot = cache.get(chunkKey);
        if (snapshot == null) return;

        long posKey = event.pos.asLong();
        int weight = classify(event.newState);
        if (weight == 0) snapshot.markers.remove(posKey);
        else snapshot.markers.put(posKey, new Marker(posKey, weight));
        snapshot.recalculateScore();
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
        SettingColor color = getRenderColor();
        int renderedMarkers = 0;
        int markerLimit = maxMarkersPerFrame.get();

        for (Map.Entry<Long, ChunkSnapshot> entry : cache.entrySet()) {
            ChunkSnapshot snapshot = entry.getValue();
            if (snapshot.score < minScore.get()) continue;

            int chunkX = (int) (long) entry.getKey();
            int chunkZ = (int) (entry.getKey() >> 32);
            double centerX = chunkX * 16.0 + 8.0;
            double centerZ = chunkZ * 16.0 + 8.0;
            double dx = centerX - playerX;
            double dz = centerZ - playerZ;
            double nearestChunkDistanceSq = dx * dx + dz * dz;
            double renderRadius = maxDistance + 12.0;
            if (nearestChunkDistanceSq > renderRadius * renderRadius) continue;

            AABB chunkBounds = new AABB(
                chunkX * 16.0, lowY, chunkZ * 16.0,
                chunkX * 16.0 + 16.0, highY + 1.0, chunkZ * 16.0 + 16.0
            );
            event.renderer.box(chunkBounds, color, color, ShapeMode.Lines, 0);

            if (!showMarkers.get() || renderedMarkers >= markerLimit) continue;
            for (Marker marker : snapshot.markers.values()) {
                if (renderedMarkers >= markerLimit) break;
                BlockPos pos = BlockPos.of(marker.pos);
                int y = pos.getY();
                if (y < lowY || y > highY) continue;

                double bx = pos.getX() + 0.5 - playerX;
                double by = pos.getY() + 0.5 - playerY;
                double bz = pos.getZ() + 0.5 - playerZ;
                if (bx * bx + by * by + bz * bz > maxDistanceSq) continue;

                AABB blockBounds = new AABB(pos).inflate(0.002);
                event.renderer.box(blockBounds, color, color, ShapeMode.Lines, 0);
                renderedMarkers++;
            }
        }
    }

    private void cacheChunk(LevelChunk chunk) {
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;
        ChunkSnapshot snapshot = new ChunkSnapshot();

        // Block-entity positions are the compact candidate list; no 16x16xheight block scan.
        for (BlockPos pos : chunk.getBlockEntities().keySet()) {
            int weight = classify(chunk.getBlockState(pos));
            if (weight != 0) {
                long posKey = pos.asLong();
                snapshot.markers.put(posKey, new Marker(posKey, weight));
            }
        }

        snapshot.recalculateScore();
        cache.put(chunkKey(chunkX, chunkZ), snapshot);
    }

    private SettingColor getRenderColor() {
        float h = chroma.get()
            ? ((System.currentTimeMillis() * chromaSpeed.get()) % 12000L) / 12000.0f
            : hue.get() / 360.0f;
        int rgb = Color.HSBtoRGB(h, saturation.get() / 100.0f, brightness.get() / 100.0f);
        return new SettingColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha.get());
    }

    private static int classify(BlockState state) {
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return switch (id) {
            case "chest", "trapped_chest", "barrel", "ender_chest" -> 5;
            case "shulker_box", "white_shulker_box", "orange_shulker_box", "magenta_shulker_box",
                 "light_blue_shulker_box", "yellow_shulker_box", "lime_shulker_box", "pink_shulker_box",
                 "gray_shulker_box", "light_gray_shulker_box", "cyan_shulker_box", "purple_shulker_box",
                 "blue_shulker_box", "brown_shulker_box", "green_shulker_box", "red_shulker_box",
                 "black_shulker_box" -> 6;
            case "hopper", "dispenser", "dropper", "crafter" -> 4;
            case "furnace", "smoker", "blast_furnace", "brewing_stand", "lectern" -> 2;
            case "spawner", "trial_spawner", "vault", "beacon", "conduit",
                 "command_block", "chain_command_block", "repeating_command_block" -> 8;
            default -> 0;
        };
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

    private static final class Marker {
        private final long pos;
        private final int weight;

        private Marker(long pos, int weight) {
            this.pos = pos;
            this.weight = weight;
        }
    }

    private static final class ChunkSnapshot {
        private final Map<Long, Marker> markers = new HashMap<>();
        private int score;

        private void recalculateScore() {
            int totalWeight = 0;
            for (Marker marker : markers.values()) totalWeight += marker.weight;
            score = Math.min(100, totalWeight * 4 + Math.max(0, markers.size() - 2) * 3);
        }
    }
}
