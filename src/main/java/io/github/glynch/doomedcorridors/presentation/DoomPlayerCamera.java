/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import io.github.glynch.doomedcorridors.world.DoomPlayerState;
import io.github.glynch.jscene3d.cameras.PerspectiveCamera;
import java.util.Objects;

/** Maps headless Doom player state onto a JScene3D perspective camera. */
public final class DoomPlayerCamera {
    /** Prevents construction of this stateless presentation adapter. */
    private DoomPlayerCamera() {
        throw new AssertionError("DoomPlayerCamera cannot be instantiated");
    }

    /**
     * Applies player position and view orientation to a camera.
     *
     * @param camera camera receiving the player view
     * @param player current headless player state
     */
    public static void apply(PerspectiveCamera camera, DoomPlayerState player) {
        PerspectiveCamera validCamera = Objects.requireNonNull(camera, "camera");
        DoomPlayerState validPlayer = Objects.requireNonNull(player, "player");
        float horizontal = (float) Math.cos(validPlayer.pitchRadians());
        float directionX = (float) Math.cos(validPlayer.yawRadians()) * horizontal;
        float directionY = (float) Math.sin(validPlayer.pitchRadians());
        float directionZ = -(float) Math.sin(validPlayer.yawRadians()) * horizontal;
        validCamera.setPosition(validPlayer.x(), validPlayer.eyeHeight(), validPlayer.z());
        validCamera.lookAt(
                validPlayer.x() + directionX,
                validPlayer.eyeHeight() + directionY,
                validPlayer.z() + directionZ);
    }
}
