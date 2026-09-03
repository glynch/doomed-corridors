/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.actor;

import java.util.Objects;

/** One visible, difficulty-filtered map actor in renderer-independent world coordinates. */
public record DoomActor(
        int thingIndex, DoomActorDefinition definition, float x, float floorHeight, float z, float yawRadians) {
    /** Creates one finite resolved actor placement. */
    public DoomActor {
        if (thingIndex < 0) {
            throw new IllegalArgumentException("thingIndex must not be negative");
        }
        Objects.requireNonNull(definition, "definition");
        requireFinite(x, "x");
        requireFinite(floorHeight, "floorHeight");
        requireFinite(z, "z");
        requireFinite(yawRadians, "yawRadians");
        if (definition.spriteFrame().isEmpty()) {
            throw new IllegalArgumentException("resolved actors require a sprite frame");
        }
    }

    /** Requires one finite coordinate or angle. */
    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
