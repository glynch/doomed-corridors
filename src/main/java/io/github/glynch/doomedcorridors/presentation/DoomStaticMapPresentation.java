/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import static io.github.glynch.doomedcorridors.internal.Preconditions.requireFinite;
import static io.github.glynch.doomedcorridors.internal.Preconditions.requireNonNegative;

import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.world.DoomStaticGeometry;
import io.github.glynch.doomedcorridors.world.DoomSurface;
import io.github.glynch.doomedcorridors.world.DoomUnits;
import io.github.glynch.jscene3d.geometries.BufferAttribute;
import io.github.glynch.jscene3d.geometries.BufferGeometry;
import io.github.glynch.jscene3d.materials.BasicMaterial;
import io.github.glynch.jscene3d.objects.Mesh;
import io.github.glynch.jscene3d.objects.Object3D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owns static JScene3D objects deterministically derived from one imported Doom map. */
public final class DoomStaticMapPresentation implements AutoCloseable {
    private final Object3D root;
    private final DoomPresentationResources resources;
    private final List<CeilingBinding> ceilingBindings;
    private boolean closed;

    /** Stores one complete derived scene subtree and its owned GPU-facing resources. */
    private DoomStaticMapPresentation(
            Object3D root,
            DoomPresentationResources resources,
            List<CeilingBinding> ceilingBindings) {
        this.root = root;
        this.resources = resources;
        this.ceilingBindings = List.copyOf(ceilingBindings);
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
        List<CeilingBinding> ceilingBindings = new ArrayList<>();
        for (DoomSurface surface : validGeometry.surfaces()) {
            BufferGeometry bufferGeometry = resources.createGeometry(surface);
            BasicMaterial material = materialCache.computeIfAbsent(
                    DoomPresentationResources.materialKey(surface),
                    key -> resources.createMapMaterial(key, validMaterials));
            root.add(new Mesh(bufferGeometry, material));
            surface.movingCeilingSector().ifPresent(sectorIndex -> ceilingBindings.add(
                    CeilingBinding.create(surface, bufferGeometry, validMaterials, sectorIndex)));
        }
        return new DoomStaticMapPresentation(root, resources, ceilingBindings);
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

    /** Applies one effective sector ceiling height to every presentation surface that follows it. */
    public void setSectorCeilingHeight(int sectorIndex, float height) {
        requireOpen();
        requireNonNegative(sectorIndex, "sectorIndex");
        requireFinite(height, "height");
        ceilingBindings.stream()
                .filter(binding -> binding.sectorIndex == sectorIndex)
                .forEach(binding -> binding.setHeight(height));
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

    /** Mutable GPU-facing attributes and immutable mapping for one moving ceiling surface. */
    private static final class CeilingBinding {
        private final DoomSurface.Type surfaceType;
        private final int sectorIndex;
        private final BufferAttribute positions;
        private final BufferAttribute textureCoordinates;
        private final float initialHeight;
        private final float initialBottomV;
        private final float textureHeight;

        /** Retains dynamic attributes and source values needed for deterministic updates. */
        private CeilingBinding(
                DoomSurface.Type surfaceType,
                int sectorIndex,
                BufferAttribute positions,
                BufferAttribute textureCoordinates,
                float initialHeight,
                float initialBottomV,
                float textureHeight) {
            this.surfaceType = surfaceType;
            this.sectorIndex = sectorIndex;
            this.positions = positions;
            this.textureCoordinates = textureCoordinates;
            this.initialHeight = initialHeight;
            this.initialBottomV = initialBottomV;
            this.textureHeight = textureHeight;
        }

        /** Creates a checked binding for a ceiling plane or an upper wall's lower edge. */
        private static CeilingBinding create(
                DoomSurface surface,
                BufferGeometry geometry,
                DoomMapMaterials materials,
                int sectorIndex) {
            BufferAttribute positions = Objects.requireNonNull(
                    geometry.attribute(BufferGeometry.POSITION), "position attribute");
            BufferAttribute textureCoordinates = Objects.requireNonNull(
                    geometry.attribute(BufferGeometry.UV), "texture-coordinate attribute");
            float textureHeight = surface.type() == DoomSurface.Type.UPPER_WALL
                    ? materials.wallTextures().get(surface.materialName()).image().height()
                    : 1.0F;
            return new CeilingBinding(
                    surface.type(),
                    sectorIndex,
                    positions,
                    textureCoordinates,
                    positions.value(0, 1),
                    textureCoordinates.value(0, 1),
                    textureHeight);
        }

        /** Moves all plane vertices or only the lower edge of an upper-wall quad. */
        private void setHeight(float height) {
            if (surfaceType == DoomSurface.Type.CEILING) {
                positions.edit(editor -> {
                    for (int vertex = 0; vertex < positions.count(); vertex++) {
                        editor.set(vertex, 1, height);
                    }
                });
                return;
            }
            positions.edit(editor -> {
                editor.set(0, 1, height);
                editor.set(1, 1, height);
            });
            float bottomV = initialBottomV - DoomUnits.fromWorldFloat(height - initialHeight) / textureHeight;
            textureCoordinates.edit(editor -> {
                editor.set(0, 1, bottomV);
                editor.set(1, 1, bottomV);
            });
        }
    }
}
