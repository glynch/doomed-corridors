package io.github.glynch.doomedcorridors.wad;

import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Imported map materials, when valid, and ordered diagnostics suitable for tools and GUIs. */
public record DoomMaterialImportResult(
        Optional<DoomMapMaterials> materials, List<WadDiagnostic> diagnostics) {
    /** Creates an immutable import result. */
    public DoomMaterialImportResult {
        Objects.requireNonNull(materials, "materials");
        diagnostics = List.copyOf(diagnostics);
        if (materials.isPresent()
                && diagnostics.stream().anyMatch(item -> item.severity() == WadDiagnostic.Severity.ERROR)) {
            throw new IllegalArgumentException("materials cannot accompany error diagnostics");
        }
    }

    /** Returns whether import produced a usable material set. */
    public boolean isValid() {
        return materials.isPresent();
    }
}
