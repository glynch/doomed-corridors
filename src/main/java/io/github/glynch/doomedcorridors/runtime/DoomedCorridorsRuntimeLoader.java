/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.runtime;

import io.github.glynch.jscene3d.project.diagnostic.ProjectDiagnostic;
import io.github.glynch.jscene3d.project.extension.ExtensionCatalogLoadResult;
import io.github.glynch.jscene3d.project.extension.ExtensionCatalogLoader;
import io.github.glynch.jscene3d.project.importing.ImportManager;
import io.github.glynch.jscene3d.project.importing.ImportState;
import io.github.glynch.jscene3d.project.importing.PreparedImport;
import io.github.glynch.jscene3d.project.imports.ImportDefinition;
import io.github.glynch.jscene3d.project.imports.ImportLoadResult;
import io.github.glynch.jscene3d.project.imports.ImportLoader;
import io.github.glynch.jscene3d.project.manifest.GameProject;
import io.github.glynch.jscene3d.project.manifest.ProjectLoadResult;
import io.github.glynch.jscene3d.project.manifest.ProjectLoader;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntime;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntimeLoadResult;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntimeLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Loads the declarative application after publishing every stale or missing project import. */
final class DoomedCorridorsRuntimeLoader {
    private static final String ENGINE_VERSION = "0.1.0-SNAPSHOT";

    /** Prevents construction of this stateless application bootstrap. */
    private DoomedCorridorsRuntimeLoader() {
        throw new AssertionError("DoomedCorridorsRuntimeLoader cannot be instantiated");
    }

    /** Composes the project through its descriptors, imports, and service-discovered extensions. */
    static ProjectRuntime load(Path projectDirectory) {
        ProjectLoadResult projectResult = new ProjectLoader(ENGINE_VERSION).load(projectDirectory);
        GameProject project = require(projectResult.project(), projectResult.diagnostics(), "project");
        ClassLoader classLoader = DoomedCorridorsRuntimeLoader.class.getClassLoader();
        ExtensionCatalogLoadResult catalogResult =
                new ExtensionCatalogLoader(ENGINE_VERSION).load(project, classLoader);
        if (!catalogResult.isComplete()) {
            throw failure("extension catalog", catalogResult.diagnostics());
        }
        ImportManager importManager = ImportManager.create(
                project,
                catalogResult.catalog(),
                project.root().resolve("target/import-cache"),
                classLoader,
                List.of());
        publishImports(project, importManager);
        ProjectRuntimeLoadResult runtimeResult =
                new ProjectRuntimeLoader(ENGINE_VERSION).load(project, classLoader, List.of(), importManager);
        return require(runtimeResult.runtime(), runtimeResult.diagnostics(), "project runtime");
    }

    /** Publishes each import when its source, definition, or dependencies changed. */
    private static void publishImports(GameProject project, ImportManager manager) {
        ImportLoader loader = new ImportLoader();
        for (Path path : project.imports()) {
            ImportLoadResult result = loader.load(project, path);
            ImportDefinition definition = require(result.definition(), result.diagnostics(), "import definition");
            if (manager.status(definition).state() != ImportState.CURRENT) {
                publish(manager, definition);
            }
        }
    }

    /** Atomically publishes one validated import generation. */
    private static void publish(ImportManager manager, ImportDefinition definition) {
        try (PreparedImport prepared = manager.prepare(definition)) {
            if (!prepared.preview().isValid()) {
                throw failure("import " + definition.id(), prepared.preview().diagnostics());
            }
            prepared.commit();
        }
    }

    /** Requires a successful loading value while retaining structured diagnostics. */
    private static <T> T require(Optional<T> value, List<ProjectDiagnostic> diagnostics, String description) {
        return value.orElseThrow(() -> failure(description, diagnostics));
    }

    /** Creates one readable bootstrap failure from structured diagnostics. */
    private static IllegalStateException failure(String description, List<ProjectDiagnostic> diagnostics) {
        return new IllegalStateException(description + " could not be loaded: " + diagnostics);
    }
}
