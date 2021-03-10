package io.github.speedbridgemc.entityevents.api;

import io.github.speedbridgemc.entityevents.impl.event.DamageInternals;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.tag.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public interface EntityDamageEvents<E extends Entity> {
    static <E extends Entity> @NotNull EntityDamageEvents<E> of(@NotNull EntityType<E> type) {
        return DamageInternals.of(type);
    }

    static <E extends Entity> @NotNull EntityDamageEvents<E> ofClass(@NotNull Class<E> clazz) {
        return DamageInternals.ofClass(clazz);
    }

    static @NotNull EntityDamageEvents<Entity> all() {
        return ofClass(Entity.class);
    }

    static @NotNull EntityDamageEvents<LivingEntity> living() {
        return ofClass(LivingEntity.class);
    }

    static @NotNull EntityDamageEvents<Entity> matching(@NotNull Predicate<Entity> predicate) {
        return DamageInternals.matching(predicate);
    }

    static @NotNull EntityDamageEvents<Entity> inTag(@NotNull Tag<EntityType<?>> tag) {
        return matching(entity -> entity.getType().isIn(tag));
    }

    @NotNull EntityDamageEvents<E> registerBefore(@NotNull Before<E> callback);
    @NotNull EntityDamageEvents<E> registerAfter(@NotNull After<E> callback);
    @NotNull EntityDamageEvents<E> registerCancelled(@NotNull Cancelled<E> callback);

    @FunctionalInterface
    interface Before<E extends Entity> {
        @NotNull TriState beforeDamaged(@NotNull E entity, @NotNull DamageSource source, float amount);
    }

    @FunctionalInterface
    interface After<E extends Entity> {
        void afterDamaged(@NotNull E entity, @NotNull DamageSource source, float amount);
    }

    @FunctionalInterface
    interface Cancelled<E extends Entity> {
        void damageCancelled(@NotNull E entity, @NotNull DamageSource source, float amount);
    }
}
