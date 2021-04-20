package io.github.speedbridgemc.entityevents.testmod;

import io.github.speedbridgemc.entityevents.api.EntityDamageEvents;
import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.EntityType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Initializer implements ModInitializer {
    private static final Logger LOGGER = LogManager.getLogger("EntityEvents|TestMod");

    @Override
    public void onInitialize() {
        LOGGER.info("Hello from the testmod!");
        // attacks which don't kill Creepers ignite them
        EntityDamageEvents.of(EntityType.CREEPER).registerAfter((entity, source, amount) -> {
            if (!entity.world.isClient) {
                // can't use Entity.isDead() since this happens _before_ damage is applied
                // TODO maybe run after/cancelled events on end tick?
                if (entity.getHealth() - amount > 0)
                    entity.ignite();
            }
        });
    }
}
