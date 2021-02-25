package adudecalledleo.speedbridge.entityevents.impl;

import adudecalledleo.speedbridge.entityevents.impl.event.DamageInternals;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class Initializer implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerTickEvents.END_WORLD_TICK.register(world -> DamageInternals.endTick());
    }
}
