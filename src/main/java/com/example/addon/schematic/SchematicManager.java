package com.example.addon.schematic;

import net.minecraft.core.BlockPos;

/** Holds the currently loaded schematic and its optional world anchor. */
public final class SchematicManager {
    private static SchematicData current;
    private static BlockPos anchor;

    private SchematicManager() {
    }

    public static void setCurrent(SchematicData schematic) {
        current = schematic;
        anchor = null;
    }

    public static SchematicData getCurrent() {
        return current;
    }

    public static void setAnchor(BlockPos pos) {
        anchor = pos == null ? null : pos.immutable();
    }

    public static BlockPos getAnchor() {
        return anchor;
    }

    public static void clear() {
        current = null;
        anchor = null;
    }
}
