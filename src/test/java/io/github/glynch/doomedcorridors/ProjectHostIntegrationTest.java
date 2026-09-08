/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.glynch.jscene3d.game.input.ActionSnapshot;
import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.game.input.InputWorldModule;
import io.github.glynch.jscene3d.game.input.ProjectInput;
import io.github.glynch.jscene3d.objects.BillboardAlignment;
import io.github.glynch.jscene3d.project.asset.AssetId;
import io.github.glynch.jscene3d.project.component.ComponentId;
import io.github.glynch.jscene3d.project.desktop.StandardProjectEnvironment;
import io.github.glynch.jscene3d.project.entity.EntityId;
import io.github.glynch.jscene3d.project.physics3d.CharacterBody3d;
import io.github.glynch.jscene3d.project.physics3d.CollisionRaycastHit3d;
import io.github.glynch.jscene3d.project.physics3d.Physics3dWorldModule;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.EntityInstantiationKind;
import io.github.glynch.jscene3d.project.runtime.HostedProject;
import io.github.glynch.jscene3d.project.runtime.ProjectHost;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntimeHost;
import io.github.glynch.jscene3d.project.spatial3d.BillboardRenderer3d;
import io.github.glynch.jscene3d.project.spatial3d.Material3dResource;
import io.github.glynch.jscene3d.project.spatial3d.Mesh3dResource;
import io.github.glynch.jscene3d.project.spatial3d.MeshRenderer3d;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dWorldModule;
import io.github.glynch.jscene3d.project.spatial3d.Transform3d;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises MAP01 publication and composition through the generic project-host boundary. */
final class ProjectHostIntegrationTest {
    private static final String ENGINE_VERSION = "0.1.0-SNAPSHOT";
    private static final String IMPORT_ID = "freedoom-map01";
    private static final String ACTOR_IMPORT_ID = "freedoom-map01-actors";
    private static final AssetId MAP_DEFINITION = AssetId.from("15a64477-b57f-3ae3-bf65-33cd6baab7b6");
    private static final AssetId ACTOR_MAP_DEFINITION = AssetId.from("d0bc35d1-a26e-3d3f-bc8b-9e909b4d5efe");
    private static final EntityId PLAYER_ENTITY = EntityId.from("0b295328-b5a3-4f41-9f34-e9b4abc430a7");
    private static final EntityId MAP_PLACEMENT = EntityId.from("9107e22b-adc5-4449-bd08-0e2066f50563");
    private static final EntityId ACTOR_MAP_PLACEMENT = EntityId.from("cff5c049-16fb-488f-aeb7-caad1843211f");
    private static final ComponentId PLAYER_TRANSFORM = ComponentId.from("3e940be7-e58d-4f3a-8b5e-e61c99c00904");
    private static final ComponentId PLAYER_BODY = ComponentId.from("4d8cae80-322d-4bdf-b8c0-5703699de177");
    private static final ComponentId VIEW_TRANSFORM = ComponentId.from("82b8ae6d-47e9-4df6-9d85-01a6fca09dc6");
    private static final ComponentId FIRST_RENDERER = componentId("maps/MAP01/root/mesh-renderers/00000");
    private static final ComponentId STATIC_COLLISION_SHAPE = componentId("maps/MAP01/root/collision/static-shape");
    private static final ComponentId ZOMBIEMAN_TRANSFORM =
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/transform");
    private static final ComponentId ZOMBIEMAN_BILLBOARD =
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/billboard");
    private static final ComponentId STIMPACK_PICKUP =
            actorComponentId("maps/MAP01/actors/definitions/stimpack/root/pickup");
    private static final ComponentId PLAYER_CONTROLLER = ComponentId.from("486f49a3-fe97-4a6c-b92d-533a1995493c");
    private static final InputAction MOVE = new InputAction("move");
    private static final InputAction LOOK = new InputAction("look");
    private static final InputAction TURN_RIGHT = new InputAction("turn-right");
    private static final Path PROJECT_ROOT = Path.of(".").toAbsolutePath().normalize();

    @TempDir
    private Path temporaryDirectory;

