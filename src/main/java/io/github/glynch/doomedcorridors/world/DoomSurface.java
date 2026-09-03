/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import java.util.Objects;

/** One textured static surface with source-map provenance. */
public record DoomSurface(Type type, String materialName, int sectorIndex, DoomMeshData mesh) {
    /** Supported static surface roles. */
    public enum Type {
        /** A BSP-subsector floor plane. */
        FLOOR,
        /** A BSP-subsector ceiling plane. */
        CEILING,
        /** An opaque middle texture on a one-sided linedef. */
        MIDDLE_WALL,
        /** An upper texture above a neighboring ceiling. */
        UPPER_WALL,
        /** A lower texture below a neighboring floor. */
        LOWER_WALL,
        /** A masked middle texture inside a two-sided opening. */
        MASKED_MIDDLE_WALL
    }

    /** Creates a validated surface. */
    public DoomSurface {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(materialName, "materialName");
        Objects.requireNonNull(mesh, "mesh");
        if (materialName.isBlank()) {
            throw new IllegalArgumentException("materialName must not be blank");
        }
        if (sectorIndex < 0) {
            throw new IllegalArgumentException("sectorIndex must not be negative");
        }
    }
}
