/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Exercises the shared Doom-to-world coordinate policy. */
final class DoomUnitsTest {
    /** Converts measurements, deltas, and axes in both directions. */
    @Test
    void convertsCoordinates() {
        assertThat(DoomUnits.toWorld(64.0F)).isEqualTo(2.0F);
        assertThat(DoomUnits.deltaToWorld(96, 32)).isEqualTo(2.0F);
        assertThat(DoomUnits.yToWorldZ(64.0)).isEqualTo(-2.0F);
        assertThat(DoomUnits.fromWorld(2.0F)).isEqualTo(64.0);
        assertThat(DoomUnits.worldZToY(-2.0F)).isEqualTo(64.0);
    }

    /** Avoids negative zero when mapping the Doom y-axis origin. */
    @Test
    void preservesPositiveZeroForWorldZ() {
        assertThat(Float.floatToIntBits(DoomUnits.yToWorldZ(0.0))).isZero();
    }

    /** Promotes integral operands before calculating a coordinate delta. */
    @Test
    void promotesCoordinateDeltaBeforeSubtraction() {
        float expected = ((float) Integer.MAX_VALUE - Integer.MIN_VALUE) / 32.0F;

        assertThat(DoomUnits.deltaToWorld(Integer.MAX_VALUE, Integer.MIN_VALUE))
                .isEqualTo(expected);
    }
}
