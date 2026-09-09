/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Verifies deterministic reaction, memory, movement, and stopping independent of rendering. */
final class DoomEnemyPursuitTest {
    private static final Vector3f ENEMY = new Vector3f(1.0F, 0.875F, 1.0F);
    private static final Vector3f TARGET = new Vector3f(7.0F, 1.25F, 9.0F);

    /** Remains dormant until the target is actually observed. */
    @Test
    void ignoresUnseenTarget() {
        DoomEnemyPursuit pursuit = pursuit();

        Vector3f velocity = pursuit.advance(ENEMY, TARGET, false, Duration.ofSeconds(1));

        assertThat(pursuit.isAlerted()).isFalse();
        assertThat(velocity).isEqualTo(new Vector3f());
    }

    /** Waits once, moves at provider speed, and continues toward the last observed position. */
    @Test
    void pursuesLastObservedPositionAfterReactionDelay() {
        DoomEnemyPursuit pursuit = pursuit();

        Vector3f waiting = pursuit.advance(ENEMY, TARGET, true, Duration.ofMillis(299));
        Vector3f moving = pursuit.advance(ENEMY, new Vector3f(20.0F, 4.0F, -5.0F), false, Duration.ofMillis(1));

        assertThat(pursuit.isAlerted()).isTrue();
        assertThat(waiting).isEqualTo(new Vector3f());
        assertThat(moving).satisfies(velocity -> {
            assertThat(velocity.length()).isCloseTo(3.0F, within(1.0E-6F));
            assertThat(velocity.y).isZero();
            assertThat(velocity.x).isPositive();
            assertThat(velocity.z).isPositive();
        });
    }

    /** Stops while a visible target is inside the provider-authored preferred range. */
    @Test
    void stopsAtPreferredRange() {
        DoomEnemyPursuit pursuit = pursuit();
        Vector3f nearbyTarget = new Vector3f(2.0F, 5.0F, 1.0F);

        Vector3f velocity = pursuit.advance(ENEMY, nearbyTarget, true, Duration.ofMillis(300));

        assertThat(pursuit.isAlerted()).isTrue();
        assertThat(velocity).isEqualTo(new Vector3f());
    }

    private static DoomEnemyPursuit pursuit() {
        return new DoomEnemyPursuit(3.0F, 3.0F, Duration.ofMillis(300));
    }
}
