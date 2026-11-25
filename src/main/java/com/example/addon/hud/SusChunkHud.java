package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import com.example.addon.modules.SusChunkDetector;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class SusChunkHud extends HudElement {
    public static final HudElementInfo<SusChunkHud> INFO =
        new HudElementInfo<>(AddonTemplate.HUD_GROUP, "sus-chunk-scores", "Top suspicious chunks with score, distance and direction.", SusChunkHud::new);

    public SusChunkHud() { super(INFO); }

    @Override public void render(HudRenderer renderer) {
        String text = SusChunkDetector.hudText;
        int lines = Math.max(1, text.split("\\n", -1).length);
        setSize(renderer.textWidth(text, true), renderer.textHeight(true) * lines);
        renderer.quad(x, y, getWidth() + 8, getHeight() + 4, new Color(20, 20, 24, 165));
        renderer.text(text, x + 4, y + 2, Color.WHITE, true);
    }
}
