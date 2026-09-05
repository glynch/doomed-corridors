/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import java.util.List;
import java.util.Objects;

/** Public game-system boundary for the supported Doom door mechanisms in one level. */
public final class DoomDoorSystem {
    private final DoomDoorMechanisms mechanisms;

    /** Creates the door mechanisms discovered in one imported map. */
    private DoomDoorSystem(DoomMap map) {
        mechanisms = new DoomDoorMechanisms(map);
    }

    /**
     * Creates a door system for one decoded map.
     *
     * @param map decoded classic Doom map
     * @return newly initialized door system
     */
    public static DoomDoorSystem create(DoomMap map) {
        return new DoomDoorSystem(Objects.requireNonNull(map, "map"));
    }

    /**
     * Activates the nearest supported door intersected by a player use ray.
     *
     * @param x player world X coordinate
     * @param z player world Z coordinate
     * @param yawRadians player yaw in radians
     * @return whether a door accepted the interaction
     */
    public boolean interact(float x, float z, float yawRadians) {
        return mechanisms.interact(x, z, yawRadians);
    }

    /** Advances every active door by one classic 35 Hz simulation tick. */
    public void advanceFixedStep() {
        mechanisms.advanceFixedStep();
    }

    /**
     * Returns immutable door snapshots in source-sector discovery order.
     *
     * @return current door states
     */
    public List<DoomDoorState> states() {
        return mechanisms.states();
    }
}
