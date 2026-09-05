package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.jscene3d.project.diagnostic.ProjectDiagnostic;
import io.github.glynch.jscene3d.project.extension.ExtensionCatalogLoadResult;
import io.github.glynch.jscene3d.project.extension.ExtensionCatalogLoader;
import io.github.glynch.jscene3d.project.imports.ImportDefinition;
import io.github.glynch.jscene3d.project.imports.ImportLoadResult;
import io.github.glynch.jscene3d.project.imports.ImportLoader;
import io.github.glynch.jscene3d.project.input.InputMapLoadResult;
import io.github.glynch.jscene3d.project.input.InputMapLoader;
import io.github.glynch.jscene3d.project.manifest.GameProject;
import io.github.glynch.jscene3d.project.manifest.ProjectLoadResult;
import io.github.glynch.jscene3d.project.manifest.ProjectLoader;
import io.github.glynch.jscene3d.project.scene.SceneDefinition;
import io.github.glynch.jscene3d.project.scene.SceneLoadResult;
import io.github.glynch.jscene3d.project.scene.SceneLoader;
import io.github.glynch.jscene3d.project.scene.SceneNodeDefinition;
import io.github.glynch.jscene3d.project.value.ProjectValue;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies the repository remains a loadable JScene3D game project. */
final class ProjectManifestTest {
    private static final String ENGINE_VERSION = "0.1.0-SNAPSHOT";
    private static final String EXTENSION_ID = "io.github.glynch.doomed-corridors";

    /** Loads the project's identity and runtime metadata. */
    @Test
    void loadsDoomedCorridorsProject() {
        ProjectLoadResult result = loadProject();

        assertThat(result.isValid()).isTrue();
        GameProject project = result.project().orElseThrow();
        assertThat(project.identity().name()).isEqualTo("Doomed Corridors");
        assertThat(project.runtime().applicationExtension()).isEqualTo(EXTENSION_ID);
        assertThat(project.runtime().entryScene()).isEqualTo(project.root().resolve("application/main.scene.json"));
        assertThat(project.runtime().inputMap())
                .contains(project.root().resolve("application/input-map.json"));
        assertThat(project.imports())
                .containsExactly(
                        project.root().resolve("imports/freedoom-maps.import.json"),
                        project.root().resolve("imports/freedoom-map-materials.import.json"));
    }

