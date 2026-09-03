/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.actor;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Loaded actor catalog, when valid, and ordered diagnostics. */
public record DoomActorCatalogLoadResult(
        Optional<DoomActorCatalog> catalog, List<DoomActorDiagnostic> diagnostics) {
    /** Creates an immutable load result. */
    public DoomActorCatalogLoadResult {
        Objects.requireNonNull(catalog, "catalog");
        diagnostics = List.copyOf(diagnostics);
        if (catalog.isPresent()
                && diagnostics.stream().anyMatch(item -> item.severity() == DoomActorDiagnostic.Severity.ERROR)) {
            throw new IllegalArgumentException("catalog cannot accompany error diagnostics");
        }
    }

    /** Returns whether loading produced a usable catalog. */
    public boolean isValid() {
        return catalog.isPresent();
    }
}
