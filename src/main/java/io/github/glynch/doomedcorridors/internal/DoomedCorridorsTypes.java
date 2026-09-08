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

    /** WAD-to-project map presentation importer type. */
    public static final RegisteredType MAP_IMPORTER = new RegisteredType(EXTENSION_IDENTIFIER + "/map-importer", 1);

    /** Prevents construction of this type-identity namespace. */
    private DoomedCorridorsTypes() {
        throw new AssertionError("DoomedCorridorsTypes cannot be instantiated");
    }
}
