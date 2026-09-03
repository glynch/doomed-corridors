/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.actor;

/** Broad actor role used by tools before detailed behavior is implemented. */
public enum DoomActorCategory {
    /** Non-rendered map metadata such as player starts and teleport destinations. */
    MARKER,
    /** Hostile actor whose behavior will be supplied by the combat runtime. */
    ENEMY,
    /** Collectable weapon. */
    WEAPON,
    /** Collectable ammunition. */
    AMMUNITION,
    /** Collectable health item. */
    HEALTH,
    /** Collectable armor item. */
    ARMOR,
    /** Inert environmental object. */
    DECORATION,
    /** Inert actor remains. */
    CORPSE
}
