/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.jscene3d.project.runtime.Entity;
import org.joml.Vector3f;

/** Game-specific target geometry selected through the descriptor-declared hitscan-target capability. */
interface DoomHitscanTarget extends DoomDamageable {
    /** Returns the exact entity which owns this target and its solid collision body. */
    Entity owner();

    /** Copies the current world-space hitscan aim point into the supplied destination. */
    Vector3f aimPoint(Vector3f destination);

    /** Returns the target's horizontal collision radius in engine world units. */
    float aimRadius();
}
