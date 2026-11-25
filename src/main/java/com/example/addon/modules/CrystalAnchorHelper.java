package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.BlockHitResult;

public class CrystalAnchorHelper extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Double> safeDistance = sg.add(new DoubleSetting.Builder().name("minimum-safe-distance").description("Minimum distance from the targeted explosive block before selecting an explosive item.").defaultValue(6.0).range(3.0, 12.0).build());
    private String status = "Aim at an anchor or placement block";

    public CrystalAnchorHelper() {
        super(AddonTemplate.PVP_CATEGORY, "crystal-anchor-helper", "Hotbar selection shortcuts and a safety-aware anchor/crystal sequence readout; placement and activation remain manual.");
        sg.add(new KeybindSetting.Builder().name("select-end-crystal").defaultValue(Keybind.none()).action(() -> select(Items.END_CRYSTAL)).build());
        sg.add(new KeybindSetting.Builder().name("select-glowstone").defaultValue(Keybind.none()).action(() -> select(Items.GLOWSTONE)).build());
        sg.add(new KeybindSetting.Builder().name("select-respawn-anchor").defaultValue(Keybind.none()).action(() -> select(Items.RESPAWN_ANCHOR)).build());
    }

    @Override public void onDeactivate() { status = "Inactive"; }

    private void select(Item item) {
        if (mc.player == null || mc.level == null) return;
        BlockPos target = mc.hitResult instanceof BlockHitResult hit ? hit.getBlockPos() : mc.player.blockPosition();
        double distance = mc.player.distanceToSqr(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        boolean chargedAnchor = mc.level.getBlockState(target).getBlock() instanceof RespawnAnchorBlock
            && mc.level.getBlockState(target).getValue(RespawnAnchorBlock.CHARGE) > 0;
        boolean explosiveSelection = item == Items.END_CRYSTAL || item == Items.RESPAWN_ANCHOR
            || (item == Items.GLOWSTONE && chargedAnchor);
        if (explosiveSelection && distance < safeDistance.get() * safeDistance.get()) {
            status = "Blocked: move farther from explosive target";
            return;
        }
        for (int slot = 0; slot < 9; slot++) {
            if (mc.player.getInventory().getItem(slot).is(item)) {
                mc.player.getInventory().selected = slot;
                status = "Selected " + mc.player.getInventory().getItem(slot).getHoverName().getString();
                return;
            }
        }
        status = "Item not found in hotbar";
    }

    @EventHandler private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) return;
        String stage = "Crystal: select / place manually";
        if (mc.hitResult instanceof BlockHitResult hit && mc.level.getBlockState(hit.getBlockPos()).getBlock() instanceof RespawnAnchorBlock) {
            int charge = mc.level.getBlockState(hit.getBlockPos()).getValue(RespawnAnchorBlock.CHARGE);
            stage = charge == 0 ? "Anchor: add Glowstone" : "Anchor: " + charge + "/4 charges - activation is manual";
        } else if (mc.player.getMainHandItem().is(Items.END_CRYSTAL)) stage = "Crystal: ready to place manually";
        else if (mc.player.getMainHandItem().is(Items.GLOWSTONE)) stage = "Glowstone: ready to charge anchor manually";
        draw(event, status + " | " + stage);
    }

    private static void draw(Render2DEvent event, String text) {
        TextRenderer tr = TextRenderer.get();
        tr.begin();
        double w = tr.getWidth(text, true);
        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(7, 7, w + 10, tr.getHeight(true) + 8, new Color(15, 15, 20, 175));
        Renderer2D.COLOR.render(event.drawContext.pose());
        tr.render(text, 12, 11, Color.WHITE, true);
        tr.end();
    }
}
