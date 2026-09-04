/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import java.util.Objects;

/** Circle-versus-linedef movement shared by player and actor simulation. */
public final class DoomCollisionWorld {
    /** Classic player collision radius expressed in JScene3D world units. */
    public static final float PLAYER_RADIUS = DoomUnits.toWorld(16.0F);

    /** Classic player collision height expressed in JScene3D world units. */
    public static final float PLAYER_HEIGHT = DoomUnits.toWorld(56.0F);

    /** Classic player eye height expressed in JScene3D world units. */
    public static final float PLAYER_EYE_HEIGHT = DoomUnits.toWorld(41.0F);

    private static final float MAXIMUM_STEP = DoomUnits.toWorld(24.0F);
    private static final int BLOCKING_LINE = 0x0001;
    private static final int SAFE_FRACTION_ITERATIONS = 14;
    private static final float COLLISION_TOLERANCE = 0.000_01F;

    private final DoomMap map;

    /**
     * Retains validated decoded map records.
     *
     * @param map decoded classic map used for movement queries
     */
    public DoomCollisionWorld(DoomMap map) {
        this.map = Objects.requireNonNull(map, "map");
    }

    /** Moves a player circle as far as possible and slides the remainder along the first wall. */
    Position move(float startX, float startZ, float deltaX, float deltaZ) {
        return move(
                startX,
                startZ,
                deltaX,
                deltaZ,
                new BodyDimensions(PLAYER_RADIUS, PLAYER_HEIGHT, MAXIMUM_STEP));
    }

    /**
     * Moves one actor circle through the classic map while respecting its configured dimensions.
     *
     * @param startX initial world X coordinate
     * @param startZ initial world Z coordinate
     * @param deltaX requested world X displacement
     * @param deltaZ requested world Z displacement
     * @param radius positive actor collision radius
     * @param height positive actor collision height
     * @param maximumStep non-negative traversable floor-height increase
     * @return accepted position and supporting floor
     */
    public Position moveActor(
            float startX,
            float startZ,
            float deltaX,
            float deltaZ,
            float radius,
            float height,
            float maximumStep) {
        BodyDimensions body = new BodyDimensions(radius, height, maximumStep);
        return move(startX, startZ, deltaX, deltaZ, body);
    }

    /** Moves a configured circle as far as possible and slides its remainder along a wall. */
    private Position move(
            float startX,
            float startZ,
            float deltaX,
            float deltaZ,
            BodyDimensions body) {
        float floorHeight = floorHeight(startX, startZ);
        BlockedBy directBlock =
                blockingLine(startX + deltaX, startZ + deltaZ, floorHeight, body);
        if (directBlock == null) {
            return position(startX + deltaX, startZ + deltaZ);
        }
        float safeFraction = safeFraction(startX, startZ, deltaX, deltaZ, floorHeight, body);
        float safeX = startX + deltaX * safeFraction;
        float safeZ = startZ + deltaZ * safeFraction;
        float remaining = 1.0F - safeFraction;
        Slide slide = projectOntoLine(deltaX * remaining, deltaZ * remaining, directBlock.linedef());
        float slideFraction =
                safeFraction(safeX, safeZ, slide.deltaX(), slide.deltaZ(), floorHeight, body);
        return position(safeX + slide.deltaX() * slideFraction, safeZ + slide.deltaZ() * slideFraction);
    }

    /** Returns the sector floor under one world-coordinate point. */
    float floorHeight(float x, float z) {
        int sectorIndex = sectorAt(x, z);
        return DoomUnits.toWorld(map.sectors().get(sectorIndex).floorHeight());
    }

    /** Finds the largest collision-free fraction of a proposed movement. */
    private float safeFraction(
            float startX,
            float startZ,
            float deltaX,
            float deltaZ,
            float floorHeight,
            BodyDimensions body) {
        if (deltaX == 0.0F && deltaZ == 0.0F) {
            return 0.0F;
        }
        if (blockingLine(startX + deltaX, startZ + deltaZ, floorHeight, body) == null) {
            return 1.0F;
        }
        float safe = 0.0F;
        float blocked = 1.0F;
        for (int iteration = 0; iteration < SAFE_FRACTION_ITERATIONS; iteration++) {
            float candidate = (safe + blocked) * 0.5F;
            if (blockingLine(
                            startX + deltaX * candidate,
                            startZ + deltaZ * candidate,
                            floorHeight,
                            body)
                    == null) {
                safe = candidate;
            } else {
                blocked = candidate;
            }
        }
        return safe;
    }

    /** Returns the first wall preventing a circle from occupying the candidate position. */
    private BlockedBy blockingLine(
            float x, float z, float currentFloor, BodyDimensions body) {
        for (int index = 0; index < map.linedefs().size(); index++) {
            DoomMap.Linedef linedef = map.linedefs().get(index);
            if (blocksBody(linedef, currentFloor, body) && touches(linedef, x, z, body.radius())) {
                return new BlockedBy(index, linedef);
            }
        }
        return null;
    }

