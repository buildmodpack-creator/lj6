package com.example.addon.modules;

import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import java.awt.Color;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Small shared color helper; no per-frame buffer allocations. */
final class VisualColorUtil {
    private VisualColorUtil() {}
    static Setting<SettingColor> addColor(SettingGroup group, String name, SettingColor fallback) {
        return group.add(new ColorSetting.Builder().name(name).defaultValue(fallback).build());
    }
    static Setting<Boolean> addChroma(SettingGroup group) {
        return group.add(new BoolSetting.Builder().name("chroma").defaultValue(false).build());
    }
    static Setting<Integer> addChromaSpeed(SettingGroup group, Setting<Boolean> chroma) {
        return group.add(new IntSetting.Builder().name("chroma-speed").defaultValue(3).range(1, 20).visible(chroma::get).build());
    }
    static Setting<Integer> addAlpha(SettingGroup group, int initial) {
        return group.add(new IntSetting.Builder().name("alpha").defaultValue(initial).range(0, 255).build());
    }
    static Setting<Integer> addHue(SettingGroup group, int initial) {
        return group.add(new IntSetting.Builder().name("hue").defaultValue(initial).range(0, 360).build());
    }
    static Setting<Integer> addSaturation(SettingGroup group) {
        return group.add(new IntSetting.Builder().name("saturation").defaultValue(90).range(0, 100).build());
    }
    static Setting<Integer> addBrightness(SettingGroup group) {
        return group.add(new IntSetting.Builder().name("brightness").defaultValue(100).range(0, 100).build());
    }
    static Vec3 lerpPosition(Entity entity, float tickDelta) {\n        return new Vec3(entity.xo + (entity.getX() - entity.xo) * tickDelta, entity.yo + (entity.getY() - entity.yo) * tickDelta, entity.zo + (entity.getZ() - entity.zo) * tickDelta);\n    }\n    static SettingColor resolve(SettingColor base, boolean chroma, int speed, int hue, int saturation, int brightness, int alpha) {
        float h = chroma ? ((System.currentTimeMillis() * speed) % 12000L) / 12000f : hue / 360f;
        int rgb = chroma ? Color.HSBtoRGB(h, saturation / 100f, brightness / 100f) : Color.HSBtoRGB(h, saturation / 100f, brightness / 100f);
        if (!chroma && base != null) rgb = (base.r << 16) | (base.g << 8) | base.b;
        return new SettingColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha);
    }
}
