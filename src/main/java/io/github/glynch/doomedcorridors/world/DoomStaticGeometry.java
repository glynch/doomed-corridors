/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import java.util.List;
import java.util.Objects;

/** Complete immutable static presentation geometry for one decoded map. */
public record DoomStaticGeometry(List<DoomSurface> surfaces, DoomPlayerStart playerStart) {
    /** Creates immutable geometry with a required player-one start. */
    public DoomStaticGeometry {
        surfaces = List.copyOf(surfaces);
        Objects.requireNonNull(playerStart, "playerStart");
    }
}
