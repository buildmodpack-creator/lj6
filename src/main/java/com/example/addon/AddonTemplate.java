package com.example.addon;

import com.example.addon.commands.CommandExample;
import com.example.addon.commands.SchematicCommand;
import com.example.addon.hud.HudExample;
import com.example.addon.hud.SchematicProgressHud;
import com.example.addon.hud.SusChunkHud;
import com.example.addon.modules.BuildAssist;
import com.example.addon.modules.AutoBlockSelect;
import com.example.addon.modules.ChunkVisual;
import com.example.addon.modules.EntityOutline;
import com.example.addon.modules.CustomLowFire;
import com.example.addon.modules.EntityCuller;
import com.example.addon.modules.ParticleLimiter;
import com.example.addon.modules.StorageChunkCacheScanner;
import com.example.addon.modules.SusChunkDetector;
import com.example.addon.modules.NewOldChunkDetector;
import com.example.addon.modules.StashAndTunnelFinder;
import com.example.addon.modules.CS2KnifeViewmodel;
import com.example.addon.modules.WingsAndHalo;
import com.example.addon.modules.EntityChamsAndCape;
import com.example.addon.modules.FlightAndKillVisuals;
import com.example.addon.modules.SchematicAutoBuilder;
import com.example.addon.modules.SprintReset;
import com.example.addon.modules.CrystalAnchorHelper;
import com.example.addon.modules.MaceSlamAssist;
import com.example.addon.modules.ElytraChestplateSwap;
import com.example.addon.modules.TargetHUD;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("LJCT");
    public static final Category VISUAL_CATEGORY = new Category("Visual");
    public static final Category PVP_CATEGORY = new Category("PvP");
    public static final Category FIX_LAG_CATEGORY = new Category("Fix Lag");
    public static final HudGroup HUD_GROUP = new HudGroup("LJCT");

    @Override
    public void onInitialize() {
        LOG.info("Initializing LJCT Meteor Addon");

        Modules.get().add(new BuildAssist());
        Modules.get().add(new AutoBlockSelect());
        Modules.get().add(new ChunkVisual());
        Modules.get().add(new EntityOutline());
        Modules.get().add(new CS2KnifeViewmodel());
        Modules.get().add(new WingsAndHalo());
        Modules.get().add(new EntityChamsAndCape());
        Modules.get().add(new FlightAndKillVisuals());

        Modules.get().add(new SprintReset());
        Modules.get().add(new CrystalAnchorHelper());
        Modules.get().add(new MaceSlamAssist());
        Modules.get().add(new ElytraChestplateSwap());
        Modules.get().add(new TargetHUD());

        Modules.get().add(new CustomLowFire());
        Modules.get().add(new EntityCuller());
        Modules.get().add(new ParticleLimiter());
        Modules.get().add(new StorageChunkCacheScanner());
        Modules.get().add(new SusChunkDetector());
        Modules.get().add(new NewOldChunkDetector());
        Modules.get().add(new StashAndTunnelFinder());
        Modules.get().add(new SchematicAutoBuilder());

        Commands.add(new CommandExample());
        Commands.add(new SchematicCommand());
        Hud.get().register(HudExample.INFO);
        Hud.get().register(SchematicProgressHud.INFO);
        Hud.get().register(SusChunkHud.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
        Modules.registerCategory(VISUAL_CATEGORY);
        Modules.registerCategory(PVP_CATEGORY);
        Modules.registerCategory(FIX_LAG_CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("buildmodpack-creator", "lj6");
    }
}
