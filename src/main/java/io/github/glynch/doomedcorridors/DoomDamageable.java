/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

/** Java representation verified after the damageable descriptor capability selects an exact component. */
interface DoomDamageable {
    /** Returns current health. */
    int health();

    /** Applies positive damage and returns the amount actually removed. */
    int damage(int amount);
}
