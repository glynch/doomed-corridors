/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Specifies collision barriers derived from Doom linedef semantics rather than presentation surfaces. */
final class DoomBlockingLineGeometryBuilderTest {
    @Test
    void buildsFullHeightBarriersForSolidLinesOnly() {
        DoomMap map = mapWithOneSidedBlockingAndPortalLines();

        List<DoomMeshData> barriers = new DoomBlockingLineGeometryBuilder().build(map);

        assertThat(barriers).hasSize(2).allSatisfy(barrier -> {
            assertThat(barrier.vertexCount()).isEqualTo(4);
            assertThat(barrier.triangleCount()).isEqualTo(2);
            assertThat(barrier.positions())
                    .contains(DoomUnits.toWorld(Short.MIN_VALUE), DoomUnits.toWorld(Short.MAX_VALUE));
        });
        assertThat(barriers.get(0).position(0)).containsExactly(0.0F, -1024.0F, 0.0F);
        assertThat(barriers.get(1).position(0)).containsExactly(0.0F, -1024.0F, -1.0F);
    }

    /** Creates a one-sided line, an explicitly blocking two-sided line, and an ordinary portal. */
    private static DoomMap mapWithOneSidedBlockingAndPortalLines() {
        List<DoomMap.Vertex> vertices = List.of(
                new DoomMap.Vertex(0, 0),
                new DoomMap.Vertex(32, 0),
                new DoomMap.Vertex(0, 32),
                new DoomMap.Vertex(32, 32),
                new DoomMap.Vertex(0, 64),
                new DoomMap.Vertex(32, 64));
        List<DoomMap.Linedef> lines = List.of(
                new DoomMap.Linedef(0, 1, 0, 0, 0, 0, -1),
                new DoomMap.Linedef(2, 3, 0x0001, 0, 0, 0, 1),
                new DoomMap.Linedef(4, 5, 0, 0, 0, 0, 1));
        List<DoomMap.Sidedef> sides =
                List.of(new DoomMap.Sidedef(0, 0, "-", "-", "WALL", 0), new DoomMap.Sidedef(0, 0, "-", "-", "-", 1));
        List<DoomMap.Sector> sectors = List.of(
                new DoomMap.Sector(0, 16, "FLOOR", "CEILING", 160, 0, 0),
                new DoomMap.Sector(0, 16, "FLOOR", "CEILING", 160, 0, 0));
        return new DoomMap(
                "MAP01",
                List.of(),
                new DoomMap.Geometry(vertices, lines, sides, sectors),
                new DoomMap.Bsp(List.of(), List.of(), List.of()),
                List.of(0),
                new DoomMap.Blockmap(0, 0, 1, 1, List.of(List.of())));
    }
}
