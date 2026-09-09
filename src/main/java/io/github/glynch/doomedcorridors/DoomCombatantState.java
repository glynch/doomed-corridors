/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.combat.DoomCombatRules;
import io.github.glynch.jscene3d.doom.geometry.DoomUnits;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.World;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dDescriptors;
import io.github.glynch.jscene3d.project.spatial3d.Transform3d;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import java.util.Objects;
import org.joml.Vector3f;

/** Mutable descriptor-backed health for one imported combatant entity. */
final class DoomCombatantState implements DoomHitscanTarget, DoomRuleConsumer {
    private final Entity owner;
    private final World world;
    private final ResourceReference actorCatalog;
    private final ResourceReference combatRules;
    private final String actorId;
    private int health;
    private float aimRadius;
    private float aimHeight;
    private Transform3d transform;
    private boolean configured;

    /** Retains explicit authored identities until application preparation initializes health. */
    DoomCombatantState(
            Entity owner, World world, ResourceReference actorCatalog, ResourceReference combatRules, String actorId) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.world = Objects.requireNonNull(world, "world");
        this.actorCatalog = requireSourceAsset(actorCatalog, "actor-catalog");
        this.combatRules = requireSourceAsset(combatRules, "combat-rules");
        this.actorId = requireText(actorId, "actor-id");
    }

    @Override
    public ResourceReference actorCatalog() {
        return actorCatalog;
    }

    @Override
    public ResourceReference combatRules() {
        return combatRules;
    }

    @Override
    public void configure(DoomCombatRules rules) {
        if (configured) {
            throw new IllegalStateException("combatant state is already configured");
        }
        DoomCombatRules validRules = Objects.requireNonNull(rules, "rules");
        DoomCombatRules.CombatantBounds bounds =
                validRules.findCombatantBounds(actorId).orElseThrow();
        health = validRules.combatantStartingHealth(actorId);
        aimRadius = DoomUnits.toWorld(bounds.radius());
        aimHeight = DoomUnits.toWorld(bounds.height());
        transform = owner.capability(Spatial3dDescriptors.spatialCapability(), Transform3d.class)
                .orElseThrow(() -> new IllegalStateException("combatant entity has no spatial-3d capability"));
        configured = true;
    }

    @Override
    public Entity owner() {
        return owner;
    }

    @Override
    public Vector3f aimPoint(Vector3f destination) {
        requireConfigured();
        return requiredTransform()
                .worldMatrix()
                .getTranslation(Objects.requireNonNull(destination, "destination"))
                .add(0.0F, aimHeight * 0.5F, 0.0F);
    }

    @Override
    public float aimRadius() {
        requireConfigured();
        return aimRadius;
    }

    @Override
    public int health() {
        requireConfigured();
        return health;
    }

    @Override
    public int damage(int amount) {
        requireConfigured();
        if (amount <= 0) {
            throw new IllegalArgumentException("damage must be positive");
        }
        int applied = (int) Math.min((long) health, amount);
        health -= applied;
        if (health == 0) {
            world.destroy(owner);
        }
        return applied;
    }

    private static ResourceReference requireSourceAsset(ResourceReference reference, String name) {
        ResourceReference validReference = Objects.requireNonNull(reference, name);
        if (validReference.kind() != ResourceReference.Kind.ASSET) {
            throw new IllegalArgumentException(name + " must reference a source asset");
        }
        return validReference;
    }

    private static String requireText(String value, String name) {
        String validValue = Objects.requireNonNull(value, name);
        if (validValue.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return validValue;
    }

    private void requireConfigured() {
        if (!configured) {
            throw new IllegalStateException("combatant state has not been configured");
        }
    }

    private Transform3d requiredTransform() {
        if (transform == null) {
            throw new IllegalStateException("combatant transform has not been configured");
        }
        return transform;
    }
}
