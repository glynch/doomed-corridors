/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.actor;

/** Provider-authored cylindrical bounds for one solid classic actor. */
public record DoomActorCollisionBounds(int radius, int height) {
    /** Requires positive dimensions compatible with the engine's vertical capsule shape. */
    public DoomActorCollisionBounds {
        if (radius <= 0 || height < radius * 2) {
            throw new IllegalArgumentException("actor collision must satisfy radius > 0 and height >= 2 * radius");
        }
    }
}
