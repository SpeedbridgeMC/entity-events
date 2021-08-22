package io.github.speedbridgemc.entityevents.impl.event;

import org.jetbrains.annotations.NotNull;

class EventUtils {
    public static boolean isSuperclassValid(@NotNull Class<?> clazz) {
        Class<?> superclass = clazz.getSuperclass();
        return superclass != null && superclass != Object.class;
    }
}
