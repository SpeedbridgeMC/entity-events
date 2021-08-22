package io.github.speedbridgemc.entityevents.api;

import io.github.speedbridgemc.entityevents.impl.event.TickInternals;
import net.fabricmc.fabric.api.util.TriState;
import org.jetbrains.annotations.NotNull;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.tag.Tag;
import java.util.function.Predicate;

public interface EntityTickEvents<E extends Entity> {
    static <E extends Entity> @NotNull EntityTickEvents<E> of(@NotNull EntityType<E> type) {
        return TickInternals.of(type);
    }

    static <E extends Entity> @NotNull EntityTickEvents<E> ofClass(@NotNull Class<E> clazz) {
        return TickInternals.ofClass(clazz);
    }

    static @NotNull EntityTickEvents<Entity> all() {
        return ofClass(Entity.class);
    }

    static @NotNull EntityTickEvents<LivingEntity> living() {
        return ofClass(LivingEntity.class);
    }

    static @NotNull EntityTickEvents<Entity> matching(@NotNull Predicate<Entity> predicate) {
        return TickInternals.matching(predicate);
    }

    static @NotNull EntityTickEvents<Entity> inTag(@NotNull Tag<EntityType<?>> tag) {
        return matching(entity -> entity.getType().isIn(tag));
    }

    @NotNull EntityTickEvents<E> registerBefore(@NotNull Before<E> callback);
    @NotNull EntityTickEvents<E> registerAfter(@NotNull After<E> callback);
    @NotNull EntityTickEvents<E> registerCancelled(@NotNull Cancelled<E> callback);

    @FunctionalInterface
    interface Before<E extends Entity> {
        @NotNull TriState beforeTick(@NotNull E entity);
    }

    @FunctionalInterface
    interface After<E extends Entity> {
        void afterTick(@NotNull E entity);
    }

    @FunctionalInterface
    interface Cancelled<E extends Entity> {
        void tickCancelled(@NotNull E entity);
    }
}
