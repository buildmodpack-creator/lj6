package com.example.addon.mixin;

import com.example.addon.modules.EntityCuller;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void ljct$cullDistantAndOffView(
        E entity, Frustum frustum, double x, double y, double z,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (EntityCuller.shouldCull(entity)) cir.setReturnValue(false);
    }
}
