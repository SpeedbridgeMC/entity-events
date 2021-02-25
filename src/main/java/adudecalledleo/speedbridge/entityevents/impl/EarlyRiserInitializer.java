package adudecalledleo.speedbridge.entityevents.impl;

import com.chocohead.mm.api.ClassTinkerers;

public final class EarlyRiserInitializer implements Runnable {
    @Override
    public void run() {
        try {
            EntityClassScanner.scan();
        } catch (Exception e) {
            System.err.println("Failed to scan for entity classes!");
            e.printStackTrace();
            System.exit(1);
        }
        for (String className : EntityClassScanner.ENTITY_CLASS_NAMES)
            ClassTinkerers.addTransformation(className, EventCallbackInjector::transform);
    }
}
