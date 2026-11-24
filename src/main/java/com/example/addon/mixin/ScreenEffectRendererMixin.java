package com.example.addon.mixin;

import com.example.addon.modules.CustomLowFire;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {
    @Inject(method = "renderFire", at = @At("HEAD"))
    private static void ljct$lowerAndTintFire(PoseStack matrices, MultiBufferSource buffers, CallbackInfo ci) {
        if (!CustomLowFire.isEnabled()) return;

        matrices.pushPose();
        matrices.translate(0.0, -CustomLowFire.getLowerOffset(), 0.0);
        matrices.scale(1.0f, CustomLowFire.getHeight(), 1.0f);
        RenderSystem.setShaderColor(
            CustomLowFire.getRed(),
            CustomLowFire.getGreen(),
            CustomLowFire.getBlue(),
            CustomLowFire.getAlpha()
        );
    }

    @Inject(method = "renderFire", at = @At("RETURN"))
    private static void ljct$restoreFireState(PoseStack matrices, MultiBufferSource buffers, CallbackInfo ci) {
        if (!CustomLowFire.isEnabled()) return;
        matrices.popPose();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
