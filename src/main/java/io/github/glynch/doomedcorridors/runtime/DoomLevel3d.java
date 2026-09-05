/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.runtime;

import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.presentation.DoomStaticMapPresentation;
import io.github.glynch.doomedcorridors.world.DoomGeometryBuildResult;
import io.github.glynch.doomedcorridors.world.DoomPlayerStart;
import io.github.glynch.doomedcorridors.world.DoomStaticGeometry;
import io.github.glynch.doomedcorridors.world.DoomStaticGeometryBuilder;
import io.github.glynch.jscene3d.doom.map.DoomMap;
import io.github.glynch.jscene3d.objects.Object3D;
import io.github.glynch.jscene3d.project.runtime.extension.ProjectValues;
import io.github.glynch.jscene3d.project.runtime.extension.SceneNodeContext;
import io.github.glynch.jscene3d.project.runtime.lwjgl.Scene3dRuntimeObject;
import io.github.glynch.jscene3d.project.value.ResourceReference;

/** Runtime scene node presenting one declaratively selected imported Doom map. */
final class DoomLevel3d implements Scene3dRuntimeObject {
    private final DoomMap map;
    private final DoomPlayerStart playerStart;
    private final DoomStaticMapPresentation presentation;
    private boolean started;
    private boolean closed;

    /** Stores one typed map and its deterministic derived presentation. */
    private DoomLevel3d(DoomMap map, DoomPlayerStart playerStart, DoomStaticMapPresentation presentation) {
        this.map = map;
        this.playerStart = playerStart;
        this.presentation = presentation;
    }

    /** Resolves authored imports and constructs the derived spatial subtree. */
    static DoomLevel3d create(SceneNodeContext context) {
        DoomMap map = resolveImported(context, "map", DoomMap.class);
        DoomMapMaterials materials =
                resolveImported(context, "materials", DoomMapMaterials.class);
        if (!materials.mapName().equals(map.name())) {
            throw new IllegalArgumentException(
                    "Imported Doom map and materials must identify the same map");
        }
        DoomGeometryBuildResult result = new DoomStaticGeometryBuilder().build(map, materials);
        DoomStaticGeometry geometry = result.geometry()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Imported Doom map geometry could not be built: " + result.diagnostics()));
        DoomStaticMapPresentation presentation =
                DoomStaticMapPresentation.create(geometry, materials);
        attach(context, presentation.root());
        return new DoomLevel3d(map, geometry.playerStart(), presentation);
    }

    /** Returns the imported runtime map after confirming this node remains open. */
    DoomMap map() {
        requireOpen();
        return map;
    }

    /** Returns the authored player-one start resolved from the imported level. */
    DoomPlayerStart playerStart() {
        requireOpen();
        return playerStart;
    }

    /** Returns the number of statically generated map surfaces. */
    int surfaceCount() {
        requireOpen();
        return presentation.surfaceCount();
    }

    @Override
    public Object3D object3d() {
        requireOpen();
        return presentation.root();
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
        if (closed) {
            return;
        }
        presentation.close();
        closed = true;
    }

    /** Resolves one required imported resource through the generic runtime cache boundary. */
    private static <T> T resolveImported(
            SceneNodeContext context, String property, Class<T> valueType) {
        ResourceReference reference = ProjectValues.reference(context.properties(), property);
        if (reference.kind() != ResourceReference.Kind.IMPORT) {
            throw new IllegalArgumentException(property + " must reference an imported resource");
        }
        return context.resolveResource(reference, valueType);
    }

    /** Attaches the generated subtree to an authored spatial parent when one is present. */
    private static void attach(SceneNodeContext context, Object3D object) {
        context.parent().ifPresent(parent -> {
            if (!(parent.object() instanceof Scene3dRuntimeObject spatialParent)) {
                throw new IllegalArgumentException("doom-level-3d requires a spatial 3d parent");
            }
            spatialParent.object3d().add(object);
        });
    }

    /** Rejects access after terminal lifecycle closure. */
    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Doom level is closed");
        }
    }
}
