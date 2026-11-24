package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * First LJCT Builder milestone: highlights the looked-at block and the adjacent
 * placement position when the player is holding a block.
 * This is a visual aid and leaves movement and placement under player control.
 */
public class BuildAssist extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showPlacementPreview = sgGeneral.add(new BoolSetting.Builder()
        .name("placement-preview")
        .description("Show the adjacent position where the held block could be placed.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> targetColor = sgGeneral.add(new ColorSetting.Builder()
        .name("target-color")
        .description("Color of the block you're looking at.")
        .defaultValue(new SettingColor(235, 120, 255, 70))
        .build()
    );

    private final Setting<SettingColor> placementColor = sgGeneral.add(new ColorSetting.Builder()
        .name("placement-color")
        .description("Color of the adjacent placement preview.")
        .defaultValue(new SettingColor(80, 220, 160, 100))
        .build()
    );

    public BuildAssist() {
        super(AddonTemplate.CATEGORY, "build-assist", "Highlights the targeted block and previews the adjacent placement position.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;
        if (!(mc.hitResult instanceof BlockHitResult hit)) return;

        AABB targetBox = new AABB(hit.getBlockPos()).inflate(0.002);
        SettingColor target = targetColor.get();
        event.renderer.box(targetBox, target, target, ShapeMode.Both, 0);

        if (!showPlacementPreview.get()) return;
        if (!(mc.player.getMainHandItem().getItem() instanceof BlockItem)) return;

        BlockPos placementPos = hit.getBlockPos().relative(hit.getDirection());
        AABB placementBox = new AABB(placementPos).inflate(0.004);
        SettingColor placement = placementColor.get();
        event.renderer.box(placementBox, placement, placement, ShapeMode.Both, 0);
    }
}
