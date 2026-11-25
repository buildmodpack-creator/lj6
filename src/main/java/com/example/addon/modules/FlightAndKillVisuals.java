package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FlightAndKillVisuals extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Boolean> ribbon = sg.add(new BoolSetting.Builder().name("flight-ribbon").defaultValue(true).build());
    private final Setting<Boolean> hitRipple = sg.add(new BoolSetting.Builder().name("hit-ripple").defaultValue(true).build());
    private final Setting<Boolean> killColumn = sg.add(new BoolSetting.Builder().name("kill-column").defaultValue(true).build());
    private final Setting<Integer> maxRibbonPoints = sg.add(new IntSetting.Builder().name("ribbon-points").defaultValue(18).range(4, 40).build());
    private final Setting<Integer> hue = VisualColorUtil.addHue(sg, 185);
    private final Setting<Integer> saturation = VisualColorUtil.addSaturation(sg);
    private final Setting<Integer> brightness = VisualColorUtil.addBrightness(sg);
    private final Setting<Integer> alpha = VisualColorUtil.addAlpha(sg, 180);
    private final Setting<Boolean> chroma = VisualColorUtil.addChroma(sg);
    private final Setting<Integer> chromaSpeed = VisualColorUtil.addChromaSpeed(sg, chroma);
    private final SettingColor base = new SettingColor(75, 220, 255, 180);
    private final ArrayDeque<Vec3> trail = new ArrayDeque<>();
    private final Map<Integer, Float> health = new HashMap<>();
    private final Map<Integer, Long> lastHit = new HashMap<>();
    private final List<Effect> effects = new ArrayList<>();
    private int lastTrailTick = -1;

    public FlightAndKillVisuals() { super(AddonTemplate.VISUAL_CATEGORY, "flight-and-kill-visuals", "Adds flight ribbons, hit ripples and fading elimination columns."); }

    @Override public void onDeactivate() { trail.clear(); health.clear(); lastHit.clear(); effects.clear(); lastTrailTick = -1; }

    @EventHandler private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        long now = System.currentTimeMillis();
        SettingColor color = VisualColorUtil.resolve(base, chroma.get(), chromaSpeed.get(), hue.get(), saturation.get(), brightness.get(), alpha.get());
        boolean flying = mc.player.isFallFlying() || (mc.player.getAbilities().flying && !mc.player.onGround());
        if (ribbon.get() && flying) {
            if (lastTrailTick != mc.player.tickCount) {
                trail.addFirst(VisualColorUtil.lerpPosition(mc.player, event.tickDelta));
                while (trail.size() > maxRibbonPoints.get()) trail.removeLast();
                lastTrailTick = mc.player.tickCount;
            }
            Vec3 previous = null;
            int i = 0;
            for (Vec3 point : trail) {
                if (previous != null) {
                    int a = Math.max(15, color.a - i * 8);
                    SettingColor fade = new SettingColor(color.r, color.g, color.b, a);
                    event.renderer.line(previous.x, previous.y, previous.z, point.x, point.y, point.z, fade);
                }
                previous = point; i++;
            }
        } else if (!flying) {
            while (trail.size() > 1) trail.removeLast();
        }

        if (hitRipple.get() || killColumn.get()) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity living) || entity == mc.player) continue;
                float hp = living.getHealth();
                Float old = health.put(entity.getId(), hp);
                if (old != null && hp < old && hitRipple.get()) {
                    lastHit.put(entity.getId(), now);
                    if (old > 0 && hp <= 0 && killColumn.get()) effects.add(new Effect(living.position(), now));
                }
            }
        }
        for (Iterator<Map.Entry<Integer, Long>> it = lastHit.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, Long> e = it.next();
            long age = now - e.getValue();
            if (age > 700) { it.remove(); continue; }
            Entity target = mc.level.getEntity(e.getKey());
            if (target == null) continue;
            double radius = 0.35 + age / 700.0 * 1.2;
            double y = target.getY() + 0.15 + age / 700.0 * 0.55;
            ring(event, target.getX(), y, target.getZ(), radius, new SettingColor(color.r, color.g, color.b, (int)(color.a * (1 - age / 700.0))));
        }
        for (Iterator<Effect> it = effects.iterator(); it.hasNext();) {
            Effect effect = it.next();
            long age = now - effect.time;
            if (age > 1200) { it.remove(); continue; }
            int a = (int)(color.a * (1 - age / 1200.0));
            SettingColor fade = new SettingColor(color.r, color.g, color.b, a);
            event.renderer.box(effect.pos.x - 0.12, effect.pos.y, effect.pos.z - 0.12, effect.pos.x + 0.12, effect.pos.y + 3.5 * age / 1200.0, effect.pos.z + 0.12, fade, fade, meteordevelopment.meteorclient.renderer.ShapeMode.Both, 0);
        }
        if (health.size() > 256) health.clear();
    }

    private static void ring(Render3DEvent event, double x, double y, double z, double radius, SettingColor color) {
        Vec3 previous = null;
        for (int i = 0; i <= 24; i++) {
            double a = i * Math.PI * 2 / 24;
            Vec3 p = new Vec3(x + Math.cos(a) * radius, y, z + Math.sin(a) * radius);
            if (previous != null) event.renderer.line(previous.x, previous.y, previous.z, p.x, p.y, p.z, color);
            previous = p;
        }
    }

    private static final class Effect {
        final Vec3 pos; final long time;
        Effect(Vec3 pos, long time) { this.pos = pos; this.time = time; }
    }
}
