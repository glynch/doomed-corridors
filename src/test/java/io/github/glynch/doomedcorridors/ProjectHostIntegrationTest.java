/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsRuntimeTypes;
import io.github.glynch.jscene3d.doom.runtime.DoomCollisionCategories;
import io.github.glynch.jscene3d.doom.runtime.DoomDoor;
import io.github.glynch.jscene3d.doom.runtime.DoomDoorDescriptors;
import io.github.glynch.jscene3d.doom.runtime.DoomFloor;
import io.github.glynch.jscene3d.doom.runtime.DoomFloorDescriptors;
import io.github.glynch.jscene3d.game.application.ApplicationCommand;
import io.github.glynch.jscene3d.game.application.ApplicationControl;
import io.github.glynch.jscene3d.game.input.ActionSnapshot;
import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.game.input.InputWorldModule;
import io.github.glynch.jscene3d.game.input.ProjectInput;
import io.github.glynch.jscene3d.game.presentation.PositionalSoundAttenuation;
import io.github.glynch.jscene3d.game.presentation.ScreenNumber;
import io.github.glynch.jscene3d.objects.BillboardAlignment;
import io.github.glynch.jscene3d.project.asset.AssetId;
import io.github.glynch.jscene3d.project.component.ComponentId;
import io.github.glynch.jscene3d.project.entity.EntityId;
import io.github.glynch.jscene3d.project.physics3d.CharacterBody3d;
import io.github.glynch.jscene3d.project.physics3d.CollisionRaycastHit3d;
import io.github.glynch.jscene3d.project.physics3d.CollisionShape3d;
import io.github.glynch.jscene3d.project.physics3d.Physics3dWorldModule;
import io.github.glynch.jscene3d.project.physics3d.TriangleMeshCollisionShape3dResource;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.EntityInstantiationKind;
import io.github.glynch.jscene3d.project.runtime.HostedProject;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntimeHost;
import io.github.glynch.jscene3d.project.spatial3d.BillboardRenderer3d;
import io.github.glynch.jscene3d.project.spatial3d.Material3dResource;
import io.github.glynch.jscene3d.project.spatial3d.Mesh3dResource;
import io.github.glynch.jscene3d.project.spatial3d.MeshRenderer3d;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dWorldModule;
import io.github.glynch.jscene3d.project.spatial3d.Transform3d;
import io.github.glynch.jscene3d.project.spatial3d.descriptor.Spatial3dDescriptors;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.joml.Quaternionf;
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
    private static final EntityId PLAYER_CONTROLS = EntityId.from("5c76aa79-5270-4b2a-83c2-7451c50a7c40");
    private static final EntityId MAP_PLACEMENT = EntityId.from("9107e22b-adc5-4449-bd08-0e2066f50563");
    private static final EntityId ACTOR_MAP_PLACEMENT = EntityId.from("cff5c049-16fb-488f-aeb7-caad1843211f");
    private static final EntityId PLAYER_HUD = EntityId.from("c4f5ca56-661a-424d-aec2-b423d299af47");
    private static final EntityId GAME_OVER = EntityId.from("ae31f849-77f6-4269-be03-bc3d90937324");
    private static final EntityId RESTART_CURSOR = EntityId.from("a02bfece-c08b-4044-9f9f-1c5d27ccfe82");
    private static final EntityId MAIN_MENU_CURSOR = EntityId.from("7cea67f4-5e36-4256-a658-a27056d421d0");
    private static final EntityId MAIN_MENU = EntityId.from("eb1af3a2-fe1b-4688-ac54-e930e354e23c");
    private static final ComponentId PLAYER_TRANSFORM = ComponentId.from("3e940be7-e58d-4f3a-8b5e-e61c99c00904");
    private static final ComponentId PLAYER_BODY = ComponentId.from("4d8cae80-322d-4bdf-b8c0-5703699de177");
    private static final ComponentId VIEW_TRANSFORM = ComponentId.from("82b8ae6d-47e9-4df6-9d85-01a6fca09dc6");
    private static final ComponentId FIRST_RENDERER = componentId("maps/MAP01/root/mesh-renderers/00000");
    private static final ComponentId STATIC_COLLISION_SHAPE = componentId("maps/MAP01/root/collision/static-shape");
    private static final ComponentId MOVING_FLOOR_SHAPE = componentId("maps/MAP01/floors/00034/collision/shape");
    private static final ComponentId ZOMBIEMAN_TRANSFORM =
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/transform");
    private static final ComponentId ZOMBIEMAN_BILLBOARD =
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/billboard");
    private static final ComponentId ZOMBIEMAN_STATE =
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/combatant-state");
    private static final ComponentId ZOMBIEMAN_SHAPE =
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/combatant-shape");
    private static final ComponentId ZOMBIEMAN_BODY =
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/combatant-body");
    private static final ComponentId ZOMBIEMAN_BEHAVIOR =
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/enemy-behavior");
    private static final List<ComponentId> ZOMBIEMAN_WALK_FRAMES = List.of(
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/combatant-presentation/walk/0"),
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/combatant-presentation/walk/1"),
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/combatant-presentation/walk/2"),
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/combatant-presentation/walk/3"));
    private static final List<ComponentId> ZOMBIEMAN_ATTACK_FRAMES = List.of(
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/combatant-presentation/attack/0"),
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/combatant-presentation/attack/1"),
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/combatant-presentation/attack/2"));
    private static final ComponentId ZOMBIEMAN_PAIN_FRAME =
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/combatant-presentation/pain/0");
    private static final List<ComponentId> ZOMBIEMAN_DEATH_FRAMES = List.of(
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/combatant-presentation/death/0"),
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/combatant-presentation/death/1"),
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/combatant-presentation/death/2"),
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/combatant-presentation/death/3"),
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/combatant-presentation/death/4"));
    private static final ComponentId ZOMBIEMAN_PRESENTATION =
            actorComponentId("maps/MAP01/actors/definitions/zombieman/root/combatant-presentation");
    private static final ComponentId ENEMY_TARGET = actorComponentId("maps/MAP01/actors/root/enemy-target");
    private static final ComponentId STIMPACK_PICKUP =
            actorComponentId("maps/MAP01/actors/definitions/stimpack/root/pickup");
    private static final ComponentId PLAYER_CONTROLLER = ComponentId.from("486f49a3-fe97-4a6c-b92d-533a1995493c");
    private static final ComponentId DOOR_INTERACTOR = ComponentId.from("2b67d91b-24ac-4f1c-94e7-8308dfc2ed70");
    private static final ComponentId PLAYER_WEAPON = ComponentId.from("3cf4b320-4186-4610-b67d-ebd843d53fc9");
    private static final ComponentId WEAPON_PRESENTATION = ComponentId.from("64fbcd73-b051-4348-9fc5-834183678794");
    private static final ComponentId PLAYER_PRESENTATION = ComponentId.from("7be3f3e4-e576-4710-8382-260037317921");
    private static final ComponentId PLAYER_LIFECYCLE = ComponentId.from("10bfeecd-9583-4182-8f0c-1165e98fc714");
    private static final ComponentId MAIN_MENU_COMPONENT = ComponentId.from("f4222e5c-4e20-4b75-a7ae-a6ea88b07cc9");
    private static final ComponentId HEALTH_NUMBER = ComponentId.from("2313f424-d11e-4c6b-95a2-1c8dbe583b7d");
    private static final ComponentId AMMO_NUMBER = ComponentId.from("00f54e60-dd1c-47e2-a180-83bfcefdc2d9");
    private static final InputAction MOVE = new InputAction("move");
    private static final InputAction LOOK = new InputAction("look");
    private static final InputAction TURN_RIGHT = new InputAction("turn-right");
    private static final InputAction FIRE_PRIMARY = new InputAction("fire-primary");
    private static final InputAction INTERACT = new InputAction("interact");
    private static final InputAction MENU_NEXT = new InputAction("menu-next");
    private static final InputAction MENU_CONFIRM = new InputAction("menu-confirm");
    private static final InputAction MENU = new InputAction("menu");
    private static final Path PROJECT_ROOT = Path.of(".").toAbsolutePath().normalize();

    @TempDir
    private Path temporaryDirectory;

    /** Composes the project-authored startup menu independently from the gameplay entry world. */
    @Test
    void composesAuthoredStartupMenu() {
        Path cache = temporaryDirectory.resolve("menu-import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);

        try (HostedProject loaded = loadStartup(cache)) {
            Entity menu = root(loaded, MAIN_MENU);

            assertThat(loaded.project().runtime().startupScene())
                    .contains(PROJECT_ROOT.resolve("worlds/main-menu.world.json"));
            assertThat(loaded.world().roots())
                    .extracting(entity -> entity.name().orElseThrow())
                    .containsExactly("Main Menu");
            assertThat(menu.component(MAIN_MENU_COMPONENT, DoomMainMenu.class)).isPresent();
        }
    }

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
                    .containsExactly(
                            "Player",
                            "MAP01 Geometry",
                            "MAP01 Actors",
                            "Player HUD",
                            "Game Over",
                            "World Presentation");
            assertThat(character.isClosed()).isFalse();
            assertThat(player.componentIds())
                    .contains(PLAYER_WEAPON, WEAPON_PRESENTATION, PLAYER_PRESENTATION, PLAYER_LIFECYCLE)
                    .doesNotContain(PLAYER_CONTROLLER);
            assertThat(new float[] {
                        playerTransform.position().x(),
                        playerTransform.position().y(),
                        playerTransform.position().z()
                    })
                    .containsExactly(-6.0F, 0.875F, 6.0F);
            assertThat(viewTransform.position().y()).isEqualTo(0.40625F);
            assertThat(placement.instantiationKind()).isEqualTo(EntityInstantiationKind.PLACEMENT);
            assertThat(placement.instantiatedDefinition()).contains(MAP_DEFINITION);
            assertThat(placement.componentIds()).hasSize(81);
            assertThat(placement.children()).hasSize(6);
            assertThat(renderer.isVisible()).isTrue();
            assertThat(mesh.isClosed()).isFalse();
            assertThat(material.isClosed()).isFalse();
            assertThat(loaded.world().requireModule(Physics3dWorldModule.class).collisionObjectCount())
                    .isEqualTo(47);
            assertThat(loaded.world().requireModule(Physics3dWorldModule.class).collisionShapeCount())
                    .isEqualTo(47);
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
            Entity player = root(loaded, PLAYER_ENTITY);
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
            CollisionShape3d shape =
                    zombieman.component(ZOMBIEMAN_SHAPE, CollisionShape3d.class).orElseThrow();
            CharacterBody3d body =
                    zombieman.component(ZOMBIEMAN_BODY, CharacterBody3d.class).orElseThrow();
            DoomCombatantState state = zombieman
                    .component(ZOMBIEMAN_STATE, DoomCombatantState.class)
                    .orElseThrow();
            DoomEnemyBehavior behavior = zombieman
                    .component(ZOMBIEMAN_BEHAVIOR, DoomEnemyBehavior.class)
                    .orElseThrow();
            DoomEnemyTarget target =
                    actors.component(ENEMY_TARGET, DoomEnemyTarget.class).orElseThrow();
            List<BillboardRenderer3d> walkFrames = ZOMBIEMAN_WALK_FRAMES.stream()
                    .map(component -> zombieman
                            .component(component, BillboardRenderer3d.class)
                            .orElseThrow())
                    .toList();
            List<BillboardRenderer3d> attackFrames = ZOMBIEMAN_ATTACK_FRAMES.stream()
                    .map(component -> zombieman
                            .component(component, BillboardRenderer3d.class)
                            .orElseThrow())
                    .toList();

            assertThat(actors.instantiationKind()).isEqualTo(EntityInstantiationKind.PLACEMENT);
            assertThat(actors.instantiatedDefinition()).contains(ACTOR_MAP_DEFINITION);
            assertThat(actors.children()).hasSize(119);
            assertThat(zombieman.instantiationKind()).isEqualTo(EntityInstantiationKind.PLACEMENT);
            assertThat(zombieman.componentIds())
                    .containsExactly(
                            ZOMBIEMAN_TRANSFORM,
                            ZOMBIEMAN_BILLBOARD,
                            ZOMBIEMAN_STATE,
                            ZOMBIEMAN_SHAPE,
                            ZOMBIEMAN_BODY,
                            ZOMBIEMAN_BEHAVIOR,
                            ZOMBIEMAN_WALK_FRAMES.get(0),
                            ZOMBIEMAN_WALK_FRAMES.get(1),
                            ZOMBIEMAN_WALK_FRAMES.get(2),
                            ZOMBIEMAN_WALK_FRAMES.get(3),
                            ZOMBIEMAN_ATTACK_FRAMES.get(0),
                            ZOMBIEMAN_ATTACK_FRAMES.get(1),
                            ZOMBIEMAN_ATTACK_FRAMES.get(2),
                            ZOMBIEMAN_PAIN_FRAME,
                            ZOMBIEMAN_DEATH_FRAMES.get(0),
                            ZOMBIEMAN_DEATH_FRAMES.get(1),
                            ZOMBIEMAN_DEATH_FRAMES.get(2),
                            ZOMBIEMAN_DEATH_FRAMES.get(3),
                            ZOMBIEMAN_DEATH_FRAMES.get(4),
                            ZOMBIEMAN_PRESENTATION);
            assertThat(Float.isFinite(actorTransform.position().x())
                            && Float.isFinite(actorTransform.position().y())
                            && Float.isFinite(actorTransform.position().z()))
                    .isTrue();
            assertThat(billboard.alignment()).isEqualTo(BillboardAlignment.CYLINDRICAL);
            assertThat(billboard.size().x()).isPositive();
            assertThat(billboard.size().y()).isPositive();
            assertThat(billboard.isVisible()).isTrue();
            assertThat(walkFrames).allMatch(frame -> !frame.isVisible());
            assertThat(attackFrames).allMatch(frame -> !frame.isVisible());
            assertThat(shape.localPosition().y()).isEqualTo(0.875F);
            assertThat(shape.filter().categoryBits()).isEqualTo(DoomCollisionCategories.MONSTER);
            assertThat(body.isClosed()).isFalse();
            assertThat(state.health()).isEqualTo(20);
            assertThat(behavior.isAlerted()).isFalse();
            assertThat(target.player()).isSameAs(player);
            assertThat(zombieman
                            .capability(DoomedCorridorsRuntimeTypes.DAMAGEABLE_CAPABILITY, DoomDamageable.class)
                            .orElseThrow())
                    .isSameAs(state);
            assertThat(zombieman
                            .capability(DoomedCorridorsRuntimeTypes.ENEMY_BEHAVIOR_CAPABILITY, DoomEnemyBehavior.class)
                            .orElseThrow())
                    .isSameAs(behavior);

            loaded.world().activate();
            Physics3dWorldModule physics = loaded.world().requireModule(Physics3dWorldModule.class);
            CollisionRaycastHit3d combatant = physics.raycast(
                            new Vector3f(actorTransform.position()).add(0.0F, 3.0F, 0.0F),
                            new Vector3f(0.0F, -1.0F, 0.0F),
                            4.0F)
                    .orElseThrow();
            assertThat(combatant.object().componentId()).isEqualTo(ZOMBIEMAN_BODY);
            assertThat(combatant.shape().componentId()).isEqualTo(ZOMBIEMAN_SHAPE);
        }

        assertThat(billboard.isClosed()).isTrue();
    }

    /** Presents authored pain and death while retaining a non-blocking corpse entity. */
    @Test
    void presentsCombatantDamageAndRetainsCorpse() {
        Path cache = temporaryDirectory.resolve("combatant-presentation-import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);
        TestPresentationWorldModule presentationWorld = new TestPresentationWorldModule();

        try (HostedProject loaded = load(cache, presentationWorld)) {
            Entity actors = root(loaded, ACTOR_MAP_PLACEMENT);
            Entity zombieman = actors.children().stream()
                    .filter(entity -> entity.name().orElseThrow().startsWith("Zombieman "))
                    .findFirst()
                    .orElseThrow();
            DoomCombatantState state = zombieman
                    .component(ZOMBIEMAN_STATE, DoomCombatantState.class)
                    .orElseThrow();
            CharacterBody3d body =
                    zombieman.component(ZOMBIEMAN_BODY, CharacterBody3d.class).orElseThrow();
            BillboardRenderer3d idle = zombieman
                    .component(ZOMBIEMAN_BILLBOARD, BillboardRenderer3d.class)
                    .orElseThrow();
            BillboardRenderer3d pain = zombieman
                    .component(ZOMBIEMAN_PAIN_FRAME, BillboardRenderer3d.class)
                    .orElseThrow();
            List<BillboardRenderer3d> death = ZOMBIEMAN_DEATH_FRAMES.stream()
                    .map(component -> zombieman
                            .component(component, BillboardRenderer3d.class)
                            .orElseThrow())
                    .toList();
            DoomCombatantPresentation reaction = zombieman
                    .component(ZOMBIEMAN_PRESENTATION, DoomCombatantPresentation.class)
                    .orElseThrow();
            Physics3dWorldModule physics = loaded.world().requireModule(Physics3dWorldModule.class);

            loaded.world().activate();
            assertThat(presentationWorld.positionalAttenuations())
                    .contains(new PositionalSoundAttenuation(5.0F, 37.5F, 1.0F));
            assertThat(state.damage(5)).isEqualTo(5);
            assertThat(state.health()).isEqualTo(15);
            assertThat(reaction.currentFrame()).isSameAs(pain);
            assertThat(pain.isVisible()).isTrue();
            assertThat(idle.isVisible()).isFalse();
            assertThat(presentationWorld.positionalRestarts()).isEqualTo(1);

            loaded.world().advanceFrame(Duration.ofMillis(140), 0.0F);
            assertThat(reaction.currentFrame()).isSameAs(idle);
            assertThat(idle.isVisible()).isTrue();

            assertThat(state.damage(20)).isEqualTo(15);
            assertThat(state.health()).isZero();
            assertThat(reaction.isDead()).isTrue();
            assertThat(reaction.currentFrame()).isSameAs(death.getFirst());
            assertThat(body.isClosed()).isTrue();
            assertThat(zombieman.isDestroyed()).isFalse();
            assertThat(actors.children()).hasSize(119);
            assertThat(physics.collisionObjectCount()).isEqualTo(46);
            assertThat(physics.collisionShapeCount()).isEqualTo(46);
            assertThat(presentationWorld.positionalRestarts()).isEqualTo(2);

            loaded.world().advanceFrame(Duration.ofMillis(560), 0.0F);
            assertThat(reaction.currentFrame()).isSameAs(death.getLast());
            assertThat(death.getLast().isVisible()).isTrue();
            loaded.world().advanceFrame(Duration.ofSeconds(1), 0.0F);
            assertThat(reaction.currentFrame()).isSameAs(death.getLast());
        }

        assertThat(presentationWorld.positionalSoundClosed()).isTrue();
    }

    /** Fires authored semantic input along the player view and kills the first visible damageable actor. */
    @Test
    void firesPlayerWeaponThroughHostedWorld() {
        Path cache = temporaryDirectory.resolve("weapon-import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);
        TestPresentationWorldModule presentation = new TestPresentationWorldModule();

        try (HostedProject loaded = load(cache, presentation)) {
            Entity player = root(loaded, PLAYER_ENTITY);
            DoomPlayerState playerState = player.capability(
                            DoomedCorridorsRuntimeTypes.PLAYER_RESOURCES_CAPABILITY, DoomPlayerState.class)
                    .orElseThrow();
            assertThat(player.capability(DoomedCorridorsRuntimeTypes.DAMAGEABLE_CAPABILITY, DoomDamageable.class)
                            .orElseThrow())
                    .isSameAs(playerState);
            Transform3d view = player.children()
                    .getFirst()
                    .component(VIEW_TRANSFORM, Transform3d.class)
                    .orElseThrow();
            Entity actors = root(loaded, ACTOR_MAP_PLACEMENT);
            ProjectInput input = (ProjectInput) loaded.world().requireModule(InputWorldModule.class);
            Physics3dWorldModule physics = loaded.world().requireModule(Physics3dWorldModule.class);
            DoomWeaponPresentation weaponPresentation = player.component(
                            WEAPON_PRESENTATION, DoomWeaponPresentation.class)
                    .orElseThrow();
            Entity hud = root(loaded, PLAYER_HUD);
            ScreenNumber healthNumber = hud.children()
                    .getFirst()
                    .component(HEALTH_NUMBER, ScreenNumber.class)
                    .orElseThrow();
            ScreenNumber ammoNumber = hud.children()
                    .getLast()
                    .component(AMMO_NUMBER, ScreenNumber.class)
                    .orElseThrow();

            loaded.world().activate();
            loaded.world().advanceFrame(Duration.ZERO, 0.0F);
            assertThat(healthNumber.value()).isEqualTo(100);
            assertThat(ammoNumber.value()).isEqualTo(50);
            assertThat(presentation.overlayCount()).isEqualTo(4);
            input.publish(ActionSnapshot.builder().pressed(FIRE_PRIMARY).build());
            loaded.world().advanceFixed(Duration.ofMillis(25));
            input.publish(ActionSnapshot.empty());
            loaded.world().advanceFrame(Duration.ZERO, 0.0F);
            assertThat(playerState.bullets()).isEqualTo(49);
            assertThat(ammoNumber.value()).isEqualTo(49);
            assertThat(presentation.restarts()).isEqualTo(1);
            assertThat(weaponPresentation.isFiring()).isTrue();
            assertThat(weaponPresentation.isHitIndicatorVisible()).isFalse();

            ShotLine assistedLine = unobstructedAutoAimShot(actors, physics);
            DoomHitscanTarget assistedTarget = assistedLine
                    .target()
                    .capability(DoomedCorridorsRuntimeTypes.HITSCAN_TARGET_CAPABILITY, DoomHitscanTarget.class)
                    .orElseThrow();
            int healthBeforeAssistedShot = assistedTarget.health();
            var assistedOrientation =
                    new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, -1.0F), assistedLine.direction());
            view.setWorldPose(assistedLine.origin(), assistedOrientation);
            Vector3f runtimeDirection = view.worldMatrix()
                    .transformDirection(new Vector3f(0.0F, 0.0F, -1.0F))
                    .normalize();
            assertThat(runtimeDirection.angle(assistedLine.direction())).isCloseTo(0.0F, within(1.0E-4F));
            Vector3f assistedAimDirection = assistedTarget
                    .aimPoint(new Vector3f())
                    .sub(assistedLine.origin())
                    .normalize();
            assertThat(runtimeDirection.angle(assistedAimDirection))
                    .isCloseTo((float) Math.toRadians(5.25F), within(1.0E-4F));
            input.publish(ActionSnapshot.builder().pressed(FIRE_PRIMARY).build());
            loaded.world().advanceFixed(Duration.ofMillis(25));
            input.publish(ActionSnapshot.empty());

            assertThat(assistedTarget.health()).isLessThan(healthBeforeAssistedShot);
            assertThat(weaponPresentation.isHitIndicatorVisible()).isTrue();
            assertThat(weaponPresentation.hitIndicatorPosition(1600, 900).distance(800.0F, 450.0F))
                    .isGreaterThan(40.0F);
            loaded.world().advanceFrame(Duration.ofMillis(120), 0.0F);
            assertThat(weaponPresentation.isHitIndicatorVisible()).isFalse();

            ShotLine firingLine = unobstructedShot(actors, physics);
            Entity target = firingLine.target();
            var orientation = new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, -1.0F), firingLine.direction());
            view.setWorldPose(firingLine.origin(), orientation);

            for (int shot = 0; shot < 4; shot++) {
                input.publish(ActionSnapshot.builder().pressed(FIRE_PRIMARY).build());
                loaded.world().advanceFixed(Duration.ofMillis(25));
                input.publish(ActionSnapshot.empty());
            }

            assertThat(playerState.bullets()).isEqualTo(44);
            assertThat(target.isDestroyed()).isFalse();
            assertThat(actors.children()).hasSize(119);
            assertThat(physics.collisionObjectCount()).isEqualTo(46);
            assertThat(physics.collisionShapeCount()).isEqualTo(46);
            assertThat(presentation.restarts()).isEqualTo(6);
        }
        assertThat(presentation.overlayCount()).isZero();
        assertThat(presentation.soundClosed()).isTrue();
    }

    /** Presents player damage and disables only input-driven controls when health reaches zero. */
    @Test
    void presentsPlayerDamageAndAppliesTerminalLifecycle() {
        Path cache = temporaryDirectory.resolve("player-presentation-import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);
        TestPresentationWorldModule presentationWorld = new TestPresentationWorldModule();
        RecordingApplicationControl application = new RecordingApplicationControl();

        try (HostedProject loaded = load(cache, presentationWorld, application)) {
            loaded.world().activate();
            assertHealthyPlayerPresentation(presentationWorld, loaded);
            assertTerminalPlayerState(presentationWorld, loaded);
            assertGameOverCommands(application, loaded);
        }

        assertThat(presentationWorld.overlayCount()).isZero();
        assertThat(presentationWorld.soundClosed()).isTrue();
    }

    /** Verifies the active player's initial presentation and non-terminal damage response. */
    private static void assertHealthyPlayerPresentation(
            TestPresentationWorldModule presentationWorld, HostedProject loaded) {
        Entity player = root(loaded, PLAYER_ENTITY);
        Entity controls = child(player, PLAYER_CONTROLS);
        Entity gameOver = root(loaded, GAME_OVER);
        DoomPlayerState state = player.component(
                        ComponentId.from("c416639d-dd1d-40d7-a9bd-6042f7206434"), DoomPlayerState.class)
                .orElseThrow();
        DoomPlayerPresentation presentation = player.component(PLAYER_PRESENTATION, DoomPlayerPresentation.class)
                .orElseThrow();
        assertThat(presentationWorld.overlayCount()).isEqualTo(4);
        assertThat(gameOver.isLocallyEnabled()).isFalse();
        assertThat(controls.componentIds()).containsExactly(PLAYER_CONTROLLER, DOOR_INTERACTOR, MAIN_MENU_COMPONENT);
        assertThat(state.damage(10)).isEqualTo(10);
        assertThat(presentation.isDead()).isFalse();
        assertThat(presentation.currentRedOpacity()).isEqualTo(0.32F);
        assertThat(presentation.currentShadeOpacity()).isZero();
        assertThat(presentationWorld.restarts()).isEqualTo(1);
        assertThat(controls.isEnabled()).isTrue();

        loaded.world().advanceFrame(Duration.ofMillis(250), 0.0F);
        assertThat(presentation.currentRedOpacity()).isZero();
    }

    /** Verifies terminal presentation and the suppression of player-controlled actions. */
    private static void assertTerminalPlayerState(TestPresentationWorldModule presentationWorld, HostedProject loaded) {
        Entity player = root(loaded, PLAYER_ENTITY);
        Entity controls = child(player, PLAYER_CONTROLS);
        Entity view = player.children().stream()
                .filter(entity -> !entity.authoredId().equals(PLAYER_CONTROLS))
                .findFirst()
                .orElseThrow();
        Entity hud = root(loaded, PLAYER_HUD);
        DoomPlayerState state = player.component(
                        ComponentId.from("c416639d-dd1d-40d7-a9bd-6042f7206434"), DoomPlayerState.class)
                .orElseThrow();
        DoomPlayerPresentation presentation = player.component(PLAYER_PRESENTATION, DoomPlayerPresentation.class)
                .orElseThrow();
        DoomPlayerLifecycle lifecycle =
                player.component(PLAYER_LIFECYCLE, DoomPlayerLifecycle.class).orElseThrow();
        Transform3d transform =
                player.component(PLAYER_TRANSFORM, Transform3d.class).orElseThrow();
        ProjectInput input = (ProjectInput) loaded.world().requireModule(InputWorldModule.class);
        assertThat(state.damage(90)).isEqualTo(90);
        assertThat(presentation.isDead()).isTrue();
        assertThat(presentation.currentRedOpacity()).isEqualTo(0.45F);
        assertThat(presentation.currentShadeOpacity()).isEqualTo(0.18F);
        assertThat(presentationWorld.restarts()).isEqualTo(2);
        assertThat(lifecycle.isDead()).isTrue();
        assertThat(controls.isLocallyEnabled()).isFalse();
        assertThat(view.isEnabled()).isTrue();
        assertThat(hud.isEnabled()).isTrue();

        Vector3f position = new Vector3f(transform.position());
        int bullets = state.bullets();
        input.publish(ActionSnapshot.builder()
                .axis2d(MOVE, 0.0F, 1.0F)
                .pressed(FIRE_PRIMARY)
                .build());
        loaded.world().advanceFixed(Duration.ofMillis(100));
        assertThat(transform.position()).isEqualTo(position);
        assertThat(state.bullets()).isEqualTo(bullets);
    }

    /** Verifies delayed Game Over visibility and every terminal navigation command. */
    private static void assertGameOverCommands(RecordingApplicationControl application, HostedProject loaded) {
        Entity player = root(loaded, PLAYER_ENTITY);
        Entity gameOver = root(loaded, GAME_OVER);
        DoomPlayerLifecycle lifecycle =
                player.component(PLAYER_LIFECYCLE, DoomPlayerLifecycle.class).orElseThrow();
        DoomPlayerPresentation presentation = player.component(PLAYER_PRESENTATION, DoomPlayerPresentation.class)
                .orElseThrow();
        ProjectInput input = (ProjectInput) loaded.world().requireModule(InputWorldModule.class);
        loaded.world().advanceFrame(Duration.ofMillis(999), 0.0F);
        assertThat(presentation.currentRedOpacity()).isZero();
        assertThat(presentation.currentShadeOpacity()).isEqualTo(0.18F);
        assertThat(gameOver.isLocallyEnabled()).isFalse();

        loaded.world().advanceFrame(Duration.ofMillis(1), 0.0F);
        assertThat(lifecycle.isGameOverVisible()).isTrue();
        assertThat(gameOver.isLocallyEnabled()).isTrue();
        assertThat(child(gameOver, RESTART_CURSOR).isLocallyEnabled()).isTrue();
        assertThat(child(gameOver, MAIN_MENU_CURSOR).isLocallyEnabled()).isFalse();

        input.publish(ActionSnapshot.builder().pressed(MENU_CONFIRM).build());
        loaded.world().advanceFixed(Duration.ofMillis(25));
        assertThat(application.requested()).isEqualTo(ApplicationCommand.NEW_GAME);

        application.clear();
        input.publish(ActionSnapshot.builder().pressed(MENU_NEXT).build());
        loaded.world().advanceFixed(Duration.ofMillis(25));
        assertThat(child(gameOver, RESTART_CURSOR).isLocallyEnabled()).isFalse();
        assertThat(child(gameOver, MAIN_MENU_CURSOR).isLocallyEnabled()).isTrue();
        input.publish(ActionSnapshot.builder().pressed(MENU_CONFIRM).build());
        loaded.world().advanceFixed(Duration.ofMillis(25));
        assertThat(application.requested()).isEqualTo(ApplicationCommand.RETURN_TO_MENU);

        application.clear();
        input.publish(ActionSnapshot.builder().pressed(MENU).build());
        loaded.world().advanceFixed(Duration.ofMillis(25));
        assertThat(application.requested()).isEqualTo(ApplicationCommand.RETURN_TO_MENU);
    }

    /** Lowers the authored view and first-person weapon through their terminal transitions. */
    @Test
    void lowersViewAndWeaponAfterPlayerDeath() {
        Path cache = temporaryDirectory.resolve("player-terminal-transition-import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);

        try (HostedProject loaded = load(cache, new TestPresentationWorldModule())) {
            Entity player = root(loaded, PLAYER_ENTITY);
            Entity view = player.children().stream()
                    .filter(entity -> !entity.authoredId().equals(PLAYER_CONTROLS))
                    .findFirst()
                    .orElseThrow();
            DoomPlayerState state = player.component(
                            ComponentId.from("c416639d-dd1d-40d7-a9bd-6042f7206434"), DoomPlayerState.class)
                    .orElseThrow();
            DoomWeaponPresentation weapon = player.component(WEAPON_PRESENTATION, DoomWeaponPresentation.class)
                    .orElseThrow();
            Transform3d viewTransform =
                    view.component(VIEW_TRANSFORM, Transform3d.class).orElseThrow();

            loaded.world().activate();
            float standingViewY = viewTransform.position().y();
            assertThat(state.damage(100)).isEqualTo(100);
            assertThat(weapon.isVisible()).isTrue();
            assertThat(weapon.deathLowerProgress()).isZero();

            loaded.world().advanceFrame(Duration.ofMillis(500), 0.0F);
            assertThat(viewTransform.position().y()).isEqualTo(standingViewY - 35.0F / 64.0F);
            assertThat(weapon.deathLowerProgress()).isEqualTo(0.5F);

            loaded.world().advanceFrame(Duration.ofMillis(500), 0.0F);
            assertThat(viewTransform.position().y()).isEqualTo(standingViewY - 35.0F / 32.0F);
            assertThat(weapon.isVisible()).isFalse();
        }
    }

    /** Collects one useful imported stimpack through its authored physics-signal connection. */
    @Test
    void collectsUsefulPickupThroughHostedWorld() {
        Path cache = temporaryDirectory.resolve("useful-pickup-import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);

        try (HostedProject loaded = load(cache)) {
            Entity player = root(loaded, PLAYER_ENTITY);
            DoomPlayerState state = player.capability(
                            DoomedCorridorsRuntimeTypes.PLAYER_RESOURCES_CAPABILITY, DoomPlayerState.class)
                    .orElseThrow();
            Transform3d playerTransform =
                    player.component(PLAYER_TRANSFORM, Transform3d.class).orElseThrow();
            Entity actors = root(loaded, ACTOR_MAP_PLACEMENT);
            Entity stimpack = actor(actors, "Stimpack 86");
            DoomPickup pickup =
                    stimpack.component(STIMPACK_PICKUP, DoomPickup.class).orElseThrow();
            ProjectInput input = (ProjectInput) loaded.world().requireModule(InputWorldModule.class);
            loaded.world().activate();
            state.damage(10);

            assertThat(state.health()).isEqualTo(90);
            assertThat(stimpack.isDestroyed()).isFalse();

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
            DoomPlayerState state = player.capability(
                            DoomedCorridorsRuntimeTypes.PLAYER_RESOURCES_CAPABILITY, DoomPlayerState.class)
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

    /** Activates imported movable geometry through the authored interaction action and declared door capability. */
    @Test
    void opensPublishedDoorThroughPlayerInteraction() {
        Path cache = temporaryDirectory.resolve("door-import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);
        TestPresentationWorldModule presentation = new TestPresentationWorldModule();

        try (HostedProject loaded = load(cache, presentation)) {
            Entity player = root(loaded, PLAYER_ENTITY);
            Transform3d view = player.children()
                    .getFirst()
                    .component(VIEW_TRANSFORM, Transform3d.class)
                    .orElseThrow();
            Entity doorEntity = root(loaded, MAP_PLACEMENT).children().getFirst();
            DoomDoor door = doorEntity
                    .capability(DoomDoorDescriptors.DOOR_CAPABILITY, DoomDoor.class)
                    .orElseThrow();
            Transform3d doorTransform = doorEntity
                    .capability(Spatial3dDescriptors.spatialCapability(), Transform3d.class)
                    .orElseThrow();
            ProjectInput input = (ProjectInput) loaded.world().requireModule(InputWorldModule.class);
            Physics3dWorldModule physics = loaded.world().requireModule(Physics3dWorldModule.class);

            loaded.world().activate();
            loaded.world().disable(root(loaded, ACTOR_MAP_PLACEMENT));
            CollisionRaycastHit3d hit = findDoorSurface(physics, doorEntity);
            Vector3f normal = hit.normal(new Vector3f());
            Vector3f origin = hit.point(new Vector3f()).fma(0.5F, normal);
            Vector3f direction = normal.negate(new Vector3f());
            view.setWorldPose(origin, new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, -1.0F), direction));
            float closedHeight = doorTransform.position().y();
            int soundsBeforeOpening = presentation.positionalRestarts();

            input.publish(ActionSnapshot.builder().pressed(INTERACT).build());
            loaded.world().advanceFixed(Duration.ofMillis(25));
            input.publish(ActionSnapshot.empty());

            assertThat(door.phase()).isEqualTo(DoomDoor.Phase.OPENING);
            assertThat(doorTransform.position().y()).isGreaterThan(closedHeight);
            assertThat(presentation.positionalRestarts()).isEqualTo(soundsBeforeOpening + 1);

            advanceFixed(loaded, 25);
            assertThat(door.phase()).isEqualTo(DoomDoor.Phase.WAITING);
            assertThat(physics.raycast(origin, direction, 1.0F)
                            .map(result -> result.object().owner())
                            .filter(doorEntity::equals))
                    .isEmpty();
            advanceFixed(loaded, 200);
            assertThat(door.phase()).isEqualTo(DoomDoor.Phase.CLOSED);
            assertThat(doorTransform.position().y()).isEqualTo(closedHeight);
            assertThat(presentation.positionalRestarts()).isEqualTo(soundsBeforeOpening + 2);
            assertThat(physics.raycast(origin, direction, 1.0F))
                    .hasValueSatisfying(
                            result -> assertThat(result.object().owner()).isSameAs(doorEntity));
        }
        assertThat(presentation.positionalSoundsClosed()).isEqualTo(presentation.positionalSoundsCreated());
    }

    /** Reopens a closing blaze door when its generated character-only sensor reaches the player. */
    @Test
    void reopensClosingDoorAroundPlayer() {
        Path cache = temporaryDirectory.resolve("obstructed-closing-door-import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);

        try (HostedProject loaded = load(cache)) {
            Entity player = root(loaded, PLAYER_ENTITY);
            Transform3d playerTransform =
                    player.component(PLAYER_TRANSFORM, Transform3d.class).orElseThrow();
            CharacterBody3d playerBody =
                    player.component(PLAYER_BODY, CharacterBody3d.class).orElseThrow();
            Entity doorEntity = root(loaded, MAP_PLACEMENT).children().getFirst();
            DoomDoor door = doorEntity
                    .capability(DoomDoorDescriptors.DOOR_CAPABILITY, DoomDoor.class)
                    .orElseThrow();
            float closedHeight = door.currentHeight();

            loaded.world().activate();
            loaded.world().disable(root(loaded, ACTOR_MAP_PLACEMENT));
            loaded.world().disable(child(player, PLAYER_CONTROLS));
            door.activate();
            advanceFixed(loaded, 25);
            assertThat(door.phase()).isEqualTo(DoomDoor.Phase.WAITING);
            Vector3f doorway = doorEntity
                    .children()
                    .getFirst()
                    .capability(Spatial3dDescriptors.spatialCapability(), Transform3d.class)
                    .orElseThrow()
                    .worldMatrix()
                    .getTranslation(new Vector3f());
            for (int step = 0; step < 300; step++) {
                Vector3f direction = new Vector3f(
                        doorway.x() - playerTransform.position().x(),
                        0.0F,
                        doorway.z() - playerTransform.position().z());
                if (direction.lengthSquared() <= 0.01F) {
                    break;
                }
                playerBody.move(direction.normalize(8.0F), Duration.ofMillis(25));
                loaded.world().advanceFixed(Duration.ofMillis(25));
            }
            assertThat(new Vector3f(
                                    doorway.x() - playerTransform.position().x(),
                                    0.0F,
                                    doorway.z() - playerTransform.position().z())
                            .length())
                    .isLessThan(0.5F);

            for (int step = 0; step < 250 && door.phase() != DoomDoor.Phase.OPENING; step++) {
                loaded.world().advanceFixed(Duration.ofMillis(25));
            }

            assertThat(door.phase()).isEqualTo(DoomDoor.Phase.OPENING);
            assertThat(door.currentHeight()).isGreaterThan(closedHeight);
        }
    }

    /** Composes generated doors closed before activation so the editor preview starts from authored state. */
    @Test
    void composesClosedDoorPreviewState() {
        Path cache = temporaryDirectory.resolve("door-preview-import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);

        try (HostedProject loaded = load(cache)) {
            List<Entity> doors = root(loaded, MAP_PLACEMENT).children().stream()
                    .filter(entity -> entity.capability(DoomDoorDescriptors.DOOR_CAPABILITY, DoomDoor.class)
                            .isPresent())
                    .toList();
            assertThat(doors).hasSize(4).allSatisfy(entity -> {
                DoomDoor door = entity.capability(DoomDoorDescriptors.DOOR_CAPABILITY, DoomDoor.class)
                        .orElseThrow();
                Transform3d transform = entity.capability(Spatial3dDescriptors.spatialCapability(), Transform3d.class)
                        .orElseThrow();
                assertThat(door.phase()).isEqualTo(DoomDoor.Phase.CLOSED);
                assertThat(transform.position().y()).isEqualTo(door.currentHeight());
            });
        }
    }

    /** Composes MAP01's type-19 floor raised and moves its rendered collision surface to the derived destination. */
    @Test
    void composesAndLowersMovingFloor() {
        Path cache = temporaryDirectory.resolve("floor-import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);

        try (HostedProject loaded = load(cache)) {
            Entity floorEntity = movingFloor(root(loaded, MAP_PLACEMENT));
            DoomFloor floor = floorEntity
                    .capability(DoomFloorDescriptors.FLOOR_CAPABILITY, DoomFloor.class)
                    .orElseThrow();
            Transform3d transform = floorEntity
                    .capability(Spatial3dDescriptors.spatialCapability(), Transform3d.class)
                    .orElseThrow();
            Physics3dWorldModule physics = loaded.world().requireModule(Physics3dWorldModule.class);

            assertThat(floor.profile()).isEqualTo(DoomFloor.Profile.WALK_ONCE_LOWER_TO_HIGHEST_SURROUNDING);
            assertThat(floor.phase()).isEqualTo(DoomFloor.Phase.RAISED);
            assertThat(transform.position().y()).isEqualTo(floor.currentHeight());

            loaded.world().activate();
            CollisionRaycastHit3d raisedSurface = findMovingFloorSurface(physics, floorEntity);
            float raisedHeight = raisedSurface.point(new Vector3f()).y;
            assertThat(floor.activate()).isTrue();

            advanceFixed(loaded, 50);

            assertThat(floor.phase()).isEqualTo(DoomFloor.Phase.LOWERED);
            assertThat(floor.currentHeight()).isCloseTo(raisedHeight - 1.0F, within(1.0E-5F));
            assertThat(transform.position().y()).isEqualTo(floor.currentHeight());
            CollisionRaycastHit3d loweredSurface = physics.raycast(
                            new Vector3f(
                                    raisedSurface.point(new Vector3f()).x,
                                    raisedHeight - 0.1F,
                                    raisedSurface.point(new Vector3f()).z),
                            new Vector3f(0.0F, -1.0F, 0.0F),
                            4.0F)
                    .orElseThrow();
            assertThat(loweredSurface.object().owner()).isSameAs(floorEntity);
            assertThat(loweredSurface.point(new Vector3f()).y).isCloseTo(floor.currentHeight(), within(1.0E-5F));
            assertThat(floor.activate()).isFalse();
        }
    }

    /** Leaves an imported door closed when a nearer non-player solid obstructs the authored interaction ray. */
    @Test
    void doesNotActivateDoorThroughNearerWall() {
        Path cache = temporaryDirectory.resolve("obstructed-door-import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);

        try (HostedProject loaded = load(cache)) {
            Entity player = root(loaded, PLAYER_ENTITY);
            Transform3d view = player.children()
                    .getFirst()
                    .component(VIEW_TRANSFORM, Transform3d.class)
                    .orElseThrow();
            ProjectInput input = (ProjectInput) loaded.world().requireModule(InputWorldModule.class);
            Physics3dWorldModule physics = loaded.world().requireModule(Physics3dWorldModule.class);

            loaded.world().activate();
            InteractionLine line = findObstructedDoorLine(physics, root(loaded, MAP_PLACEMENT), player);
            DoomDoor door = line.door()
                    .capability(DoomDoorDescriptors.DOOR_CAPABILITY, DoomDoor.class)
                    .orElseThrow();
            view.setWorldPose(
                    line.origin(), new Quaternionf().rotationTo(new Vector3f(0.0F, 0.0F, -1.0F), line.direction()));

            input.publish(ActionSnapshot.builder().pressed(INTERACT).build());
            loaded.world().advanceFixed(Duration.ofMillis(25));

            assertThat(door.phase()).isEqualTo(DoomDoor.Phase.CLOSED);
        }
    }

    /** Loads the authored project through the same generic host used by desktop exports. */
    private static HostedProject load(Path cache) {
        return load(cache, new TestPresentationWorldModule());
    }

    /** Loads through a desktop-equivalent environment with native presentation replaced by an observable test seam. */
    private static HostedProject load(Path cache, TestPresentationWorldModule presentation) {
        ProjectRuntimeHost host = new ProjectRuntimeHost(
                ENGINE_VERSION,
                ProjectHostIntegrationTest.class.getClassLoader(),
                new TestProjectEnvironment(cache, presentation));
        return host.loadEntry(PROJECT_ROOT);
    }

    /** Loads entry gameplay with observable host-owned application transitions. */
    private static HostedProject load(
            Path cache, TestPresentationWorldModule presentation, ApplicationControl application) {
        ProjectRuntimeHost host = new ProjectRuntimeHost(
                ENGINE_VERSION,
                ProjectHostIntegrationTest.class.getClassLoader(),
                new TestProjectEnvironment(cache, presentation, application));
        return host.loadEntry(PROJECT_ROOT);
    }

    /** Loads the manifest-selected startup world through the desktop-equivalent environment. */
    private static HostedProject loadStartup(Path cache) {
        ProjectRuntimeHost host = new ProjectRuntimeHost(
                ENGINE_VERSION,
                ProjectHostIntegrationTest.class.getClassLoader(),
                new TestProjectEnvironment(cache, new TestPresentationWorldModule()));
        return host.load(PROJECT_ROOT);
    }

    /** Finds one authored world root by its stable placement identity. */
    private static Entity root(HostedProject loaded, EntityId authoredId) {
        return loaded.world().roots().stream()
                .filter(entity -> entity.authoredId().equals(authoredId))
                .findFirst()
                .orElseThrow();
    }

    /** Finds a horizontal collision sample owned by one generated door without encoding map coordinates in the test. */
    private static CollisionRaycastHit3d findDoorSurface(Physics3dWorldModule physics, Entity door) {
        List<Vector3f> directions = List.of(
                new Vector3f(1.0F, 0.0F, 0.0F),
                new Vector3f(-1.0F, 0.0F, 0.0F),
                new Vector3f(0.0F, 0.0F, 1.0F),
                new Vector3f(0.0F, 0.0F, -1.0F));
        for (int x = -64; x <= 64; x++) {
            for (int z = -64; z <= 64; z++) {
                Vector3f origin = new Vector3f(x, 0.5F, z);
                for (Vector3f direction : directions) {
                    Optional<CollisionRaycastHit3d> hit = physics.raycast(origin, direction, 1.0F);
                    if (hit.map(result -> result.object().owner() == door).orElse(false)) {
                        return hit.orElseThrow();
                    }
                }
            }
        }
        throw new AssertionError("no collision surface found for generated door " + door.authoredId());
    }

    /** Finds the generated MAP01 floor by its engine-owned semantic capability. */
    private static Entity movingFloor(Entity map) {
        return map.children().stream()
                .filter(entity -> entity.capability(DoomFloorDescriptors.FLOOR_CAPABILITY, DoomFloor.class)
                        .isPresent())
                .findFirst()
                .orElseThrow();
    }

    /** Finds a downward collision sample owned by the independently movable floor. */
    private static CollisionRaycastHit3d findMovingFloorSurface(Physics3dWorldModule physics, Entity floor) {
        CollisionShape3d shape =
                floor.component(MOVING_FLOOR_SHAPE, CollisionShape3d.class).orElseThrow();
        TriangleMeshCollisionShape3dResource mesh = (TriangleMeshCollisionShape3dResource) shape.resource();
        Vector3f sample = new Vector3f();
        for (int index = 0; index < 3; index++) {
            sample.add(mesh.vertex(mesh.index(index), new Vector3f()));
        }
        sample.div(3.0F);
        floor.capability(Spatial3dDescriptors.spatialCapability(), Transform3d.class)
                .orElseThrow()
                .worldMatrix()
                .transformPosition(sample);
        return physics.raycast(new Vector3f(sample).add(0.0F, 0.1F, 0.0F), new Vector3f(0.0F, -1.0F, 0.0F), 4.0F)
                .filter(hit -> hit.object().owner() == floor)
                .orElseThrow(() -> new AssertionError(
                        "generated moving floor has no collision at its first triangle: " + floor.authoredId()));
    }

    /** Finds a short ray which reaches one generated door only after crossing a nearer static solid. */
    private static InteractionLine findObstructedDoorLine(Physics3dWorldModule physics, Entity map, Entity player) {
        List<Vector3f> directions = List.of(
                new Vector3f(1.0F, 0.0F, 0.0F),
                new Vector3f(-1.0F, 0.0F, 0.0F),
                new Vector3f(0.0F, 0.0F, 1.0F),
                new Vector3f(0.0F, 0.0F, -1.0F));
        for (int x = -64; x <= 64; x++) {
            for (int z = -64; z <= 64; z++) {
                Vector3f origin = new Vector3f(x, 0.5F, z);
                for (Vector3f direction : directions) {
                    Optional<CollisionRaycastHit3d> first = physics.raycast(origin, direction, 2.0F);
                    if (first.isEmpty()
                            || first.orElseThrow().object().owner() == player
                            || map.children()
                                    .contains(first.orElseThrow().object().owner())) {
                        continue;
                    }
                    float advance = first.orElseThrow().distance() + 0.01F;
                    if (advance >= 2.0F) {
                        continue;
                    }
                    Vector3f beyondBlocker = new Vector3f(origin).fma(advance, direction);
                    Optional<Entity> door = physics.raycast(beyondBlocker, direction, 2.0F - advance)
                            .map(result -> result.object().owner())
                            .filter(map.children()::contains);
                    if (door.isPresent()) {
                        return new InteractionLine(door.orElseThrow(), origin, direction);
                    }
                }
            }
        }
        throw new AssertionError("MAP01 has no short interaction line with a wall before a generated door");
    }

    /** Finds one direct child by its stable authored identity. */
    private static Entity child(Entity parent, EntityId authoredId) {
        return parent.children().stream()
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

    /** Finds one short unobstructed ray into a damageable actor from its surrounding map space. */
    private static ShotLine unobstructedShot(Entity actors, Physics3dWorldModule physics) {
        List<Vector3f> offsets = List.of(
                new Vector3f(2.0F, 0.0F, 0.0F),
                new Vector3f(-2.0F, 0.0F, 0.0F),
                new Vector3f(0.0F, 0.0F, 2.0F),
                new Vector3f(0.0F, 0.0F, -2.0F),
                new Vector3f(0.0F, 2.0F, 0.0F));
        for (Entity entity : actors.children()) {
            if (entity.capability(DoomedCorridorsRuntimeTypes.DAMAGEABLE_CAPABILITY, DoomDamageable.class)
                    .isEmpty()) {
                continue;
            }
            Vector3f center = entity.component(ZOMBIEMAN_TRANSFORM, Transform3d.class)
                    .orElseThrow()
                    .worldMatrix()
                    .getTranslation(new Vector3f())
                    .add(0.0F, 0.875F, 0.0F);
            for (Vector3f offset : offsets) {
                Vector3f origin = new Vector3f(center).add(offset);
                Vector3f direction = new Vector3f(center).sub(origin).normalize();
                Optional<CollisionRaycastHit3d> hit = physics.raycast(origin, direction, offset.length() + 0.5F);
                if (hit.map(result -> result.object().owner() == entity).orElse(false)) {
                    return new ShotLine(entity, origin, direction);
                }
            }
        }
        throw new AssertionError("MAP01 has no combatant with an unobstructed test ray");
    }

    /** Finds a visible target ray offset far enough to miss geometry but still lie within the authored aim cone. */
    private static ShotLine unobstructedAutoAimShot(Entity actors, Physics3dWorldModule physics) {
        List<Vector3f> axes = List.of(
                new Vector3f(1.0F, 0.0F, 0.0F),
                new Vector3f(-1.0F, 0.0F, 0.0F),
                new Vector3f(0.0F, 0.0F, 1.0F),
                new Vector3f(0.0F, 0.0F, -1.0F));
        float missAngle = (float) Math.toRadians(5.25F);
        for (Entity entity : actors.children()) {
            Optional<DoomHitscanTarget> selected =
                    entity.capability(DoomedCorridorsRuntimeTypes.HITSCAN_TARGET_CAPABILITY, DoomHitscanTarget.class);
            if (selected.isEmpty()) {
                continue;
            }
            DoomHitscanTarget target = selected.orElseThrow();
            Vector3f center = target.aimPoint(new Vector3f());
            for (float distance : List.of(8.0F, 12.0F, 16.0F)) {
                if (Math.asin(target.aimRadius() / distance) >= missAngle) {
                    continue;
                }
                for (Vector3f axis : axes) {
                    Vector3f origin = new Vector3f(axis).mul(distance).add(center);
                    Vector3f exactDirection = new Vector3f(center).sub(origin).normalize();
                    Optional<CollisionRaycastHit3d> exactHit = physics.raycast(origin, exactDirection, distance + 1.0F);
                    if (exactHit.map(result -> result.object().owner() == entity)
                            .orElse(false)) {
                        Vector3f missedDirection = new Vector3f(exactDirection).rotateY(missAngle);
                        Optional<CollisionRaycastHit3d> missedHit =
                                physics.raycast(origin, missedDirection, distance + 1.0F);
                        if (missedHit
                                .map(result -> result.object().owner() != entity)
                                .orElse(true)) {
                            return new ShotLine(entity, origin, missedDirection);
                        }
                    }
                }
            }
        }
        throw new AssertionError("MAP01 has no visible combatant suitable for an assisted test shot");
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

    /** One verified unobstructed weapon ray used by the host integration test. */
    private record ShotLine(Entity target, Vector3f origin, Vector3f direction) {}

    /** One player-facing test ray with a static obstruction before the selected generated door. */
    private record InteractionLine(Entity door, Vector3f origin, Vector3f direction) {}

    /** Captures world-requested application transitions without owning a desktop session. */
    private static final class RecordingApplicationControl implements ApplicationControl {
        private ApplicationCommand requested;

        @Override
        public boolean canResume() {
            return false;
        }

        @Override
        public void request(ApplicationCommand command) {
            requested = command;
        }

        /** Returns the latest requested transition. */
        private ApplicationCommand requested() {
            return requested;
        }

        /** Clears the previous observation before another independent transition. */
        private void clear() {
            requested = null;
        }

        @Override
        public void close() {
            // Test adapter owns no resources.
        }
    }
}
