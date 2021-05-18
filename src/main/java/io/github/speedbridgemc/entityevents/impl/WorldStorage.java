package io.github.speedbridgemc.entityevents.impl;

import io.github.speedbridgemc.entityevents.impl.event.DamageInternals;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public final class WorldStorage {
    private DamageInternals damageInternals;

    WorldStorage() { }

    public @NotNull Optional<DamageInternals> getDamageInternals() {
        return Optional.ofNullable(damageInternals);
    }

    public @NotNull DamageInternals getOrCreateDamageInternals() {
        if (damageInternals == null)
            damageInternals = new DamageInternals();
        return damageInternals;
    }
}
