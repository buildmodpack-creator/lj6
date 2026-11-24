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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Read-only visual building helper. All movement and block placement remain manual.
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
        .description("Draw an outline preview of the loaded schematic at your current block position.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> compareWorld = sgGeneral.add(new BoolSetting.Builder()
        .name("compare-world")
        .description("Color schematic positions by whether the matching block is present in the world.")
        .defaultValue(true)
        .visible(showSchematicPreview::get)
        .build()
    );

    private final Setting<Integer> previewLimit = sgGeneral.add(new IntSetting.Builder()
        .name("preview-block-limit")
        .description("Maximum number of non-air schematic blocks drawn per frame. Keep this low on mobile.")
        .defaultValue(256)
        .min(16)
        .sliderMax(2048)
        .visible(showSchematicPreview::get)
        .build()
    );

    private final Setting<SettingColor> schematicColor = sgGeneral.add(new ColorSetting.Builder()
        .name("schematic-color")
        .description("Outline color for blocks that already match.")
        .defaultValue(new SettingColor(90, 210, 255, 90))
        .visible(showSchematicPreview::get)
        .build()
    );

    private final Setting<SettingColor> missingColor = sgGeneral.add(new ColorSetting.Builder()
        .name("missing-color")
        .description("Color for positions where the expected block is missing.")
        .defaultValue(new SettingColor(80, 220, 120, 130))
        .visible(() -> showSchematicPreview.get() && compareWorld.get())
        .build()
    );

    private final Setting<SettingColor> wrongColor = sgGeneral.add(new ColorSetting.Builder()
        .name("wrong-color")
        .description("Color for positions occupied by a different block.")
        .defaultValue(new SettingColor(245, 70, 70, 150))
        .visible(() -> showSchematicPreview.get() && compareWorld.get())
        .build()
    );

    private final Setting<SettingColor> wrongStateColor = sgGeneral.add(new ColorSetting.Builder()
        .name("wrong-state-color")
        .description("Yellow marks the right block type with incorrect orientation or block-state properties.")
        .defaultValue(new SettingColor(255, 210, 45, 170))
        .visible(() -> showSchematicPreview.get() && compareWorld.get())
        .build()
    );

    public BuildAssist() {
        super(AddonTemplate.CATEGORY, "build-assist", "Highlights targets and compares a loaded schematic with the world.");
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

        BlockPos origin = SchematicManager.getAnchor();
        if (origin == null) origin = mc.player.blockPosition();
        int drawn = 0;
        int limit = previewLimit.get();

        for (int y = 0; y < schematic.getHeight() && drawn < limit; y++) {
            for (int z = 0; z < schematic.getLength() && drawn < limit; z++) {
                for (int x = 0; x < schematic.getWidth() && drawn < limit; x++) {
                    String expectedState = schematic.getBlockState(x, y, z);
                    if (SchematicData.isAir(expectedState)) continue;

                    BlockPos pos = origin.offset(x, y, z);
                    SettingColor color = schematicColor.get();

                    if (compareWorld.get()) {
                        var actualState = mc.level.getBlockState(pos);
                        if (actualState.isAir()) {
                            color = missingColor.get();
                        } else {
                            String actualId = BuiltInRegistries.BLOCK.getKey(actualState.getBlock()).toString();
                            if (!actualId.equals(SchematicData.getBlockId(expectedState))) {
                                color = wrongColor.get();
                            } else if (!hasMatchingBlockState(actualState.toString(), expectedState)) {
                                color = wrongStateColor.get();
                            }
                        }
                    }

                    event.renderer.box(new AABB(pos).inflate(0.001), color, color, ShapeMode.Lines, 0);
                    drawn++;
                }
            }
        }
    }

    /**
     * Compares serialized block-state properties, such as facing, axis, half,
     * rotation, and redstone connection flags. Block IDs are checked separately.
     */
    private static boolean hasMatchingBlockState(String actualState, String expectedState) {
        return normalizeState(actualState).equals(normalizeState(expectedState));
    }

    private static String normalizeState(String state) {
        int propertiesStart = state.indexOf('[');
        if (propertiesStart < 0) return "";
        int propertiesEnd = state.lastIndexOf(']');
        if (propertiesEnd < propertiesStart) return state.substring(propertiesStart);
        return state.substring(propertiesStart + 1, propertiesEnd);
    }
}
