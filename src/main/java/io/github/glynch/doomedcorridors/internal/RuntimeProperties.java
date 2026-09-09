/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.internal;

import io.github.glynch.jscene3d.project.component.PropertyId;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentProperties;
import io.github.glynch.jscene3d.project.value.ProjectValue;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import java.util.List;
import java.util.Objects;

/** Exact conversions used by application runtime-component factories. */
public final class RuntimeProperties {
    /** Prevents construction of this stateless conversion component. */
    private RuntimeProperties() {
        throw new AssertionError("RuntimeProperties cannot be instantiated");
    }

    /** Converts one descriptor-validated number into a positive exact integer. */
    public static int positiveInteger(ComponentProperties properties, PropertyId property) {
        var number = number(properties, property);
        final int converted;
        try {
            converted = number.intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(property + " must be representable as an exact integer", exception);
        }
        if (converted <= 0) {
            throw new IllegalArgumentException(property + " must be positive");
        }
        return converted;
    }

    /** Converts one descriptor-validated number into a positive finite float. */
    public static float positiveFloat(ComponentProperties properties, PropertyId property) {
        float converted = finiteFloat(properties, property);
        if (converted <= 0.0F) {
            throw new IllegalArgumentException(property + " must be positive");
        }
        return converted;
    }

    /** Converts one descriptor-validated number into a non-negative finite float. */
    public static float nonNegativeFloat(ComponentProperties properties, PropertyId property) {
        float converted = finiteFloat(properties, property);
        if (converted < 0.0F) {
            throw new IllegalArgumentException(property + " must be non-negative");
        }
        return converted;
    }

    /** Converts one homogeneous descriptor-validated array into immutable resource references. */
    public static List<ResourceReference> resourceReferences(ComponentProperties properties, PropertyId property) {
        ProjectValue value =
                Objects.requireNonNull(properties, "properties").value(Objects.requireNonNull(property, "property"));
        if (!(value instanceof ProjectValue.ArrayValue(var elements))) {
            throw new IllegalArgumentException(property + " must be an array");
        }
        return elements.stream()
                .map(element -> resourceReference(element, property))
                .toList();
    }

    /** Converts one already shape-validated array element into its resource reference. */
    private static ResourceReference resourceReference(ProjectValue value, PropertyId property) {
        if (!(value instanceof ProjectValue.ReferenceValue(ResourceReference reference))) {
            throw new IllegalArgumentException(property + " must contain only resource references");
        }
        return reference;
    }

    /** Converts one descriptor-validated number into a finite float. */
    private static float finiteFloat(ComponentProperties properties, PropertyId property) {
        float converted = number(properties, property).floatValue();
        if (!Float.isFinite(converted)) {
            throw new IllegalArgumentException(property + " must be representable as a finite float");
        }
        return converted;
    }

    /** Returns one descriptor-validated numeric value. */
    private static java.math.BigDecimal number(ComponentProperties properties, PropertyId property) {
        ProjectValue value =
                Objects.requireNonNull(properties, "properties").value(Objects.requireNonNull(property, "property"));
        if (!(value instanceof ProjectValue.NumberValue(var number))) {
            throw new IllegalArgumentException(property + " must be a number");
        }
        return number;
    }
}
