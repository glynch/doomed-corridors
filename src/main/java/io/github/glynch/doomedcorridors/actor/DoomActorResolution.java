/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.actor;

import java.util.List;

/** Visible map actors and non-fatal placement diagnostics in source order. */
public record DoomActorResolution(List<DoomActor> actors, List<DoomActorDiagnostic> diagnostics) {
    /** Creates an immutable actor resolution. */
    public DoomActorResolution {
        actors = List.copyOf(actors);
        diagnostics = List.copyOf(diagnostics);
    }
}
