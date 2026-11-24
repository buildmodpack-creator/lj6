package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.schematic.SchematicData;
import com.example.addon.schematic.SchematicManager;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Visual building helper. All block placement remains manual.
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

    private final Setting<Boolean> showSchematicPreview = sgGeneral.add(new BoolSetting.Builder()
        .name("schematic-preview")
        .description("Draw a lightweight outline preview of the loaded schematic at your current block position.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> previewLimit = sgGeneral.add(new IntSetting.Builder()
        .name("preview-block-limit")
        .description("Maximum number of schematic blocks drawn per frame. Keep this low on mobile.")
        .defaultValue(256)
        .min(16)
        .sliderMax(2048)
        .visible(showSchematicPreview::get)
        .build()
    );

    private final Setting<SettingColor> schematicColor = sgGeneral.add(new ColorSetting.Builder()
        .name("schematic-color")
        .description("Outline color for the schematic preview.")
        .defaultValue(new SettingColor(90, 210, 255, 90))
        .visible(showSchematicPreview::get)
        .build()
    );

    public BuildAssist() {
        super(AddonTemplate.CATEGORY, "build-assist", "Highlights the targeted block and previews manual building positions.");
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;

        if (mc.hitResult instanceof BlockHitResult hit && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            AABB targetBox = new AABB(hit.getBlockPos()).inflate(0.002);
            SettingColor target = targetColor.get();
            event.renderer.box(targetBox, target, target, ShapeMode.Both, 0);

            if (showPlacementPreview.get() && mc.player.getMainHandItem().getItem() instanceof BlockItem) {
                BlockPos placementPos = hit.getBlockPos().relative(hit.getDirection());
                AABB placementBox = new AABB(placementPos).inflate(0.004);
                SettingColor placement = placementColor.get();
                event.renderer.box(placementBox, placement, placement, ShapeMode.Both, 0);
            }
        }

        if (!showSchematicPreview.get()) return;
        SchematicData schematic = SchematicManager.getCurrent();
        if (schematic == null) return;

        BlockPos origin = mc.player.blockPosition();
        SettingColor color = schematicColor.get();
        int drawn = 0;
        int limit = previewLimit.get();

        for (int y = 0; y < schematic.getHeight() && drawn < limit; y++) {
            for (int z = 0; z < schematic.getLength() && drawn < limit; z++) {
                for (int x = 0; x < schematic.getWidth() && drawn < limit; x++) {
                    String state = schematic.getBlockState(x, y, z);
                    if (state.equals("minecraft:air") || state.startsWith("minecraft:cave_air")
                        || state.startsWith("minecraft:void_air")) continue;

                    BlockPos pos = origin.offset(x, y, z);
                    event.renderer.box(new AABB(pos).inflate(0.001), color, color, ShapeMode.Lines, 0);
                    drawn++;
                }
            }
        }
    }
}
