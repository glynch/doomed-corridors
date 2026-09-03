package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.jscene3d.project.GameProject;
import io.github.glynch.jscene3d.project.ProjectDiagnostic;
import io.github.glynch.jscene3d.project.ProjectLoadResult;
import io.github.glynch.jscene3d.project.ProjectLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies the repository remains a loadable JScene3D game project. */
final class ProjectManifestTest {
    /** Loads project metadata with or without the ignored local WAD installation. */
    @Test
    void loadsDoomedCorridorsProject() {
        ProjectLoadResult result = new ProjectLoader("0.1.0-SNAPSHOT").load(Path.of("."));

        assertThat(result.isValid()).isTrue();
        GameProject project = result.project().orElseThrow();
        assertThat(project.identity().name()).isEqualTo("Doomed Corridors");
        assertThat(project.runtime().startup()).isEqualTo(new GameProject.StartupTarget("freedoom", "MAP01"));
        assertThat(project.assets()).singleElement().satisfies(asset -> {
            assertThat(asset.type()).isEqualTo("doom-wad");
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
                assertThat(diagnostic.location()).isEqualTo("/assets/0/path");
            });
        }
    }

    /** Keeps the editor-facing schema copy identical to the engine contract. */
    @Test
    void vendorsCurrentProjectSchema() throws IOException {
        String resource = "/META-INF/jscene3d/project/project-1.schema.json";
        String localSchema = Files.readString(Path.of("schema/project-1.schema.json"));

        try (var input = ProjectLoader.class.getResourceAsStream(resource)) {
            assertThat(input).isNotNull();
            String engineSchema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(localSchema).isEqualTo(engineSchema);
        }
    }
}
