/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

/** Immutable observable player position and view orientation in JScene3D world coordinates. */
public record DoomPlayerState(
        float x, float eyeHeight, float z, float yawRadians, float pitchRadians) {
    /** Creates a finite player state. */
    public DoomPlayerState {
        if (!Float.isFinite(x)
                || !Float.isFinite(eyeHeight)
                || !Float.isFinite(z)
                || !Float.isFinite(yawRadians)
                || !Float.isFinite(pitchRadians)) {
            throw new IllegalArgumentException("player state values must be finite");
        }
    }
}
