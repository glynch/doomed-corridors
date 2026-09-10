/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import io.github.glynch.jscene3d.doom.geometry.DoomUnits;
import io.github.glynch.jscene3d.doom.map.DoomMap;
import java.util.Objects;

/** Resolves the source-map floor beneath imported actor placements without retaining a game runtime. */
final class DoomActorFloorResolver {
    private final DoomMap map;

    /** Retains the decoded map needed for BSP point queries during publication. */
    DoomActorFloorResolver(DoomMap map) {
        this.map = Objects.requireNonNull(map, "map");
    }

    /** Returns the source sector's floor at one engine-space horizontal position. */
    float floorHeight(float x, float z) {
        int sectorIndex = sectorAt(x, z);
        return DoomUnits.toWorld(map.sectors().get(sectorIndex).floorHeight());
    }

    /** Locates the BSP subsector containing one engine-coordinate point. */
    private int sectorAt(float x, float z) {
        if (map.nodes().isEmpty()) {
            return sectorForSubsector(0);
        }
        double doomX = DoomUnits.fromWorld(x);
        double doomY = DoomUnits.worldZToY(z);
        DoomMap.NodeChild child = new DoomMap.NodeChild(false, map.nodes().size() - 1);
        while (!child.subsector()) {
            DoomMap.Node node = map.nodes().get(child.index());
            DoomMap.Partition partition = node.partition();
            double side = partition.deltaX() * (doomY - partition.y()) - partition.deltaY() * (doomX - partition.x());
            child = side < 0.0 ? node.right().child() : node.left().child();
        }
        return sectorForSubsector(child.index());
    }

    /** Resolves one subsector's sector from its first directed segment. */
    private int sectorForSubsector(int subsectorIndex) {
        DoomMap.Subsector subsector = map.subsectors().get(subsectorIndex);
        DoomMap.Seg segment = map.segs().get(subsector.firstSeg());
        DoomMap.Linedef linedef = map.linedefs().get(segment.linedef());
        int sidedef = segment.direction() == 0 ? linedef.rightSidedef() : linedef.leftSidedef();
        return map.sidedefs().get(sidedef).sector();
    }
}
