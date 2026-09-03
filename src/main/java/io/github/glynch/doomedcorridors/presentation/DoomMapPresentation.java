/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import io.github.glynch.doomedcorridors.actor.DoomActor;
import io.github.glynch.doomedcorridors.actor.DoomActorSprite;
import io.github.glynch.doomedcorridors.actor.DoomActorSprites;
import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.material.DoomMaterial;
import io.github.glynch.doomedcorridors.material.RgbaImage;
import io.github.glynch.doomedcorridors.world.DoomMeshData;
import io.github.glynch.doomedcorridors.world.DoomPlayerStart;
import io.github.glynch.doomedcorridors.world.DoomPlayerState;
import io.github.glynch.doomedcorridors.world.DoomStaticGeometry;
import io.github.glynch.doomedcorridors.world.DoomSurface;
import io.github.glynch.jscene3d.cameras.PerspectiveCamera;
import io.github.glynch.jscene3d.geometries.BufferGeometry;
import io.github.glynch.jscene3d.materials.AlphaMode;
import io.github.glynch.jscene3d.materials.BasicMaterial;
import io.github.glynch.jscene3d.math.Color;
import io.github.glynch.jscene3d.objects.Billboard;
import io.github.glynch.jscene3d.objects.BillboardAlignment;
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
import java.util.Optional;

/** Owns the JScene3D scene resources adapted from one headless Doom map. */
public final class DoomMapPresentation implements AutoCloseable {
    private static final float VERTICAL_FIELD_OF_VIEW = (float) Math.toRadians(60.0);
    private static final float NEAR_CLIP = 0.05F;
    private static final float FAR_CLIP = 256.0F;

    private final Scene scene;
    private final PerspectiveCamera camera;
    private final OwnedResources resources;
    private final Map<Integer, ActorVisual> actorVisuals;
    private final Map<String, BasicMaterial> spriteMaterials;
    private final Optional<DoomCombatAssets> combatAssets;
    private boolean closed;

    /** Retains a fully constructed presentation and its owned resources. */
    private DoomMapPresentation(
            Scene scene,
            PerspectiveCamera camera,
            OwnedResources resources,
            Map<Integer, ActorVisual> actorVisuals,
            Map<String, BasicMaterial> spriteMaterials,
            Optional<DoomCombatAssets> combatAssets) {
        this.scene = scene;
        this.camera = camera;
        this.resources = resources;
        this.actorVisuals = actorVisuals;
        this.spriteMaterials = spriteMaterials;
        this.combatAssets = combatAssets;
    }

    /**
     * Adapts headless geometry and imported images into an owned scene and player camera.
     *
     * @param geometry renderer-independent static map geometry
     * @param sourceMaterials imported wall textures and flats
     * @param actors visible renderer-independent map actors
     * @param sourceSprites imported actor spawn sprites
     * @param aspectRatio positive viewport width divided by height
     * @return a presentation owning all created JScene3D resources
     */
    public static DoomMapPresentation create(
            DoomStaticGeometry geometry,
            DoomMapMaterials sourceMaterials,
            List<DoomActor> actors,
            DoomActorSprites sourceSprites,
            float aspectRatio) {
        return createInternal(
                geometry, sourceMaterials, actors, sourceSprites, Optional.empty(), aspectRatio);
    }

    /**
     * Adapts a map plus decoded combat frames into an owned, event-updatable scene.
     *
     * @param geometry renderer-independent static map geometry
     * @param sourceMaterials imported wall textures and flats
     * @param actors visible renderer-independent map actors
     * @param sourceSprites imported actor spawn sprites
     * @param sourceCombatAssets imported pain and death frames
     * @param aspectRatio positive viewport width divided by height
     * @return a presentation owning all created JScene3D resources
     */
    public static DoomMapPresentation create(
            DoomStaticGeometry geometry,
            DoomMapMaterials sourceMaterials,
            List<DoomActor> actors,
            DoomActorSprites sourceSprites,
            DoomCombatAssets sourceCombatAssets,
            float aspectRatio) {
        return createInternal(geometry, sourceMaterials, actors, sourceSprites,
                Optional.of(Objects.requireNonNull(sourceCombatAssets, "sourceCombatAssets")), aspectRatio);
    }

