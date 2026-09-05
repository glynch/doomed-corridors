/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import io.github.glynch.jscene3d.game.GameRuntime;
import io.github.glynch.jscene3d.game.input.ActionSnapshot;
import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.project.extension.RegisteredType;
import io.github.glynch.jscene3d.project.importing.ImportArtifactDescriptor;
import io.github.glynch.jscene3d.project.importing.ImportedArtifact;
import io.github.glynch.jscene3d.project.importing.ImportedArtifactLookup;
import io.github.glynch.jscene3d.project.importing.ImportedArtifactMetadata;
import io.github.glynch.jscene3d.project.imports.ImportDefinition;
import io.github.glynch.jscene3d.project.manifest.GameProject;
import io.github.glynch.jscene3d.project.manifest.ProjectLoader;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntime;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntimeLoadResult;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntimeLoader;
import io.github.glynch.jscene3d.project.runtime.scene3d.JScene3dRuntimeExtension;
import io.github.glynch.jscene3d.project.runtime.scene3d.Scene3dRuntimeObject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Exercises imported map presentation through the generic project loader. */
final class DoomedCorridorsRuntimeExtensionTest {
    private static final RegisteredType MAP_TYPE =
            new RegisteredType("io.github.glynch.jscene3d.doom/map", 1);
    private static final RegisteredType MATERIALS_TYPE =
            new RegisteredType("io.github.glynch.doomed-corridors/map-materials", 1);
    private static final String MAP_RESOURCE = """
            {
              "schemaVersion": 1,
              "type": "io.github.glynch.jscene3d.doom/map",
              "typeVersion": 1,
              "properties": {
                "name": "MAP01",
                "source": {
                  "asset": "freedoom",
                  "archiveKind": "IWAD",
                  "size": 1,
                  "sha256": "a8772e088847032510d97ba2312406a6998f21cbab44d4ff10696faa9c0ecd4b"
                },
                "things": [{"x": 64, "y": 64, "angle": 0, "type": 1, "flags": 7}],
                "geometry": {
                  "vertices": [
                    {"x": 0, "y": 0},
                    {"x": 0, "y": 128},
                    {"x": 128, "y": 128},
                    {"x": 128, "y": 0}
                  ],
                  "linedefs": [
                    {"startVertex": 0, "endVertex": 1, "flags": 0, "special": 0, "tag": 0, "rightSidedef": 0, "leftSidedef": -1},
                    {"startVertex": 1, "endVertex": 2, "flags": 0, "special": 0, "tag": 0, "rightSidedef": 1, "leftSidedef": -1},
                    {"startVertex": 2, "endVertex": 3, "flags": 0, "special": 0, "tag": 0, "rightSidedef": 2, "leftSidedef": -1},
                    {"startVertex": 3, "endVertex": 0, "flags": 0, "special": 0, "tag": 0, "rightSidedef": 3, "leftSidedef": -1}
                  ],
                  "sidedefs": [
                    {"xOffset": 0, "yOffset": 0, "upperTexture": "-", "lowerTexture": "-", "middleTexture": "WALL", "sector": 0},
                    {"xOffset": 0, "yOffset": 0, "upperTexture": "-", "lowerTexture": "-", "middleTexture": "WALL", "sector": 0},
                    {"xOffset": 0, "yOffset": 0, "upperTexture": "-", "lowerTexture": "-", "middleTexture": "WALL", "sector": 0},
                    {"xOffset": 0, "yOffset": 0, "upperTexture": "-", "lowerTexture": "-", "middleTexture": "WALL", "sector": 0}
                  ],
                  "sectors": [{"floorHeight": 0, "ceilingHeight": 128, "floorTexture": "FLOOR", "ceilingTexture": "CEILING", "lightLevel": 160, "special": 0, "tag": 0}]
                },
                "bsp": {
                  "segs": [
                    {"startVertex": 0, "endVertex": 1, "angle": 0, "linedef": 0, "direction": 0, "offset": 0},
                    {"startVertex": 1, "endVertex": 2, "angle": 0, "linedef": 1, "direction": 0, "offset": 0},
                    {"startVertex": 2, "endVertex": 3, "angle": 0, "linedef": 2, "direction": 0, "offset": 0},
                    {"startVertex": 3, "endVertex": 0, "angle": 0, "linedef": 3, "direction": 0, "offset": 0}
                  ],
                  "subsectors": [{"segCount": 4, "firstSeg": 0}],
                  "nodes": []
                },
                "reject": [0],
                "blockmap": {"originX": 0, "originY": 0, "columns": 1, "rows": 1, "cells": [[]]}
              }
            }
            """;
    private static final String MATERIALS_RESOURCE = """
            {
              "schemaVersion": 1,
              "type": "io.github.glynch.doomed-corridors/map-materials",
              "typeVersion": 1,
              "properties": {
                "map": "MAP01",
                "wall-textures": [
                  {"name": "WALL", "width": 1, "height": 1, "pixels-base64": "/////w=="}
                ],
                "flats": [
                  {"name": "FLOOR", "width": 1, "height": 1, "pixels-base64": "/////w=="},
                  {"name": "CEILING", "width": 1, "height": 1, "pixels-base64": "/////w=="}
                ]
              }
            }
            """;

