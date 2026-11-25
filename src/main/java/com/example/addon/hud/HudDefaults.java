package com.example.addon.hud;

import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.utils.PostInit;

/** Adds the LJCT movement HUD after Meteor has restored the user's HUD configuration. */
public final class HudDefaults {
    private HudDefaults() {
    }

    @PostInit
    public static void addMovementHud() {
        Hud hud = Hud.get();
        for (HudElement element : hud) {
            if (element.info == MovementEquipmentHud.INFO) return;
        }

        hud.add(MovementEquipmentHud.INFO, 8, 72);
    }
}