    /** Builds the shared map presentation for optional combat-frame support. */
    private static DoomMapPresentation createInternal(
            DoomStaticGeometry geometry,
            DoomMapMaterials sourceMaterials,
            List<DoomActor> actors,
            DoomActorSprites sourceSprites,
            Optional<DoomCombatAssets> sourceCombatAssets,
            float aspectRatio) {
        DoomStaticGeometry validGeometry = Objects.requireNonNull(geometry, "geometry");
        DoomMapMaterials validMaterials = Objects.requireNonNull(sourceMaterials, "sourceMaterials");
        List<DoomActor> validActors = List.copyOf(Objects.requireNonNull(actors, "actors"));
        DoomActorSprites validSprites = Objects.requireNonNull(sourceSprites, "sourceSprites");
        Scene scene = new Scene();
        scene.setBackground(Color.BLACK);
        PerspectiveCamera camera = createCamera(validGeometry.playerStart(), aspectRatio);
        OwnedResources resources = new OwnedResources(validGeometry.surfaces().size(), validActors.size());
        Map<MaterialKey, BasicMaterial> materialCache = new LinkedHashMap<>();
        for (DoomSurface surface : validGeometry.surfaces()) {
            BufferGeometry bufferGeometry = createBufferGeometry(surface.mesh());
            BasicMaterial material = materialCache.computeIfAbsent(
                    materialKey(surface), key -> createMaterial(
                            key, validMaterials, resources.materials, resources.textures));
            resources.geometries.add(bufferGeometry);
            scene.add(new Mesh(bufferGeometry, material));
        }
        Map<String, BasicMaterial> spriteMaterialCache = new LinkedHashMap<>();
        Map<Integer, ActorVisual> actorVisuals = new LinkedHashMap<>();
        for (DoomActor actor : validActors) {
            ActorVisual visual = addActor(
                    scene,
                    actor,
                    validSprites,
                    spriteMaterialCache,
                    resources.billboards,
                    resources.materials,
                    resources.textures);
            if (visual != null) {
                actorVisuals.put(actor.thingIndex(), visual);
            }
        }
        return new DoomMapPresentation(
                scene, camera, resources, Map.copyOf(actorVisuals), spriteMaterialCache, sourceCombatAssets);
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

    /** Applies the latest headless player position and view orientation to the camera. */
    public void applyPlayerState(DoomPlayerState player) {
        requireOpen();
        applyCamera(camera, Objects.requireNonNull(player, "player"));
    }

    /** Applies current pain/death frame overrides to indexed actor billboards. */
    public void applyCombatState(DoomCombatPresentationState state) {
        requireOpen();
        DoomCombatPresentationState validState = Objects.requireNonNull(state, "state");
        DoomCombatAssets assets = combatAssets.orElseThrow(
                () -> new IllegalStateException("Combat assets were not supplied to this presentation"));
        actorVisuals.forEach((thingIndex, visual) -> {
            DoomActorSprite sprite = validState.actorFrame(thingIndex)
                    .map(assets::image)
                    .orElse(visual.idleSprite);
            BasicMaterial material = spriteMaterials.computeIfAbsent(
                    sprite.lumpName(), ignored -> createSpriteMaterial(
                            sprite, resources.materials, resources.textures));
            visual.apply(sprite, material);
        });
    }

    /** Closes all adapter-owned geometries, materials, and textures. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        scene.clear();
        resources.billboards.forEach(Billboard::close);
        resources.geometries.forEach(BufferGeometry::close);
        resources.materials.forEach(BasicMaterial::close);
        resources.textures.forEach(Texture::close);
        closed = true;
    }

    /** Adds one grounded cylindrical billboard when its imported frame is available. */
    private static ActorVisual addActor(
            Scene scene,
            DoomActor actor,
            DoomActorSprites sprites,
            Map<String, BasicMaterial> materialCache,
            List<Billboard> billboards,
            List<BasicMaterial> ownedMaterials,
            List<Texture> ownedTextures) {
        String frame = actor.definition().spriteFrame().orElseThrow();
        DoomActorSprite sprite = sprites.sprite(frame).orElse(null);
        if (sprite == null) {
            return null;
        }
        BasicMaterial material = materialCache.computeIfAbsent(
                sprite.lumpName(), ignored -> createSpriteMaterial(sprite, ownedMaterials, ownedTextures));
        Billboard billboard = new Billboard(material);
        billboard.setAlignment(BillboardAlignment.CYLINDRICAL);
        billboard.setPosition(actor.x(), actor.floorHeight(), actor.z());
        applyActorSprite(billboard, sprite, material);
        billboards.add(billboard);
        scene.add(billboard);
        return new ActorVisual(billboard, sprite);
    }

    /** Applies patch-dependent material, anchor, and size to one actor billboard. */
    private static void applyActorSprite(
            Billboard billboard, DoomActorSprite sprite, BasicMaterial material) {
        billboard.setMaterial(material);
        billboard.setAnchor(
                sprite.leftOffset() / (float) sprite.image().width(),
                (sprite.image().height() - sprite.topOffset()) / (float) sprite.image().height());
        billboard.setScale(world(sprite.image().width()), world(sprite.image().height()), 1.0F);
    }

    /** Creates a camera looking along the classic Doom thing angle. */
    private static PerspectiveCamera createCamera(DoomPlayerStart start, float aspectRatio) {
        PerspectiveCamera camera =
                new PerspectiveCamera(VERTICAL_FIELD_OF_VIEW, aspectRatio, NEAR_CLIP, FAR_CLIP);
        applyCamera(
                camera,
                new DoomPlayerState(start.x(), start.eyeHeight(), start.z(), start.yawRadians(), 0.0F));
        return camera;
    }

    /** Maps headless yaw and pitch onto the camera's right-handed look direction. */
    private static void applyCamera(PerspectiveCamera camera, DoomPlayerState player) {
        float horizontal = (float) Math.cos(player.pitchRadians());
        float directionX = (float) Math.cos(player.yawRadians()) * horizontal;
        float directionY = (float) Math.sin(player.pitchRadians());
        float directionZ = -(float) Math.sin(player.yawRadians()) * horizontal;
        camera.setPosition(player.x(), player.eyeHeight(), player.z());
        camera.lookAt(
                player.x() + directionX,
                player.eyeHeight() + directionY,
                player.z() + directionZ);
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
        return createImageMaterial(
                source.image(),
                TextureWrap.REPEAT,
                TextureCoordinateOrigin.TOP_LEFT,
                ownedMaterials,
                ownedTextures);
    }

    /** Creates one alpha-masked, edge-clamped material for an actor sprite. */
    private static BasicMaterial createSpriteMaterial(
            DoomActorSprite sprite,
            List<BasicMaterial> ownedMaterials,
            List<Texture> ownedTextures) {
        return createImageMaterial(
                sprite.image(),
                TextureWrap.CLAMP_TO_EDGE,
                TextureCoordinateOrigin.BOTTOM_LEFT,
                ownedMaterials,
                ownedTextures);
    }

    /** Creates and tracks a nearest-filtered unlit image material. */
    private static BasicMaterial createImageMaterial(
            RgbaImage image,
            TextureWrap wrap,
            TextureCoordinateOrigin coordinateOrigin,
            List<BasicMaterial> ownedMaterials,
            List<Texture> ownedTextures) {
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
        ownedTextures.add(texture);
        ownedMaterials.add(material);
        return material;
    }

    /** Converts classic pixel/map units to JScene3D world units. */
    private static float world(float value) {
        return value / 32.0F;
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

    /** Mutable grouped ownership retained solely by the presentation lifecycle. */
    private static final class OwnedResources {
        private final List<BufferGeometry> geometries;
        private final List<Billboard> billboards;
        private final List<BasicMaterial> materials = new ArrayList<>();
        private final List<Texture> textures = new ArrayList<>();

        private OwnedResources(int surfaceCount, int actorCount) {
            geometries = new ArrayList<>(surfaceCount);
            billboards = new ArrayList<>(actorCount);
        }
    }

    /** One actor billboard paired with its provider-selected idle sprite. */
    private static final class ActorVisual {
        private final Billboard billboard;
        private final DoomActorSprite idleSprite;

        private ActorVisual(Billboard billboard, DoomActorSprite idleSprite) {
            this.billboard = billboard;
            this.idleSprite = idleSprite;
        }

        private void apply(DoomActorSprite sprite, BasicMaterial material) {
            applyActorSprite(billboard, sprite, material);
        }
    }
}
