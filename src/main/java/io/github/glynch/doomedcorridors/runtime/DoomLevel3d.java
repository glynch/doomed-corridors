/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.runtime;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntimeObject;
import io.github.glynch.jscene3d.project.runtime.extension.SceneNodeContext;
import io.github.glynch.jscene3d.project.value.ProjectValue;
import io.github.glynch.jscene3d.project.value.ResourceReference;

/** Runtime root for one declaratively selected imported Doom map. */
final class DoomLevel3d implements ProjectRuntimeObject {
    private final DoomMap map;
    private boolean started;
    private boolean closed;

    /** Stores the typed imported map resolved during scene composition. */
    private DoomLevel3d(DoomMap map) {
        this.map = map;
    }

    /** Resolves the authored map reference through the generic runtime resource boundary. */
    static DoomLevel3d create(SceneNodeContext context) {
        ProjectValue value = context.properties().get("map");
        if (!(value instanceof ProjectValue.ReferenceValue referenceValue)) {
            throw new IllegalArgumentException("map must be a resource reference");
        }
        ResourceReference reference = referenceValue.reference();
        if (reference.kind() != ResourceReference.Kind.IMPORT) {
            throw new IllegalArgumentException("map must reference an imported resource");
        }
        return new DoomLevel3d(context.resolveResource(reference, DoomMap.class));
    }

    /** Returns the imported runtime map after confirming this node remains open. */
    DoomMap map() {
        requireOpen();
        return map;
    }

    /** Returns whether scene lifecycle startup has completed. */
    boolean isStarted() {
        return started;
    }

    @Override
    public void start() {
        requireOpen();
        if (started) {
            throw new IllegalStateException("Doom level has already started");
        }
        started = true;
    }

    @Override
    public void close() {
        closed = true;
    }

    /** Rejects access after terminal lifecycle closure. */
    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Doom level is closed");
        }
    }
}
