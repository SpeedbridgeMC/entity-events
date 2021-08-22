package io.github.speedbridgemc.entityevents.impl;

import io.github.speedbridgemc.entityevents.impl.event.DamageInternals;
import io.github.speedbridgemc.entityevents.impl.event.TickInternals;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class WorldStorage {
    private DamageInternals damageInternals;
    private TickInternals tickInternals;

    WorldStorage() { }

    public @NotNull Optional<DamageInternals> getDamageInternals() {
        return Optional.ofNullable(damageInternals);
    }

    public @NotNull DamageInternals getOrCreateDamageInternals() {
        if (damageInternals == null)
            damageInternals = new DamageInternals();
        return damageInternals;
    }

    public @NotNull Optional<TickInternals> getTickInternals() {
        return Optional.ofNullable(tickInternals);
    }

    public @NotNull TickInternals getOrCreateTickInternals() {
        if (tickInternals == null)
            tickInternals = new TickInternals();
        return tickInternals;
    }
}
