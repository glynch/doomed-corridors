/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import static io.github.glynch.doomedcorridors.internal.Preconditions.requireFinite;
import static io.github.glynch.doomedcorridors.internal.Preconditions.requireInRange;
import static io.github.glynch.doomedcorridors.internal.Preconditions.requireNonNegative;

import java.util.Objects;

/** Immutable editor-visible state of one door derived from imported Doom map behavior. */
public record DoomDoorState(
        int sectorIndex,
        Phase phase,
        float closedCeilingHeight,
        float openCeilingHeight,
        float currentCeilingHeight) {
    /** Runtime phases supported by the first manual open-and-stay door slice. */
    public enum Phase {
        /** The door blocks its sector opening. */
        CLOSED,
        /** The door ceiling is moving toward its destination. */
        OPENING,
        /** The door has reached its destination and remains open. */
        OPEN,
        /** The door is holding at its destination before closing. */
        WAITING,
        /** The door ceiling is returning to its closed height. */
        CLOSING
    }

    /** Validates one finite ordered door snapshot. */
    public DoomDoorState {
        requireNonNegative(sectorIndex, "sectorIndex");
        Objects.requireNonNull(phase, "phase");
        requireFinite(closedCeilingHeight, "closedCeilingHeight");
        requireFinite(openCeilingHeight, "openCeilingHeight");
        requireFinite(currentCeilingHeight, "currentCeilingHeight");
        if (openCeilingHeight < closedCeilingHeight) {
            throw new IllegalArgumentException("openCeilingHeight must not be below closedCeilingHeight");
        }
        requireInRange(
                currentCeilingHeight, closedCeilingHeight, openCeilingHeight, "currentCeilingHeight");
    }
}
