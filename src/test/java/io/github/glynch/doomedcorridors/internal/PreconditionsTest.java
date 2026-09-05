/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class PreconditionsTest {
    @Test
    void acceptsFiniteValues() {
        assertThat(Preconditions.requireFinite(-2.5F, "value")).isEqualTo(-2.5F);
    }

    @Test
    void rejectsNonFiniteValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Preconditions.requireFinite(Float.NaN, "value"))
                .withMessageContaining("value");
    }

    @Test
    void requiresPositiveValues() {
        assertThat(Preconditions.requirePositive(2.5F, "floatValue")).isEqualTo(2.5F);
        assertThat(Preconditions.requirePositive(4, "intValue")).isEqualTo(4);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Preconditions.requirePositive(0.0F, "floatValue"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Preconditions.requirePositive(0, "intValue"));
    }

    @Test
    void requiresNonNegativeValues() {
        assertThat(Preconditions.requireNonNegative(0.0F, "floatValue")).isZero();
        assertThat(Preconditions.requireNonNegative(0, "value")).isZero();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Preconditions.requireNonNegative(-0.1F, "floatValue"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Preconditions.requireNonNegative(-1, "value"));
    }

    @Test
    void requiresFloatingPointValuesWithinInclusiveRange() {
        assertThat(Preconditions.requireInRange(-1.0F, -1.0F, 1.0F, "value")).isEqualTo(-1.0F);
        assertThat(Preconditions.requireInRange(1.0F, -1.0F, 1.0F, "value")).isEqualTo(1.0F);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Preconditions.requireInRange(1.1F, -1.0F, 1.0F, "value"));
    }

    @Test
    void requiresIntegersWithinInclusiveRange() {
        assertThat(Preconditions.requireInRange(0, 0, 2, "value")).isZero();
        assertThat(Preconditions.requireInRange(2, 0, 2, "value")).isEqualTo(2);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Preconditions.requireInRange(3, 0, 2, "value"));
    }
}
