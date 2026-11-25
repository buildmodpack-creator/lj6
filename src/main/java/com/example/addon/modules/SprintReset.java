package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class SprintReset extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<ResetMode> mode = sg.add(new EnumSetting.Builder<ResetMode>().name("reset-mode").defaultValue(ResetMode.WTap).build());
    private final Setting<Integer> delay = sg.add(new IntSetting.Builder().name("delay-ticks").description("Ticks after a confirmed health reduction before sprint is released.").defaultValue(0).range(0, 8).build());
    private final Setting<Integer> hold = sg.add(new IntSetting.Builder().name("hold-ticks").description("Ticks to keep sprint released before restoring it.").defaultValue(1).range(1, 6).build());

    private Entity pendingTarget;
    private float healthBefore;
    private int confirmAt = -1;
    private int pendingUntil = -1;
    private int restoreAt = -1;
    private boolean restoreSprint;

    public SprintReset() {
        super(AddonTemplate.PVP_CATEGORY, "sprint-reset", "Times a W-Tap or D-Tap sprint reset after a confirmed hit.");
    }

    @Override public void onDeactivate() {
        if (restoreSprint && mc.player != null) mc.player.setSprinting(true);
        clear();
    }

    @EventHandler private void onAttack(AttackEntityEvent event) {
        if (mc.player == null || !(event.entity instanceof LivingEntity living) || living == mc.player) return;
        pendingTarget = living;
        healthBefore = living.getHealth();
        confirmAt = mc.player.tickCount;
        pendingUntil = mc.player.tickCount + 4;
    }

    @EventHandler private void onTick(TickEvent.Pre event) {
        if (mc.player == null) { clear(); return; }
        int tick = mc.player.tickCount;
        if (pendingTarget != null) {
            boolean hitConfirmed = pendingTarget.isAlive() ? pendingTarget.getHealth() < healthBefore : healthBefore > 0;
            if (hitConfirmed) {
                pendingTarget = null;
                pendingUntil = -1;
                confirmAt = tick + delay.get();
            } else if (tick >= pendingUntil) {
                pendingTarget = null;
                pendingUntil = -1;
                confirmAt = -1;
            }
        }
        if (pendingTarget == null && confirmAt >= 0 && tick >= confirmAt) {
            confirmAt = -1;
            if (mc.player.isSprinting()) {
                restoreSprint = true;
                mc.player.setSprinting(false);
                restoreAt = tick + hold.get();
            }
        }
        if (restoreAt >= 0 && tick >= restoreAt) {
            boolean moving = mode.get() == ResetMode.WTap
                ? mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.0001
                : mc.player.getDeltaMovement().horizontalDistanceSqr() > 0.0004;
            if (restoreSprint && moving && mc.options.keySprint.isDown() && mc.player.getFoodData().getFoodLevel() > 6 && !mc.player.isUsingItem()) {
                mc.player.setSprinting(true);
            }
            restoreSprint = false;
            restoreAt = -1;
        }
    }

    private void clear() { pendingTarget = null; confirmAt = -1; pendingUntil = -1; restoreAt = -1; restoreSprint = false; }

    public enum ResetMode { WTap, DTap }
}
