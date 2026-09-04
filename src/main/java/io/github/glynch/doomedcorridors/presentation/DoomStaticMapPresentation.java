/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.world.DoomStaticGeometry;
import io.github.glynch.doomedcorridors.world.DoomSurface;
import io.github.glynch.jscene3d.geometries.BufferGeometry;
import io.github.glynch.jscene3d.materials.BasicMaterial;
import io.github.glynch.jscene3d.objects.Mesh;
import io.github.glynch.jscene3d.objects.Object3D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Owns static JScene3D objects deterministically derived from one imported Doom map. */
public final class DoomStaticMapPresentation implements AutoCloseable {
    private final Object3D root;
    private final DoomPresentationResources resources;
    private boolean closed;

    /** Stores one complete derived scene subtree and its owned GPU-facing resources. */
    private DoomStaticMapPresentation(Object3D root, DoomPresentationResources resources) {
        this.root = root;
        this.resources = resources;
    }

    /**
     * Builds a static spatial subtree from renderer-independent geometry and imported images.
     *
     * @param geometry complete static map geometry
     * @param sourceMaterials wall textures and flats referenced by the geometry
     * @return owned derived map presentation
     */
    public static DoomStaticMapPresentation create(
            DoomStaticGeometry geometry, DoomMapMaterials sourceMaterials) {
        DoomStaticGeometry validGeometry = Objects.requireNonNull(geometry, "geometry");
        DoomMapMaterials validMaterials = Objects.requireNonNull(sourceMaterials, "sourceMaterials");
        Object3D root = new Object3D();
        DoomPresentationResources resources =
                new DoomPresentationResources(validGeometry.surfaces().size());
        Map<DoomPresentationResources.MapMaterialKey, BasicMaterial> materialCache =
                new LinkedHashMap<>();
        for (DoomSurface surface : validGeometry.surfaces()) {
            BufferGeometry bufferGeometry = resources.createGeometry(surface.mesh());
            BasicMaterial material = materialCache.computeIfAbsent(
                    DoomPresentationResources.materialKey(surface),
                    key -> resources.createMapMaterial(key, validMaterials));
            root.add(new Mesh(bufferGeometry, material));
        }
        return new DoomStaticMapPresentation(root, resources);
    }

    /** Returns the derived root used for authored parent-child attachment. */
    public Object3D root() {
        requireOpen();
        return root;
    }

    /** Returns the number of generated map surfaces. */
    public int surfaceCount() {
        requireOpen();
        return resources.geometryCount();
    }

    /** Releases every geometry, material, and texture owned by the derived subtree. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        root.detach();
        root.clear();
        resources.close();
        closed = true;
    }

    /** Rejects access after terminal closure. */
    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Doom static map presentation is closed");
        }
    }
}
