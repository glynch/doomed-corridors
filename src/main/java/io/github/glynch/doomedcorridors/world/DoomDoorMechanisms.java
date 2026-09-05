/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Interprets and advances supported door behavior from immutable imported map records. */
final class DoomDoorMechanisms {
    private static final int MANUAL_OPEN_STAY = 31;
    private static final int MANUAL_BLAZE_RAISE = 117;
    private static final int BLOCKING_LINE = 0x0001;
    private static final float USE_RANGE = DoomUnits.toWorld(64.0F);
    private static final float OPEN_CLEARANCE = DoomUnits.toWorld(4.0F);
    private static final float NORMAL_SPEED_PER_TICK = DoomUnits.toWorld(2.0F);
    private static final float BLAZE_SPEED_PER_TICK = DoomUnits.toWorld(8.0F);
    private static final int BLAZE_WAIT_TICKS = 150;
    private static final float INTERSECTION_TOLERANCE = 0.000_001F;

    private final DoomMap map;
    private final Map<Integer, Door> doorsBySector;
    private final Map<Integer, Door> doorsByLinedef;

    /** Discovers supported manual doors and groups multiple activation lines by target sector. */
    DoomDoorMechanisms(DoomMap map) {
        this.map = Objects.requireNonNull(map, "map");
        doorsBySector = new LinkedHashMap<>();
        doorsByLinedef = new LinkedHashMap<>();
        discoverDoors();
    }

    /** Activates the nearest supported linedef crossed by the player's use ray. */
    boolean interact(float x, float z, float yawRadians) {
        float rayX = (float) Math.cos(yawRadians) * USE_RANGE;
        float rayZ = -(float) Math.sin(yawRadians) * USE_RANGE;
        Door nearest = null;
        float nearestAmount = Float.POSITIVE_INFINITY;
        for (int linedefIndex = 0; linedefIndex < map.linedefs().size(); linedefIndex++) {
            DoomMap.Linedef linedef = map.linedefs().get(linedefIndex);
            if (!blocksInteractionRay(linedef) && !doorsByLinedef.containsKey(linedefIndex)) {
                continue;
            }
            float amount = intersectionAmount(x, z, rayX, rayZ, linedef);
            if (amount >= 0.0F && amount < nearestAmount) {
                nearest = doorsByLinedef.get(linedefIndex);
                nearestAmount = amount;
            }
        }
        return nearest != null && nearest.activate();
    }

    /** Advances every active door by one classic 35 Hz simulation tick. */
    void advanceFixedStep() {
        doorsBySector.values().forEach(Door::advance);
    }

    /** Returns the effective ceiling height in application world units. */
    float ceilingHeight(int sectorIndex) {
        Door door = doorsBySector.get(sectorIndex);
        return door == null
                ? DoomUnits.toWorld(map.sectors().get(sectorIndex).ceilingHeight())
                : door.currentHeight;
    }

    /** Returns stable immutable snapshots in source-sector discovery order. */
    List<DoomDoorState> states() {
        return doorsBySector.values().stream().map(Door::state).toList();
    }

    /** Finds every supported manual linedef and creates one door per referenced back sector. */
    private void discoverDoors() {
        for (int linedefIndex = 0; linedefIndex < map.linedefs().size(); linedefIndex++) {
            DoomMap.Linedef linedef = map.linedefs().get(linedefIndex);
            if (!isSupported(linedef.special()) || linedef.leftSidedef() < 0) {
                continue;
            }
            int sectorIndex = sectorForSide(linedef.leftSidedef());
            Door door = doorsBySector.computeIfAbsent(
                    sectorIndex, ignored -> createDoor(sectorIndex, linedef.special()));
            doorsByLinedef.put(linedefIndex, door);
        }
    }

    /** Calculates the classic open destination from the lowest adjacent ceiling. */
    private Door createDoor(int sectorIndex, int special) {
        float closed = DoomUnits.toWorld(map.sectors().get(sectorIndex).ceilingHeight());
        float adjacent = Float.POSITIVE_INFINITY;
        for (DoomMap.Linedef candidate : map.linedefs()) {
            if (candidate.leftSidedef() < 0) {
                continue;
            }
            int right = sectorForSide(candidate.rightSidedef());
            int left = sectorForSide(candidate.leftSidedef());
            if (right == sectorIndex && left != sectorIndex) {
                adjacent = Math.min(adjacent, DoomUnits.toWorld(map.sectors().get(left).ceilingHeight()));
            } else if (left == sectorIndex && right != sectorIndex) {
                adjacent = Math.min(adjacent, DoomUnits.toWorld(map.sectors().get(right).ceilingHeight()));
            }
        }
        if (!Float.isFinite(adjacent) || adjacent - OPEN_CLEARANCE < closed) {
            throw new IllegalArgumentException("manual door sector has no valid open destination: " + sectorIndex);
        }
        DoorBehavior behavior = special == MANUAL_BLAZE_RAISE
                ? new DoorBehavior(BLAZE_SPEED_PER_TICK, BLAZE_WAIT_TICKS)
                : new DoorBehavior(NORMAL_SPEED_PER_TICK, 0);
        return new Door(sectorIndex, closed, adjacent - OPEN_CLEARANCE, behavior);
    }

