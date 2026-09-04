/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.runtime;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsTypes;
import io.github.glynch.jscene3d.project.runtime.extension.ProjectRuntimeExtension;
import io.github.glynch.jscene3d.project.runtime.extension.ProjectRuntimeRegistry;
import java.util.Objects;

/** Registers executable scene types owned by the Doomed Corridors application. */
public final class DoomedCorridorsRuntimeExtension implements ProjectRuntimeExtension {
    /** Creates the stateless service-discovered application extension. */
    public DoomedCorridorsRuntimeExtension() {
        // Public construction is required by ServiceLoader.
    }

    @Override
    public String id() {
        return DoomedCorridorsTypes.EXTENSION_IDENTIFIER;
    }

    @Override
    public void register(ProjectRuntimeRegistry registry) {
        ProjectRuntimeRegistry validRegistry = Objects.requireNonNull(registry, "registry");
        validRegistry.registerResource(
                DoomedCorridorsTypes.MAP_MATERIALS,
                DoomMapMaterialsResourceDecoder::decode);
        validRegistry.registerSceneNode(DoomedCorridorsTypes.DOOM_LEVEL_3D, DoomLevel3d::create);
    }
}
