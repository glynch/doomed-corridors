/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

import static io.github.glynch.doomedcorridors.internal.Preconditions.requireFinite;
import static io.github.glynch.doomedcorridors.internal.Preconditions.requireInRange;
import static io.github.glynch.doomedcorridors.internal.Preconditions.requirePositive;

import io.github.glynch.doomedcorridors.actor.DoomActor;
import java.util.Objects;

/** Immutable observable health, position, and collision bounds of one combatant. */
public final class DoomCombatantState {
    private final int thingIndex;
    private final String actorId;
    private final float x;
    private final float floorHeight;
    private final float z;
    private final float radius;
    private final float height;
    private final int health;
    private final int maximumHealth;
    private final DoomCombatantActivity activity;

    /** Creates one validated combatant snapshot. */
    DoomCombatantState(DoomActor actor, float radius, float height, int maximumHealth) {
        DoomActor validActor = Objects.requireNonNull(actor, "actor");
        requirePositive(radius, "radius");
        requirePositive(height, "height");
        requirePositive(maximumHealth, "maximumHealth");
        thingIndex = validActor.thingIndex();
        actorId = validActor.definition().id();
        x = validActor.x();
        floorHeight = validActor.floorHeight();
        z = validActor.z();
        this.radius = radius;
        this.height = height;
        health = maximumHealth;
        this.maximumHealth = maximumHealth;
        activity = DoomCombatantActivity.DORMANT;
    }

    /** Copies one snapshot while replacing its mutable simulation values. */
    private DoomCombatantState(
            DoomCombatantState source,
            float x,
            float floorHeight,
            float z,
            int health,
            DoomCombatantActivity activity) {
        requireInRange(health, 0, source.maximumHealth, "health");
        requireFinite(x, "x");
        requireFinite(floorHeight, "floorHeight");
        requireFinite(z, "z");
        thingIndex = source.thingIndex;
        actorId = source.actorId;
        this.x = x;
        this.floorHeight = floorHeight;
        this.z = z;
        radius = source.radius;
        height = source.height;
        this.health = health;
        maximumHealth = source.maximumHealth;
        this.activity = Objects.requireNonNull(activity, "activity");
        if ((health == 0) != (activity == DoomCombatantActivity.DEAD)) {
            throw new IllegalArgumentException("dead activity must match zero combatant health");
        }
    }

    /** Returns the source-map thing index identifying this combatant. */
    public int thingIndex() {
        return thingIndex;
    }

    /** Returns the stable provider actor identifier. */
    public String actorId() {
        return actorId;
    }

    /** Returns the world-coordinate center on the X axis. */
    public float x() {
        return x;
    }

    /** Returns the supporting floor height in world coordinates. */
    public float floorHeight() {
        return floorHeight;
    }

    /** Returns the world-coordinate center on the Z axis. */
    public float z() {
        return z;
    }

    /** Returns the horizontal collision radius in world units. */
    public float radius() {
        return radius;
    }

    /** Returns the vertical collision height in world units. */
    public float height() {
        return height;
    }

    /** Returns current health in the inclusive range from zero through maximum health. */
    public int health() {
        return health;
    }

    /** Returns the combatant's initial and maximum health. */
    public int maximumHealth() {
        return maximumHealth;
    }

    /** Returns the current high-level headless behavior. */
    public DoomCombatantActivity activity() {
        return activity;
    }

    /** Returns whether this combatant still participates in targeting. */
    public DoomCombatantStatus status() {
        return health == 0 ? DoomCombatantStatus.DEAD : DoomCombatantStatus.ALIVE;
    }

    /** Returns a new snapshot with adjusted health. */
    DoomCombatantState withHealth(int newHealth) {
        DoomCombatantActivity newActivity =
                newHealth == 0 ? DoomCombatantActivity.DEAD : activity;
        return new DoomCombatantState(this, x, floorHeight, z, newHealth, newActivity);
    }

    /** Returns a new live snapshot with an adjusted position and activity. */
    DoomCombatantState withPose(
            float newX,
            float newFloorHeight,
            float newZ,
            DoomCombatantActivity newActivity) {
        if (health == 0) {
            throw new IllegalStateException("dead combatants cannot move or change activity");
        }
        return new DoomCombatantState(
                this, newX, newFloorHeight, newZ, health, newActivity);
    }

    /** Returns a new live snapshot with only its activity adjusted. */
    DoomCombatantState withActivity(DoomCombatantActivity newActivity) {
        return withPose(x, floorHeight, z, newActivity);
    }

}
