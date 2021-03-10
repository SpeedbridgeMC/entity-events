package io.github.speedbridgemc.entityevents.impl;

import io.github.speedbridgemc.entityevents.impl.event.DamageInternals;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class Initializer implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerTickEvents.END_WORLD_TICK.register(world -> DamageInternals.endTick());
    }
}
