/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable observable player resources, combatants, and collected map pickups. */
public final class DoomCombatState {
    private final int playerHealth;
    private final int maximumPlayerHealth;
    private final int bullets;
    private final int maximumBullets;
    private final String primaryWeaponId;
    private final List<DoomCombatantState> combatants;
    private final List<Integer> collectedPickupThingIndices;

    /** Creates one validated combat snapshot. */
    DoomCombatState(
            int playerHealth,
            int maximumPlayerHealth,
            int bullets,
            int maximumBullets,
            String primaryWeaponId,
            List<DoomCombatantState> combatants,
            List<Integer> collectedPickupThingIndices) {
        if (maximumPlayerHealth <= 0 || playerHealth < 0 || playerHealth > maximumPlayerHealth) {
            throw new IllegalArgumentException("player health is outside its valid range");
        }
        if (maximumBullets < 0 || bullets < 0 || bullets > maximumBullets) {
            throw new IllegalArgumentException("bullet ammunition is outside its valid range");
        }
        this.playerHealth = playerHealth;
        this.maximumPlayerHealth = maximumPlayerHealth;
        this.bullets = bullets;
        this.maximumBullets = maximumBullets;
        this.primaryWeaponId = Objects.requireNonNull(primaryWeaponId, "primaryWeaponId");
        this.combatants = List.copyOf(Objects.requireNonNull(combatants, "combatants"));
        this.collectedPickupThingIndices = copyThingIndices(collectedPickupThingIndices);
    }

    /** Returns current player health. */
    public int playerHealth() {
        return playerHealth;
    }

    /** Returns the absolute player-health capacity, including configured over-health. */
    public int maximumPlayerHealth() {
        return maximumPlayerHealth;
    }

    /** Returns remaining bullets for the primary weapon. */
    public int bullets() {
        return bullets;
    }

    /** Returns the player's bullet-ammunition capacity. */
    public int maximumBullets() {
        return maximumBullets;
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

    /** Returns collected pickup source-map thing indices in collection order. */
    public List<Integer> collectedPickupThingIndices() {
        return collectedPickupThingIndices;
    }

    /** Returns whether one source-map pickup has been consumed. */
    public boolean isPickupCollected(int thingIndex) {
        return collectedPickupThingIndices.contains(thingIndex);
    }

    /** Returns whether player health has reached zero. */
    public boolean isPlayerDead() {
        return playerHealth == 0;
    }

    /** Copies non-negative, duplicate-free source-map thing indices. */
    private static List<Integer> copyThingIndices(List<Integer> indices) {
        List<Integer> copy = List.copyOf(Objects.requireNonNull(indices, "collectedPickupThingIndices"));
        Set<Integer> unique = new HashSet<>();
        for (Integer thingIndex : copy) {
            Objects.requireNonNull(thingIndex, "collected pickup thing index");
            if (thingIndex < 0 || !unique.add(thingIndex)) {
                throw new IllegalArgumentException(
                        "collected pickup thing indices must be non-negative and unique");
            }
        }
        return copy;
    }
}
