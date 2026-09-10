/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.combat.DoomCombatRules;
import io.github.glynch.doomedcorridors.internal.DoomedCorridorsRuntimeTypes;
import io.github.glynch.jscene3d.project.runtime.RuntimeSignal;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpointBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import java.util.Objects;

/** Mutable project-runtime health and ammunition state for one player entity. */
final class DoomPlayerState implements DoomDamageable, DoomRuleConsumer, ComponentEndpointBinder {
    private final ResourceReference actorCatalog;
    private final ResourceReference combatRules;
    private int health;
    private int maximumHealth;
    private int bullets;
    private int maximumBullets;
    private RuntimeSignal hurtSignal;
    private RuntimeSignal diedSignal;
    private boolean invulnerable;
    private boolean configured;

    /** Retains explicit source-asset references until application preparation loads their rules. */
    DoomPlayerState(ResourceReference actorCatalog, ResourceReference combatRules) {
        this.actorCatalog = requireSourceAsset(actorCatalog, "actor-catalog");
        this.combatRules = requireSourceAsset(combatRules, "combat-rules");
    }

    /** Returns the actor-catalog source selected by the authored player component. */
    @Override
    public ResourceReference actorCatalog() {
        return actorCatalog;
    }

    /** Returns the combat-rules source selected by the authored player component. */
    @Override
    public ResourceReference combatRules() {
        return combatRules;
    }

    @Override
    public void bindEndpoints(ComponentEndpoints endpoints) {
        ComponentEndpoints validEndpoints = Objects.requireNonNull(endpoints, "endpoints");
        hurtSignal = validEndpoints.signal(DoomedCorridorsRuntimeTypes.HURT_SIGNAL);
        diedSignal = validEndpoints.signal(DoomedCorridorsRuntimeTypes.DIED_SIGNAL);
    }

    /** Initializes resources exactly once from validated provider rules before world activation. */
    @Override
    public void configure(DoomCombatRules rules) {
        DoomCombatRules validRules = Objects.requireNonNull(rules, "rules");
        if (configured) {
            throw new IllegalStateException("player state is already configured");
        }
        health = validRules.startingHealth();
        maximumHealth = validRules.maximumHealth();
        bullets = validRules.startingBullets();
        maximumBullets = validRules.maximumBullets();
        configured = true;
    }

    /** Selects whether incoming damage is ignored for a local playtest launch. */
    void setInvulnerable(boolean invulnerable) {
        if (configured) {
            throw new IllegalStateException("player invulnerability must be selected before configuration");
        }
        this.invulnerable = invulnerable;
    }

    /** Returns current player health after application preparation. */
    @Override
    public int health() {
        requireConfigured();
        return health;
    }

    /** Returns current bullet ammunition after application preparation. */
    int bullets() {
        requireConfigured();
        return bullets;
    }

    /** Spends the requested positive bullet amount when available. */
    boolean spendBullets(int amount) {
        requireConfigured();
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (bullets < amount) {
            return false;
        }
        bullets -= amount;
        return true;
    }

    /** Applies positive incoming damage, emits the corresponding state signal, and returns the accepted amount. */
    @Override
    public int damage(int amount) {
        requireConfigured();
        if (amount <= 0) {
            throw new IllegalArgumentException("damage must be positive");
        }
        if (invulnerable) {
            return 0;
        }
        int applied = (int) Math.min((long) health, amount);
        health -= applied;
        if (applied == 0) {
            return 0;
        }
        if (health > 0) {
            requiredHurtSignal().emit();
        } else {
            requiredDiedSignal().emit();
        }
        return applied;
    }

    /** Applies one useful pickup and returns the exact amount accepted by the player. */
    int collect(DoomPickup.Resource resource, int amount, int limit) {
        requireConfigured();
        return switch (Objects.requireNonNull(resource, "resource")) {
            case HEALTH -> {
                int applied = acceptedAmount(health, maximumHealth, amount, limit);
                health += applied;
                yield applied;
            }
            case BULLETS -> {
                int applied = acceptedAmount(bullets, maximumBullets, amount, limit);
                bullets += applied;
                yield applied;
            }
        };
    }

    /** Computes one bounded positive resource increase without overflowing integer arithmetic. */
    private static int acceptedAmount(int current, int capacity, int amount, int limit) {
        int effectiveLimit = Math.min(capacity, limit);
        long available = Math.max(0L, (long) effectiveLimit - current);
        return (int) Math.min(available, amount);
    }

    /** Requires the reference to address one manifest-declared source asset. */
    private static ResourceReference requireSourceAsset(ResourceReference reference, String name) {
        ResourceReference validReference = Objects.requireNonNull(reference, name);
        if (validReference.kind() != ResourceReference.Kind.ASSET) {
            throw new IllegalArgumentException(name + " must reference a source asset");
        }
        return validReference;
    }

    /** Rejects resource access before application preparation. */
    private void requireConfigured() {
        if (!configured) {
            throw new IllegalStateException("player state has not been configured");
        }
    }

    /** Returns the descriptor-declared non-fatal damage signal. */
    private RuntimeSignal requiredHurtSignal() {
        if (hurtSignal == null) {
            throw new IllegalStateException("player hurt signal has not been bound");
        }
        return hurtSignal;
    }

    /** Returns the descriptor-declared terminal damage signal. */
    private RuntimeSignal requiredDiedSignal() {
        if (diedSignal == null) {
            throw new IllegalStateException("player died signal has not been bound");
        }
        return diedSignal;
    }
}
