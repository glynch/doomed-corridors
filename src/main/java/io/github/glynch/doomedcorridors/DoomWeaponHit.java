/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Immutable camera-relative location carried by a successful weapon-hit signal. */
record DoomWeaponHit(float horizontalSlope, float verticalSlope, float fieldOfViewDegrees) {
    /** Validates the finite perspective coordinates captured when the shot resolves. */
    DoomWeaponHit {
        if (!Float.isFinite(horizontalSlope) || !Float.isFinite(verticalSlope)) {
            throw new IllegalArgumentException("weapon hit slopes must be finite");
        }
        if (!Float.isFinite(fieldOfViewDegrees) || fieldOfViewDegrees <= 0.0F || fieldOfViewDegrees >= 180.0F) {
            throw new IllegalArgumentException("weapon hit field of view must be between zero and 180 degrees");
        }
    }

    /** Projects the captured hit location into the supplied logical viewport. */
    Vector2f project(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("viewport dimensions must be positive");
        }
        float halfWidth = width * 0.5F;
        float halfHeight = height * 0.5F;
        float verticalScale = (float) Math.tan(Math.toRadians(fieldOfViewDegrees * 0.5F));
        float horizontalScale = verticalScale * width / height;
        return new Vector2f(
                halfWidth + horizontalSlope / horizontalScale * halfWidth,
                halfHeight - verticalSlope / verticalScale * halfHeight);
    }

    /** Captures one world direction in the firing camera's local perspective coordinates. */
    static DoomWeaponHit fromWorldDirection(
            Vector3fc worldDirection, Matrix4fc cameraWorldMatrix, float fieldOfViewDegrees) {
        Vector3f localDirection = new Matrix4f(cameraWorldMatrix)
                .invert()
                .transformDirection(new Vector3f(worldDirection))
                .normalize();
        float forward = -localDirection.z;
        if (!Float.isFinite(forward) || forward <= 0.0F) {
            throw new IllegalArgumentException("weapon hit direction must be in front of the camera");
        }
        return new DoomWeaponHit(localDirection.x / forward, localDirection.y / forward, fieldOfViewDegrees);
    }
}
