/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import static io.github.glynch.doomedcorridors.internal.Preconditions.requireFinite;

/** Immutable observable player position and view orientation in JScene3D world coordinates. */
public record DoomPlayerState(
        float x, float eyeHeight, float z, float yawRadians, float pitchRadians) {
    /** Creates a finite player state. */
    public DoomPlayerState {
        requireFinite(x, "x");
        requireFinite(eyeHeight, "eyeHeight");
        requireFinite(z, "z");
        requireFinite(yawRadians, "yawRadians");
        requireFinite(pitchRadians, "pitchRadians");
    }
}
