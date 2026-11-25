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
import net.minecraft.world.phys.Vec3;

/** Lightweight cosmetic knife silhouette drawn in camera space; does not replace item models. */
public class CS2KnifeViewmodel extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Integer> model = sg.add(new IntSetting.Builder().name("knife-model").description("0 Karambit, 1 Butterfly, 2 M9 Bayonet, 3 Katana.").defaultValue(0).range(0, 3).build());
    private final Setting<Boolean> thirdPerson = sg.add(new BoolSetting.Builder().name("third-person").defaultValue(false).build());
    private final Setting<Boolean> inspect = sg.add(new BoolSetting.Builder().name("inspect-spin").defaultValue(true).build());
    private final Setting<Double> x = sg.add(new DoubleSetting.Builder().name("x").defaultValue(0.36).range(-1.0, 1.0).build());
    private final Setting<Double> y = sg.add(new DoubleSetting.Builder().name("y").defaultValue(-0.28).range(-1.0, 1.0).build());
    private final Setting<Double> z = sg.add(new DoubleSetting.Builder().name("z").defaultValue(0.72).range(0.1, 1.5).build());
    private final Setting<Double> scale = sg.add(new DoubleSetting.Builder().name("scale").defaultValue(0.65).range(0.2, 1.5).build());
    private final Setting<Integer> spinSpeed = sg.add(new IntSetting.Builder().name("spin-speed").defaultValue(3).range(1, 10).visible(inspect::get).build());
    private final SettingColor base = new SettingColor(100, 205, 255, 230);
    private final Setting<Boolean> chroma = VisualColorUtil.addChroma(sg);
    private final Setting<Integer> hue = VisualColorUtil.addHue(sg, 195);
    private final Setting<Integer> saturation = VisualColorUtil.addSaturation(sg);
    private final Setting<Integer> brightness = VisualColorUtil.addBrightness(sg);
    private final Setting<Integer> alpha = VisualColorUtil.addAlpha(sg, 230);
    private final Setting<Integer> chromaSpeed = VisualColorUtil.addChromaSpeed(sg, chroma);

    public CS2KnifeViewmodel() {
        super(AddonTemplate.VISUAL_CATEGORY, "cs2-knife-viewmodel", "Draws a lightweight cosmetic knife silhouette near the camera.");
    }

    @EventHandler private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        if (!firstPerson && !thirdPerson.get()) return;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        float yaw = mc.player.getYRot(), pitch = mc.player.getXRot();
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        Vec3 forward = new Vec3(-Math.sin(yr) * Math.cos(pr), -Math.sin(pr), Math.cos(yr) * Math.cos(pr)).normalize();
        Vec3 right = new Vec3(Math.cos(yr), 0, Math.sin(yr)).normalize();
        Vec3 up = right.cross(forward).normalize();
        double spin = inspect.get() ? (System.currentTimeMillis() * spinSpeed.get() % 2400L) / 2400.0 * Math.PI * 2.0 : 0;
        Vec3 grip = camera.add(right.scale(x.get())).add(up.scale(y.get())).add(forward.scale(z.get()));
        double s = scale.get();
        Vec3 axis = right.scale(Math.cos(spin)).add(up.scale(Math.sin(spin)));
        Vec3 tip = grip.add(axis.scale(s * (model.get() == 3 ? 1.8 : 1.15))).add(forward.scale(s * 0.18));
        Vec3 bladeSide = forward.cross(axis).normalize().scale(s * (model.get() == 3 ? 0.11 : 0.075));
        Vec3 bladeBase = grip.add(axis.scale(s * 0.42));
        SettingColor c = VisualColorUtil.resolve(base, chroma.get(), chromaSpeed.get(), hue.get(), saturation.get(), brightness.get(), alpha.get());
        SettingColor dark = new SettingColor(Math.max(0, c.r / 2), Math.max(0, c.g / 2), Math.max(0, c.b / 2), c.a);
        line(event, grip, bladeBase, dark);
        line(event, bladeBase.add(bladeSide), tip, c);
        line(event, bladeBase.subtract(bladeSide), tip, c);
        line(event, bladeBase.add(bladeSide), bladeBase.subtract(bladeSide), c);
        line(event, grip.add(bladeSide.scale(0.8)), grip.subtract(bladeSide.scale(0.8)), dark);
        if (model.get() == 0) {
            Vec3 hook = tip.add(axis.scale(-s * 0.23)).add(bladeSide.scale(1.8));
            line(event, tip, hook, c);
            line(event, hook, tip.add(axis.scale(-s * 0.38)), c);
        } else if (model.get() == 1) {
            line(event, grip.add(axis.scale(-s * 0.2)).add(bladeSide), grip.add(axis.scale(0.3)), dark);
            line(event, grip.add(axis.scale(-s * 0.2)).subtract(bladeSide), grip.add(axis.scale(0.3)), dark);
        }
    }

    private static void line(Render3DEvent event, Vec3 a, Vec3 b, SettingColor color) {
        event.renderer.line(a.x, a.y, a.z, b.x, b.y, b.z, color);
    }
}
