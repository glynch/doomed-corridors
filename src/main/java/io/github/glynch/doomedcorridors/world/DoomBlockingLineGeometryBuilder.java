/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds full-height collision barriers for linedefs that Doom declares unconditionally blocking. */
public final class DoomBlockingLineGeometryBuilder {
    private static final int BLOCKING_LINE = 0x0001;
    private static final float MINIMUM_HEIGHT = DoomUnits.toWorld(Short.MIN_VALUE);
    private static final float MAXIMUM_HEIGHT = DoomUnits.toWorld(Short.MAX_VALUE);

    /** Builds one double-sided vertical barrier for every one-sided or explicitly blocking linedef. */
    public List<DoomMeshData> build(DoomMap map) {
        DoomMap validMap = Objects.requireNonNull(map, "map");
        List<DoomMeshData> barriers = new ArrayList<>();
        for (DoomMap.Linedef linedef : validMap.linedefs()) {
            if (blocks(linedef) && hasLength(validMap, linedef)) {
                barriers.add(barrier(validMap, linedef));
            }
        }
        return List.copyOf(barriers);
    }

    /** Returns whether source semantics make this line unconditionally impassable. */
    private static boolean blocks(DoomMap.Linedef linedef) {
        return linedef.leftSidedef() < 0 || (linedef.flags() & BLOCKING_LINE) != 0;
    }

    /** Excludes zero-length source lines which cannot define a collision plane. */
    private static boolean hasLength(DoomMap map, DoomMap.Linedef linedef) {
        DoomMap.Vertex start = map.vertices().get(linedef.startVertex());
        DoomMap.Vertex end = map.vertices().get(linedef.endVertex());
        return start.x() != end.x() || start.y() != end.y();
    }

    /** Extrudes one source line across the complete signed Doom height domain. */
    private static DoomMeshData barrier(DoomMap map, DoomMap.Linedef linedef) {
        DoomMap.Vertex start = map.vertices().get(linedef.startVertex());
        DoomMap.Vertex end = map.vertices().get(linedef.endVertex());
        float startX = DoomUnits.toWorld(start.x());
        float startZ = DoomUnits.yToWorldZ(start.y());
        float endX = DoomUnits.toWorld(end.x());
        float endZ = DoomUnits.yToWorldZ(end.y());
        float directionX = endX - startX;
        float directionZ = endZ - startZ;
        float inverseLength = 1.0F / (float) Math.hypot(directionX, directionZ);
        float normalX = -directionZ * inverseLength;
        float normalZ = directionX * inverseLength;
        return new DoomMeshData(
                new float[] {
                    startX, MINIMUM_HEIGHT, startZ,
                    endX, MINIMUM_HEIGHT, endZ,
                    endX, MAXIMUM_HEIGHT, endZ,
                    startX, MAXIMUM_HEIGHT, startZ
                },
                new float[] {
                    normalX, 0.0F, normalZ,
                    normalX, 0.0F, normalZ,
                    normalX, 0.0F, normalZ,
                    normalX, 0.0F, normalZ
                },
                new float[8],
                new int[] {0, 1, 2, 0, 2, 3});
    }
}
