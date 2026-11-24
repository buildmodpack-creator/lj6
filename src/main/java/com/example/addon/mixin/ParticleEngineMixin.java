package com.example.addon.mixin;

import com.example.addon.modules.ParticleLimiter;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Queue;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Shadow @Final private Map<ParticleRenderType, Queue<Particle>> particles;
    @Shadow @Final private Queue<Particle> particlesToAdd;

    @Inject(method = "tick", at = @At("HEAD"))
    private void ljct$refreshParticleCount(CallbackInfo ci) {
        int count = particlesToAdd.size();
        for (Queue<Particle> layer : particles.values()) count += layer.size();
        ParticleLimiter.beginTick(count);
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void ljct$limitParticleSpawn(Particle particle, CallbackInfo ci) {
        if (!ParticleLimiter.allowParticle(particle)) ci.cancel();
    }
}
