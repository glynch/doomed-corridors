/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

/** Observable lifecycle state of one map combatant. */
public enum DoomCombatantStatus {
    /** The combatant can receive damage and block hitscan attacks. */
    ALIVE,
    /** The combatant has zero health and no longer blocks hitscan attacks. */
    DEAD
}
