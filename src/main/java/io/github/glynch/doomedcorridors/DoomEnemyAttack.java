/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import java.time.Duration;
import java.util.Objects;

/** Maintains one enemy's provider-authored attack range and repeat interval. */
final class DoomEnemyAttack {
    private final float range;
    private final long intervalNanos;
    private long remainingCooldownNanos;

    /** Retains validated world-unit range and attack interval. */
    DoomEnemyAttack(float range, Duration interval) {
        if (!Float.isFinite(range) || range <= 0.0F) {
            throw new IllegalArgumentException("range must be finite and positive");
        }
        Duration validInterval = Objects.requireNonNull(interval, "interval");
        if (validInterval.isNegative() || validInterval.isZero()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        this.range = range;
        intervalNanos = validInterval.toNanos();
    }

    /** Returns whether this fixed step authorizes an attack and starts its next cooldown when it does. */
    boolean advance(boolean ready, boolean visible, boolean targetAlive, float distance, Duration step) {
        if (!Float.isFinite(distance) || distance < 0.0F) {
            throw new IllegalArgumentException("distance must be finite and non-negative");
        }
        Duration validStep = Objects.requireNonNull(step, "step");
        if (validStep.isNegative()) {
            throw new IllegalArgumentException("step must not be negative");
        }
        if (!ready) {
            return false;
        }
        remainingCooldownNanos = Math.max(0L, remainingCooldownNanos - validStep.toNanos());
        if (!visible || !targetAlive || distance > range || remainingCooldownNanos > 0L) {
            return false;
        }
        remainingCooldownNanos = intervalNanos;
        return true;
    }
}
