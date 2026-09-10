/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.glynch.doomedcorridors.internal.DoomedCorridorsDescriptors;
import io.github.glynch.jscene3d.project.component.ComponentLifecycle;
import io.github.glynch.jscene3d.project.component.ComponentUpdatePhase;
import io.github.glynch.jscene3d.project.diagnostic.ProjectDiagnostic;
import io.github.glynch.jscene3d.project.extension.ExtensionCatalogLoadResult;
import io.github.glynch.jscene3d.project.extension.ExtensionCatalogLoader;
import io.github.glynch.jscene3d.project.imports.ImportDefinition;
import io.github.glynch.jscene3d.project.imports.ImportLoadResult;
import io.github.glynch.jscene3d.project.imports.ImportLoader;
import io.github.glynch.jscene3d.project.input.InputBinding;
import io.github.glynch.jscene3d.project.input.InputMapLoadResult;
import io.github.glynch.jscene3d.project.input.InputMapLoader;
import io.github.glynch.jscene3d.project.input.InputValueType;
import io.github.glynch.jscene3d.project.manifest.GameProject;
import io.github.glynch.jscene3d.project.manifest.ProjectLoadResult;
import io.github.glynch.jscene3d.project.manifest.ProjectLoader;
import io.github.glynch.jscene3d.project.value.ProjectValue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Verifies the repository remains a loadable project for the generic JScene3D host. */
final class ProjectManifestTest {
    private static final String ENGINE_VERSION = "0.1.0-SNAPSHOT";
    private static final String EXTENSION_ID = "io.github.glynch.doomed-corridors";

    /** Loads the project's identity and generic runtime entry points. */
    @Test
    void loadsDoomedCorridorsProject() {
        ProjectLoadResult result = loadProject();

        assertThat(result.isValid()).isTrue();
        GameProject project = result.project().orElseThrow();
        assertThat(project.identity().name()).isEqualTo("Doomed Corridors");
        assertThat(project.runtime().applicationExtension()).isEqualTo(EXTENSION_ID);
        assertThat(project.runtime().entryScene()).isEqualTo(project.root().resolve("worlds/map01.world.json"));
        assertThat(project.runtime().startupScene()).contains(project.root().resolve("worlds/main-menu.world.json"));
        assertThat(project.runtime().inputMap()).contains(project.root().resolve("application/input-map.json"));
        assertThat(project.launch().splash()).get().satisfies(splash -> {
            assertThat(splash.background())
                    .isEqualTo(project.root().resolve("application/branding/images/corridor-background.png"));
            assertThat(splash.title())
                    .isEqualTo(project.root().resolve("application/branding/images/doomed-corridors-title.png"));
            assertThat(splash.studioLogo()).isEmpty();
            assertThat(splash.poweredByBadges())
                    .containsExactly(project.root().resolve("application/branding/images/powered-by-jscene3d.png"));
            assertThat(splash.minimumDuration()).isEqualTo(Duration.ofSeconds(2));
        });
        assertThat(project.imports())
                .containsExactly(
                        project.root().resolve("imports/freedoom-map01.import.json"),
                        project.root().resolve("imports/freedoom-map01-actors.import.json"));
    }

