/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.actor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Specifies provider-owned actor catalog loading and validation. */
final class DoomActorCatalogLoaderTest {
    @TempDir
    Path temporaryDirectory;

    /** Loads the checked-in catalog covering every MAP01 thing type. */
    @Test
    void loadsProjectActorCatalog() {
        DoomActorCatalogLoadResult result = new DoomActorCatalogLoader().load(Path.of("game/actors.json"));

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        DoomActorCatalog catalog = result.catalog().orElseThrow();
        assertThat(catalog.definitions()).hasSize(37);
        assertThat(catalog.definition(3004)).hasValueSatisfying(definition -> {
            assertThat(definition.id()).isEqualTo("zombieman");
            assertThat(definition.category()).isEqualTo(DoomActorCategory.ENEMY);
            assertThat(definition.spriteFrame()).contains("POSSA");
        });
        assertThat(catalog.definition(14)).hasValueSatisfying(definition -> {
            assertThat(definition.category()).isEqualTo(DoomActorCategory.MARKER);
            assertThat(definition.spriteFrame()).isEmpty();
        });
    }

    /** Rejects duplicate provider identifiers rather than selecting one implicitly. */
    @Test
    void rejectsDuplicateActorIds() throws IOException {
        Path source = temporaryDirectory.resolve("actors.json");
        Files.writeString(
                source,
                """
                {
                  "schemaVersion": 1,
                  "actors": [
                    { "thingType": 1, "id": "same", "name": "First", "category": "marker" },
                    { "thingType": 2, "id": "same", "name": "Second", "category": "marker" }
                  ]
                }
                """);

        DoomActorCatalogLoadResult result = new DoomActorCatalogLoader().load(source);

        assertThat(result.catalog()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.actor.catalog-invalid");
            assertThat(diagnostic.message()).contains("Duplicate actor id: same");
        });
    }

    /** Rejects malformed actor identifiers without relying on a backtracking regular expression. */
    @Test
    void rejectsMalformedActorId() {
        Optional<String> spriteFrame = Optional.of("BADDA");

        assertThatThrownBy(() -> new DoomActorDefinition(
                        1, "bad--id", "Malformed", DoomActorCategory.ENEMY, spriteFrame))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id has an invalid value: bad--id");
    }

    /** Rejects future schemas before attempting to interpret their actor fields. */
    @Test
    void rejectsUnsupportedSchemaVersion() throws IOException {
        Path source = temporaryDirectory.resolve("future.json");
        Files.writeString(source, "{\"schemaVersion\":2,\"actors\":[]}");

        DoomActorCatalogLoadResult result = new DoomActorCatalogLoader().load(source);

        assertThat(result.catalog()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.actor.catalog-version");
            assertThat(diagnostic.location()).isEqualTo("/schemaVersion");
        });
    }
}
