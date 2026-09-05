/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.material.DoomMaterial;
import io.github.glynch.doomedcorridors.material.RgbaImage;
import io.github.glynch.doomedcorridors.world.DoomMeshData;
import io.github.glynch.doomedcorridors.world.DoomSurface;
import io.github.glynch.jscene3d.geometries.BufferAttribute;
import io.github.glynch.jscene3d.geometries.BufferGeometry;
import io.github.glynch.jscene3d.geometries.BufferUsage;
import io.github.glynch.jscene3d.materials.AlphaMode;
import io.github.glynch.jscene3d.materials.BasicMaterial;
import io.github.glynch.jscene3d.textures.Texture;
import io.github.glynch.jscene3d.textures.TextureCoordinateOrigin;
import io.github.glynch.jscene3d.textures.TextureFilter;
import io.github.glynch.jscene3d.textures.TextureWrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Owns shared JScene3D resources and the Doom-specific policy used to create them. */
final class DoomPresentationResources implements AutoCloseable {
    private final List<BufferGeometry> geometries;
    private final List<BasicMaterial> materials = new ArrayList<>();
    private final List<Texture> textures = new ArrayList<>();
    private boolean closed;

    /** Creates ownership storage sized for the known static surface count. */
    DoomPresentationResources(int surfaceCount) {
        geometries = new ArrayList<>(surfaceCount);
    }

    /** Copies and retains one renderer-independent mesh. */
    BufferGeometry createGeometry(DoomSurface surface) {
        requireOpen();
        DoomMeshData mesh = surface.mesh();
        BufferAttribute positions = BufferAttribute.of(
                mesh.positions(),
                3,
                surface.movingCeilingSector().isPresent() ? BufferUsage.DYNAMIC : BufferUsage.STATIC);
        BufferAttribute textureCoordinates = BufferAttribute.of(
                mesh.textureCoordinates(),
                2,
                surface.movingCeilingSector().isPresent() ? BufferUsage.DYNAMIC : BufferUsage.STATIC);
        BufferGeometry geometry = BufferGeometry.builder()
                .attribute(BufferGeometry.POSITION, positions)
                .normals(mesh.normals())
                .attribute(BufferGeometry.UV, textureCoordinates)
                .indices(mesh.indices())
                .build();
        geometries.add(geometry);
        return geometry;
    }

    /** Creates and retains one nearest-filtered map material. */
    BasicMaterial createMapMaterial(MapMaterialKey key, DoomMapMaterials sourceMaterials) {
        DoomMaterial source = sourceMaterial(key, sourceMaterials);
        return createImageMaterial(
                source.image(), TextureWrap.REPEAT, TextureCoordinateOrigin.TOP_LEFT);
    }

    /** Creates and retains one nearest-filtered image material. */
    BasicMaterial createImageMaterial(
            RgbaImage image, TextureWrap wrap, TextureCoordinateOrigin coordinateOrigin) {
        requireOpen();
        byte[] pixels = image.pixels();
        Texture texture = Texture.baseColor(image.width(), image.height(), pixels);
        texture.setCoordinateOrigin(coordinateOrigin);
        texture.setHorizontalWrap(wrap);
        texture.setVerticalWrap(wrap);
        texture.setMinificationFilter(TextureFilter.NEAREST_MIPMAP_NEAREST);
        texture.setMagnificationFilter(TextureFilter.NEAREST);
        BasicMaterial material = new BasicMaterial();
        material.setColorMap(texture);
        if (hasTransparentPixel(pixels)) {
            material.setAlphaMode(AlphaMode.MASK);
            material.setAlphaCutoff(0.5F);
        }
        textures.add(texture);
        materials.add(material);
        return material;
    }

    /** Returns the number of retained geometry resources. */
    int geometryCount() {
        requireOpen();
        return geometries.size();
    }

    /** Releases every retained geometry, material, and texture. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        geometries.forEach(BufferGeometry::close);
        materials.forEach(BasicMaterial::close);
        textures.forEach(Texture::close);
        closed = true;
    }

    /** Creates a namespace-aware material key for one surface. */
    static MapMaterialKey materialKey(DoomSurface surface) {
        DoomMaterial.Kind kind = switch (surface.type()) {
            case FLOOR, CEILING -> DoomMaterial.Kind.FLAT;
            case MIDDLE_WALL, UPPER_WALL, LOWER_WALL, MASKED_MIDDLE_WALL ->
                DoomMaterial.Kind.WALL_TEXTURE;
        };
        return new MapMaterialKey(kind, surface.materialName());
    }

    /** Resolves a material from its distinct flat or wall-texture namespace. */
    private static DoomMaterial sourceMaterial(
            MapMaterialKey key, DoomMapMaterials materials) {
        Map<String, DoomMaterial> namespace =
                key.kind == DoomMaterial.Kind.FLAT ? materials.flats() : materials.wallTextures();
        DoomMaterial material = namespace.get(key.name);
        if (material == null) {
            throw new IllegalArgumentException("Missing presentation material: " + key.name);
        }
        return material;
    }

    /** Returns whether any source pixel requires alpha masking. */
    private static boolean hasTransparentPixel(byte[] pixels) {
        for (int alpha = 3; alpha < pixels.length; alpha += 4) {
            if (Byte.toUnsignedInt(pixels[alpha]) < 255) {
                return true;
            }
        }
        return false;
    }

    /** Rejects mutation or inspection after terminal closure. */
    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Doom presentation resources are closed");
        }
    }

    /** Separates equal names in the flat and wall-texture namespaces. */
    record MapMaterialKey(DoomMaterial.Kind kind, String name) {}
}
