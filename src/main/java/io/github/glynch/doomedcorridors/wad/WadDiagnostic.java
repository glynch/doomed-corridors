package io.github.glynch.doomedcorridors.wad;

import java.nio.file.Path;
import java.util.Objects;

/** A stable, user-facing diagnostic produced while opening a WAD. */
public record WadDiagnostic(Severity severity, String code, Path source, String location, String message) {
    /** Diagnostic severity. */
    public enum Severity {
        /** The WAD cannot be used. */
        ERROR,
        /** The WAD can be used, but deserves attention. */
        WARNING
    }

    /** Creates a diagnostic. */
    public WadDiagnostic {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(message, "message");
    }
}
