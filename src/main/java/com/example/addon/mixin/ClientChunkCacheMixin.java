package com.example.addon.mixin;

import com.example.addon.modules.StorageChunkCacheScanner;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {
    @Inject(method = "drop", at = @At("HEAD"))
    private void ljct$clearScannerChunk(ChunkPos pos, CallbackInfo ci) {
        StorageChunkCacheScanner.onChunkUnload(pos.x, pos.z);
    }
}
