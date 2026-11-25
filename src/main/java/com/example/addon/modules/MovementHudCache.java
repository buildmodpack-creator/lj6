package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Builds the movement/equipment readout on ticks so HUD rendering only reads cached text. */
public class MovementHudCache extends Module {
    public static volatile String line1 = "";
    public static volatile String line2 = "";

    public MovementHudCache() {
        super(AddonTemplate.FIX_LAG_CATEGORY, "movement-equipment-hud",
            "Caches movement speed and armor durability for the Movement & Equipment HUD.");
    }

    @Override
    public void onDeactivate() {
        clear();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        clear();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        updateCache();
    }

    /** Also called by the HUD element so it works without requiring this helper module to be enabled. */
    public static void updateCache() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) {
            clear();
            return;
        }

        Vec3 velocity = client.player.getDeltaMovement();
        double horizontalSpeed = velocity.horizontalDistance() * 20.0;
        double verticalSpeed = velocity.y * 20.0;

        StringBuilder first = new StringBuilder(40);
        first.append("Speed ").append(tenths(horizontalSpeed)).append(" b/s  |  Y ")
            .append(signedTenths(verticalSpeed));

        StringBuilder second = new StringBuilder(72);
        ItemStack chest = client.player.getItemBySlot(EquipmentSlot.CHEST);
        second.append(chest.is(net.minecraft.world.item.Items.ELYTRA) ? "Elytra " : "Chest ")
            .append(durability(chest))
            .append("  H ").append(durability(client.player.getItemBySlot(EquipmentSlot.HEAD)))
            .append("  L ").append(durability(client.player.getItemBySlot(EquipmentSlot.LEGS)))
            .append("  B ").append(durability(client.player.getItemBySlot(EquipmentSlot.FEET)));

        line1 = first.toString();
        line2 = second.toString();
    }

    private static void clear() {
        line1 = "";
        line2 = "";
    }

    private static String durability(ItemStack stack) {
        if (stack.isEmpty()) return "--";
        int max = stack.getMaxDamage();
        if (max <= 0) return "n/a";
        int remaining = Math.max(0, max - stack.getDamageValue());
        return Integer.toString((remaining * 100 + max / 2) / max) + "%";
    }

    private static String tenths(double value) {
        long rounded = Math.round(Math.abs(value) * 10.0);
        return (rounded / 10) + "." + (rounded % 10);
    }

    private static String signedTenths(double value) {
        return (value > 0.04 ? "+" : value < -0.04 ? "-" : "") + tenths(value);
    }
}
