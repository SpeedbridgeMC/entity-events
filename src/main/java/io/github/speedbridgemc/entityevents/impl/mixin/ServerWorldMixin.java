package io.github.speedbridgemc.entityevents.impl.mixin;

import io.github.speedbridgemc.entityevents.impl.ServerWorldHooks;
import io.github.speedbridgemc.entityevents.impl.WorldStorage;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ServerWorld.class)
public abstract class ServerWorldMixin implements ServerWorldHooks {
    private @Unique WorldStorage worldStorage;

    @Override
    public WorldStorage entityevents_getWorldStorage() {
        return worldStorage;
    }

    @Override
    public void entityevents_setWorldStorage(@NotNull WorldStorage damageInternals) {
        this.worldStorage = damageInternals;
    }
}
