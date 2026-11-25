package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import com.example.addon.modules.SusChunkDetector;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

/** On-screen numeric labels for the highest-scoring nearby cached chunks. */
public class SusChunkHud extends HudElement {
    public static final HudElementInfo<SusChunkHud> INFO =
        new HudElementInfo<>(AddonTemplate.HUD_GROUP, "sus-chunk-scores", "Nearby suspicious chunk score labels.", SusChunkHud::new);

    public SusChunkHud() { super(INFO); }

    @Override public void render(HudRenderer renderer) {
        String text = SusChunkDetector.hudText;
        setSize(renderer.textWidth(text, true), renderer.textHeight(true) * Math.max(1, text.split("\\n").length));
        renderer.quad(x, y, getWidth() + 8, getHeight() + 4, new Color(20, 20, 24, 150));
        renderer.text(text, x + 4, y + 2, Color.WHITE, true);
    }
}
