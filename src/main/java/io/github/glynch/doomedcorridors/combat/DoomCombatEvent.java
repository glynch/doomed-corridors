/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

import java.util.Objects;

/** One presentation-neutral fact emitted by a combat operation. */
public record DoomCombatEvent(Type type, int thingIndex, int amount) {
    /** Marker used when an event concerns the player or weapon rather than a map actor. */
    public static final int PLAYER = -1;

    /** Creates a validated combat event. */
    public DoomCombatEvent {
        Objects.requireNonNull(type, "type");
        if (thingIndex < PLAYER) {
            throw new IllegalArgumentException("thingIndex must be -1 or a source-map index");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
    }

    /** Observable event kinds consumed later by presentation and audio adapters. */
    public enum Type {
        /** The primary weapon consumed ammunition and emitted a shot. */
        WEAPON_FIRED,
        /** The selected weapon did not have enough ammunition. */
        WEAPON_EMPTY,
        /** A living combatant lost health. */
        COMBATANT_DAMAGED,
        /** A combatant reached zero health. */
        COMBATANT_KILLED,
        /** A dormant combatant acquired the player and entered active behavior. */
        COMBATANT_ALERTED,
        /** A combatant performed its configured ranged attack. */
        COMBATANT_ATTACKED,
        /** The player lost health. */
        PLAYER_DAMAGED,
        /** The player reached zero health. */
        PLAYER_KILLED
    }
}
