/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.importing.internal;

import io.github.glynch.jscene3d.project.extension.RegisteredType;
import io.github.glynch.jscene3d.project.importing.extension.ProjectImportExtension;
import io.github.glynch.jscene3d.project.importing.extension.ProjectImportRegistry;
import java.util.Objects;

/** Registers game-owned Doom actor publication without taking ownership of generic WAD or map decoding. */
public final class DoomedCorridorsImportExtension implements ProjectImportExtension {
    static final String EXTENSION_ID = "io.github.glynch.doomed-corridors";
    static final RegisteredType ACTOR_IMPORTER = new RegisteredType(EXTENSION_ID + "/actors", 2);

    /** Creates the stateless service-discovered import extension. */
    public DoomedCorridorsImportExtension() {
        // Public construction is required by ServiceLoader on the class path and module path.
    }

    @Override
    public String id() {
        return EXTENSION_ID;
    }

    @Override
    public void register(ProjectImportRegistry registry) {
        Objects.requireNonNull(registry, "registry")
                .registerImporter(ACTOR_IMPORTER, new DoomedCorridorsActorImporter());
    }
}
