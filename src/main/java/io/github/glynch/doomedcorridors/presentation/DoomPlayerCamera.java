/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import static io.github.glynch.doomedcorridors.internal.Preconditions.requireFinite;

import io.github.glynch.doomedcorridors.world.DoomPlayerState;
import io.github.glynch.jscene3d.cameras.PerspectiveCamera;
import java.util.Objects;
import org.joml.Quaternionf;

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

    /**
     * Applies an eye offset and pitch to a camera whose parent supplies world position and yaw.
     *
     * <p>The resulting local camera direction is positive X when pitch is zero, matching Doom's
     * authored heading convention. Parent rotation then supplies the player's world-space yaw.
     *
     * @param camera child camera receiving the local view transform
     * @param eyeHeight local eye height above the character-body origin
     * @param pitchRadians local view pitch in radians
     */
    public static void applyLocalView(PerspectiveCamera camera, float eyeHeight, float pitchRadians) {
        PerspectiveCamera validCamera = Objects.requireNonNull(camera, "camera");
        float validEyeHeight = requireFinite(eyeHeight, "eyeHeight");
        float validPitch = requireFinite(pitchRadians, "pitchRadians");
        validCamera.setPosition(0.0F, validEyeHeight, 0.0F);
        validCamera.setQuaternion(new Quaternionf()
                .rotationY(-(float) Math.PI / 2.0F)
                .rotateX(validPitch));
    }
}
