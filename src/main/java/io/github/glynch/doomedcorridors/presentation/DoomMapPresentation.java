/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.material.DoomMaterial;
import io.github.glynch.doomedcorridors.material.RgbaImage;
import io.github.glynch.doomedcorridors.world.DoomMeshData;
import io.github.glynch.doomedcorridors.world.DoomPlayerStart;
import io.github.glynch.doomedcorridors.world.DoomStaticGeometry;
import io.github.glynch.doomedcorridors.world.DoomSurface;
import io.github.glynch.jscene3d.cameras.PerspectiveCamera;
import io.github.glynch.jscene3d.geometries.BufferGeometry;
import io.github.glynch.jscene3d.materials.AlphaMode;
import io.github.glynch.jscene3d.materials.BasicMaterial;
import io.github.glynch.jscene3d.math.Color;
import io.github.glynch.jscene3d.objects.Mesh;
import io.github.glynch.jscene3d.scenes.Scene;
import io.github.glynch.jscene3d.textures.Texture;
import io.github.glynch.jscene3d.textures.TextureCoordinateOrigin;
import io.github.glynch.jscene3d.textures.TextureFilter;
import io.github.glynch.jscene3d.textures.TextureWrap;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Owns the JScene3D scene resources adapted from one headless Doom map. */
public final class DoomMapPresentation implements AutoCloseable {
    private static final float VERTICAL_FIELD_OF_VIEW = (float) Math.toRadians(60.0);
    private static final float NEAR_CLIP = 0.05F;
    private static final float FAR_CLIP = 256.0F;

    private final Scene scene;
    private final PerspectiveCamera camera;
    private final List<BufferGeometry> geometries;
    private final List<BasicMaterial> materials;
    private final List<Texture> textures;
    private boolean closed;

    /** Retains a fully constructed presentation and its owned resources. */
    private DoomMapPresentation(
            Scene scene,
            PerspectiveCamera camera,
            List<BufferGeometry> geometries,
            List<BasicMaterial> materials,
            List<Texture> textures) {
        this.scene = scene;
        this.camera = camera;
        this.geometries = geometries;
        this.materials = materials;
        this.textures = textures;
    }

    /**
     * Adapts headless geometry and imported images into an owned scene and player camera.
     *
     * @param geometry renderer-independent static map geometry
     * @param sourceMaterials imported wall textures and flats
     * @param aspectRatio positive viewport width divided by height
     * @return a presentation owning all created JScene3D resources
     */
    public static DoomMapPresentation create(
            DoomStaticGeometry geometry, DoomMapMaterials sourceMaterials, float aspectRatio) {
        DoomStaticGeometry validGeometry = Objects.requireNonNull(geometry, "geometry");
        DoomMapMaterials validMaterials = Objects.requireNonNull(sourceMaterials, "sourceMaterials");
        Scene scene = new Scene();
        scene.setBackground(Color.BLACK);
        PerspectiveCamera camera = createCamera(validGeometry.playerStart(), aspectRatio);
        List<BufferGeometry> geometries = new ArrayList<>(validGeometry.surfaces().size());
        List<BasicMaterial> materials = new ArrayList<>();
        List<Texture> textures = new ArrayList<>();
        Map<MaterialKey, BasicMaterial> materialCache = new LinkedHashMap<>();
        for (DoomSurface surface : validGeometry.surfaces()) {
            BufferGeometry bufferGeometry = createBufferGeometry(surface.mesh());
            BasicMaterial material = materialCache.computeIfAbsent(
                    materialKey(surface), key -> createMaterial(key, validMaterials, materials, textures));
            geometries.add(bufferGeometry);
            scene.add(new Mesh(bufferGeometry, material));
        }
        return new DoomMapPresentation(scene, camera, geometries, materials, textures);
    }

    /** Returns the adapted scene. */
    public Scene scene() {
        requireOpen();
        return scene;
    }

    /** Returns the player-view camera. */
    public PerspectiveCamera camera() {
        requireOpen();
        return camera;
    }

    /** Updates the camera projection for a resized viewport. */
    public void resize(float aspectRatio) {
        requireOpen();
        camera.setAspectRatio(aspectRatio);
    }