    /** Returns whether this slice interprets the given classic linedef special. */
    private static boolean isSupported(int special) {
        return special == MANUAL_OPEN_STAY || special == MANUAL_BLAZE_RAISE;
    }

    /** Returns the fractional distance along a ray where it crosses a finite linedef. */
    private float intersectionAmount(
            float rayStartX,
            float rayStartZ,
            float rayX,
            float rayZ,
            DoomMap.Linedef linedef) {
        DoomMap.Vertex first = map.vertices().get(linedef.startVertex());
        DoomMap.Vertex second = map.vertices().get(linedef.endVertex());
        float lineStartX = DoomUnits.toWorld(first.x());
        float lineStartZ = DoomUnits.yToWorldZ(first.y());
        float lineX = DoomUnits.deltaToWorld(second.x(), first.x());
        float lineZ = DoomUnits.deltaToWorld(first.y(), second.y());
        float denominator = cross(rayX, rayZ, lineX, lineZ);
        if (Math.abs(denominator) <= INTERSECTION_TOLERANCE) {
            return -1.0F;
        }
        float offsetX = lineStartX - rayStartX;
        float offsetZ = lineStartZ - rayStartZ;
        float rayAmount = cross(offsetX, offsetZ, lineX, lineZ) / denominator;
        float lineAmount = cross(offsetX, offsetZ, rayX, rayZ) / denominator;
        return rayAmount >= 0.0F && rayAmount <= 1.0F && lineAmount >= 0.0F && lineAmount <= 1.0F
                ? rayAmount
                : -1.0F;
    }

    /** Returns one sector index referenced by a validated sidedef index. */
    private int sectorForSide(int sidedefIndex) {
        return map.sidedefs().get(sidedefIndex).sector();
    }

    /** Returns whether a non-door line prevents the use ray from reaching farther geometry. */
    private boolean blocksInteractionRay(DoomMap.Linedef linedef) {
        if (linedef.leftSidedef() < 0 || (linedef.flags() & BLOCKING_LINE) != 0) {
            return true;
        }
        int right = sectorForSide(linedef.rightSidedef());
        int left = sectorForSide(linedef.leftSidedef());
        float openingFloor = DoomUnits.toWorld(Math.max(
                map.sectors().get(right).floorHeight(), map.sectors().get(left).floorHeight()));
        float openingCeiling = Math.min(ceilingHeight(right), ceilingHeight(left));
        return openingCeiling <= openingFloor;
    }

    /** Returns the signed two-dimensional cross product. */
    private static float cross(float firstX, float firstZ, float secondX, float secondZ) {
        return firstX * secondZ - firstZ * secondX;
    }

    /** Mutable state for one sector door hidden behind immutable snapshots. */
    private static final class Door {
        private final int sectorIndex;
        private final float closedHeight;
        private final float openHeight;
        private final DoorBehavior behavior;
        private DoomDoorState.Phase phase = DoomDoorState.Phase.CLOSED;
        private float currentHeight;
        private int waitTicks;

        private Door(
                int sectorIndex,
                float closedHeight,
                float openHeight,
                DoorBehavior behavior) {
            this.sectorIndex = sectorIndex;
            this.closedHeight = closedHeight;
            this.openHeight = openHeight;
            this.behavior = behavior;
            currentHeight = closedHeight;
        }

        private boolean activate() {
            if (phase == DoomDoorState.Phase.CLOSING) {
                phase = DoomDoorState.Phase.OPENING;
                return true;
            }
            if (phase != DoomDoorState.Phase.CLOSED) {
                return false;
            }
            phase = DoomDoorState.Phase.OPENING;
            return true;
        }

        private void advance() {
            if (phase == DoomDoorState.Phase.OPENING) {
                openFurther();
            } else if (phase == DoomDoorState.Phase.WAITING) {
                waitBeforeClosing();
            } else if (phase == DoomDoorState.Phase.CLOSING) {
                closeFurther();
            }
        }

        private void openFurther() {
            currentHeight = Math.min(currentHeight + behavior.speedPerTick, openHeight);
            if (currentHeight != openHeight) {
                return;
            }
            if (behavior.waitTicks == 0) {
                phase = DoomDoorState.Phase.OPEN;
            } else {
                waitTicks = behavior.waitTicks;
                phase = DoomDoorState.Phase.WAITING;
            }
        }

        private void waitBeforeClosing() {
            waitTicks--;
            if (waitTicks == 0) {
                phase = DoomDoorState.Phase.CLOSING;
            }
        }

        private void closeFurther() {
            currentHeight = Math.max(currentHeight - behavior.speedPerTick, closedHeight);
            if (currentHeight == closedHeight) {
                phase = DoomDoorState.Phase.CLOSED;
            }
        }

        private DoomDoorState state() {
            return new DoomDoorState(sectorIndex, phase, closedHeight, openHeight, currentHeight);
        }
    }

    /** Motion speed and optional hold time selected by one supported special. */
    private record DoorBehavior(float speedPerTick, int waitTicks) {}
}
