package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.awt.Color;

/** Configures the first-person fire overlay through ScreenEffectRendererMixin. */
public class CustomLowFire extends Module {
    private static volatile CustomLowFire INSTANCE;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Setting<Double> height = sgGeneral.add(new DoubleSetting.Builder()
        .name("height")
        .description("Visible height of the first-person fire overlay.")
        .defaultValue(0.45)
        .range(0.15, 1.0)
        .build()
    );
    private final Setting<Double> lowerOffset = sgGeneral.add(new DoubleSetting.Builder()
        .name("lower-offset")
        .description("Moves the fire overlay farther down from the center of the screen.")
        .defaultValue(0.18)
        .range(0.0, 0.6)
        .build()
    );
    private final Setting<Integer> alpha = sgGeneral.add(new IntSetting.Builder()
        .name("alpha")
        .description("Opacity of the fire overlay tint.")
        .defaultValue(190)
        .range(0, 255)
        .build()
    );
    private final Setting<SettingColor> tint = sgGeneral.add(new ColorSetting.Builder()
        .name("color")
        .description("Tint applied to the fire overlay.")
        .defaultValue(new SettingColor(255, 120, 65, 255))
        .build()
    );
    private final Setting<Boolean> chroma = sgGeneral.add(new BoolSetting.Builder()
        .name("chroma")
        .description("Cycles the fire overlay tint through the hue spectrum.")
        .defaultValue(false)
        .build()
    );
    private final Setting<Integer> chromaSpeed = sgGeneral.add(new IntSetting.Builder()
        .name("chroma-speed")
        .description("Hue cycling speed.")
        .defaultValue(3)
        .range(1, 20)
        .visible(chroma::get)
        .build()
    );

    public CustomLowFire() {
        super(AddonTemplate.VISUAL_CATEGORY, "custom-low-fire",
            "Lowers and tints the first-person fire overlay.");
        INSTANCE = this;
    }

    public static boolean isEnabled() {
        return INSTANCE != null && INSTANCE.isActive();
    }

    public static float getHeight() {
        return INSTANCE == null ? 1.0f : INSTANCE.height.get().floatValue();
    }

    public static float getLowerOffset() {
        return INSTANCE == null ? 0.0f : INSTANCE.lowerOffset.get().floatValue();
    }

    public static float getRed() {
        return channel(0);
    }

    public static float getGreen() {
        return channel(1);
    }

    public static float getBlue() {
        return channel(2);
    }

    public static float getAlpha() {
        return INSTANCE == null ? 1.0f : INSTANCE.alpha.get() / 255.0f;
    }

    private static float channel(int index) {
        if (INSTANCE == null) return 1.0f;
        SettingColor c = INSTANCE.tint.get();
        if (INSTANCE.chroma.get()) {
            float hue = ((System.currentTimeMillis() * INSTANCE.chromaSpeed.get()) % 10000L) / 10000.0f;
            int rgb = Color.HSBtoRGB(hue, 0.9f, 1.0f);
            return ((rgb >> (16 - index * 8)) & 255) / 255.0f;
        }
        return switch (index) {
            case 0 -> c.r / 255.0f;
            case 1 -> c.g / 255.0f;
            default -> c.b / 255.0f;
        };
    }
}
