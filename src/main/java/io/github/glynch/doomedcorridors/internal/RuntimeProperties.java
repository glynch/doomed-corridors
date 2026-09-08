/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.internal;

import io.github.glynch.jscene3d.project.component.PropertyId;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentProperties;
import io.github.glynch.jscene3d.project.value.ProjectValue;
import java.util.Objects;

/** Exact conversions used by application runtime-component factories. */
public final class RuntimeProperties {
    /** Prevents construction of this stateless conversion component. */
    private RuntimeProperties() {
        throw new AssertionError("RuntimeProperties cannot be instantiated");
    }

    /** Converts one descriptor-validated number into a positive exact integer. */
    public static int positiveInteger(ComponentProperties properties, PropertyId property) {
        ProjectValue value =
                Objects.requireNonNull(properties, "properties").value(Objects.requireNonNull(property, "property"));
        if (!(value instanceof ProjectValue.NumberValue(var number))) {
            throw new IllegalArgumentException(property + " must be a number");
        }
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
}
