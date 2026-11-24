package com.example.addon.commands;

import com.example.addon.schematic.SchematicData;
import com.example.addon.schematic.SchematicManager;
import com.example.addon.schematic.SchematicReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loads a Sponge .schem file from the game's schematics folder.
 * Only the named file in that folder can be opened.
 */
public class SchematicCommand extends Command {
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[A-Za-z0-9._ -]+\\.schem");

    public SchematicCommand() {
        super("ljct-schem", "Loads and inspects a Sponge .schem blueprint.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
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

                    schematic.getMaterialCounts().entrySet().stream()
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

        builder.then(literal("clear").executes(context -> {
            SchematicManager.clear();
            info("Cleared the loaded schematic.");
            return SINGLE_SUCCESS;
        }));
    }
}
