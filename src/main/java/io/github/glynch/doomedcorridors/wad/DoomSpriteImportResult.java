/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.wad;

import io.github.glynch.doomedcorridors.actor.DoomActorSprites;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Imported actor sprites, when usable, and ordered source diagnostics. */
public record DoomSpriteImportResult(
        Optional<DoomActorSprites> sprites, List<WadDiagnostic> diagnostics) {
    /** Creates an immutable sprite import result. */
    public DoomSpriteImportResult {
        Objects.requireNonNull(sprites, "sprites");
        diagnostics = List.copyOf(diagnostics);
        if (sprites.isPresent()
                && diagnostics.stream().anyMatch(item -> item.severity() == WadDiagnostic.Severity.ERROR)) {
            throw new IllegalArgumentException("sprites cannot accompany error diagnostics");
        }
    }

    /** Returns whether importing produced a usable, potentially partial sprite set. */
    public boolean isValid() {
        return sprites.isPresent();
    }
}