    /** Loads declared assets with or without the ignored local WAD installation. */
    @Test
    void loadsDoomedCorridorsAssets() {
        ProjectLoadResult result = loadProject();
        GameProject project = result.project().orElseThrow();

        assertThat(project.assets())
                .extracting(GameProject.AssetSource::id)
                .containsExactly("actors", "combat", "combat-presentation", "freedoom");
        assertThat(project.assets().getFirst()).satisfies(asset -> {
            assertThat(asset.type()).isEqualTo(EXTENSION_ID + "/actor-catalog");
            assertThat(asset.path()).isEqualTo(project.root().resolve("game/actors.json"));
            assertThat(asset.sha256()).isEmpty();
        });
        assertThat(project.assets().get(1)).satisfies(asset -> {
            assertThat(asset.type()).isEqualTo(EXTENSION_ID + "/combat-rules");
            assertThat(asset.path()).isEqualTo(project.root().resolve("game/combat.json"));
            assertThat(asset.sha256()).isEmpty();
        });
        assertThat(project.assets().get(2)).satisfies(asset -> {
            assertThat(asset.type()).isEqualTo(EXTENSION_ID + "/combat-presentation");
            assertThat(asset.path()).isEqualTo(project.root().resolve("game/combat-presentation.json"));
            assertThat(asset.sha256()).isEmpty();
        });
        assertThat(project.assets().get(3)).satisfies(asset -> {
            assertThat(asset.type()).isEqualTo("io.github.glynch.jscene3d.wad/source");
            assertThat(asset.path()).isEqualTo(project.root().resolve("assets/freedoom2.wad"));
            assertThat(asset.sha256())
                    .contains("a8772e088847032510d97ba2312406a6998f21cbab44d4ff10696faa9c0ecd4b");
        });
        if (Files.isRegularFile(project.root().resolve("assets/freedoom2.wad"))) {
            assertThat(result.diagnostics()).isEmpty();
        } else {
            assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
                assertThat(diagnostic.severity()).isEqualTo(ProjectDiagnostic.Severity.WARNING);
                assertThat(diagnostic.code()).isEqualTo("project.path.missing");
                assertThat(diagnostic.location()).isEqualTo("/assets/3/path");
            });
        }
    }

    /** Loads and catalog-validates the declarative Doom level entry scene. */
    @Test
    void loadsDoomLevelEntryScene() {
        GameProject project = loadProject().project().orElseThrow();
        SceneLoadResult sceneResult = new SceneLoader().loadEntryScene(project);

        assertThat(sceneResult.isValid()).isTrue();
        assertThat(sceneResult.diagnostics()).isEmpty();
        SceneDefinition scene = sceneResult.scene().orElseThrow();
        assertThat(scene.id()).isEqualTo("main");
        assertThat(scene.root().source()).isInstanceOf(SceneNodeDefinition.TypedNode.class);
        SceneNodeDefinition.TypedNode root = (SceneNodeDefinition.TypedNode) scene.root().source();
        assertThat(root.type().id()).isEqualTo("io.github.glynch.jscene3d/group-3d");
        assertThat(scene.root().children()).hasSize(1);
        SceneNodeDefinition levelDefinition = scene.root().children().getFirst();
        assertThat(levelDefinition.source()).isInstanceOf(SceneNodeDefinition.TypedNode.class);
        SceneNodeDefinition.TypedNode level =
                (SceneNodeDefinition.TypedNode) levelDefinition.source();
        assertThat(level.type().id()).isEqualTo(EXTENSION_ID + "/doom-level-3d");
        assertThat(level.properties()).containsOnlyKeys("map", "materials");
        assertThat(level.properties().get("map"))
                .isEqualTo(new ProjectValue.ReferenceValue(
                        ResourceReference.imported("freedoom-maps/maps/MAP01")));
        assertThat(level.properties().get("materials"))
                .isEqualTo(new ProjectValue.ReferenceValue(ResourceReference.imported(
                        "freedoom-map-materials/materials/MAP01")));
        assertThat(levelDefinition.controller()).isPresent();
        assertThat(levelDefinition.children()).singleElement()
                .extracting(SceneNodeDefinition::id)
                .isEqualTo("camera");

        ExtensionCatalogLoadResult catalogResult = new ExtensionCatalogLoader(ENGINE_VERSION)
                .load(project, ProjectManifestTest.class.getClassLoader());
        assertThat(catalogResult.diagnostics()).isEmpty();
        assertThat(catalogResult.catalog().validate(scene)).isEmpty();
    }

    /** Keeps the editor-facing schema copy identical to the engine contract. */
    @Test
    void vendorsCurrentProjectSchema() throws IOException {
        assertBundledSchemaMatches("project-1.schema.json");
    }

    /** Keeps the editor-facing scene schema copy identical to the engine contract. */
    @Test
    void vendorsCurrentSceneSchema() throws IOException {
        assertBundledSchemaMatches("scene-1.schema.json");
    }

    /** Loads the semantic input map selected by the project manifest. */
    @Test
    void loadsProjectInputMap() {
        GameProject project = loadProject().project().orElseThrow();

        InputMapLoadResult result = new InputMapLoader().load(project, project.runtime().inputMap().orElseThrow());

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.definition().orElseThrow().actions())
                .containsOnlyKeys(
                        "move-forward",
                        "move-backward",
                        "strafe-left",
                        "strafe-right",
                        "turn-left",
                        "turn-right");
    }

    /** Keeps the editor-facing input-map schema copy identical to the engine contract. */
    @Test
    void vendorsCurrentInputMapSchema() throws IOException {
        assertBundledSchemaMatches("input-map-1.schema.json");
    }

    /** Loads the MAP01 import definition through its declared generic Doom importer. */
    @Test
    void loadsFreedoomMapImport() {
        GameProject project = loadProject().project().orElseThrow();
        ImportLoadResult result = new ImportLoader().load(project, project.imports().getFirst());

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        ImportDefinition definition = result.definition().orElseThrow();
        assertThat(definition.id()).isEqualTo("freedoom-maps");
        assertThat(definition.asset().id()).isEqualTo("freedoom");
        assertThat(definition.importer()).isEqualTo("io.github.glynch.jscene3d.doom/maps");
        assertThat(definition.selection()).containsExactly("maps/MAP01");
    }

    /** Loads the MAP01 material import independently from runtime presentation. */
    @Test
    void loadsFreedoomMapMaterialsImport() {
        GameProject project = loadProject().project().orElseThrow();
        ImportLoadResult result = new ImportLoader().load(project, project.imports().get(1));

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        ImportDefinition definition = result.definition().orElseThrow();
        assertThat(definition.id()).isEqualTo("freedoom-map-materials");
        assertThat(definition.asset().id()).isEqualTo("freedoom");
        assertThat(definition.importer())
                .isEqualTo(EXTENSION_ID + "/map-materials-importer");
        assertThat(definition.selection()).containsExactly("materials/MAP01");
    }

    /** Keeps the editor-facing import schema copy identical to the engine contract. */
    @Test
    void vendorsCurrentImportSchema() throws IOException {
        assertBundledSchemaMatches("import-1.schema.json");
    }

    /** Compares a vendored schema with the engine resource of the same name. */
    private static void assertBundledSchemaMatches(String name) throws IOException {
        String resource = "/META-INF/jscene3d/project/" + name;
        String localSchema = Files.readString(Path.of("schema").resolve(name));

        try (var input = ProjectLoader.class.getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            String engineSchema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(localSchema).isEqualTo(engineSchema);
        }
    }

    /** Loads the repository's project manifest. */
    private static ProjectLoadResult loadProject() {
        return new ProjectLoader(ENGINE_VERSION).load(Path.of("."));
    }
}