    /** Resolves imported resources, derives the map, and advances its declared player controller. */
    @Test
    void composesImportedMapAsDoomLevel() {
        GameProject project = new ProjectLoader("0.1.0-SNAPSHOT")
                .load(Path.of("."))
                .project()
                .orElseThrow();
        ProjectRuntimeLoadResult result = new ProjectRuntimeLoader("0.1.0-SNAPSHOT")
                .load(
                        project,
                        getClass().getClassLoader(),
                        List.of(JScene3dRuntimeExtension.headless()),
                        new ResourceArtifactLookup());

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.isOpen()).isTrue();
        ProjectRuntime projectRuntime = result.runtime().orElseThrow();
        var levelNode = projectRuntime.root().children().getFirst();
        DoomLevel3d level = (DoomLevel3d) levelNode.object();
        var playerNode = levelNode.children().getFirst();
        DoomPlayerController controller =
                (DoomPlayerController) playerNode.controller().orElseThrow();
        float initialX = controller.player().x();
        try (GameRuntime runtime = new GameRuntime(projectRuntime)) {
            assertThat(level.isStarted()).isFalse();
            assertThat(level.map().name()).isEqualTo("MAP01");
            assertThat(level.map()).isInstanceOf(DoomMap.class);
            assertThat(level.surfaceCount()).isEqualTo(6);
            assertThat(level.object3d().children()).hasSize(7);
            assertThat(playerNode.definition().id()).isEqualTo("player");
            assertThat(playerNode.children()).singleElement().satisfies(camera ->
                    assertThat(camera.definition().id()).isEqualTo("camera"));

            runtime.start();
            runtime.advance(
                    Duration.ofMillis(30),
                    ActionSnapshot.builder()
                            .down(new InputAction("move-forward"))
                            .build());

            assertThat(level.isStarted()).isTrue();
            assertThat(controller.player().x()).isGreaterThan(initialX);
            Scene3dRuntimeObject playerObject = (Scene3dRuntimeObject) playerNode.object();
            assertThat(playerObject.object3d().position().x()).isEqualTo(controller.player().x());
        }
    }

    /** Supplies both expected native resources without the ignored Freedoom installation. */
    private static final class ResourceArtifactLookup implements ImportedArtifactLookup {
        @Override
        public Optional<ImportedArtifact> openArtifact(
                ImportDefinition definition, String identity) {
            if (definition.id().equals("freedoom-maps") && identity.equals("maps/MAP01")) {
                return Optional.of(new TestArtifact(identity, MAP_TYPE, MAP_RESOURCE));
            }
            if (definition.id().equals("freedoom-map-materials")
                    && identity.equals("materials/MAP01")) {
                return Optional.of(new TestArtifact(identity, MATERIALS_TYPE, MATERIALS_RESOURCE));
            }
            return Optional.empty();
        }
    }

    /** In-memory imported-resource handle used by the project runtime test. */
    private static final class TestArtifact implements ImportedArtifact {
        private final byte[] content;
        private final ImportedArtifactMetadata metadata;
        private boolean closed;

        /** Stores one typed resource document and derives its immutable metadata. */
        private TestArtifact(String identity, RegisteredType type, String resource) {
            content = resource.getBytes(StandardCharsets.UTF_8);
            metadata = new ImportedArtifactMetadata(
                    ImportArtifactDescriptor.resource(identity, type, List.of()),
                    "0000000000000000000000000000000000000000000000000000000000000000",
                    content.length);
        }

        @Override
        public ImportedArtifactMetadata metadata() {
            requireOpen();
            return metadata;
        }

        @Override
        public InputStream openStream() {
            requireOpen();
            return new ByteArrayInputStream(content);
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }

        /** Rejects reads after the runtime closes this owned handle. */
        private void requireOpen() {
            if (closed) {
                throw new IllegalStateException("artifact is closed");
            }
        }
    }
}
