/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.combat.DoomCombatRules;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import java.util.Objects;

/** Mutable project-runtime health and ammunition state for one player entity. */
final class DoomPlayerState {
    private final ResourceReference actorCatalog;
    private final ResourceReference combatRules;
    private int health;
    private int maximumHealth;
    private int bullets;
    private int maximumBullets;
    private boolean configured;

    /** Retains explicit source-asset references until application preparation loads their rules. */
    DoomPlayerState(ResourceReference actorCatalog, ResourceReference combatRules) {
        this.actorCatalog = requireSourceAsset(actorCatalog, "actor-catalog");
        this.combatRules = requireSourceAsset(combatRules, "combat-rules");
    }

    /** Returns the actor-catalog source selected by the authored player component. */
    ResourceReference actorCatalog() {
        return actorCatalog;
    }

    /** Returns the combat-rules source selected by the authored player component. */
    ResourceReference combatRules() {
        return combatRules;
    }

    /** Initializes resources exactly once from validated provider rules before world activation. */
    void configure(DoomCombatRules rules) {
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

    /** Returns current player health after application preparation. */
    int health() {
        requireConfigured();
        return health;
    }

    /** Returns current bullet ammunition after application preparation. */
    int bullets() {
        requireConfigured();
        return bullets;
    }

    /** Applies positive incoming damage without reducing health below zero. */
    void damage(int amount) {
        requireConfigured();
        if (amount <= 0) {
            throw new IllegalArgumentException("damage must be positive");
        }
        health = (int) Math.max(0L, (long) health - amount);
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
}
