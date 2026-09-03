/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Loaded combat rules or structured diagnostics describing why loading failed. */
public record DoomCombatRulesLoadResult(
        Optional<DoomCombatRules> rules, List<DoomCombatDiagnostic> diagnostics) {
    /** Creates an immutable load result. */
    public DoomCombatRulesLoadResult {
        Objects.requireNonNull(rules, "rules");
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    /** Returns whether rules are available and no error diagnostic was emitted. */
    public boolean isValid() {
        return rules.isPresent()
                && diagnostics.stream().noneMatch(diagnostic ->
                        diagnostic.severity() == DoomCombatDiagnostic.Severity.ERROR);
    }
}
