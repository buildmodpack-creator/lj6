package com.example.addon.mixin;

import com.example.addon.modules.ParticleLimiter;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void ljct$resetParticleSpawnBudget(CallbackInfo ci) {
        ParticleLimiter.beginTick();
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void ljct$limitParticleSpawn(Particle particle, CallbackInfo ci) {
        if (!ParticleLimiter.allowParticle(particle)) ci.cancel();
    }
}