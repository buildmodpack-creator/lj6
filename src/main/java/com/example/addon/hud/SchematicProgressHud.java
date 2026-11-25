package com.example.addon.hud;

import com.example.addon.AddonTemplate;
import com.example.addon.modules.SchematicAutoBuilder;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

/** Live progress readout for the active schematic printer. */
public class SchematicProgressHud extends HudElement {
    public static final HudElementInfo<SchematicProgressHud> INFO =
        new HudElementInfo<>(AddonTemplate.HUD_GROUP,"schematic-progress","Schematic printer progress and status.",SchematicProgressHud::new);
    public SchematicProgressHud(){super(INFO);}
    @Override public void render(HudRenderer renderer){
        String text=SchematicAutoBuilder.hudText;
        setSize(renderer.textWidth(text,true),renderer.textHeight(true));
        renderer.quad(x,y,getWidth()+8,getHeight()+4,new Color(20,20,24,150));
        renderer.text(text,x+4,y+2,Color.WHITE,true);
    }
}