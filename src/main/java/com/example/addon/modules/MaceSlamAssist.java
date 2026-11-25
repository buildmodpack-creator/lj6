package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.item.Items;

public class MaceSlamAssist extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Double> triggerFall = sg.add(new DoubleSetting.Builder().name("strike-window-fall-distance").description("Fall distance at which the HUD marks a useful strike window.").defaultValue(4.0).range(1.0, 20.0).build());
    private float peakFall;

    public MaceSlamAssist() { super(AddonTemplate.PVP_CATEGORY, "mace-slam-assist", "Shows fall-damage estimate and mace attack readiness; it never swings automatically."); }
    @Override public void onDeactivate() { peakFall = 0; }

    @EventHandler private void onRender2D(Render2DEvent event) {
        if (mc.player == null || !mc.player.getMainHandItem().is(Items.MACE)) return;
        float fall = mc.player.fallDistance;
        peakFall = Math.max(peakFall, fall);
        double estimated = Math.max(0, peakFall - 3.0);
        boolean ready = fall >= triggerFall.get() && mc.player.getAttackStrengthScale(0.5f) >= 0.9f;
        String text = String.format(java.util.Locale.ROOT, "Mace | fall %.1f | est. %.1f | %s", fall, estimated, ready ? "STRIKE WINDOW" : "WAIT");
        TextRenderer tr = TextRenderer.get();
        tr.begin(event.graphics);
        double w = tr.getWidth(text, true);
        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(7, 30, w + 10, tr.getHeight(true) + 8, new Color(12, 12, 18, 180));
        Renderer2D.COLOR.render();
        tr.render(text, 12, 34, ready ? new Color(100, 255, 130) : Color.WHITE, true);
        tr.end();
        if (mc.player.onGround()) peakFall = 0;
    }
}
