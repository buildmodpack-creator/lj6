package com.example.addon.schematic;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

/**
 * Reads Sponge schematic v2/v3 files (.schem).
 *
 * The parser only reads block-state data. It deliberately does not execute
 * commands, place blocks, or modify the world.
 */
public final class SchematicReader {
    private static final long MAX_NBT_BYTES = 128L * 1024L * 1024L;
    private static final int MAX_BLOCKS = 16_000_000;

    private SchematicReader() {
    }

    public static SchematicData read(Path file) throws IOException {
        if (file == null) throw new IOException("No schematic file was selected.");
        if (!file.getFileName().toString().toLowerCase().endsWith(".schem")) {
            throw new IOException("Only Sponge .schem files are supported in this version.");
        }

        CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.create(MAX_NBT_BYTES));
        if (root.contains("Schematic")) root = root.getCompound("Schematic");

        int version = root.getInt("Version");
        if (version != 2 && version != 3) {
            throw new IOException("Unsupported schematic version: " + version + ". LJCT currently supports Sponge v2 and v3.");
        }

        int width = unsignedDimension(root, "Width");
        int height = unsignedDimension(root, "Height");
        int length = unsignedDimension(root, "Length");
        long volume = (long) width * height * length;
        if (volume <= 0 || volume > MAX_BLOCKS) {
            throw new IOException("Schematic size is invalid or too large (maximum " + MAX_BLOCKS + " blocks).");
        }

        CompoundTag blocks = version == 3 ? root.getCompound("Blocks") : root;
        String paletteKey = "Palette";
        String dataKey = version == 3 ? "Data" : "BlockData";

        if (!blocks.contains(paletteKey) || !blocks.contains(dataKey)) {
            throw new IOException("Schematic is missing its block palette or block data.");
        }

        CompoundTag paletteTag = blocks.getCompound(paletteKey);
        Map<Integer, String> palette = new LinkedHashMap<>();
        for (String state : paletteTag.getAllKeys()) {
            int index = paletteTag.getInt(state);
            if (index < 0 || palette.put(index, normalizeState(state)) != null) {
                throw new IOException("Schematic contains an invalid or duplicate palette index.");
            }
        }
        if (palette.isEmpty()) throw new IOException("Schematic block palette is empty.");

        byte[] encoded = blocks.getByteArray(dataKey);
        String[] states = decodeBlockData(encoded, (int) volume, palette);
        return new SchematicData(width, height, length, states, palette);
    }

    private static int unsignedDimension(CompoundTag tag, String key) throws IOException {
        int value = tag.getShort(key) & 0xFFFF;
        if (value < 1) throw new IOException("Invalid schematic dimension: " + key);
        return value;
    }

    private static String normalizeState(String state) {
        return state.indexOf(':') >= 0 ? state : "minecraft:" + state;
    }

    private static String[] decodeBlockData(byte[] bytes, int expectedCount, Map<Integer, String> palette) throws IOException {
        String[] result = new String[expectedCount];
        int cursor = 0;

        for (int i = 0; i < expectedCount; i++) {
            int value = 0;
            int shift = 0;
            boolean complete = false;

            while (cursor < bytes.length && shift <= 28) {
                int next = bytes[cursor++] & 0xFF;
                value |= (next & 0x7F) << shift;
                if ((next & 0x80) == 0) {
                    complete = true;
                    break;
                }
                shift += 7;
            }

            if (!complete) throw new IOException("Schematic block data is truncated or has an invalid VarInt.");
            String state = palette.get(value);
            if (state == null) throw new IOException("Schematic references missing palette entry " + value + ".");
            result[i] = state;
        }

        if (cursor != bytes.length) {
            throw new IOException("Schematic contains extra bytes after its block data.");
        }
        return result;
    }
}
