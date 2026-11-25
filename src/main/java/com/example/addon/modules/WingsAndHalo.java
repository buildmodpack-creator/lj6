package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class WingsAndHalo extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Boolean> wings = sg.add(new BoolSetting.Builder().name("wings").defaultValue(true).build());
    private final Setting<Boolean> halo = sg.add(new BoolSetting.Builder().name("halo").defaultValue(true).build());
    private final Setting<Boolean> foldWhenIdle = sg.add(new BoolSetting.Builder().name("fold-when-idle").defaultValue(true).build());
    private final Setting<Boolean> foldWithElytra = sg.add(new BoolSetting.Builder().name("fold-with-elytra").defaultValue(true).build());
    private final Setting<Double> idleSeconds = sg.add(new DoubleSetting.Builder().name("idle-seconds").defaultValue(8.0).range(1.0, 30.0).build());
    private final Setting<Integer> hue = VisualColorUtil.addHue(sg, 285);
    private final Setting<Integer> saturation = VisualColorUtil.addSaturation(sg);
    private final Setting<Integer> brightness = VisualColorUtil.addBrightness(sg);
    private final Setting<Integer> alpha = VisualColorUtil.addAlpha(sg, 175);
    private final Setting<Boolean> chroma = VisualColorUtil.addChroma(sg);
    private final Setting<Integer> chromaSpeed = VisualColorUtil.addChromaSpeed(sg, chroma);
    private final SettingColor base = new SettingColor(190, 110, 255, 175);
    private long lastMotion = System.currentTimeMillis();
    private Vec3 lastPos = Vec3.ZERO;
    private double wingOpen;

    public WingsAndHalo() { super(AddonTemplate.VISUAL_CATEGORY, "wings-and-halo", "Animated layered wings and a rotating halo around your player."); }

    @Override public void onDeactivate() { wingOpen = 0; lastPos = Vec3.ZERO; }

    @EventHandler private void onRender(Render3DEvent event) {
        if (mc.player == null) return;
        Vec3 pos = mc.player.position();
        if (pos.distanceToSqr(lastPos) > 0.0004) lastMotion = System.currentTimeMillis();
        lastPos = pos;
        boolean elytra = mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
        boolean idle = System.currentTimeMillis() - lastMotion > idleSeconds.get() * 1000;
        double target = (foldWhenIdle.get() && idle) || (foldWithElytra.get() && elytra) ? 0.18 : 1.0;
        wingOpen += (target - wingOpen) * Math.min(1.0, event.frameTime * 8.0);
        double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
        double yaw = Math.toRadians(mc.player.getYRot());
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
        SettingColor c = VisualColorUtil.resolve(base, chroma.get(), chromaSpeed.get(), hue.get(), saturation.get(), brightness.get(), alpha.get());
        double beat = Math.sin((System.currentTimeMillis() + event.tickDelta * 50) / 280.0) * 0.12;
        if (wings.get()) {
            for (int side = -1; side <= 1; side += 2) {
                Vec3 root = new Vec3(x, y + 1.25, z).add(right.scale(side * 0.18));
                Vec3 prev = root;
                for (int i = 1; i <= 7; i++) {
                    double t = i / 7.0;
                    double span = (0.35 + t * 1.05) * wingOpen;
                    double lift = Math.sin(t * Math.PI) * (0.8 + beat) + t * 0.22;
                    Vec3 point = root.add(right.scale(side * span)).add(back.scale(0.12 + t * 0.18)).add(0, lift, 0);
                    event.renderer.line(prev.x, prev.y, prev.z, point.x, point.y, point.z, c);
                    Vec3 feather = point.add(right.scale(side * 0.12)).add(0, -0.13, 0);
                    event.renderer.line(point.x, point.y, point.z, feather.x, feather.y, feather.z, c);
                    prev = point;
                }
            }
        }
        if (halo.get()) {
            double angle = (System.currentTimeMillis() + event.tickDelta * 50) / 850.0;
            Vec3 center = new Vec3(x, y + 2.35, z);
            Vec3 prev = null;
            for (int i = 0; i <= 40; i++) {
                double a = angle + i * Math.PI * 2 / 40;
                Vec3 p = center.add(Math.cos(a) * 0.42, Math.sin(a) * 0.08, Math.sin(a) * 0.42);
                if (prev != null) event.renderer.line(prev.x, prev.y, prev.z, p.x, p.y, p.z, c);
                prev = p;
            }
        }
    }
}
