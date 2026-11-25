package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.schematic.SchematicData;
import com.example.addon.schematic.SchematicManager;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Selects a matching block from the hotbar for the schematic position
 * the player is aiming to place into. It never clicks or places blocks.
 */
public class AutoBlockSelect extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyWhenMissing = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-missing")
        .description("Only switch when the target schematic position does not already contain the expected block.")
        .defaultValue(true)
        .build()
    );

    public AutoBlockSelect() {
        super(AddonTemplate.CATEGORY, "auto-block-select",
            "Automatically selects the matching block from your hotbar for the schematic position you're aiming at. Does not place blocks.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit)) return;

        SchematicData schematic = SchematicManager.getCurrent();
        if (schematic == null) return;

        BlockPos origin = SchematicManager.getAnchor();
        if (origin == null) origin = mc.player.blockPosition();

        BlockPos target = hit.getBlockPos().relative(hit.getDirection());
        BlockPos local = SchematicManager.toLocalPos(target, origin);
        int x = local.getX();
        int y = local.getY();
        int z = local.getZ();

        if (x < 0 || x >= schematic.getWidth()
            || y < 0 || y >= schematic.getHeight()
            || z < 0 || z >= schematic.getLength()) return;

        String expectedState = schematic.getBlockState(x, y, z);
        if (SchematicData.isAir(expectedState)) return;

        if (onlyWhenMissing.get()) {
            String actualId = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(target).getBlock()).toString();
            if (actualId.equals(SchematicData.getBlockId(expectedState))) return;
        }

        for (int slot = 0; slot < 9; slot++) {
            var stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) continue;

            String blockId = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString();
            if (blockId.equals(SchematicData.getBlockId(expectedState))) {
                mc.player.getInventory().selected = slot;
                return;
            }
        }
    }
}
