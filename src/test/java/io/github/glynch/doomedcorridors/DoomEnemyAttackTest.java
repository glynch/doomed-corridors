/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Verifies enemy attack authorization independently of rendering and native physics. */
final class DoomEnemyAttackTest {
    /** Attacks immediately after reaction and then observes the complete repeat interval. */
    @Test
    void enforcesReadinessAndRepeatInterval() {
        DoomEnemyAttack attack = new DoomEnemyAttack(64.0F, Duration.ofSeconds(1));

        List<Boolean> results = List.of(
                attack.advance(false, true, true, 6.0F, Duration.ofMillis(300)),
                attack.advance(true, true, true, 6.0F, Duration.ZERO),
                attack.advance(true, true, true, 6.0F, Duration.ofMillis(999)),
                attack.advance(true, true, true, 6.0F, Duration.ofMillis(1)));

        assertThat(results).containsExactly(false, true, false, true);
    }

    /** Refuses attacks without current visibility, inside neither range, or against terminal health. */
    @Test
    void requiresVisibleLivingTargetWithinRange() {
        DoomEnemyAttack attack = new DoomEnemyAttack(64.0F, Duration.ofSeconds(1));

        List<Boolean> results = List.of(
                attack.advance(true, false, true, 6.0F, Duration.ofSeconds(1)),
                attack.advance(true, true, true, 64.01F, Duration.ZERO),
                attack.advance(true, true, false, 6.0F, Duration.ZERO),
                attack.advance(true, true, true, 64.0F, Duration.ZERO));

        assertThat(results).containsExactly(false, false, false, true);
    }
}
