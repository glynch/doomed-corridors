/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsRuntimeTypes;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceResolver;
import java.util.Objects;

/** Resolves and retains the stable player entity supplied through an imported actor group's public contract. */
final class DoomEnemyTarget implements ComponentReferenceBinder {
    private Entity player;

    /** Creates an unbound target which becomes usable after descriptor reference binding. */
    DoomEnemyTarget() {
        // Reference binding supplies the required authored player entity.
    }

    @Override
    public void bindReferences(ComponentReferenceResolver references) {
        player = Objects.requireNonNull(references, "references")
                .entity(DoomedCorridorsRuntimeTypes.PLAYER_TARGET_PROPERTY);
    }

    /** Returns the exact authored player entity. */
    Entity player() {
        if (player == null) {
            throw new IllegalStateException("enemy player target has not been bound");
        }
        return player;
    }

    /** Returns the damageable state capability on the exact authored player entity. */
    DoomDamageable damageable() {
        return player().capability(DoomedCorridorsRuntimeTypes.DAMAGEABLE_CAPABILITY, DoomDamageable.class)
                .orElseThrow(() -> new IllegalStateException("enemy player target has no damageable capability"));
    }
}