    /** Closes all adapter-owned geometries, materials, and textures. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        scene.clear();
        geometries.forEach(BufferGeometry::close);
        materials.forEach(BasicMaterial::close);
        textures.forEach(Texture::close);
        closed = true;
    }

    /** Creates a camera looking along the classic Doom thing angle. */
    private static PerspectiveCamera createCamera(DoomPlayerStart start, float aspectRatio) {
        PerspectiveCamera camera =
                new PerspectiveCamera(VERTICAL_FIELD_OF_VIEW, aspectRatio, NEAR_CLIP, FAR_CLIP);
        camera.setPosition(start.x(), start.eyeHeight(), start.z());
        float targetX = start.x() + (float) Math.cos(start.yawRadians());
        float targetZ = start.z() - (float) Math.sin(start.yawRadians());
        camera.lookAt(targetX, start.eyeHeight(), targetZ);
        return camera;
    }

    /** Copies one headless mesh description into JScene3D buffer ownership. */
    private static BufferGeometry createBufferGeometry(DoomMeshData mesh) {
        return BufferGeometry.builder()
                .positions(mesh.positions())
                .normals(mesh.normals())
                .uvs(mesh.textureCoordinates())
                .indices(mesh.indices())
                .build();
    }

    /** Creates and tracks one cached texture material. */
    private static BasicMaterial createMaterial(
            MaterialKey key,
            DoomMapMaterials sourceMaterials,
            List<BasicMaterial> ownedMaterials,
            List<Texture> ownedTextures) {
        DoomMaterial source = sourceMaterial(key, sourceMaterials);
        RgbaImage image = source.image();
        byte[] pixels = image.pixels();
        Texture texture = Texture.baseColor(image.width(), image.height(), pixels);
        texture.setCoordinateOrigin(TextureCoordinateOrigin.TOP_LEFT);
        texture.setHorizontalWrap(TextureWrap.REPEAT);
        texture.setVerticalWrap(TextureWrap.REPEAT);
        texture.setMinificationFilter(TextureFilter.NEAREST_MIPMAP_NEAREST);
        texture.setMagnificationFilter(TextureFilter.NEAREST);
        BasicMaterial material = new BasicMaterial();
        material.setColorMap(texture);
        if (hasTransparentPixel(pixels)) {
            material.setAlphaMode(AlphaMode.MASK);
            material.setAlphaCutoff(0.5F);
        }
        ownedTextures.add(texture);
        ownedMaterials.add(material);
        return material;
    }

    /** Resolves a source image using the surface role rather than a global name namespace. */
    private static DoomMaterial sourceMaterial(MaterialKey key, DoomMapMaterials materials) {
        Map<String, DoomMaterial> source =
                key.kind == DoomMaterial.Kind.FLAT ? materials.flats() : materials.wallTextures();
        DoomMaterial material = source.get(key.name);
        if (material == null) {
            throw new IllegalArgumentException("Missing presentation material: " + key.name);
        }
        return material;
    }

    /** Creates the cache key for one flat or wall texture. */
    private static MaterialKey materialKey(DoomSurface surface) {
        DoomMaterial.Kind kind = switch (surface.type()) {
            case FLOOR, CEILING -> DoomMaterial.Kind.FLAT;
            case MIDDLE_WALL, UPPER_WALL, LOWER_WALL, MASKED_MIDDLE_WALL ->
                DoomMaterial.Kind.WALL_TEXTURE;
        };
        return new MaterialKey(kind, surface.materialName());
    }

    /** Returns whether any RGBA pixel participates in alpha masking. */
    private static boolean hasTransparentPixel(byte[] pixels) {
        for (int alpha = 3; alpha < pixels.length; alpha += 4) {
            if (Byte.toUnsignedInt(pixels[alpha]) < 255) {
                return true;
            }
        }
        return false;
    }

    /** Rejects access after terminal resource closure. */
    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Doom map presentation is closed");
        }
    }

    /** Separates identical names in the flat and wall-texture namespaces. */
    private record MaterialKey(DoomMaterial.Kind kind, String name) {}
}
