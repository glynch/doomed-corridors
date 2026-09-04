/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.importing;

import io.github.glynch.doomedcorridors.importing.internal.DoomMapMaterialsProjectImporter;
import io.github.glynch.doomedcorridors.internal.DoomedCorridorsTypes;
import io.github.glynch.jscene3d.project.importing.extension.ProjectImportExtension;
import io.github.glynch.jscene3d.project.importing.extension.ProjectImportRegistry;
import java.util.Objects;

/** Registers application-owned import adapters used by declarative project resources. */
public final class DoomedCorridorsImportExtension implements ProjectImportExtension {
    /** Creates the stateless service-discovered import extension. */
    public DoomedCorridorsImportExtension() {
        // Public construction is required by ServiceLoader.
    }

    @Override
    public String id() {
        return DoomedCorridorsTypes.EXTENSION_IDENTIFIER;
    }

    @Override
    public void register(ProjectImportRegistry registry) {
        Objects.requireNonNull(registry, "registry")
                .registerImporter(
                        DoomedCorridorsTypes.MAP_MATERIALS_IMPORTER,
                        new DoomMapMaterialsProjectImporter());
    }
}
