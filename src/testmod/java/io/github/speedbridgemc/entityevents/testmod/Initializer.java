package io.github.speedbridgemc.entityevents.testmod;

import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Initializer implements ModInitializer {
    private static final Logger LOGGER = LogManager.getLogger("EntityEvents|TestMod");

    @Override
    public void onInitialize() {
        LOGGER.info("Hello from the testmod!");
    }
}
