package io.github.speedbridgemc.entityevents.impl;

import com.chocohead.mm.api.ClassTinkerers;

public final class EarlyRiserInitializer implements Runnable {
    @Override
    public void run() {
        EntityClassScanner scanner = new EntityClassScanner();
        try {
            scanner.scan();
        } catch (Exception e) {
            System.err.println("Failed to scan for entity classes!");
            e.printStackTrace();
            System.exit(1);
        }
        for (String className : scanner.entityClassNames)
            ClassTinkerers.addTransformation(className, EventCallbackInjector::transform);
    }
}
