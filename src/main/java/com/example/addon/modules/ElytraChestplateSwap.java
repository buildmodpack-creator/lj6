package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ElytraChestplateSwap extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Integer> cooldown = sg.add(new IntSetting.Builder()
        .name("swap-cooldown-ticks")
        .description("Minimum delay between successful equipment swaps.")
        .defaultValue(12).range(1, 60).build());
    private final Setting<Boolean> chestplateOnGround = sg.add(new BoolSetting.Builder()
        .name("chestplate-when-grounded")
        .description("Use a chestplate while grounded and return to Elytra while airborne.")
        .defaultValue(false).build());

    private long nextSwapTick;

    public ElytraChestplateSwap() {
        super(AddonTemplate.PVP_CATEGORY, "elytra-chestplate-swap",
            "Safely swaps the chest equipment slot between an Elytra and a chestplate.");
        sg.add(new KeybindSetting.Builder().name("swap-key")
            .defaultValue(Keybind.none()).action(this::swap).build());
    }

    @Override
    public void onDeactivate() {
        nextSwapTick = 0L;
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        nextSwapTick = 0L;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!chestplateOnGround.get() || mc.player == null) return;

        ItemStack equipped = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (mc.player.onGround() && equipped.is(Items.ELYTRA)) {
            swap();
        } else if (!mc.player.onGround() && !equipped.is(Items.ELYTRA) && isChestplate(equipped)) {
            swap();
        }
    }

    private void swap() {
        if (mc.player == null || mc.gameMode == null) return;
        if (mc.player.tickCount < nextSwapTick) return;

        AbstractContainerMenu menu = mc.player.inventoryMenu;
        if (menu == null || menu.slots.size() <= 6 || !menu.getCarried().isEmpty()) return;

        ItemStack equipped = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        boolean wantElytra = !equipped.is(Items.ELYTRA);
        int inventoryIndex = wantElytra ? find(Items.ELYTRA) : findChestplate();
        if (inventoryIndex < 0) return;

        int menuSlot = inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
        if (menuSlot < 0 || menuSlot >= menu.slots.size() || menuSlot == 6) return;

        ItemStack target = menu.getSlot(menuSlot).getItem();
        if (target.isEmpty() || (wantElytra ? !target.is(Items.ELYTRA) : !isChestplate(target))) return;
        ItemStack chestSlot = menu.getSlot(6).getItem();
        if (chestSlot.isEmpty() != equipped.isEmpty()) return;
        if (!chestSlot.isEmpty() && chestSlot.getItem() != equipped.getItem()) return;

        int syncId = menu.containerId;
        mc.gameMode.handleInventoryMouseClick(syncId, 6, 0, ClickType.PICKUP, mc.player);
        mc.gameMode.handleInventoryMouseClick(syncId, menuSlot, 0, ClickType.PICKUP, mc.player);
        mc.gameMode.handleInventoryMouseClick(syncId, 6, 0, ClickType.PICKUP, mc.player);

        nextSwapTick = (long) mc.player.tickCount + cooldown.get();
    }

    private int find(Item item) {
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    private int findChestplate() {
        for (int i = 0; i < 36; i++) {
            if (isChestplate(mc.player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    private static boolean isChestplate(ItemStack stack) {
        return stack.is(Items.LEATHER_CHESTPLATE)
            || stack.is(Items.CHAINMAIL_CHESTPLATE)
            || stack.is(Items.IRON_CHESTPLATE)
            || stack.is(Items.GOLDEN_CHESTPLATE)
            || stack.is(Items.DIAMOND_CHESTPLATE)
            || stack.is(Items.NETHERITE_CHESTPLATE);
    }
}
