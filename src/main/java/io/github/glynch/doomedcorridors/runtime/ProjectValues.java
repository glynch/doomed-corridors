/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.runtime;

import io.github.glynch.jscene3d.project.value.ProjectValue;
import java.util.List;
import java.util.Map;

/** Strict typed access to portable project values consumed by application factories. */
final class ProjectValues {
    /** Prevents construction of this stateless value reader. */
    private ProjectValues() {
        throw new AssertionError("ProjectValues cannot be instantiated");
    }

    /** Returns one required text property. */
    static String text(Map<String, ProjectValue> properties, String name) {
        ProjectValue value = required(properties, name);
        if (value instanceof ProjectValue.TextValue text) {
            return text.value();
        }
        throw wrongKind(name, "text");
    }

    /** Returns one required exact integer property. */
    static int integer(Map<String, ProjectValue> properties, String name) {
        ProjectValue value = required(properties, name);
        if (value instanceof ProjectValue.NumberValue number) {
            try {
                return number.value().intValueExact();
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException(name + " must be an exact integer", exception);
            }
        }
        throw wrongKind(name, "number");
    }

    /** Returns one required ordered array property. */
    static List<ProjectValue> array(Map<String, ProjectValue> properties, String name) {
        ProjectValue value = required(properties, name);
        if (value instanceof ProjectValue.ArrayValue array) {
            return array.values();
        }
        throw wrongKind(name, "array");
    }

    /** Returns one required object value. */
    static Map<String, ProjectValue> object(ProjectValue value, String name) {
        if (value instanceof ProjectValue.ObjectValue object) {
            return object.values();
        }
        throw wrongKind(name, "object");
    }

    /** Returns one required resource reference value. */
    static ProjectValue.ReferenceValue reference(
            Map<String, ProjectValue> properties, String name) {
        ProjectValue value = required(properties, name);
        if (value instanceof ProjectValue.ReferenceValue reference) {
            return reference;
        }
        throw wrongKind(name, "reference");
    }

    /** Requires a declared property. */
    private static ProjectValue required(Map<String, ProjectValue> properties, String name) {
        ProjectValue value = properties.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Missing required property: " + name);
        }
        return value;
    }

    /** Creates a consistent property-kind failure. */
    private static IllegalArgumentException wrongKind(String name, String expected) {
        return new IllegalArgumentException(name + " must be a " + expected + " value");
    }
}
