package io.github.glynch.doomedcorridors.wad;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result of loading WAD directory metadata without interpreting game content. */
public record WadLoadResult(Optional<WadArchive> archive, List<WadDiagnostic> diagnostics) {
    /** Creates an immutable result. */
    public WadLoadResult {
        Objects.requireNonNull(archive, "archive");
        diagnostics = List.copyOf(diagnostics);
    }

    /** Returns whether the WAD was accepted. */
    public boolean isValid() {
        return archive.isPresent()
                && diagnostics.stream().noneMatch(item -> item.severity() == WadDiagnostic.Severity.ERROR);
    }
}
