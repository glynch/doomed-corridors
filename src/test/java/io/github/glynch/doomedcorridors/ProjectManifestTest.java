/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        assertThat(project.runtime().inputMap()).contains(project.root().resolve("application/input-map.json"));
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
                .containsExactly("actors", "combat", "combat-presentation", "player-capsule", "freedoom");
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
        assertThat(project.assets().get(4)).satisfies(asset -> {
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
                .containsEntry("combat-rules", new ProjectValue.TextValue("combat"));
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
        assertThat(actions).containsOnlyKeys("move", "look", "turn-left", "turn-right", "interact");
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
        assertThat(result.catalog().componentTypes())
                .extracting(type -> type.type().id().value())
                .containsExactly(EXTENSION_ID + "/player-state", EXTENSION_ID + "/pickup");
    }

    /** Loads the repository's project manifest. */
    private static ProjectLoadResult loadProject() {
        return new ProjectLoader(ENGINE_VERSION).load(Path.of("."));
    }
}
