/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import static io.github.glynch.doomedcorridors.internal.Preconditions.requireFinite;
import static io.github.glynch.doomedcorridors.internal.Preconditions.requireInRange;

/** Held movement and turn axes, frame-relative view rotation, and a discrete interaction request. */
public record DoomPlayerCommand(
        float forward,
        float strafe,
        float turn,
        float yawDelta,
        float pitchDelta,
        boolean interact) {
    /** Creates a movement-only command for callers that have no discrete interaction request. */
    public DoomPlayerCommand(float forward, float strafe, float turn, float yawDelta, float pitchDelta) {
        this(forward, strafe, turn, yawDelta, pitchDelta, false);
    }

    /** Creates a finite command whose held axes are each in the inclusive range [-1, 1]. */
    public DoomPlayerCommand {
        requireInRange(forward, -1.0F, 1.0F, "forward");
        requireInRange(strafe, -1.0F, 1.0F, "strafe");
        requireInRange(turn, -1.0F, 1.0F, "turn");
        requireFinite(yawDelta, "yawDelta");
        requireFinite(pitchDelta, "pitchDelta");
    }

    /** Returns a command with no movement or view change. */
    public static DoomPlayerCommand idle() {
        return new DoomPlayerCommand(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, false);
    }
}
