package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class TargetHUD extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Double> range = sg.add(new DoubleSetting.Builder().name("attack-range-indicator").description("Radius of the horizontal reach indicator around the local player.").defaultValue(3.0).range(1.0, 6.0).build());
    private final Setting<Boolean> showRing = sg.add(new meteordevelopment.meteorclient.settings.BoolSetting.Builder().name("show-range-ring").defaultValue(true).build());

    public TargetHUD() { super(AddonTemplate.PVP_CATEGORY, "target-hud", "Displays crosshair target details and a configurable attack-range ring."); }
    @Override public void onDeactivate() {}

    @EventHandler private void onRender2D(Render2DEvent event) {
        if (mc.player == null || !(mc.crosshairPickEntity instanceof LivingEntity target) || target == mc.player) return;
        String held = target.getMainHandItem().isEmpty() ? "Empty" : target.getMainHandItem().getHoverName().getString();
        double distance = mc.player.distanceTo(target);
        String[] lines = {
            target.getName().getString(),
            String.format(java.util.Locale.ROOT, "HP %.1f | Armor %d", target.getHealth(), target.getArmorValue()),
            "Held: " + held,
            String.format(java.util.Locale.ROOT, "Distance: %.1f m", distance)
        };
        TextRenderer tr = TextRenderer.get();
        tr.begin(event.graphics);
        double width = 0;
        for (String line : lines) width = Math.max(width, tr.getWidth(line, true));
        double height = tr.getHeight(true);
        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(event.screenWidth - width - 22, 12, width + 14, height * lines.length + 14, new Color(12, 12, 18, 185));
        Renderer2D.COLOR.render();
        double y = 18;
        for (int i = 0; i < lines.length; i++) {
            tr.render(lines[i], event.screenWidth - width - 15, y, i == 0 ? new Color(120, 220, 255) : Color.WHITE, true);
            y += height + 2;
        }
        tr.end();
    }

    @EventHandler private void onRender3D(Render3DEvent event) {
        if (!showRing.get() || mc.player == null) return;
        Vec3 center = mc.player.position().add(0, 0.04, 0);
        double r = range.get();
        Vec3 prev = null;
        boolean targetInRange = mc.crosshairPickEntity instanceof LivingEntity living && living != mc.player && mc.player.distanceTo(living) <= r;
        Color color = targetInRange ? new Color(90, 255, 120, 180) : new Color(255, 190, 70, 130);
        for (int i = 0; i <= 48; i++) {
            double a = i * Math.PI * 2 / 48;
            Vec3 p = center.add(Math.cos(a) * r, 0, Math.sin(a) * r);
            if (prev != null) event.renderer.line(prev.x, prev.y, prev.z, p.x, p.y, p.z, new meteordevelopment.meteorclient.utils.render.color.SettingColor(color.r, color.g, color.b, color.a));
            prev = p;
        }
    }
}
