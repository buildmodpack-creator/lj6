package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class EntityChamsAndCape extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Integer> distance = sg.add(new IntSetting.Builder().name("distance").defaultValue(48).range(8, 128).build());
    private final Setting<Boolean> players = sg.add(new BoolSetting.Builder().name("players").defaultValue(true).build());
    private final Setting<Boolean> mobs = sg.add(new BoolSetting.Builder().name("other-entities").defaultValue(false).build());
    private final Setting<Boolean> cape = sg.add(new BoolSetting.Builder().name("animated-cape").defaultValue(true).build());
    private final Setting<Boolean> filled = sg.add(new BoolSetting.Builder().name("filled-overlay").defaultValue(false).build());
    private final Setting<Integer> hue = VisualColorUtil.addHue(sg, 325);
    private final Setting<Integer> saturation = VisualColorUtil.addSaturation(sg);
    private final Setting<Integer> brightness = VisualColorUtil.addBrightness(sg);
    private final Setting<Integer> alpha = VisualColorUtil.addAlpha(sg, 85);
    private final Setting<Boolean> chroma = VisualColorUtil.addChroma(sg);
    private final Setting<Integer> chromaSpeed = VisualColorUtil.addChromaSpeed(sg, chroma);
    private final SettingColor base = new SettingColor(255, 90, 165, 85);

    public EntityChamsAndCape() { super(AddonTemplate.VISUAL_CATEGORY, "entity-chams-and-cape", "Draws lightweight entity overlays and animated player cape ribbons."); }

    @EventHandler private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        double maxSq = (double) distance.get() * distance.get();
        SettingColor color = VisualColorUtil.resolve(base, chroma.get(), chromaSpeed.get(), hue.get(), saturation.get(), brightness.get(), alpha.get());
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || !entity.isAlive() || entity.distanceToSqr(mc.player) > maxSq) continue;
            if (entity instanceof Player) {
                if (!players.get()) continue;
            } else if (!mobs.get()) continue;
            Vec3 interpolated = VisualColorUtil.lerpPosition(entity, event.tickDelta);
            Vec3 offset = interpolated.subtract(entity.position());
            AABB box = entity.getBoundingBox().move(offset).inflate(0.025);
            event.renderer.box(box, color, color, filled.get() ? ShapeMode.Both : ShapeMode.Lines, 0);
            if (cape.get() && entity instanceof Player player) drawCape(event, player, color, event.tickDelta);
        }
    }

    private static void drawCape(Render3DEvent event, Player player, SettingColor color, float tickDelta) {
        double yaw = Math.toRadians(player.getYRot());
        Vec3 back = new Vec3(Math.sin(yaw), 0, -Math.cos(yaw));
        Vec3 right = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3 p = VisualColorUtil.lerpPosition(player, tickDelta);
        double sway = Math.sin((System.currentTimeMillis() + event.tickDelta * 50) / 230.0 + player.getId()) * 0.12;
        Vec3 top = p.add(0, player.getBbHeight() * 0.82, 0).add(back.scale(0.13));
        Vec3 bottom = top.add(back.scale(0.16 + sway)).add(0, -0.82, 0);
        Vec3 leftTop = top.add(right.scale(-0.22)), rightTop = top.add(right.scale(0.22));
        Vec3 leftBottom = bottom.add(right.scale(-0.18)), rightBottom = bottom.add(right.scale(0.18));
        event.renderer.quad(leftTop.x,leftTop.y,leftTop.z,rightTop.x,rightTop.y,rightTop.z,rightBottom.x,rightBottom.y,rightBottom.z,leftBottom.x,leftBottom.y,leftBottom.z,color);
        event.renderer.line(leftTop.x,leftTop.y,leftTop.z,leftBottom.x,leftBottom.y,leftBottom.z,color);
        event.renderer.line(rightTop.x,rightTop.y,rightTop.z,rightBottom.x,rightBottom.y,rightBottom.z,color);
    }
}
