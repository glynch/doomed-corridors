/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.actor;

/** Classic map-placement skill group used before runtime difficulty behavior exists. */
public enum DoomSkillLevel {
    /** I'm Too Young to Die and Hey, Not Too Rough placements. */
    EASY(0x0001),
    /** Hurt Me Plenty placements. */
    NORMAL(0x0002),
    /** Ultra-Violence and Nightmare placements. */
    HARD(0x0004);

    private final int thingFlag;

    DoomSkillLevel(int thingFlag) {
        this.thingFlag = thingFlag;
    }

    /** Returns whether classic thing flags include this skill group. */
    public boolean includes(int flags) {
        return (flags & thingFlag) != 0;
    }
}
