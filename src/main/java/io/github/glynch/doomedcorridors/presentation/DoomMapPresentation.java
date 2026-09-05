/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import io.github.glynch.doomedcorridors.actor.DoomActor;
import io.github.glynch.doomedcorridors.actor.DoomActorSprite;
import io.github.glynch.doomedcorridors.actor.DoomActorSprites;
import io.github.glynch.doomedcorridors.combat.DoomCombatantState;
import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.world.DoomPlayerStart;
import io.github.glynch.doomedcorridors.world.DoomPlayerState;
import io.github.glynch.doomedcorridors.world.DoomStaticGeometry;
import io.github.glynch.doomedcorridors.world.DoomSurface;
import io.github.glynch.doomedcorridors.world.DoomUnits;
import io.github.glynch.jscene3d.cameras.PerspectiveCamera;
import io.github.glynch.jscene3d.materials.BasicMaterial;
import io.github.glynch.jscene3d.math.Color;
import io.github.glynch.jscene3d.objects.Billboard;
import io.github.glynch.jscene3d.objects.BillboardAlignment;
import io.github.glynch.jscene3d.objects.Mesh;
import io.github.glynch.jscene3d.scenes.Scene;
import io.github.glynch.jscene3d.textures.TextureCoordinateOrigin;
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
    private final DoomPresentationResources resources;
    private final List<Billboard> billboards;
    private final Map<Integer, ActorVisual> actorVisuals;
    private final Map<String, BasicMaterial> spriteMaterials;
    private final Optional<DoomCombatAssets> combatAssets;
    private boolean closed;

    /** Retains a fully constructed presentation and its owned resources. */
    private DoomMapPresentation(
            Scene scene,
            PerspectiveCamera camera,
            DoomPresentationResources resources,
            List<Billboard> billboards,
            Map<Integer, ActorVisual> actorVisuals,
            Map<String, BasicMaterial> spriteMaterials,
            Optional<DoomCombatAssets> combatAssets) {
        this.scene = scene;
        this.camera = camera;
        this.resources = resources;
        this.billboards = billboards;
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
        DoomPresentationResources resources =
                new DoomPresentationResources(validGeometry.surfaces().size());
        List<Billboard> billboards = new ArrayList<>(validActors.size());
        Map<DoomPresentationResources.MapMaterialKey, BasicMaterial> materialCache =
                new LinkedHashMap<>();
        for (DoomSurface surface : validGeometry.surfaces()) {
            var bufferGeometry = resources.createGeometry(surface.mesh());
            BasicMaterial material = materialCache.computeIfAbsent(
                    DoomPresentationResources.materialKey(surface),
                    key -> resources.createMapMaterial(key, validMaterials));
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
                    billboards,
                    resources);
            if (visual != null) {
                actorVisuals.put(actor.thingIndex(), visual);
            }
        }
        return new DoomMapPresentation(
                scene,
                camera,
                resources,
                billboards,
                Map.copyOf(actorVisuals),
                spriteMaterialCache,
                sourceCombatAssets);
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
        DoomPlayerCamera.apply(camera, Objects.requireNonNull(player, "player"));
    }

    /** Applies current pain/death frame overrides to indexed actor billboards. */
    public void applyCombatState(DoomCombatPresentationState state) {
        requireOpen();
        DoomCombatPresentationState validState = Objects.requireNonNull(state, "state");
        DoomCombatAssets assets = combatAssets.orElseThrow(
                () -> new IllegalStateException("Combat assets were not supplied to this presentation"));
        actorVisuals.forEach((thingIndex, visual) -> {
            visual.setVisible(!validState.isPickupCollected(thingIndex));
            validState.combatant(thingIndex).ifPresent(visual::move);
            DoomActorSprite sprite = validState.actorFrame(thingIndex)
                    .map(assets::image)
                    .orElse(visual.idleSprite);
            BasicMaterial material = spriteMaterials.computeIfAbsent(
                    sprite.lumpName(), ignored -> createSpriteMaterial(sprite, resources));
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
        billboards.forEach(Billboard::close);
        resources.close();
        closed = true;
    }

    /** Adds one grounded cylindrical billboard when its imported frame is available. */
    private static ActorVisual addActor(
            Scene scene,
            DoomActor actor,
            DoomActorSprites sprites,
            Map<String, BasicMaterial> materialCache,
            List<Billboard> billboards,
            DoomPresentationResources resources) {
        String frame = actor.definition().spriteFrame().orElseThrow();
        DoomActorSprite sprite = sprites.sprite(frame).orElse(null);
        if (sprite == null) {
            return null;
        }
        BasicMaterial material = materialCache.computeIfAbsent(
                sprite.lumpName(), ignored -> createSpriteMaterial(sprite, resources));
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
        billboard.setScale(
                DoomUnits.toWorld(sprite.image().width()),
                DoomUnits.toWorld(sprite.image().height()),
                1.0F);
    }

    /** Creates a camera looking along the classic Doom thing angle. */
    private static PerspectiveCamera createCamera(DoomPlayerStart start, float aspectRatio) {
        PerspectiveCamera camera =
                new PerspectiveCamera(VERTICAL_FIELD_OF_VIEW, aspectRatio, NEAR_CLIP, FAR_CLIP);
        DoomPlayerCamera.apply(
                camera,
                new DoomPlayerState(start.x(), start.eyeHeight(), start.z(), start.yawRadians(), 0.0F));
        return camera;
    }

    /** Creates one alpha-masked, edge-clamped material for an actor sprite. */
    private static BasicMaterial createSpriteMaterial(
            DoomActorSprite sprite, DoomPresentationResources resources) {
        return resources.createImageMaterial(
                sprite.image(),
                TextureWrap.CLAMP_TO_EDGE,
                TextureCoordinateOrigin.BOTTOM_LEFT);
    }

    /** Rejects access after terminal resource closure. */
    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Doom map presentation is closed");
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

        /** Applies one current simulation placement. */
        private void move(DoomCombatantState combatant) {
            billboard.setPosition(combatant.x(), combatant.floorHeight(), combatant.z());
        }

        /** Includes or excludes this actor from visible scene traversal. */
        private void setVisible(boolean visible) {
            billboard.setVisible(visible);
        }

        /** Applies the selected frame material and dimensions. */
        private void apply(DoomActorSprite sprite, BasicMaterial material) {
            applyActorSprite(billboard, sprite, material);
        }
    }
}
