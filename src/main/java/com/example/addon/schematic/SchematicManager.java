package com.example.addon.schematic;

/** Holds the currently loaded schematic for Builder features. */
public final class SchematicManager {
    private static SchematicData current;

    private SchematicManager() {
    }

    public static void setCurrent(SchematicData schematic) {
        current = schematic;
    }

    public static SchematicData getCurrent() {
        return current;
    }

    public static void clear() {
        current = null;
    }
}
