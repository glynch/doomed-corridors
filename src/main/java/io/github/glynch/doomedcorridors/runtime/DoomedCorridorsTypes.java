/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.runtime;

import io.github.glynch.jscene3d.project.extension.RegisteredType;

/** Registered types implemented by the Doomed Corridors application extension. */
final class DoomedCorridorsTypes {
    static final String EXTENSION_IDENTIFIER = "io.github.glynch.doomed-corridors";
    static final RegisteredType DOOM_LEVEL_3D = new RegisteredType(EXTENSION_IDENTIFIER + "/doom-level-3d", 1);

    /** Prevents construction of this type-identity namespace. */
    private DoomedCorridorsTypes() {
        throw new AssertionError("DoomedCorridorsTypes cannot be instantiated");
    }
}
