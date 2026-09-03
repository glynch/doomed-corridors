/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import io.github.glynch.doomedcorridors.combat.DoomCombatDiagnostic;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Loaded combat-presentation rules or structured diagnostics describing failure. */
public record DoomCombatPresentationLoadResult(
        Optional<DoomCombatPresentationRules> rules, List<DoomCombatDiagnostic> diagnostics) {
    /** Copies result values. */
    public DoomCombatPresentationLoadResult {
        Objects.requireNonNull(rules, "rules");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    /** Returns whether a usable presentation definition was loaded. */
    public boolean isValid() {
        return rules.isPresent();
    }
}
