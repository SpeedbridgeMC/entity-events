package io.github.speedbridgemc.entityevents.impl;

import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public interface ServerWorldHooks {
    static @NotNull Optional<WorldStorage> getWorldStorage(@NotNull ServerWorld world) {
        return Optional.ofNullable(((ServerWorldHooks) world).entityevents_getWorldStorage());
    }

    static @NotNull WorldStorage getOrCreateWorldStorage(@NotNull ServerWorld world) {
        ServerWorldHooks hooks = (ServerWorldHooks) world;
        WorldStorage storage = hooks.entityevents_getWorldStorage();
        if (storage == null)
            hooks.entityevents_setWorldStorage(storage = new WorldStorage());
        return storage;
    }

    WorldStorage entityevents_getWorldStorage();
    void entityevents_setWorldStorage(@NotNull WorldStorage storage);
}
