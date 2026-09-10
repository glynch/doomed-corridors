/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsDescriptors;
import io.github.glynch.jscene3d.project.physics3d.CollisionOverlap3d;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.RuntimePayload;
import io.github.glynch.jscene3d.project.runtime.World;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpointBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Collects one imported pickup when its authored sensor overlaps the player character. */
final class DoomPickup implements ComponentEndpointBinder {
    private final Entity owner;
    private final World world;
    private final Resource resource;
    private final int amount;
    private final int limit;
    private final int armorProtectionPercent;
    private final Optional<String> grantedWeapon;
    private boolean collected;

    /** Stores validated provider rules and the exact entity-lifecycle authority for this instance. */
    DoomPickup(
            Entity owner,
            World world,
            String resource,
            int amount,
            int limit,
            int armorProtectionPercent,
            String grantedWeapon) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.world = Objects.requireNonNull(world, "world");
        this.resource = Resource.from(resource);
        if (amount <= 0 || limit <= 0) {
            throw new IllegalArgumentException("pickup amount and limit must be positive");
        }
        this.amount = amount;
        this.limit = limit;
        if (armorProtectionPercent < 0 || armorProtectionPercent > 100) {
            throw new IllegalArgumentException("armorProtectionPercent must be in [0, 100]");
        }
        this.armorProtectionPercent = armorProtectionPercent;
        this.grantedWeapon = Optional.of(Objects.requireNonNull(grantedWeapon, "grantedWeapon"))
                .filter(value -> !value.isBlank());
    }

    /** Binds the descriptor-declared overlap receiver. */
    @Override
    public void bindEndpoints(ComponentEndpoints endpoints) {
        Objects.requireNonNull(endpoints, "endpoints")
                .action(DoomedCorridorsDescriptors.RECEIVE_OVERLAP_ACTION, this::receiveOverlap);
    }

    /** Returns whether this pickup accepted a player overlap and requested destruction. */
    boolean isCollected() {
        return collected;
    }

    /** Applies one useful player overlap and removes the complete pickup entity. */
    private void receiveOverlap(RuntimePayload payload) {
        Object value = Objects.requireNonNull(payload, "payload").value();
        if (!(value instanceof CollisionOverlap3d overlap)) {
            throw new IllegalArgumentException("receive-overlap requires a CollisionOverlap3d payload");
        }
        if (collected) {
            return;
        }
        DoomPlayerState player = overlap.other()
                .owner()
                .capability(DoomedCorridorsDescriptors.PLAYER_RESOURCES_CAPABILITY, DoomPlayerState.class)
                .orElse(null);
        if (player != null && player.collect(resource, amount, limit, armorProtectionPercent, grantedWeapon)) {
            collect();
        }
    }

    /** Makes repeated shape-pair signals harmless before structural destruction commits. */
    private void collect() {
        collected = true;
        world.destroy(owner);
    }

    /** Resource kinds currently represented by the player's runtime state. */
    enum Resource {
        HEALTH,
        ARMOR,
        BULLETS,
        SHELLS;

        /** Parses the lower-case provider spelling published in component data. */
        private static Resource from(String value) {
            try {
                return valueOf(Objects.requireNonNull(value, "resource").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("unsupported pickup resource: " + value, exception);
            }
        }
    }
}
