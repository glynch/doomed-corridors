/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.internal;

import io.github.glynch.jscene3d.project.extension.RegisteredType;

/** Stable registered type identities shared by application extension implementations. */
public final class DoomedCorridorsTypes {
    /** Application extension identity. */
    public static final String EXTENSION_IDENTIFIER = "io.github.glynch.doomed-corridors";

    /** Imported map-material resource type. */
    public static final RegisteredType MAP_MATERIALS =
            new RegisteredType(EXTENSION_IDENTIFIER + "/map-materials", 1);

    /** WAD map-material importer type. */
    public static final RegisteredType MAP_MATERIALS_IMPORTER =
            new RegisteredType(EXTENSION_IDENTIFIER + "/map-materials-importer", 1);

    /** Declarative Doom level scene-node type. */
    public static final RegisteredType DOOM_LEVEL_3D =
            new RegisteredType(EXTENSION_IDENTIFIER + "/doom-level-3d", 1);

    /** Player movement controller attached to a declarative Doom level. */
    public static final RegisteredType DOOM_PLAYER_CONTROLLER =
            new RegisteredType(EXTENSION_IDENTIFIER + "/doom-player-controller", 1);

    /** Prevents construction of this type-identity namespace. */
    private DoomedCorridorsTypes() {
        throw new AssertionError("DoomedCorridorsTypes cannot be instantiated");
    }
}
