/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable observable player resources and combatant snapshots. */
public final class DoomCombatState {
    private final int playerHealth;
    private final int maximumPlayerHealth;
    private final int bullets;
    private final String primaryWeaponId;
    private final List<DoomCombatantState> combatants;

    /** Creates one validated combat snapshot. */
    DoomCombatState(
            int playerHealth,
            int maximumPlayerHealth,
            int bullets,
            String primaryWeaponId,
            List<DoomCombatantState> combatants) {
        if (maximumPlayerHealth <= 0 || playerHealth < 0 || playerHealth > maximumPlayerHealth) {
            throw new IllegalArgumentException("player health is outside its valid range");
        }
        if (bullets < 0) {
            throw new IllegalArgumentException("bullets must not be negative");
        }
        this.playerHealth = playerHealth;
        this.maximumPlayerHealth = maximumPlayerHealth;
        this.bullets = bullets;
        this.primaryWeaponId = Objects.requireNonNull(primaryWeaponId, "primaryWeaponId");
        this.combatants = List.copyOf(Objects.requireNonNull(combatants, "combatants"));
    }

    /** Returns current player health. */
    public int playerHealth() {
        return playerHealth;
    }

    /** Returns initial and maximum player health. */
    public int maximumPlayerHealth() {
        return maximumPlayerHealth;
    }

    /** Returns remaining bullets for the primary weapon. */
    public int bullets() {
        return bullets;
    }

    /** Returns the stable identifier of the selected primary weapon. */
    public String primaryWeaponId() {
        return primaryWeaponId;
    }

    /** Returns combatants in resolved source-map order. */
    public List<DoomCombatantState> combatants() {
        return combatants;
    }

    /** Finds one combatant by its stable source-map thing index. */
    public Optional<DoomCombatantState> combatant(int thingIndex) {
        return combatants.stream()
                .filter(combatant -> combatant.thingIndex() == thingIndex)
                .findFirst();
    }

    /** Returns whether player health has reached zero. */
    public boolean isPlayerDead() {
        return playerHealth == 0;
    }
}
