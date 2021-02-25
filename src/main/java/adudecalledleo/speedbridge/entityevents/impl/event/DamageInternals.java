package adudecalledleo.speedbridge.entityevents.impl.event;

import adudecalledleo.speedbridge.entityevents.api.EntityDamageEvents;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashBigSet;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public final class DamageInternals {
    private DamageInternals() { }

    private static final class Events<E extends Entity> implements EntityDamageEvents<E> {
        public final Event<Before<E>> beforeEvent;
        public final Event<After<E>> afterEvent;
        public final Event<Cancelled<E>> cancelledEvent;

        public Events() {
            beforeEvent = EventFactory.createArrayBacked(Before.class, befores -> (entity, source, amount) -> {
                TriState ret = TriState.DEFAULT;
                for (Before<E> before : befores) {
                    ret = before.beforeDamaged(entity, source, amount);
                    if (ret != TriState.DEFAULT)
                        break;
                }
                return ret;
            });
            afterEvent = EventFactory.createArrayBacked(After.class, afters -> (entity, source, amount) -> {
                for (After<E> after : afters)
                    after.afterDamaged(entity, source, amount);
            });
            cancelledEvent = EventFactory.createArrayBacked(Cancelled.class, cancelleds -> (entity, source, amount) -> {
                for (Cancelled<E> cancelled : cancelleds)
                    cancelled.damageCancelled(entity, source, amount);
            });
        }

        @Override
        public @NotNull EntityDamageEvents<E> registerBefore(@NotNull Before<E> callback) {
            beforeEvent.register(callback);
            return this;
        }

        @Override
        public @NotNull EntityDamageEvents<E> registerAfter(@NotNull After<E> callback) {
            afterEvent.register(callback);
            return this;
        }

        @Override
        public @NotNull EntityDamageEvents<E> registerCancelled(@NotNull Cancelled<E> callback) {
            cancelledEvent.register(callback);
            return this;
        }
    }

    private static final ReferenceOpenHashBigSet<Entity> INVOKED_THIS_TICK
            = new ReferenceOpenHashBigSet<>();
    private static final Reference2ReferenceOpenHashMap<Class<?>, Events<Entity>> CLASS_EVENTS
            = new Reference2ReferenceOpenHashMap<>();
    private static final Reference2ReferenceOpenHashMap<EntityType<?>, Events<Entity>> TYPE_EVENTS
            = new Reference2ReferenceOpenHashMap<>();
    private static final Reference2ReferenceOpenHashMap<Predicate<Entity>, Events<Entity>> PREDICATE_EVENTS
            = new Reference2ReferenceOpenHashMap<>();

    public static <E extends Entity> @NotNull EntityDamageEvents<E> ofClass(@NotNull Class<E> clazz) {
        //noinspection unchecked
        return (EntityDamageEvents<E>) CLASS_EVENTS.computeIfAbsent(clazz, aClass -> new Events<>());
    }

    public static <E extends Entity> @NotNull EntityDamageEvents<E> of(@NotNull EntityType<E> type) {
        //noinspection unchecked
        return (EntityDamageEvents<E>) TYPE_EVENTS.computeIfAbsent(type, entityType -> new Events<>());
    }

    public static @NotNull EntityDamageEvents<Entity> matching(@NotNull Predicate<Entity> predicate) {
        return PREDICATE_EVENTS.computeIfAbsent(predicate, predicate1 -> new Events<>());
    }

    public static boolean invoke(@NotNull Entity entity, @NotNull DamageSource source, float amount) {
        if (!INVOKED_THIS_TICK.add(entity))
            return false;
        boolean cancelled = invokeBeforeClass(entity, source, amount, entity.getClass());
        if (!cancelled) {
            Events<Entity> typeEvents = TYPE_EVENTS.get(entity.getType());
            if (typeEvents != null)
                cancelled = invokeBefore(typeEvents, entity, source, amount);
        }
        if (!cancelled) {
            for (Reference2ReferenceMap.Entry<Predicate<Entity>, Events<Entity>> entry : PREDICATE_EVENTS.reference2ReferenceEntrySet()) {
                if (entry.getKey().test(entity)) {
                    cancelled = invokeBefore(entry.getValue(), entity, source, amount);
                    if (cancelled)
                        break;
                }
            }
        }
        invokeAfter(entity, source, amount, cancelled);
        return cancelled;
    }

    private static boolean invokeBefore(@NotNull Events<Entity> events, @NotNull Entity entity, @NotNull DamageSource source, float amount) {
        return events.beforeEvent.invoker().beforeDamaged(entity, source, amount).orElse(false);
    }

    private static boolean invokeBeforeClass(@NotNull Entity entity, @NotNull DamageSource source, float amount, @NotNull Class<?> clazz) {
        if (clazz.getSuperclass() != null) {
            if (!invokeBeforeClass(entity, source, amount, clazz.getSuperclass()))
                return false;
        }
        Events<Entity> classEvents = CLASS_EVENTS.get(clazz);
        if (classEvents == null)
            return false;
        return invokeBefore(classEvents, entity, source, amount);
    }

    private static void invokeAfter(@NotNull Events<Entity> events, @NotNull Entity entity, @NotNull DamageSource source, float amount, boolean cancelled) {
        if (cancelled)
            events.cancelledEvent.invoker().damageCancelled(entity, source, amount);
        else
            events.afterEvent.invoker().afterDamaged(entity, source, amount);
    }

    private static void invokeAfterClass(@NotNull Entity entity, @NotNull DamageSource source, float amount, @NotNull Class<?> clazz, boolean cancelled) {
        if (clazz.getSuperclass() != null) {
            invokeAfterClass(entity, source, amount, clazz.getSuperclass(), cancelled);
        }
        Events<Entity> classEvents = CLASS_EVENTS.get(clazz);
        if (classEvents == null)
            return;
        invokeAfter(classEvents, entity, source, amount, cancelled);
    }

    private static void invokeAfter(@NotNull Entity entity, @NotNull DamageSource source, float amount, boolean cancelled) {
        invokeAfterClass(entity, source, amount, entity.getClass(), cancelled);
        Events<Entity> typeEvents = TYPE_EVENTS.get(entity.getType());
        if (typeEvents != null)
            invokeAfter(typeEvents, entity, source, amount, cancelled);
        for (Reference2ReferenceMap.Entry<Predicate<Entity>, Events<Entity>> entry : PREDICATE_EVENTS.reference2ReferenceEntrySet()) {
            if (entry.getKey().test(entity))
                invokeAfter(entry.getValue(), entity, source, amount, cancelled);
        }
    }

    public static void endTick() {
        INVOKED_THIS_TICK.clear();
    }
}
