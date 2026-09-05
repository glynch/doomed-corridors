/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import static io.github.glynch.doomedcorridors.internal.Preconditions.requireFinite;

/** Player-one camera start expressed in JScene3D world coordinates. */
public record DoomPlayerStart(float x, float eyeHeight, float z, float yawRadians) {
    /** Creates a finite player start. */
    public DoomPlayerStart {
        requireFinite(x, "x");
        requireFinite(eyeHeight, "eyeHeight");
        requireFinite(z, "z");
        requireFinite(yawRadians, "yawRadians");
    }
}
