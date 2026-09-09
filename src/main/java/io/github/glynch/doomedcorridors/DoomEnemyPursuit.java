/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import java.time.Duration;
import java.util.Objects;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/** Maintains one enemy's reaction delay and last-observed planar pursuit state. */
final class DoomEnemyPursuit {
    private static final float POSITION_TOLERANCE = 1.0E-5F;

    private final float preferredRange;
    private final float moveSpeed;
    private final long reactionNanos;
    private final Vector3f lastObservedTarget = new Vector3f();
    private long remainingReactionNanos;
    private boolean alerted;

    /** Retains validated world-unit distances and the provider-authored reaction delay. */
    DoomEnemyPursuit(float preferredRange, float moveSpeed, Duration reaction) {
        if (!Float.isFinite(preferredRange) || preferredRange <= 0.0F) {
            throw new IllegalArgumentException("preferredRange must be finite and positive");
        }
        if (!Float.isFinite(moveSpeed) || moveSpeed <= 0.0F) {
            throw new IllegalArgumentException("moveSpeed must be finite and positive");
        }
        Duration validReaction = Objects.requireNonNull(reaction, "reaction");
        if (validReaction.isNegative() || validReaction.isZero()) {
            throw new IllegalArgumentException("reaction must be positive");
        }
        this.preferredRange = preferredRange;
        this.moveSpeed = moveSpeed;
        reactionNanos = validReaction.toNanos();
    }

    /** Advances awareness and returns the desired collision-body velocity for this fixed step. */
    Vector3f advance(Vector3fc enemyPosition, Vector3fc targetPosition, boolean visible, Duration step) {
        Vector3fc validEnemyPosition = Objects.requireNonNull(enemyPosition, "enemyPosition");
        Vector3fc validTargetPosition = Objects.requireNonNull(targetPosition, "targetPosition");
        Duration validStep = Objects.requireNonNull(step, "step");
        if (validStep.isNegative()) {
            throw new IllegalArgumentException("step must not be negative");
        }
        float visibleDistance = horizontalDistance(validEnemyPosition, validTargetPosition);
        if (visible) {
            lastObservedTarget.set(validTargetPosition);
            alert();
        }
        if (!alerted) {
            return new Vector3f();
        }
        remainingReactionNanos = Math.max(0L, remainingReactionNanos - validStep.toNanos());
        if (remainingReactionNanos > 0L || visible && visibleDistance <= preferredRange) {
            return new Vector3f();
        }
        Vector3f direction = new Vector3f(lastObservedTarget).sub(validEnemyPosition);
        direction.y = 0.0F;
        float distance = direction.length();
        if (distance <= POSITION_TOLERANCE) {
            return new Vector3f();
        }
        return direction.mul(moveSpeed / distance);
    }

    /** Returns whether this enemy has observed its target during the current lifetime. */
    boolean isAlerted() {
        return alerted;
    }

    /** Returns whether the one-time reaction delay has elapsed. */
    boolean isReady() {
        return alerted && remainingReactionNanos == 0L;
    }

    /** Starts the one-time reaction delay upon first observation. */
    private void alert() {
        if (!alerted) {
            alerted = true;
            remainingReactionNanos = reactionNanos;
        }
    }

    /** Computes planar distance without allowing vertical separation to affect stopping. */
    private static float horizontalDistance(Vector3fc first, Vector3fc second) {
        return (float) Math.hypot(second.x() - first.x(), second.z() - first.z());
    }
}
