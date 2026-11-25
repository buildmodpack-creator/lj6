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

/** Loads Sponge and Litematica blueprints from the game schematics folder. */
public class SchematicCommand extends Command {
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[A-Za-z0-9._ -]+\\.(schem|litematic)", Pattern.CASE_INSENSITIVE);

    public SchematicCommand() { super("ljct-schem", "Loads and inspects a .schem or .litematic blueprint."); }

    @Override
    public void build(LiteralArgumentBuilder<SharedSuggestionProvider> builder) {
        builder.then(literal("load").then(argument("file", StringArgumentType.greedyString()).executes(context -> {
            String name=StringArgumentType.getString(context,"file").trim();
            if(!SAFE_FILE_NAME.matcher(name).matches()){info("Use a .schem or .litematic filename without folders.");return SINGLE_SUCCESS;}
            Path folder=FabricLoader.getInstance().getGameDir().resolve("schematics");
            try {
                Files.createDirectories(folder);
                Path file=folder.resolve(name).normalize();
                if(!file.getParent().equals(folder.normalize())||!Files.isRegularFile(file)){info("File not found in: "+folder.toAbsolutePath());return SINGLE_SUCCESS;}
                SchematicData data=SchematicReader.read(file);
                SchematicManager.setCurrent(data);
                info("Loaded "+name+": "+data.getWidth()+" x "+data.getHeight()+" x "+data.getLength()+" ("+data.getBlockCount()+" cells).");
                data.getRequiredMaterialCounts().entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue(Comparator.reverseOrder())).limit(8).forEach(e->info(e.getValue()+" x "+e.getKey()));
            } catch(IOException|RuntimeException e){info("Could not load schematic: "+e.getMessage());}
            return SINGLE_SUCCESS;
        })));

        builder.then(literal("info").executes(context -> {
            SchematicData d=SchematicManager.getCurrent();
            if(d==null)info("No schematic loaded. Use .ljct-schem load filename.litematic");
            else info("Loaded: "+d.getWidth()+" x "+d.getHeight()+" x "+d.getLength()+" ("+d.getBlockCount()+" cells).");
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("materials").executes(context -> {
            SchematicData d=SchematicManager.getCurrent(); Minecraft mc=Minecraft.getInstance();
            if(d==null||mc.player==null){info("Load a schematic and enter a world first.");return SINGLE_SUCCESS;}
            Map<String,Integer> inventory=new HashMap<>();
            for(int i=0;i<mc.player.getInventory().getContainerSize();i++){
                var stack=mc.player.getInventory().getItem(i);
                if(!stack.isEmpty()&&stack.getItem() instanceof BlockItem item)inventory.merge(BuiltInRegistries.BLOCK.getKey(item.getBlock()).toString(),stack.getCount(),Integer::sum);
            }
            int missing=0;
            for(var e:d.getRequiredMaterialCounts().entrySet())missing+=Math.max(0,e.getValue()-inventory.getOrDefault(e.getKey(),0));
            info("Material check: "+missing+" blocks missing across "+d.getRequiredMaterialCounts().size()+" block types.");
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("anchor").executes(context -> {
            Minecraft mc=Minecraft.getInstance();
            if(dMissing())info("Load a schematic first.");
            else if(mc.player==null)info("Enter a world first.");
            else {SchematicManager.setAnchor(mc.player.blockPosition());info("Origin anchored at your current block position.");}
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("origin").executes(context -> {
            var p=SchematicManager.getAnchor();
            info(p==null?"No fixed origin; builder uses your current block position.":"Origin: "+p.getX()+", "+p.getY()+", "+p.getZ());
            return SINGLE_SUCCESS;
        }));
        builder.then(literal("clear").executes(context -> {SchematicManager.clear();info("Cleared schematic.");return SINGLE_SUCCESS;}));
    }
    private static boolean dMissing(){return SchematicManager.getCurrent()==null;}
}