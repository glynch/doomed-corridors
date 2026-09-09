/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsRuntimeTypes;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.World;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpointBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceResolver;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Applies terminal player lifecycle changes to an explicitly authored controls entity. */
final class DoomPlayerLifecycle implements ComponentReferenceBinder, ComponentEndpointBinder {
    private final World world;
    private @Nullable Entity controls;
    private boolean dead;

    DoomPlayerLifecycle(World world) {
        this.world = Objects.requireNonNull(world, "world");
    }

    @Override
    public void bindReferences(ComponentReferenceResolver references) {
        controls = Objects.requireNonNull(references, "references")
                .entity(DoomedCorridorsRuntimeTypes.PLAYER_CONTROL_ENTITY_PROPERTY);
    }

    @Override
    public void bindEndpoints(ComponentEndpoints endpoints) {
        Objects.requireNonNull(endpoints, "endpoints")
                .action(DoomedCorridorsRuntimeTypes.RECEIVE_PLAYER_DIED_ACTION, this::receiveDied);
    }

    /** Returns whether this lifecycle has applied terminal player death. */
    boolean isDead() {
        return dead;
    }

    /** Disables the exact authored controls entity once while leaving the view and HUD active. */
    private void receiveDied() {
        if (dead) {
            return;
        }
        Entity target = Objects.requireNonNull(controls, "player controls have not been bound");
        if (target.isLocallyEnabled()) {
            world.disable(target);
        }
        dead = true;
    }
}
