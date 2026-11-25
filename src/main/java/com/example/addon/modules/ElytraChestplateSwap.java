package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;

public class ElytraChestplateSwap extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Integer> cooldown = sg.add(new IntSetting.Builder().name("swap-cooldown-ticks").defaultValue(12).range(1, 60).build());
    private final Setting<Boolean> chestplateOnGround = sg.add(new BoolSetting.Builder().name("chestplate-when-grounded").description("Automatically equips a chestplate instead of an Elytra while standing on the ground.").defaultValue(false).build());
    private int nextSwapTick;

    public ElytraChestplateSwap() {
        super(AddonTemplate.PVP_CATEGORY, "elytra-chestplate-swap", "Cooldown-protected chest equipment swap with flight speed and durability HUD.");
        sg.add(new KeybindSetting.Builder().name("swap-key").defaultValue(Keybind.none()).action(this::swap).build());
    }

    @Override public void onDeactivate() { nextSwapTick = 0; }

    @EventHandler private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (chestplateOnGround.get() && mc.player.onGround() && mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) swap();
    }

    private void swap() {
        if (mc.player == null || mc.gameMode == null || mc.player.tickCount < nextSwapTick) return;
        ItemStack equipped = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        boolean wantElytra = !equipped.is(Items.ELYTRA);
        int inventoryIndex = find(wantElytra ? Items.ELYTRA : Items.NETHERITE_CHESTPLATE, wantElytra);
        if (inventoryIndex < 0 && !wantElytra) inventoryIndex = findAnyChestplate();
        if (inventoryIndex < 0) return;
        int menuSlot = inventoryIndex < 9 ? inventoryIndex + 36 : inventoryIndex;
        int syncId = mc.player.inventoryMenu.containerId;
        mc.gameMode.handleInventoryMouseClick(syncId, 6, 0, ClickType.PICKUP, mc.player);
        mc.gameMode.handleInventoryMouseClick(syncId, menuSlot, 0, ClickType.PICKUP, mc.player);
        mc.gameMode.handleInventoryMouseClick(syncId, 6, 0, ClickType.PICKUP, mc.player);
        nextSwapTick = mc.player.tickCount + cooldown.get();
    }

    private int find(net.minecraft.world.item.Item item, boolean exact) {
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).is(item)) return i;
        return -1;
    }

    private int findAnyChestplate() {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(Items.LEATHER_CHESTPLATE) || stack.is(Items.CHAINMAIL_CHESTPLATE)
                || stack.is(Items.IRON_CHESTPLATE) || stack.is(Items.GOLDEN_CHESTPLATE)
                || stack.is(Items.DIAMOND_CHESTPLATE) || stack.is(Items.NETHERITE_CHESTPLATE)) return i;
        }
        return -1;
    }

    @EventHandler private void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;
        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        double speed = mc.player.getDeltaMovement().horizontalDistance() * 20.0;
        String durability = chest.isEmpty() || chest.getMaxDamage() == 0 ? "n/a" : (chest.getMaxDamage() - chest.getDamageValue()) + "/" + chest.getMaxDamage();
        String text = String.format(java.util.Locale.ROOT, "Flight %.1f m/s | Chest durability %s", speed, durability);
        TextRenderer tr = TextRenderer.get();
        tr.begin();
        double w = tr.getWidth(text, true);
        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(7, 53, w + 10, tr.getHeight(true) + 8, new Color(12, 12, 18, 180));
        Renderer2D.COLOR.render(event.drawContext.pose());
        tr.render(text, 12, 57, Color.WHITE, true);
        tr.end();
    }
}
