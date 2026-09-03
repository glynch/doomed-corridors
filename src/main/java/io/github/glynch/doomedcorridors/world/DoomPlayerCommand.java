/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

/** Held movement and turn axes plus frame-relative pointer rotation for a headless game session. */
public record DoomPlayerCommand(float forward, float strafe, float turn, float yawDelta, float pitchDelta) {
    /** Creates a finite command whose held axes are each in the inclusive range [-1, 1]. */
    public DoomPlayerCommand {
        requireAxis(forward, "forward");
        requireAxis(strafe, "strafe");
        requireAxis(turn, "turn");
        requireFinite(yawDelta, "yawDelta");
        requireFinite(pitchDelta, "pitchDelta");
    }

    /** Returns a command with no movement or view change. */
    public static DoomPlayerCommand idle() {
        return new DoomPlayerCommand(0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    /** Requires one normalized movement axis. */
    private static void requireAxis(float value, String name) {
        requireFinite(value, name);
        if (value < -1.0F || value > 1.0F) {
            throw new IllegalArgumentException(name + " must be in the range [-1, 1]: " + value);
        }
    }

    /** Requires one finite scalar. */
    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
