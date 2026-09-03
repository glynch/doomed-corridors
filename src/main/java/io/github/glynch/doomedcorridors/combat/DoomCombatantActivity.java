/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

/** Observable high-level behavior of one combatant in the headless simulation. */
public enum DoomCombatantActivity {
    /** The combatant has not detected the player. */
    DORMANT,
    /** The combatant is moving toward its latest visible player position. */
    PURSUING,
    /** The combatant has line of sight and is within its configured attack range. */
    ATTACKING,
    /** The combatant has no health and performs no further behavior. */
    DEAD
}
