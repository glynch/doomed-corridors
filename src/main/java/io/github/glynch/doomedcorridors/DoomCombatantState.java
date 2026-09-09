/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.combat.DoomCombatRules;
import io.github.glynch.doomedcorridors.internal.DoomedCorridorsRuntimeTypes;
import io.github.glynch.jscene3d.doom.geometry.DoomUnits;
import io.github.glynch.jscene3d.project.physics3d.CharacterBody3d;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.RuntimeSignal;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpointBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceResolver;
import io.github.glynch.jscene3d.project.spatial3d.Transform3d;
import io.github.glynch.jscene3d.project.spatial3d.descriptor.Spatial3dDescriptors;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import java.util.Objects;
import org.joml.Vector3f;

/** Mutable descriptor-backed health for one imported combatant entity. */
final class DoomCombatantState
        implements DoomHitscanTarget, DoomRuleConsumer, ComponentReferenceBinder, ComponentEndpointBinder {
    private final Entity owner;
    private final ResourceReference actorCatalog;
    private final ResourceReference combatRules;
    private final String actorId;
    private int health;
    private float aimRadius;
    private float aimHeight;
    private Transform3d transform;
    private CharacterBody3d body;
    private RuntimeSignal hurtSignal;
    private RuntimeSignal diedSignal;
    private boolean configured;

    /** Retains explicit authored identities until application preparation initializes health. */
    DoomCombatantState(Entity owner, ResourceReference actorCatalog, ResourceReference combatRules, String actorId) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.actorCatalog = requireSourceAsset(actorCatalog, "actor-catalog");
        this.combatRules = requireSourceAsset(combatRules, "combat-rules");
        this.actorId = requireText(actorId, "actor-id");
    }

    @Override
    public void bindReferences(ComponentReferenceResolver references) {
        body = Objects.requireNonNull(references, "references")
                .component(DoomedCorridorsRuntimeTypes.COMBATANT_BODY_PROPERTY, CharacterBody3d.class);
    }

    @Override
    public void bindEndpoints(ComponentEndpoints endpoints) {
        ComponentEndpoints validEndpoints = Objects.requireNonNull(endpoints, "endpoints");
        hurtSignal = validEndpoints.signal(DoomedCorridorsRuntimeTypes.COMBATANT_HURT_SIGNAL);
        diedSignal = validEndpoints.signal(DoomedCorridorsRuntimeTypes.COMBATANT_DIED_SIGNAL);
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
        if (applied == 0) {
            return 0;
        }
        if (health > 0) {
            requiredHurtSignal().emit();
        } else {
            requiredBody().close();
            requiredDiedSignal().emit();
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

    /** Returns the explicitly bound solid body which terminal damage removes from physics. */
    private CharacterBody3d requiredBody() {
        if (body == null) {
            throw new IllegalStateException("combatant body has not been bound");
        }
        return body;
    }

    /** Returns the descriptor-declared non-fatal damage signal. */
    private RuntimeSignal requiredHurtSignal() {
        if (hurtSignal == null) {
            throw new IllegalStateException("combatant hurt signal has not been bound");
        }
        return hurtSignal;
    }

    /** Returns the descriptor-declared terminal damage signal. */
    private RuntimeSignal requiredDiedSignal() {
        if (diedSignal == null) {
            throw new IllegalStateException("combatant died signal has not been bound");
        }
        return diedSignal;
    }
}
