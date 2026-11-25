package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class AutoDisconnect extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Boolean> strangerCheck = sg.add(new BoolSetting.Builder().name("disconnect-on-other-player").description("Disconnect when another player is detected nearby.").defaultValue(false).build());
    private final Setting<Integer> playerRange = sg.add(new IntSetting.Builder().name("player-range").defaultValue(12).range(4, 64).visible(strangerCheck::get).build());
    private final Setting<Boolean> healthCheck = sg.add(new BoolSetting.Builder().name("disconnect-on-low-health").defaultValue(true).build());
    private final Setting<Double> healthThreshold = sg.add(new DoubleSetting.Builder().name("health-threshold").defaultValue(6.0).range(1.0, 20.0).visible(healthCheck::get).build());
    private int cooldown;
    public AutoDisconnect() { super(AddonTemplate.CATEGORY, "auto-disconnect", "Disconnects locally when the configured nearby-player or health condition is met."); }
    @EventHandler private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null || mc.getConnection() == null || cooldown > 0) { if (cooldown > 0) cooldown--; return; }
        if (healthCheck.get() && mc.player.getHealth() <= healthThreshold.get()) {
            disconnect("AutoDisconnect: health threshold reached");
            return;
        }
        if (strangerCheck.get()) {
            double maxSq = (double) playerRange.get() * playerRange.get();
            for (Player player : mc.level.players()) {
                if (player == mc.player || !player.isAlive()) continue;
                if (player.distanceToSqr(mc.player) <= maxSq) {
                    disconnect("AutoDisconnect: another player entered range");
                    return;
                }
            }
        }
    }
    private void disconnect(String reason) {
        if (mc.getConnection() == null) return;
        cooldown = 40;
        mc.getConnection().getConnection().disconnect(Component.literal(reason));
    }
}
