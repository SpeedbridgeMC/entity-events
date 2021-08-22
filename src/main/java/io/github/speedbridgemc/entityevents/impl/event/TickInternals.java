package io.github.speedbridgemc.entityevents.impl.event;

import io.github.speedbridgemc.entityevents.api.EntityTickEvents;
import io.github.speedbridgemc.entityevents.impl.WorldStorage;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.util.TriState;
import org.jetbrains.annotations.NotNull;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.server.world.ServerWorld;
import java.util.function.Predicate;

import static io.github.speedbridgemc.entityevents.impl.ServerWorldHooks.getOrCreateWorldStorage;
import static io.github.speedbridgemc.entityevents.impl.ServerWorldHooks.getWorldStorage;

public final class TickInternals {
    public TickInternals() { }

    private static final class Events<E extends Entity> implements EntityTickEvents<E> {
        public final Event<Before<E>> beforeEvent;
        public final Event<After<E>> afterEvent;
        public final Event<Cancelled<E>> cancelledEvent;

        public Events() {
            beforeEvent = EventFactory.createArrayBacked(Before.class, befores -> (entity) -> {
                TriState ret = TriState.DEFAULT;
                for (Before<E> before : befores) {
                    ret = before.beforeTick(entity);
                    if (ret != TriState.DEFAULT)
                        break;
                }
                return ret;
            });
            afterEvent = EventFactory.createArrayBacked(After.class, afters -> (entity) -> {
                for (After<E> after : afters)
                    after.afterTick(entity);
            });
            cancelledEvent = EventFactory.createArrayBacked(Cancelled.class, cancelleds -> (entity) -> {
                for (Cancelled<E> cancelled : cancelleds)
                    cancelled.tickCancelled(entity);
            });
        }

        @Override
        public @NotNull EntityTickEvents<E> registerBefore(@NotNull Before<E> callback) {
            beforeEvent.register(callback);
            return this;
        }

        @Override
        public @NotNull EntityTickEvents<E> registerAfter(@NotNull After<E> callback) {
            afterEvent.register(callback);
            return this;
        }

        @Override
        public @NotNull EntityTickEvents<E> registerCancelled(@NotNull Cancelled<E> callback) {
            cancelledEvent.register(callback);
            return this;
        }
    }

    private static final Reference2ReferenceOpenHashMap<Class<?>, Events<Entity>> CLASS_EVENTS
            = new Reference2ReferenceOpenHashMap<>();
    private static final Reference2ReferenceOpenHashMap<EntityType<?>, Events<Entity>> TYPE_EVENTS
            = new Reference2ReferenceOpenHashMap<>();
    private static final Reference2ReferenceOpenHashMap<Predicate<Entity>, Events<Entity>> PREDICATE_EVENTS
            = new Reference2ReferenceOpenHashMap<>();

    @SuppressWarnings("unchecked")
    public static <E extends Entity> @NotNull EntityTickEvents<E> ofClass(@NotNull Class<E> clazz) {
        return (EntityTickEvents<E>) CLASS_EVENTS.computeIfAbsent(clazz, aClass -> new Events<>());
    }

    @SuppressWarnings("unchecked")
    public static <E extends Entity> @NotNull EntityTickEvents<E> of(@NotNull EntityType<E> type) {
        return (EntityTickEvents<E>) TYPE_EVENTS.computeIfAbsent(type, entityType -> new Events<>());
    }

    public static @NotNull EntityTickEvents<Entity> matching(@NotNull Predicate<Entity> predicate) {
        return PREDICATE_EVENTS.computeIfAbsent(predicate, predicate1 -> new Events<>());
    }

    private final Reference2BooleanOpenHashMap<Entity> invokedThisTick
            = new Reference2BooleanOpenHashMap<>();

    @SuppressWarnings("unused")
    public static boolean invoke(@NotNull Entity entity) {
        if (entity.getEntityWorld().isClient())
            return false;
        return getOrCreateWorldStorage((ServerWorld) entity.getEntityWorld()).getOrCreateTickInternals().invoke0(entity);
    }

    private boolean invoke0(@NotNull Entity entity) {
        if (invokedThisTick.containsKey(entity))
            return invokedThisTick.getBoolean(entity);
        boolean cancelled = invokeBeforeClass(entity, entity.getClass());
        if (!cancelled) {
            Events<Entity> typeEvents = TYPE_EVENTS.get(entity.getType());
            if (typeEvents != null)
                cancelled = invokeBefore(typeEvents, entity);
        }
        if (!cancelled) {
            for (Reference2ReferenceMap.Entry<Predicate<Entity>, Events<Entity>> entry : PREDICATE_EVENTS.reference2ReferenceEntrySet()) {
                if (entry.getKey().test(entity)) {
                    cancelled = invokeBefore(entry.getValue(), entity);
                    if (cancelled)
                        break;
                }
            }
        }
        invokeAfter(entity, cancelled);
        invokedThisTick.put(entity, cancelled);
        return cancelled;
    }

    private boolean invokeBefore(@NotNull Events<Entity> events, @NotNull Entity entity) {
        return events.beforeEvent.invoker().beforeTick(entity).orElse(false);
    }

    private boolean invokeBeforeClass(@NotNull Entity entity, @NotNull Class<?> clazz) {
        if (EventUtils.isSuperclassValid(clazz)) {
            if (!invokeBeforeClass(entity, clazz.getSuperclass()))
                return false;
        }
        Events<Entity> classEvents = CLASS_EVENTS.get(clazz);
        if (classEvents == null)
            return false;
        return invokeBefore(classEvents, entity);
    }

    private void invokeAfter(@NotNull Events<Entity> events, @NotNull Entity entity, boolean cancelled) {
        if (cancelled)
            events.cancelledEvent.invoker().tickCancelled(entity);
        else
            events.afterEvent.invoker().afterTick(entity);
    }

    private void invokeAfterClass(@NotNull Entity entity, @NotNull Class<?> clazz, boolean cancelled) {
        if (EventUtils.isSuperclassValid(clazz))
            invokeAfterClass(entity, clazz.getSuperclass(), cancelled);
        Events<Entity> classEvents = CLASS_EVENTS.get(clazz);
        if (classEvents == null)
            return;
        invokeAfter(classEvents, entity, cancelled);
    }

    private void invokeAfter(@NotNull Entity entity, boolean cancelled) {
        invokeAfterClass(entity, entity.getClass(), cancelled);
        Events<Entity> typeEvents = TYPE_EVENTS.get(entity.getType());
        if (typeEvents != null)
            invokeAfter(typeEvents, entity, cancelled);
        for (Reference2ReferenceMap.Entry<Predicate<Entity>, Events<Entity>> entry : PREDICATE_EVENTS.reference2ReferenceEntrySet()) {
            if (entry.getKey().test(entity))
                invokeAfter(entry.getValue(), entity, cancelled);
        }
    }

    public static void endTick(@NotNull ServerWorld world) {
        getWorldStorage(world).flatMap(WorldStorage::getTickInternals).ifPresent(TickInternals::endTick0);
    }

    private void endTick0() {
        invokedThisTick.clear();
    }
}
