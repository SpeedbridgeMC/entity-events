package io.github.speedbridgemc.entityevents.impl;

import com.chocohead.mm.api.ClassTinkerers;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class EarlyRiserInitializer implements Runnable {
    private static final Logger LOGGER = LogManager.getLogger("EntityEvents|EarlyRiserInitializer");

    @Override
    public void run() {
        EntityClassScanner scanner = new EntityClassScanner();
        try {
            scanner.scan();
        } catch (Exception e) {
            LOGGER.error("Failed to scan for entity classes!", e);
            System.exit(1);
        }
        for (String className : scanner.entityClassNames)
            ClassTinkerers.addTransformation(className, EventCallbackInjector::transform);
    }
}
