/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.actor;

import static io.github.glynch.doomedcorridors.internal.Preconditions.requireFinite;
import static io.github.glynch.doomedcorridors.internal.Preconditions.requireNonNegative;

import java.util.Objects;

/** One visible, difficulty-filtered map actor in renderer-independent world coordinates. */
public record DoomActor(
        int thingIndex, DoomActorDefinition definition, float x, float floorHeight, float z, float yawRadians) {
    /** Creates one finite resolved actor placement. */
    public DoomActor {
        requireNonNegative(thingIndex, "thingIndex");
        Objects.requireNonNull(definition, "definition");
        requireFinite(x, "x");
        requireFinite(floorHeight, "floorHeight");
        requireFinite(z, "z");
        requireFinite(yawRadians, "yawRadians");
        if (definition.spriteFrame().isEmpty()) {
            throw new IllegalArgumentException("resolved actors require a sprite frame");
        }
    }

}