    /** Loads declared assets with or without the ignored local WAD installation. */
    @Test
    void loadsDoomedCorridorsAssets() {
        ProjectLoadResult result = loadProject();
        GameProject project = result.project().orElseThrow();

        assertThat(project.assets())
                .extracting(GameProject.AssetSource::id)
                .containsExactly(
                        "actors",
                        "combat",
                        "combat-presentation",
                        "player-capsule",
                        "main-menu-background",
                        "main-menu-title",
                        "freedoom");
        assertThat(project.assets().getFirst()).satisfies(asset -> {
            assertThat(asset.type()).isEqualTo(EXTENSION_ID + "/actor-catalog");
            assertThat(asset.path()).isEqualTo(project.root().resolve("game/actors.json"));
            assertThat(asset.sha256()).isEmpty();
        });
        assertThat(project.assets().get(3)).satisfies(asset -> {
            assertThat(asset.type()).isEqualTo("io.github.glynch.jscene3d.physics3d/capsule-collision-shape-3d");
            assertThat(asset.path()).isEqualTo(project.root().resolve("resources/player-capsule.resource.json"));
            assertThat(asset.sha256()).isEmpty();
        });
        assertThat(project.assets().get(6)).satisfies(asset -> {
            assertThat(asset.type()).isEqualTo("io.github.glynch.jscene3d.wad/source");
            assertThat(asset.path()).isEqualTo(project.root().resolve("assets/freedoom2.wad"));
            assertThat(asset.sha256()).contains("a8772e088847032510d97ba2312406a6998f21cbab44d4ff10696faa9c0ecd4b");
        });
        if (Files.isRegularFile(project.root().resolve("assets/freedoom2.wad"))) {
            assertThat(result.diagnostics()).isEmpty();
        } else {
            assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
                assertThat(diagnostic.severity()).isEqualTo(ProjectDiagnostic.Severity.WARNING);
                assertThat(diagnostic.code()).isEqualTo("project.path.missing");
                assertThat(diagnostic.location()).isEqualTo("/assets/4/path");
            });
        }
    }

    /** Keeps game branding project-owned while imported Doom content supplies only controls and labels. */
    @Test
    void usesProjectOwnedMainMenuBranding() throws Exception {
        JsonNode menu = new ObjectMapper()
                .readTree(Path.of("worlds/main-menu.world.json").toFile());

        assertThat(menu.at("/roots/0/components/0/properties/background/$ref").textValue())
                .isEqualTo("asset:main-menu-background");
        assertThat(menu.at("/roots/0/components/0/properties/title/$ref").textValue())
                .isEqualTo("asset:main-menu-title");
        assertThat(Files.readString(Path.of("resources/main-menu-background.resource.json")))
                .contains("project:application/branding/images/corridor-background.png")
                .contains("io.github.glynch.jscene3d.presentation/overlay-image");
        assertThat(Files.readString(Path.of("resources/main-menu-title.resource.json")))
                .contains("project:application/branding/images/doomed-corridors-title.png")
                .contains("io.github.glynch.jscene3d.presentation/overlay-image");
    }

    /** Loads the single MAP01 publication recipe through the engine Doom importer. */
    @Test
    void loadsFreedoomMapImport() {
        GameProject project = loadProject().project().orElseThrow();
        ImportLoadResult result =
                new ImportLoader().load(project, project.imports().getFirst());

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        ImportDefinition definition = result.definition().orElseThrow();
        assertThat(definition.id()).isEqualTo("freedoom-map01");
        assertThat(definition.asset().id()).isEqualTo("freedoom");
        assertThat(definition.importer()).isEqualTo("io.github.glynch.jscene3d.doom/maps");
        assertThat(definition.selection()).containsExactly("maps/MAP01");
    }

    /** Loads the game-owned MAP01 actor publication recipe and its provider-data dependencies. */
    @Test
    void loadsFreedoomMapActorImport() {
        GameProject project = loadProject().project().orElseThrow();
        ImportLoadResult result =
                new ImportLoader().load(project, project.imports().get(1));

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        ImportDefinition definition = result.definition().orElseThrow();
        assertThat(definition.id()).isEqualTo("freedoom-map01-actors");
        assertThat(definition.asset().id()).isEqualTo("freedoom");
        assertThat(definition.importer()).isEqualTo(EXTENSION_ID + "/actors");
        assertThat(definition.selection()).containsExactly("maps/MAP01");
        assertThat(definition.settings())
                .containsEntry("actor-catalog", new ProjectValue.TextValue("actors"))
                .containsEntry("combat-rules", new ProjectValue.TextValue("combat"))
                .containsEntry("combat-presentation", new ProjectValue.TextValue("combat-presentation"));
    }

    /** Loads the semantic input map independently of platform controls. */
    @Test
    void loadsProjectInputMap() {
        GameProject project = loadProject().project().orElseThrow();
        InputMapLoadResult result =
                new InputMapLoader().load(project, project.runtime().inputMap().orElseThrow());

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        var actions = result.definition().orElseThrow().actions();
        assertThat(actions)
                .containsOnlyKeys(
                        "move",
                        "look",
                        "turn-left",
                        "turn-right",
                        "interact",
                        "fire-primary",
                        "menu-previous",
                        "menu-next",
                        "menu-confirm",
                        "menu");
        assertThat(actions.get("move").valueType()).isEqualTo(InputValueType.AXIS_2D);
        assertThat(actions.get("move").bindings())
                .containsExactly(
                        new InputBinding.DirectionalKeys("W", "S", "A", "D"),
                        new InputBinding.GamepadStick("left-stick", 0.15F, true));
        assertThat(actions.get("look").bindings())
                .containsExactly(
                        new InputBinding.MouseDelta(1.0F, -1.0F),
                        new InputBinding.GamepadStick("right-stick", 0.15F, true));
        assertThat(actions.get("turn-left").bindings()).containsExactly(new InputBinding.KeyboardKey("LEFT"));
        assertThat(actions.get("turn-right").bindings()).containsExactly(new InputBinding.KeyboardKey("RIGHT"));
        assertThat(actions.get("interact").bindings())
                .containsExactly(new InputBinding.KeyboardKey("E"), new InputBinding.GamepadButton("button-west"));
        assertThat(actions.get("fire-primary").bindings())
                .containsExactly(new InputBinding.MouseButton("LEFT"), new InputBinding.GamepadButton("button-south"));
        assertThat(actions.get("menu-previous").bindings())
                .containsExactly(
                        new InputBinding.KeyboardKey("UP"),
                        new InputBinding.KeyboardKey("W"),
                        new InputBinding.GamepadButton("dpad-up"));
        assertThat(actions.get("menu-next").bindings())
                .containsExactly(
                        new InputBinding.KeyboardKey("DOWN"),
                        new InputBinding.KeyboardKey("S"),
                        new InputBinding.GamepadButton("dpad-down"));
        assertThat(actions.get("menu-confirm").bindings())
                .containsExactly(new InputBinding.KeyboardKey("ENTER"), new InputBinding.GamepadButton("button-south"));
        assertThat(actions.get("menu").bindings())
                .containsExactly(new InputBinding.KeyboardKey("ESCAPE"), new InputBinding.GamepadButton("start"));
    }

    /** Preserves MAP01's authored 16-unit step rule without embedding collision tolerance in project data. */
    @Test
    void configuresExactDoomStepHeight() throws Exception {
        JsonNode world =
                new ObjectMapper().readTree(Path.of("worlds/map01.world.json").toFile());
        float maximumStepHeight =
                world.at("/roots/0/components/2/properties/maximum-step-height").floatValue();

        assertThat(maximumStepHeight).isEqualTo(16.0F / 32.0F);
    }

    /** Loads the application descriptor alongside its engine-owned Doom importer declaration. */
    @Test
    void loadsDoomedCorridorsExtension() {
        GameProject project = loadProject().project().orElseThrow();
        ExtensionCatalogLoadResult result = new ExtensionCatalogLoader(ENGINE_VERSION)
                .load(project, getClass().getClassLoader());

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.catalog().extensions())
                .extracting(descriptor -> descriptor.id())
                .containsExactly("io.github.glynch.jscene3d.wad", "io.github.glynch.jscene3d.doom", EXTENSION_ID);
        assertThat(result.catalog().types())
                .extracting(type -> type.type().id())
                .contains("io.github.glynch.jscene3d.doom/maps", EXTENSION_ID + "/actors");
        assertThat(result.catalog().types().stream()
                        .filter(type -> type.type().id().equals(EXTENSION_ID + "/actors"))
                        .findFirst()
                        .orElseThrow()
                        .type()
                        .version())
                .isEqualTo(2);
        assertThat(result.catalog().componentTypes())
                .extracting(type -> type.type().id().value())
                .containsExactly(
                        "io.github.glynch.jscene3d.doom/door",
                        "io.github.glynch.jscene3d.doom/floor",
                        EXTENSION_ID + "/player-state",
                        EXTENSION_ID + "/pickup",
                        EXTENSION_ID + "/combatant-state",
                        EXTENSION_ID + "/combatant-presentation",
                        EXTENSION_ID + "/enemy-target",
                        EXTENSION_ID + "/enemy-behavior",
                        EXTENSION_ID + "/hitscan-weapon",
                        EXTENSION_ID + "/weapon-presentation",
                        EXTENSION_ID + "/player-presentation",
                        EXTENSION_ID + "/player-lifecycle",
                        EXTENSION_ID + "/player-hud",
                        EXTENSION_ID + "/door-interactor",
                        EXTENSION_ID + "/door-presentation",
                        EXTENSION_ID + "/main-menu",
                        EXTENSION_ID + "/game-over-menu");
    }

    /** Declares combatant presentation and authoritative hitscan weapon contracts. */
    @Test
    void declaresCombatantAndWeaponContracts() {
        GameProject project = loadProject().project().orElseThrow();
        ExtensionCatalogLoadResult result = new ExtensionCatalogLoader(ENGINE_VERSION)
                .load(project, getClass().getClassLoader());

        assertThat(result.catalog()
                        .findComponent(DoomedCorridorsDescriptors.COMBATANT_PRESENTATION_TYPE)
                        .orElseThrow())
                .satisfies(presentation -> {
                    assertThat(presentation.updatePhases()).containsExactly(ComponentUpdatePhase.FRAME_UPDATE);
                    assertThat(presentation.properties())
                            .containsKeys(
                                    DoomedCorridorsDescriptors.COMBATANT_SOUND_REFERENCE_DISTANCE_PROPERTY,
                                    DoomedCorridorsDescriptors.COMBATANT_SOUND_MAXIMUM_DISTANCE_PROPERTY,
                                    DoomedCorridorsDescriptors.COMBATANT_SOUND_ROLLOFF_FACTOR_PROPERTY);
                    assertThat(presentation.actions())
                            .containsOnlyKeys(
                                    DoomedCorridorsDescriptors.RECEIVE_COMBATANT_ALERTED_ACTION,
                                    DoomedCorridorsDescriptors.RECEIVE_COMBATANT_MOVEMENT_STARTED_ACTION,
                                    DoomedCorridorsDescriptors.RECEIVE_COMBATANT_MOVEMENT_STOPPED_ACTION,
                                    DoomedCorridorsDescriptors.RECEIVE_COMBATANT_ATTACKED_ACTION,
                                    DoomedCorridorsDescriptors.RECEIVE_COMBATANT_HURT_ACTION,
                                    DoomedCorridorsDescriptors.RECEIVE_COMBATANT_DIED_ACTION);
                });
        assertThat(result.catalog()
                        .findComponent(DoomedCorridorsDescriptors.HITSCAN_WEAPON_TYPE)
                        .orElseThrow())
                .satisfies(weapon -> {
                    assertThat(weapon.providedCapabilities())
                            .containsExactly(DoomedCorridorsDescriptors.WEAPON_CAPABILITY);
                    assertThat(weapon.requiredCapabilities())
                            .containsExactly(DoomedCorridorsDescriptors.PLAYER_RESOURCES_CAPABILITY);
                    assertThat(weapon.updatePhases()).containsExactly(ComponentUpdatePhase.AFTER_PHYSICS);
                    assertThat(weapon.signals())
                            .containsOnlyKeys(
                                    DoomedCorridorsDescriptors.WEAPON_FIRED_SIGNAL,
                                    DoomedCorridorsDescriptors.WEAPON_HIT_SIGNAL);
                    assertThat(weapon.signals()
                                    .get(DoomedCorridorsDescriptors.WEAPON_HIT_SIGNAL)
                                    .payload())
                            .contains(DoomedCorridorsDescriptors.WEAPON_HIT_PAYLOAD_TYPE);
                });
    }

    /** Declares weapon and HUD presentation contracts independently of their runtime implementation. */
    @Test
    void declaresWeaponAndHudPresentationContracts() {
        GameProject project = loadProject().project().orElseThrow();
        ExtensionCatalogLoadResult result = new ExtensionCatalogLoader(ENGINE_VERSION)
                .load(project, getClass().getClassLoader());

        assertThat(result.catalog()
                        .findComponent(DoomedCorridorsDescriptors.WEAPON_PRESENTATION_TYPE)
                        .orElseThrow())
                .satisfies(presentation -> {
                    assertThat(presentation.updatePhases()).containsExactly(ComponentUpdatePhase.FRAME_UPDATE);
                    assertThat(presentation.actions())
                            .containsOnlyKeys(
                                    DoomedCorridorsDescriptors.RECEIVE_WEAPON_FIRED_ACTION,
                                    DoomedCorridorsDescriptors.RECEIVE_WEAPON_HIT_ACTION,
                                    DoomedCorridorsDescriptors.RECEIVE_PLAYER_DIED_ACTION);
                    assertThat(presentation.properties())
                            .containsKey(DoomedCorridorsDescriptors.WEAPON_DEATH_LOWER_MILLISECONDS_PROPERTY);
                    assertThat(presentation
                                    .actions()
                                    .get(DoomedCorridorsDescriptors.RECEIVE_WEAPON_HIT_ACTION)
                                    .payload())
                            .contains(DoomedCorridorsDescriptors.WEAPON_HIT_PAYLOAD_TYPE);
                });
        assertThat(result.catalog()
                        .findComponent(DoomedCorridorsDescriptors.PLAYER_HUD_TYPE)
                        .orElseThrow())
                .satisfies(hud -> {
                    assertThat(hud.updatePhases()).containsExactly(ComponentUpdatePhase.FRAME_UPDATE);
                    assertThat(hud.properties())
                            .containsOnlyKeys(
                                    DoomedCorridorsDescriptors.HUD_PLAYER_STATE_PROPERTY,
                                    DoomedCorridorsDescriptors.HUD_HEALTH_NUMBER_PROPERTY,
                                    DoomedCorridorsDescriptors.HUD_AMMO_NUMBER_PROPERTY);
                });
    }

    /** Declares positional normal and blaze sound resources for Doom door presentation. */
    @Test
    void declaresDoorPresentationContract() {
        GameProject project = loadProject().project().orElseThrow();
        ExtensionCatalogLoadResult result = new ExtensionCatalogLoader(ENGINE_VERSION)
                .load(project, getClass().getClassLoader());

        assertThat(result.catalog()
                        .findComponent(DoomedCorridorsDescriptors.DOOR_PRESENTATION_TYPE)
                        .orElseThrow())
                .satisfies(presentation -> {
                    assertThat(presentation.lifecycle()).containsExactly(ComponentLifecycle.CREATED);
                    assertThat(presentation.updatePhases()).containsExactly(ComponentUpdatePhase.AFTER_PHYSICS);
                    assertThat(presentation.properties())
                            .containsKeys(
                                    DoomedCorridorsDescriptors.NORMAL_DOOR_OPENING_SOUND_PROPERTY,
                                    DoomedCorridorsDescriptors.NORMAL_DOOR_CLOSING_SOUND_PROPERTY,
                                    DoomedCorridorsDescriptors.BLAZE_DOOR_OPENING_SOUND_PROPERTY,
                                    DoomedCorridorsDescriptors.BLAZE_DOOR_CLOSING_SOUND_PROPERTY);
                });
    }

    /** Declares independently authored player damage presentation and terminal lifecycle behavior. */
    @Test
    void declaresPlayerPresentationAndLifecycleContracts() {
        GameProject project = loadProject().project().orElseThrow();
        ExtensionCatalogLoadResult result = new ExtensionCatalogLoader(ENGINE_VERSION)
                .load(project, getClass().getClassLoader());

        assertThat(result.catalog()
                        .findComponent(DoomedCorridorsDescriptors.PLAYER_PRESENTATION_TYPE)
                        .orElseThrow())
                .satisfies(presentation -> {
                    assertThat(presentation.updatePhases()).containsExactly(ComponentUpdatePhase.FRAME_UPDATE);
                    assertThat(presentation.properties())
                            .containsOnlyKeys(
                                    DoomedCorridorsDescriptors.PLAYER_PAIN_SOUND_PROPERTY,
                                    DoomedCorridorsDescriptors.PLAYER_DEATH_SOUND_PROPERTY,
                                    DoomedCorridorsDescriptors.PLAYER_PAIN_FLASH_MILLISECONDS_PROPERTY,
                                    DoomedCorridorsDescriptors.PLAYER_PAIN_FLASH_OPACITY_PROPERTY,
                                    DoomedCorridorsDescriptors.PLAYER_DEATH_FLASH_MILLISECONDS_PROPERTY,
                                    DoomedCorridorsDescriptors.PLAYER_DEATH_FLASH_OPACITY_PROPERTY,
                                    DoomedCorridorsDescriptors.PLAYER_TERMINAL_SHADE_OPACITY_PROPERTY,
                                    DoomedCorridorsDescriptors.PLAYER_VIEW_TRANSFORM_PROPERTY,
                                    DoomedCorridorsDescriptors.PLAYER_DEATH_VIEW_DROP_DISTANCE_PROPERTY,
                                    DoomedCorridorsDescriptors.PLAYER_DEATH_VIEW_DROP_MILLISECONDS_PROPERTY);
                    assertThat(presentation.actions())
                            .containsOnlyKeys(
                                    DoomedCorridorsDescriptors.RECEIVE_PLAYER_HURT_ACTION,
                                    DoomedCorridorsDescriptors.RECEIVE_PLAYER_DIED_ACTION);
                });
        assertThat(result.catalog()
                        .findComponent(DoomedCorridorsDescriptors.PLAYER_LIFECYCLE_TYPE)
                        .orElseThrow())
                .satisfies(lifecycle -> {
                    assertThat(lifecycle.updatePhases())
                            .containsExactly(ComponentUpdatePhase.BEFORE_PHYSICS, ComponentUpdatePhase.FRAME_UPDATE);
                    assertThat(lifecycle.properties())
                            .containsOnlyKeys(
                                    DoomedCorridorsDescriptors.PLAYER_CONTROL_ENTITY_PROPERTY,
                                    DoomedCorridorsDescriptors.PLAYER_GAME_OVER_ENTITY_PROPERTY,
                                    DoomedCorridorsDescriptors.PLAYER_GAME_OVER_DELAY_MILLISECONDS_PROPERTY,
                                    DoomedCorridorsDescriptors.PLAYER_RETURN_TO_MENU_ACTION_PROPERTY);
                    assertThat(lifecycle.actions())
                            .containsOnlyKeys(DoomedCorridorsDescriptors.RECEIVE_PLAYER_DIED_ACTION);
                });
    }

    /** Declares game-over behavior separately from its generic screen presentation. */
    @Test
    void declaresGameOverMenuContract() {
        GameProject project = loadProject().project().orElseThrow();
        ExtensionCatalogLoadResult result = new ExtensionCatalogLoader(ENGINE_VERSION)
                .load(project, getClass().getClassLoader());

        assertThat(result.catalog()
                        .findComponent(DoomedCorridorsDescriptors.GAME_OVER_MENU_TYPE)
                        .orElseThrow())
                .satisfies(menu -> {
                    assertThat(menu.updatePhases()).containsExactly(ComponentUpdatePhase.BEFORE_PHYSICS);
                    assertThat(menu.properties())
                            .containsKeys(
                                    DoomedCorridorsDescriptors.GAME_OVER_RESTART_CURSOR_PROPERTY,
                                    DoomedCorridorsDescriptors.GAME_OVER_MAIN_MENU_CURSOR_PROPERTY,
                                    DoomedCorridorsDescriptors.GAME_OVER_REFERENCE_WIDTH_PROPERTY,
                                    DoomedCorridorsDescriptors.GAME_OVER_REFERENCE_HEIGHT_PROPERTY,
                                    DoomedCorridorsDescriptors.GAME_OVER_PREVIOUS_ACTION_PROPERTY,
                                    DoomedCorridorsDescriptors.GAME_OVER_NEXT_ACTION_PROPERTY,
                                    DoomedCorridorsDescriptors.GAME_OVER_CONFIRM_ACTION_PROPERTY);
                });
    }

    /** Declares reusable damage state and fixed-update enemy attack contracts. */
    @Test
    void declaresDamageAndEnemyBehaviorContracts() {
        GameProject project = loadProject().project().orElseThrow();
        ExtensionCatalogLoadResult result = new ExtensionCatalogLoader(ENGINE_VERSION)
                .load(project, getClass().getClassLoader());

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.catalog()
                        .findComponent(DoomedCorridorsDescriptors.PLAYER_STATE_TYPE)
                        .orElseThrow()
                        .providedCapabilities())
                .containsExactly(
                        DoomedCorridorsDescriptors.PLAYER_RESOURCES_CAPABILITY,
                        DoomedCorridorsDescriptors.DAMAGEABLE_CAPABILITY);
        assertThat(result.catalog()
                        .findComponent(DoomedCorridorsDescriptors.PLAYER_STATE_TYPE)
                        .orElseThrow()
                        .signals())
                .containsOnlyKeys(DoomedCorridorsDescriptors.HURT_SIGNAL, DoomedCorridorsDescriptors.DIED_SIGNAL);
        assertThat(result.catalog()
                        .findComponent(DoomedCorridorsDescriptors.COMBATANT_STATE_TYPE)
                        .orElseThrow()
                        .providedCapabilities())
                .containsExactly(
                        DoomedCorridorsDescriptors.DAMAGEABLE_CAPABILITY,
                        DoomedCorridorsDescriptors.HITSCAN_TARGET_CAPABILITY);
        assertThat(result.catalog()
                        .findComponent(DoomedCorridorsDescriptors.ENEMY_BEHAVIOR_TYPE)
                        .orElseThrow())
                .satisfies(behavior -> {
                    assertThat(behavior.updatePhases()).containsExactly(ComponentUpdatePhase.BEFORE_PHYSICS);
                    assertThat(behavior.signals())
                            .containsOnlyKeys(
                                    DoomedCorridorsDescriptors.ENEMY_ALERTED_SIGNAL,
                                    DoomedCorridorsDescriptors.ENEMY_MOVEMENT_STARTED_SIGNAL,
                                    DoomedCorridorsDescriptors.ENEMY_MOVEMENT_STOPPED_SIGNAL,
                                    DoomedCorridorsDescriptors.ENEMY_ATTACKED_SIGNAL);
                });
    }

    /** Loads the repository's project manifest. */
    private static ProjectLoadResult loadProject() {
        return new ProjectLoader(ENGINE_VERSION).load(Path.of("."));
    }
}
