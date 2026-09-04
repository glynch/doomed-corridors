/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.jscene3d.doom.map.DoomMap;
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
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Exercises the application's runtime extension through the generic project loader. */
final class DoomedCorridorsRuntimeExtensionTest {
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
                "things": [],
                "geometry": {
                  "vertices": [],
                  "linedefs": [],
                  "sidedefs": [],
                  "sectors": []
                },
                "bsp": {
                  "segs": [],
                  "subsectors": [],
                  "nodes": []
                },
                "reject": [],
                "blockmap": {
                  "originX": 0,
                  "originY": 0,
                  "columns": 0,
                  "rows": 0,
                  "cells": []
                }
              }
            }
            """;

    /** Resolves the authored import reference into a typed map owned by the level node. */
    @Test
    void composesImportedMapAsDoomLevel() {
        GameProject project = new ProjectLoader("0.1.0-SNAPSHOT")
                .load(Path.of("."))
                .project()
                .orElseThrow();
        ProjectRuntimeLoadResult result = new ProjectRuntimeLoader("0.1.0-SNAPSHOT")
                .load(project, getClass().getClassLoader(), List.of(), new MapArtifactLookup());

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.isOpen()).isTrue();
        try (ProjectRuntime runtime = result.runtime().orElseThrow()) {
            assertThat(runtime.root().object()).isInstanceOf(DoomLevel3d.class);
            DoomLevel3d level = (DoomLevel3d) runtime.root().object();
            assertThat(level.isStarted()).isFalse();
            assertThat(level.map().name()).isEqualTo("MAP01");
            assertThat(level.map()).isInstanceOf(DoomMap.class);

            runtime.start();

            assertThat(level.isStarted()).isTrue();
        }
    }

    /** Supplies the expected native resource without requiring the ignored Freedoom installation. */
    private static final class MapArtifactLookup implements ImportedArtifactLookup {
        @Override
        public Optional<ImportedArtifact> openArtifact(ImportDefinition definition, String identity) {
            if (!definition.id().equals("freedoom-maps") || !identity.equals("maps/MAP01")) {
                return Optional.empty();
            }
            return Optional.of(new MapArtifact());
        }
    }

    /** In-memory imported-resource handle used by the project runtime test. */
    private static final class MapArtifact implements ImportedArtifact {
        private static final byte[] CONTENT = MAP_RESOURCE.getBytes(StandardCharsets.UTF_8);
        private static final ImportedArtifactMetadata METADATA = new ImportedArtifactMetadata(
                ImportArtifactDescriptor.resource(
                        "maps/MAP01", new RegisteredType("io.github.glynch.jscene3d.doom/map", 1), List.of()),
                "0000000000000000000000000000000000000000000000000000000000000000",
                CONTENT.length);
        private boolean closed;

        @Override
        public ImportedArtifactMetadata metadata() {
            requireOpen();
            return METADATA;
        }

        @Override
        public InputStream openStream() {
            requireOpen();
            return new ByteArrayInputStream(CONTENT);
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
