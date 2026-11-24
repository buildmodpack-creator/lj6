package com.example.addon.commands;

import com.example.addon.schematic.SchematicData;
import com.example.addon.schematic.SchematicManager;
import com.example.addon.schematic.SchematicReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loads and inspects a Sponge .schem file from the game's schematics folder.
 * The command only reads files and inventory contents; it never places blocks.
 */
public class SchematicCommand extends Command {
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[A-Za-z0-9._ -]+\\.schem");

    public SchematicCommand() {
        super("ljct-schem", "Loads and inspects a Sponge .schem blueprint.");
    }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("load")
            .then(argument("file", StringArgumentType.greedyString()).executes(context -> {
                String fileName = StringArgumentType.getString(context, "file").trim();
                if (!SAFE_FILE_NAME.matcher(fileName).matches() || fileName.equals(".schem")) {
                    info("Invalid filename. Use a .schem filename without folders.");
                    return SINGLE_SUCCESS;
                }

                Path folder = FabricLoader.getInstance().getGameDir().resolve("schematics");
                try {
                    Files.createDirectories(folder);
                    Path file = folder.resolve(fileName).normalize();
                    if (!file.getParent().equals(folder.normalize()) || !Files.isRegularFile(file)) {
                        info("File not found. Put the .schem file in: " + folder.toAbsolutePath());
                        return SINGLE_SUCCESS;
                    }

                    SchematicData schematic = SchematicReader.read(file);
                    SchematicManager.setCurrent(schematic);
                    info("Loaded " + fileName + ": " + schematic.getWidth() + " x "
                        + schematic.getHeight() + " x " + schematic.getLength()
                        + " (" + schematic.getBlockCount() + " blocks).");

                    schematic.getRequiredMaterialCounts().entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                        .limit(8)
                        .forEach(entry -> info(entry.getValue() + " x " + entry.getKey()));
                } catch (IOException | RuntimeException exception) {
                    info("Could not load schematic: " + exception.getMessage());
                }
                return SINGLE_SUCCESS;
            }))
        );

        builder.then(literal("info").executes(context -> {
            SchematicData schematic = SchematicManager.getCurrent();
            if (schematic == null) {
                info("No schematic loaded. Use .ljct-schem load filename.schem");
            } else {
                info("Current schematic: " + schematic.getWidth() + " x "
                    + schematic.getHeight() + " x " + schematic.getLength()
                    + " (" + schematic.getBlockCount() + " blocks).");
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("materials").executes(context -> {
            SchematicData schematic = SchematicManager.getCurrent();
            Minecraft minecraft = Minecraft.getInstance();
            if (schematic == null) {
                info("No schematic loaded. Use .ljct-schem load filename.schem");
                return SINGLE_SUCCESS;
            }
            if (minecraft.player == null) {
                info("Open a world before checking inventory materials.");
                return SINGLE_SUCCESS;
            }

            Map<String, Integer> inventory = new HashMap<>();
            for (int slot = 0; slot < minecraft.player.getInventory().getContainerSize(); slot++) {
                var stack = minecraft.player.getInventory().getItem(slot);
                if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) continue;
                String blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString();
                inventory.merge(blockId, stack.getCount(), Integer::sum);
            }

            Map<String, Integer> required = schematic.getRequiredMaterialCounts();
            int totalMissing = 0;
            for (Map.Entry<String, Integer> entry : required.entrySet()) {
                int have = inventory.getOrDefault(entry.getKey(), 0);
                totalMissing += Math.max(0, entry.getValue() - have);
            }

            info("Material check: " + required.size() + " block types; " + totalMissing + " blocks still needed.");
            required.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(20)
                .forEach(entry -> {
                    int have = inventory.getOrDefault(entry.getKey(), 0);
                    int missing = Math.max(0, entry.getValue() - have);
                    info(entry.getKey() + " | need " + entry.getValue() + " | have " + have + " | missing " + missing);
                });
            return SINGLE_SUCCESS;
        }));


        builder.then(literal("anchor").executes(context -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (SchematicManager.getCurrent() == null) {
                info("Load a schematic first.");
            } else if (minecraft.player == null) {
                info("Open a world before setting the schematic anchor.");
            } else {
                SchematicManager.setAnchor(minecraft.player.blockPosition());
                info("Schematic origin set to your current block position.");
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("origin").executes(context -> {
            var anchor = SchematicManager.getAnchor();
            if (anchor == null) {
                info("No fixed origin. Preview follows your current block position.");
            } else {
                info("Schematic origin: " + anchor.getX() + ", " + anchor.getY() + ", " + anchor.getZ());
            }
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("clear").executes(context -> {
            SchematicManager.clear();
            info("Cleared the loaded schematic.");
            return SINGLE_SUCCESS;
        }));
    }
}
