/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

import java.nio.file.Path;
import java.util.Objects;

/** One structured problem found while loading provider combat rules. */
public record DoomCombatDiagnostic(Severity severity, String code, Path source, String location, String message) {
    /** Creates a complete immutable diagnostic. */
    public DoomCombatDiagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        source = Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(message, "message");
    }

    /** Diagnostic severity independent of any graphical host. */
    public enum Severity {
        /** Invalid rules that prevent combat initialization. */
        ERROR
    }
}
