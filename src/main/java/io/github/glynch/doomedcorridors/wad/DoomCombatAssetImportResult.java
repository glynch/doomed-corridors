/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.wad;

import io.github.glynch.doomedcorridors.presentation.DoomCombatAssets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Imported combat presentation assets, when usable, and source diagnostics. */
public record DoomCombatAssetImportResult(
        Optional<DoomCombatAssets> assets, List<WadDiagnostic> diagnostics) {
    /** Copies the result values and rejects contradictory success states. */
    public DoomCombatAssetImportResult {
        Objects.requireNonNull(assets, "assets");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        if (assets.isPresent()
                && diagnostics.stream().anyMatch(item -> item.severity() == WadDiagnostic.Severity.ERROR)) {
            throw new IllegalArgumentException("assets cannot accompany error diagnostics");
        }
    }

    /** Returns whether all required combat assets were decoded. */
    public boolean isValid() {
        return assets.isPresent();
    }
}
