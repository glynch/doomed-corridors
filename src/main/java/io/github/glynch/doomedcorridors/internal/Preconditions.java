/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.internal;

/** Shared implementation-only argument checks used across application packages. */
public final class Preconditions {
    /** Prevents instantiation of this validation utility class. */
    private Preconditions() {
        throw new AssertionError("Preconditions cannot be instantiated");
    }

    /**
     * Requires a finite floating-point value.
     *
     * @param value value to validate
     * @param parameterName parameter name used in diagnostics
     * @return validated value
     */
    public static float requireFinite(float value, String parameterName) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(parameterName + " must be finite: " + value);
        }
        return value;
    }

    /**
     * Requires a finite positive floating-point value.
     *
     * @param value value to validate
     * @param parameterName parameter name used in diagnostics
     * @return validated value
     */
    public static float requirePositive(float value, String parameterName) {
        float finiteValue = requireFinite(value, parameterName);
        if (finiteValue <= 0.0F) {
            throw new IllegalArgumentException(parameterName + " must be positive: " + finiteValue);
        }
        return finiteValue;
    }

    /**
     * Requires a positive integer value.
     *
     * @param value value to validate
     * @param parameterName parameter name used in diagnostics
     * @return validated value
     */
    public static int requirePositive(int value, String parameterName) {
        if (value <= 0) {
            throw new IllegalArgumentException(parameterName + " must be positive: " + value);
        }
        return value;
    }

    /**
     * Requires a finite non-negative floating-point value.
     *
     * @param value value to validate
     * @param parameterName parameter name used in diagnostics
     * @return validated value
     */
    public static float requireNonNegative(float value, String parameterName) {
        float finiteValue = requireFinite(value, parameterName);
        if (finiteValue < 0.0F) {
            throw new IllegalArgumentException(parameterName + " must not be negative: " + finiteValue);
        }
        return finiteValue;
    }

    /**
     * Requires a non-negative integer value.
     *
     * @param value value to validate
     * @param parameterName parameter name used in diagnostics
     * @return validated value
     */
    public static int requireNonNegative(int value, String parameterName) {
        if (value < 0) {
            throw new IllegalArgumentException(parameterName + " must not be negative: " + value);
        }
        return value;
    }

    /**
     * Requires a finite floating-point value in an inclusive interval.
     *
     * @param value value to validate
     * @param minimum inclusive minimum
     * @param maximum inclusive maximum
     * @param parameterName parameter name used in diagnostics
     * @return validated value
     */
    public static float requireInRange(float value, float minimum, float maximum, String parameterName) {
        float finiteValue = requireFinite(value, parameterName);
        if (finiteValue < minimum || finiteValue > maximum) {
            throw new IllegalArgumentException(
                    parameterName + " must be between " + minimum + " and " + maximum + ": " + finiteValue);
        }
        return finiteValue;
    }

    /**
     * Requires an integer value in an inclusive interval.
     *
     * @param value value to validate
     * @param minimum inclusive minimum
     * @param maximum inclusive maximum
     * @param parameterName parameter name used in diagnostics
     * @return validated value
     */
    public static int requireInRange(int value, int minimum, int maximum, String parameterName) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    parameterName + " must be between " + minimum + " and " + maximum + ": " + value);
        }
        return value;
    }
}