    /** Determines whether a linedef is solid for a player standing on the current floor. */
    private boolean blocksBody(
            DoomMap.Linedef linedef, float currentFloor, BodyDimensions body) {
        if (linedef.leftSidedef() < 0 || (linedef.flags() & BLOCKING_LINE) != 0) {
            return true;
        }
        DoomMap.Sector right = sectorForSide(linedef.rightSidedef());
        DoomMap.Sector left = sectorForSide(linedef.leftSidedef());
        float openingBottom = DoomUnits.toWorld(Math.max(right.floorHeight(), left.floorHeight()));
        float openingTop = DoomUnits.toWorld(Math.min(right.ceilingHeight(), left.ceilingHeight()));
        return openingTop - openingBottom < body.height()
                || openingTop - currentFloor < body.height()
                || openingBottom - currentFloor > body.maximumStep();
    }

    /** Returns whether the player circle overlaps one finite linedef segment. */
    private boolean touches(DoomMap.Linedef linedef, float x, float z, float radius) {
        DoomMap.Vertex start = map.vertices().get(linedef.startVertex());
        DoomMap.Vertex end = map.vertices().get(linedef.endVertex());
        float startX = DoomUnits.toWorld(start.x());
        float startZ = DoomUnits.yToWorldZ(start.y());
        float endX = DoomUnits.toWorld(end.x());
        float endZ = DoomUnits.yToWorldZ(end.y());
        float segmentX = endX - startX;
        float segmentZ = endZ - startZ;
        float lengthSquared = segmentX * segmentX + segmentZ * segmentZ;
        float amount = lengthSquared == 0.0F
                ? 0.0F
                : Math.clamp(((x - startX) * segmentX + (z - startZ) * segmentZ) / lengthSquared, 0.0F, 1.0F);
        float nearestX = startX + segmentX * amount;
        float nearestZ = startZ + segmentZ * amount;
        float distanceX = x - nearestX;
        float distanceZ = z - nearestZ;
        float collisionRadius = radius - COLLISION_TOLERANCE;
        return distanceX * distanceX + distanceZ * distanceZ < collisionRadius * collisionRadius;
    }

    /** Projects a remaining movement vector onto a blocking linedef direction. */
    private Slide projectOntoLine(float deltaX, float deltaZ, DoomMap.Linedef linedef) {
        DoomMap.Vertex start = map.vertices().get(linedef.startVertex());
        DoomMap.Vertex end = map.vertices().get(linedef.endVertex());
        float lineX = DoomUnits.deltaToWorld(end.x(), start.x());
        float lineZ = DoomUnits.deltaToWorld(start.y(), end.y());
        float inverseLength = 1.0F / (float) Math.hypot(lineX, lineZ);
        float directionX = lineX * inverseLength;
        float directionZ = lineZ * inverseLength;
        float distance = deltaX * directionX + deltaZ * directionZ;
        return new Slide(directionX * distance, directionZ * distance);
    }

    /** Resolves the authoritative floor after an accepted movement. */
    private Position position(float x, float z) {
        return new Position(x, z, floorHeight(x, z));
    }

    /** Returns the sector referenced by one sidedef. */
    private DoomMap.Sector sectorForSide(int sidedefIndex) {
        return map.sectors().get(map.sidedefs().get(sidedefIndex).sector());
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
            double side = partition.deltaX() * (doomY - partition.y())
                    - partition.deltaY() * (doomX - partition.x());
            child = side < 0.0 ? node.right().child() : node.left().child();
        }
        return sectorForSubsector(child.index());
    }

    /** Resolves one subsector's sector from its first directed seg. */
    private int sectorForSubsector(int subsectorIndex) {
        DoomMap.Subsector subsector = map.subsectors().get(subsectorIndex);
        DoomMap.Seg seg = map.segs().get(subsector.firstSeg());
        DoomMap.Linedef linedef = map.linedefs().get(seg.linedef());
        int side = seg.direction() == 0 ? linedef.rightSidedef() : linedef.leftSidedef();
        return map.sidedefs().get(side).sector();
    }

    /** Accepted position and its supporting floor. */
    public record Position(float x, float z, float floorHeight) {}

    /** Validated circle and vertical-clearance dimensions used by one movement operation. */
    private record BodyDimensions(float radius, float height, float maximumStep) {
        private BodyDimensions {
            if (!Float.isFinite(radius)
                    || !Float.isFinite(height)
                    || !Float.isFinite(maximumStep)
                    || radius <= COLLISION_TOLERANCE
                    || height <= 0.0F
                    || maximumStep < 0.0F) {
                throw new IllegalArgumentException(
                        "collision dimensions must be finite with positive radius and height");
            }
        }
    }

    /** First linedef preventing a candidate position. */
    private record BlockedBy(int index, DoomMap.Linedef linedef) {}

    /** Remaining movement projected along a blocking wall. */
    private record Slide(float deltaX, float deltaZ) {}
}
