package io.github.speedbridgemc.entityevents.impl.mixin;

import io.github.speedbridgemc.entityevents.impl.ServerWorldHooks;
import io.github.speedbridgemc.entityevents.impl.WorldStorage;
import io.github.speedbridgemc.entityevents.impl.event.TickInternals;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin implements ServerWorldHooks {
    private @Unique WorldStorage worldStorage;

    @Override
    public WorldStorage entityevents$getWorldStorage() {
        return worldStorage;
    }

    @Override
    public void entityevents$setWorldStorage(@NotNull WorldStorage damageInternals) {
        this.worldStorage = damageInternals;
    }

    @Inject(method = "tickEntity", at = @At("HEAD"), cancellable = true)
    public void entityevents$runTickEvents(Entity entity, CallbackInfo ci) {
        if (TickInternals.invoke(entity)) {
            ci.cancel();
        }
    }
}
