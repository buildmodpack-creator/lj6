package com.example.addon.schematic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable block-state data decoded from a Sponge schematic. */
public final class SchematicData {
    private final int width;
    private final int height;
    private final int length;
    private final String[] states;
    private final Map<Integer, String> palette;

    SchematicData(int width, int height, int length, String[] states, Map<Integer, String> palette) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.states = states.clone();
        this.palette = Collections.unmodifiableMap(new LinkedHashMap<>(palette));
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getLength() { return length; }
    public int getBlockCount() { return states.length; }
    public Map<Integer, String> getPalette() { return palette; }

    /** Coordinates are local to the schematic, with X changing fastest. */
    public String getBlockState(int x, int y, int z) {
        if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= length) {
            throw new IndexOutOfBoundsException("Schematic coordinate is outside its dimensions.");
        }
        return states[x + z * width + y * width * length];
    }

    /** Counts required non-air blocks by block ID (state properties are ignored). */
    public Map<String, Integer> getRequiredMaterialCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String state : states) {
            if (isAir(state)) continue;
            counts.merge(getBlockId(state), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(counts);
    }

    /** Kept for callers that need the original palette-state counts, including air. */
    public Map<String, Integer> getMaterialCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String state : states) counts.merge(state, 1, Integer::sum);
        return Collections.unmodifiableMap(counts);
    }

    public static String getBlockId(String state) {
        int properties = state.indexOf('[');
        return properties >= 0 ? state.substring(0, properties) : state;
    }

    public static boolean isAir(String state) {
        String id = getBlockId(state);
        return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
    }
}
