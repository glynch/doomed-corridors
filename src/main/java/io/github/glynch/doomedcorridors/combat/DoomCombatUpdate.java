/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

import java.util.List;
import java.util.Objects;

/** Resulting immutable state and ordered events from one combat operation. */
public record DoomCombatUpdate(DoomCombatState state, List<DoomCombatEvent> events) {
    /** Creates an immutable update. */
    public DoomCombatUpdate {
        Objects.requireNonNull(state, "state");
        events = List.copyOf(Objects.requireNonNull(events, "events"));
    }
}
