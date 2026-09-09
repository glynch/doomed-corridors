/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Verifies successful-hit positions remain aligned with perspective-rendered targets. */
final class DoomWeaponHitTest {
    @Test
    void projectsAnAssistedHitAwayFromTheOriginalViewportCentre() {
        float horizontalSlope = (float) Math.tan(Math.toRadians(5.0));
        DoomWeaponHit hit = new DoomWeaponHit(horizontalSlope, 0.0F, 74.0F);

        var position = hit.project(1600, 900);
        float expectedX =
                800.0F + horizontalSlope / ((float) Math.tan(Math.toRadians(37.0)) * (1600.0F / 900.0F)) * 800.0F;

        assertThat(position.x).isCloseTo(expectedX, within(0.001F));
        assertThat(position.y).isEqualTo(450.0F);
    }
}
