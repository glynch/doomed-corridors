/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.actor;

import java.nio.file.Path;
import java.util.Objects;

/** Structured actor-catalog or placement diagnostic suitable for tools and GUIs. */
public record DoomActorDiagnostic(Severity severity, String code, Path source, String location, String message) {
    /** Diagnostic severity. */
    public enum Severity {
        /** Actor data cannot be used. */
        ERROR,
        /** Actor data remains usable with an explicit omission. */
        WARNING
    }

    /** Creates a complete diagnostic. */
    public DoomActorDiagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(message, "message");
    }
}
