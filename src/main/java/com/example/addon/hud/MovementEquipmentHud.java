package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import com.example.addon.modules.MovementHudCache;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

/** Compact mobile-friendly HUD backed by text updated once per client tick. */
public class MovementEquipmentHud extends HudElement {
    public static final HudElementInfo<MovementEquipmentHud> INFO =
        new HudElementInfo<>(AddonTemplate.HUD_GROUP, "movement-equipment",
            "Movement speed, vertical speed and armor/Elytra durability.", MovementEquipmentHud::new);

    public MovementEquipmentHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        String first = MovementHudCache.line1;
        String second = MovementHudCache.line2;
        if (first.isEmpty()) {
            setSize(0, 0);
            return;
        }

        double width = Math.max(renderer.textWidth(first, true), renderer.textWidth(second, true));
        double height = renderer.textHeight(true) * 2;
        setSize(width + 8, height + 4);

        renderer.quad(x, y, getWidth(), getHeight(), new Color(16, 18, 24, 175));
        renderer.text(first, x + 4, y + 2, Color.WHITE, true);
        renderer.text(second, x + 4, y + 2 + renderer.textHeight(true), Color.WHITE, true);
    }
}
