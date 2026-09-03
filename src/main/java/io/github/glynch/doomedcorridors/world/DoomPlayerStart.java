/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

/** Player-one camera start expressed in JScene3D world coordinates. */
public record DoomPlayerStart(float x, float eyeHeight, float z, float yawRadians) {
    /** Creates a finite player start. */
    public DoomPlayerStart {
        if (!Float.isFinite(x)
                || !Float.isFinite(eyeHeight)
                || !Float.isFinite(z)
                || !Float.isFinite(yawRadians)) {
            throw new IllegalArgumentException("player start values must be finite");
        }
    }
}