    /** Publishes MAP01, resolves its generated definition, and composes all resources into a world. */
    @Test
    void composesPublishedMapDefinitionAndReleasesResources() {
        Path cache = temporaryDirectory.resolve("import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);
        MeshRenderer3d renderer;
        Mesh3dResource mesh;
        Material3dResource material;

        try (HostedProject loaded = load(cache)) {
            Entity player = root(loaded, PLAYER_ENTITY);
            Entity placement = root(loaded, MAP_PLACEMENT);
            CharacterBody3d character =
                    player.component(PLAYER_BODY, CharacterBody3d.class).orElseThrow();
            Transform3d playerTransform =
                    player.component(PLAYER_TRANSFORM, Transform3d.class).orElseThrow();
            Transform3d viewTransform = player.children()
                    .getFirst()
                    .component(VIEW_TRANSFORM, Transform3d.class)
                    .orElseThrow();
            renderer = placement.component(FIRST_RENDERER, MeshRenderer3d.class).orElseThrow();
            mesh = renderer.mesh();
            material = renderer.material();

            assertThat(loaded.project().identity().id()).isEqualTo("io.github.glynch.doomed-corridors");
            assertThat(loaded.world().roots())
                    .extracting(entity -> entity.name().orElseThrow())
                    .containsExactly("Player", "MAP01 Geometry", "MAP01 Actors");
            assertThat(character.isClosed()).isFalse();
            assertThat(player.componentIds()).contains(PLAYER_CONTROLLER);
            assertThat(playerTransform.position().x()).isEqualTo(-6.0F);
            assertThat(playerTransform.position().y()).isEqualTo(0.875F);
            assertThat(playerTransform.position().z()).isEqualTo(6.0F);
            assertThat(viewTransform.position().y()).isEqualTo(0.40625F);
            assertThat(placement.instantiationKind()).isEqualTo(EntityInstantiationKind.PLACEMENT);
            assertThat(placement.instantiatedDefinition()).contains(MAP_DEFINITION);
            assertThat(placement.componentIds()).hasSize(82);
            assertThat(renderer.isVisible()).isTrue();
            assertThat(mesh.isClosed()).isFalse();
            assertThat(material.isClosed()).isFalse();
            assertThat(loaded.world().requireModule(Physics3dWorldModule.class).collisionObjectCount())
                    .isEqualTo(26);
            assertThat(loaded.world().requireModule(Physics3dWorldModule.class).collisionShapeCount())
                    .isEqualTo(26);
            assertThat(loaded.world().requireModule(Spatial3dWorldModule.class).isReadyToRender())
                    .isFalse();

            loaded.world().activate();

            assertThat(loaded.world().requireModule(Spatial3dWorldModule.class).isReadyToRender())
                    .isTrue();
            Physics3dWorldModule physics = loaded.world().requireModule(Physics3dWorldModule.class);
            CollisionRaycastHit3d floor = physics.raycast(
                            new Vector3f(-5.25F, 2.0F, 6.0F), new Vector3f(0.0F, -1.0F, 0.0F), 4.0F)
                    .orElseThrow();
            assertThat(floor.shape().componentId()).isEqualTo(STATIC_COLLISION_SHAPE);
            assertThat(floor.distance()).isCloseTo(2.0F, within(1.0E-5F));
            assertThat(floor.point(new Vector3f()).y).isCloseTo(0.0F, within(1.0E-5F));
        }

        assertThat(renderer.isClosed()).isTrue();
        assertThat(mesh.isClosed()).isTrue();
        assertThat(material.isClosed()).isTrue();
    }

    /** Composes all visible MAP01 things as placements of reusable generic billboard definitions. */
    @Test
    void composesPublishedActorDefinitions() {
        Path cache = temporaryDirectory.resolve("actor-import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);
        BillboardRenderer3d billboard;

        try (HostedProject loaded = load(cache)) {
            Entity actors = root(loaded, ACTOR_MAP_PLACEMENT);
            Entity zombieman = actors.children().stream()
                    .filter(entity -> entity.name().orElseThrow().startsWith("Zombieman "))
                    .findFirst()
                    .orElseThrow();
            Transform3d actorTransform =
                    zombieman.component(ZOMBIEMAN_TRANSFORM, Transform3d.class).orElseThrow();
            billboard = zombieman
                    .component(ZOMBIEMAN_BILLBOARD, BillboardRenderer3d.class)
                    .orElseThrow();

            assertThat(actors.instantiationKind()).isEqualTo(EntityInstantiationKind.PLACEMENT);
            assertThat(actors.instantiatedDefinition()).contains(ACTOR_MAP_DEFINITION);
            assertThat(actors.children()).hasSize(119);
            assertThat(zombieman.instantiationKind()).isEqualTo(EntityInstantiationKind.PLACEMENT);
            assertThat(zombieman.componentIds()).containsExactly(ZOMBIEMAN_TRANSFORM, ZOMBIEMAN_BILLBOARD);
            assertThat(actorTransform.position().x()).isFinite();
            assertThat(actorTransform.position().y()).isFinite();
            assertThat(actorTransform.position().z()).isFinite();
            assertThat(billboard.alignment()).isEqualTo(BillboardAlignment.CYLINDRICAL);
            assertThat(billboard.size().x()).isPositive();
            assertThat(billboard.size().y()).isPositive();
            assertThat(billboard.isVisible()).isTrue();
        }

        assertThat(billboard.isClosed()).isTrue();
    }

    /** Collects one useful imported stimpack through its authored physics-signal connection. */
    @Test
    void collectsUsefulPickupThroughHostedWorld() {
        Path cache = temporaryDirectory.resolve("useful-pickup-import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);

        try (HostedProject loaded = load(cache)) {
            Entity player = root(loaded, PLAYER_ENTITY);
            DoomPlayerState state = player.component(DoomPlayerState.COMPONENT_ID, DoomPlayerState.class)
                    .orElseThrow();
            Transform3d playerTransform =
                    player.component(PLAYER_TRANSFORM, Transform3d.class).orElseThrow();
            Entity actors = root(loaded, ACTOR_MAP_PLACEMENT);
            Entity stimpack = actor(actors, "Stimpack 86");
            DoomPickup pickup =
                    stimpack.component(STIMPACK_PICKUP, DoomPickup.class).orElseThrow();
            ProjectInput input = (ProjectInput) loaded.world().requireModule(InputWorldModule.class);
            state.damage(10);

            assertThat(state.health()).isEqualTo(90);
            assertThat(stimpack.isDestroyed()).isFalse();

            loaded.world().activate();
            input.publish(
                    ActionSnapshot.builder().axis2d(MOVE, -1.0F, 1.0F / 6.0F).build());
            advanceFixed(loaded, 40);

            assertThat(state.health())
                    .as("player position %s", playerTransform.position())
                    .isEqualTo(100);
            assertThat(pickup.isCollected()).isTrue();
            assertThat(stimpack.isDestroyed()).isTrue();
            assertThat(actors.children()).hasSize(118);
        }
    }

    /** Leaves an ordinary health pickup present while the player is already at its configured limit. */
    @Test
    void retainsPickupWhichCannotChangePlayerState() {
        Path cache = temporaryDirectory.resolve("limited-pickup-import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);

        try (HostedProject loaded = load(cache)) {
            Entity player = root(loaded, PLAYER_ENTITY);
            DoomPlayerState state = player.component(DoomPlayerState.COMPONENT_ID, DoomPlayerState.class)
                    .orElseThrow();
            Entity actors = root(loaded, ACTOR_MAP_PLACEMENT);
            Entity stimpack = actor(actors, "Stimpack 86");
            DoomPickup pickup =
                    stimpack.component(STIMPACK_PICKUP, DoomPickup.class).orElseThrow();
            ProjectInput input = (ProjectInput) loaded.world().requireModule(InputWorldModule.class);

            loaded.world().activate();
            input.publish(
                    ActionSnapshot.builder().axis2d(MOVE, -1.0F, 1.0F / 6.0F).build());
            advanceFixed(loaded, 40);

            assertThat(state.health()).isEqualTo(100);
            assertThat(pickup.isCollected()).isFalse();
            assertThat(stimpack.isDestroyed()).isFalse();
            assertThat(actors.children()).hasSize(119);
        }
    }

    /** Moves the composed player from semantic input while its child camera follows the resolved body pose. */
    @Test
    void movesHostedPlayerAndAttachedCamera() {
        Path cache = temporaryDirectory.resolve("movement-import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);

        try (HostedProject loaded = load(cache)) {
            Entity player = root(loaded, PLAYER_ENTITY);
            CharacterBody3d character =
                    player.component(PLAYER_BODY, CharacterBody3d.class).orElseThrow();
            Transform3d playerTransform =
                    player.component(PLAYER_TRANSFORM, Transform3d.class).orElseThrow();
            Transform3d viewTransform = player.children()
                    .getFirst()
                    .component(VIEW_TRANSFORM, Transform3d.class)
                    .orElseThrow();
            ProjectInput input = (ProjectInput) loaded.world().requireModule(InputWorldModule.class);
            Vector3f startingPosition = new Vector3f(playerTransform.position());
            Vector3f startingViewPosition = viewTransform.worldMatrix().getTranslation(new Vector3f());

            loaded.world().activate();
            input.publish(ActionSnapshot.builder().axis2d(MOVE, 0.0F, 1.0F).build());
            loaded.world().advanceFixed(Duration.ofMillis(100));

            Vector3f movedPosition = new Vector3f(playerTransform.position());
            Vector3f movedViewPosition = viewTransform.worldMatrix().getTranslation(new Vector3f());
            assertThat(movedPosition.x).isGreaterThan(startingPosition.x);
            assertThat(movedPosition.z).isCloseTo(startingPosition.z, within(0.01F));
            assertThat(movedViewPosition.x - startingViewPosition.x)
                    .isCloseTo(movedPosition.x - startingPosition.x, within(0.0001F));

            float startingYaw = viewTransform.orientation().getEulerAnglesYXZ(new Vector3f()).y;
            input.publish(ActionSnapshot.builder()
                    .axis2d(LOOK, 0.2F, 0.0F)
                    .pointerDelta(100.0, 0.0)
                    .build());
            loaded.world().advanceFixed(Duration.ofMillis(25));
            input.publish(input.snapshot().heldOnly());
            loaded.world().advanceFixed(Duration.ofMillis(25));

            float pointerYaw = viewTransform.orientation().getEulerAnglesYXZ(new Vector3f()).y;
            assertThat(pointerYaw).isCloseTo(startingYaw - 0.15F, within(0.0001F));

            input.publish(ActionSnapshot.builder().down(TURN_RIGHT).build());
            loaded.world().advanceFixed(Duration.ofMillis(100));

            float keyboardYaw = viewTransform.orientation().getEulerAnglesYXZ(new Vector3f()).y;
            assertThat(keyboardYaw).isCloseTo(pointerYaw - (float) Math.toRadians(18.0), within(0.0001F));

            assertThat(character.isGrounded()).isTrue();
        }
    }

    /** Stops at MAP01 collision while preserving the tangential component of diagonal movement. */
    @Test
    void blocksAndSlidesHostedPlayerAtMapWall() {
        Path cache = temporaryDirectory.resolve("wall-import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);

        try (HostedProject loaded = load(cache)) {
            Entity player = root(loaded, PLAYER_ENTITY);
            Transform3d playerTransform =
                    player.component(PLAYER_TRANSFORM, Transform3d.class).orElseThrow();
            ProjectInput input = (ProjectInput) loaded.world().requireModule(InputWorldModule.class);
            Physics3dWorldModule physics = loaded.world().requireModule(Physics3dWorldModule.class);

            loaded.world().activate();
            CollisionRaycastHit3d wall = physics.raycast(
                            new Vector3f(-5.4F, 0.875F, 6.0F), new Vector3f(1.0F, 0.0F, 0.0F), 64.0F)
                    .orElseThrow();
            assertThat(wall.shape().componentId()).isEqualTo(STATIC_COLLISION_SHAPE);
            float wallLimit = wall.point(new Vector3f()).x - 0.4375F;

            input.publish(ActionSnapshot.builder().axis2d(MOVE, 0.0F, 1.0F).build());
            advanceFixed(loaded, 160);
            assertThat(playerTransform.position().x()).isCloseTo(wallLimit, within(0.05F));
            float blockedX = playerTransform.position().x();
            float beforeSlideZ = playerTransform.position().z();

            input.publish(ActionSnapshot.builder().axis2d(MOVE, 1.0F, 1.0F).build());
            advanceFixed(loaded, 10);

            assertThat(playerTransform.position().x()).isCloseTo(blockedX, within(0.05F));
            assertThat(Math.abs(playerTransform.position().z() - beforeSlideZ)).isGreaterThan(0.25F);
        }
    }

    /** Loads the authored project through the same generic host used by desktop exports. */
    private static HostedProject load(Path cache) {
        ProjectHost host = new ProjectRuntimeHost(
                ENGINE_VERSION,
                ProjectHostIntegrationTest.class.getClassLoader(),
                new StandardProjectEnvironment(cache));
        return host.load(PROJECT_ROOT);
    }

    /** Finds one authored world root by its stable placement identity. */
    private static Entity root(HostedProject loaded, EntityId authoredId) {
        return loaded.world().roots().stream()
                .filter(entity -> entity.authoredId().equals(authoredId))
                .findFirst()
                .orElseThrow();
    }

    /** Finds one imported actor placement by its exact provider display name. */
    private static Entity actor(Entity actors, String name) {
        return actors.children().stream()
                .filter(entity -> entity.name().orElseThrow().equals(name))
                .findFirst()
                .orElseThrow();
    }

    /** Advances the active hosted world by a deterministic number of 25 ms fixed steps. */
    private static void advanceFixed(HostedProject loaded, int steps) {
        for (int step = 0; step < steps; step++) {
            loaded.world().advanceFixed(Duration.ofMillis(25));
        }
    }

    /** Reproduces the importer's stable source-derived component identity contract. */
    private static ComponentId componentId(String locator) {
        UUID id = UUID.nameUUIDFromBytes((IMPORT_ID + ':' + locator).getBytes(StandardCharsets.UTF_8));
        return new ComponentId(id);
    }

    /** Reproduces the actor importer's stable source-derived component identity contract. */
    private static ComponentId actorComponentId(String locator) {
        UUID id = UUID.nameUUIDFromBytes((ACTOR_IMPORT_ID + ':' + locator).getBytes(StandardCharsets.UTF_8));
        return new ComponentId(id);
    }
}
