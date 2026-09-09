/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.combat.DoomCombatRules;
import io.github.glynch.doomedcorridors.internal.DoomedCorridorsRuntimeTypes;
import io.github.glynch.jscene3d.doom.geometry.DoomUnits;
import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.game.input.InputWorldModule;
import io.github.glynch.jscene3d.project.physics3d.CollisionRaycastHit3d;
import io.github.glynch.jscene3d.project.physics3d.Physics3dWorldModule;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.FixedUpdateContext;
import io.github.glynch.jscene3d.project.runtime.RuntimeSignal;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpointBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceResolver;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentUpdateCallbacks;
import io.github.glynch.jscene3d.project.spatial3d.Transform3d;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.joml.Vector3f;

/** Input-driven Doom hitscan weapon using the authored player view as its firing pose. */
final class DoomHitscanWeapon
        implements DoomRuleConsumer, ComponentReferenceBinder, ComponentEndpointBinder, ComponentUpdateCallbacks {
    private static final float SELF_HIT_ADVANCE = 1.0E-4F;

    private final Entity owner;
    private final InputWorldModule input;
    private final Physics3dWorldModule physics;
    private final ResourceReference actorCatalog;
    private final ResourceReference combatRules;
    private final String weaponId;
    private final InputAction fireAction;
    private final RandomGenerator random;
    private Optional<Transform3d> viewTransform = Optional.empty();
    private RuntimeSignal fired;
    private int ammunitionPerShot;
    private float range;
    private DoomCombatRules rules;

    /** Retains authored configuration and derives a stable per-entity damage sequence. */
    DoomHitscanWeapon(
            Entity owner,
            InputWorldModule input,
            Physics3dWorldModule physics,
            ResourceReference actorCatalog,
            ResourceReference combatRules,
            String weaponId,
            String fireAction) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.input = Objects.requireNonNull(input, "input");
        this.physics = Objects.requireNonNull(physics, "physics");
        this.actorCatalog = requireSourceAsset(actorCatalog, "actor-catalog");
        this.combatRules = requireSourceAsset(combatRules, "combat-rules");
        this.weaponId = requireText(weaponId, "weapon-id");
        this.fireAction = new InputAction(requireText(fireAction, "fire-action"));
        random = new Random(owner.authoredId().value().getMostSignificantBits()
                ^ owner.authoredId().value().getLeastSignificantBits());
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
    public void configure(DoomCombatRules configuredRules) {
        if (rules != null) {
            throw new IllegalStateException("weapon is already configured");
        }
        DoomCombatRules validRules = Objects.requireNonNull(configuredRules, "configuredRules");
        if (!validRules.hasWeapon(weaponId)) {
            throw new IllegalArgumentException("combat rules do not define weapon: " + weaponId);
        }
        ammunitionPerShot = validRules.weaponAmmoPerShot(weaponId);
        range = DoomUnits.toWorld(validRules.weaponRange(weaponId));
        rules = validRules;
    }

    @Override
    public void bindReferences(ComponentReferenceResolver references) {
        viewTransform = Optional.of(Objects.requireNonNull(references, "references")
                .component(DoomedCorridorsRuntimeTypes.VIEW_TRANSFORM_PROPERTY, Transform3d.class));
    }

    /** Binds the descriptor-declared successful-shot signal. */
    @Override
    public void bindEndpoints(ComponentEndpoints endpoints) {
        fired = Objects.requireNonNull(endpoints, "endpoints").signal(DoomedCorridorsRuntimeTypes.WEAPON_FIRED_SIGNAL);
    }

    @Override
    public void onAfterPhysics(FixedUpdateContext update) {
        Objects.requireNonNull(update, "update");
        if (!input.snapshot().wasPressed(fireAction)) {
            return;
        }
        DoomPlayerState player = owner.capability(
                        DoomedCorridorsRuntimeTypes.PLAYER_RESOURCES_CAPABILITY, DoomPlayerState.class)
                .orElseThrow(() -> new IllegalStateException("weapon owner has no player resources"));
        if (!player.spendBullets(ammunitionPerShot)) {
            return;
        }
        fire();
        requiredFiredSignal().emit();
    }

    /** Traces the authored view ray, skipping only this weapon owner's own collision body. */
    private void fire() {
        DoomCombatRules configuredRules = requireRules();
        Vector3f direction = requiredViewTransform()
                .worldMatrix()
                .transformDirection(new Vector3f(0.0F, 0.0F, -1.0F))
                .normalize();
        Vector3f origin = requiredViewTransform().worldMatrix().getTranslation(new Vector3f());
        float remaining = range;
        while (remaining > 0.0F) {
            Optional<CollisionRaycastHit3d> result = physics.raycast(origin, direction, remaining);
            if (result.isEmpty()) {
                return;
            }
            CollisionRaycastHit3d hit = result.orElseThrow();
            if (hit.object().owner() != owner) {
                hit.object()
                        .owner()
                        .capability(DoomedCorridorsRuntimeTypes.DAMAGEABLE_CAPABILITY, DoomDamageable.class)
                        .ifPresent(target -> target.damage(configuredRules.rollWeaponDamage(weaponId, random)));
                return;
            }
            float advance = hit.distance() + SELF_HIT_ADVANCE;
            origin.fma(advance, direction);
            remaining -= advance;
        }
    }

    private DoomCombatRules requireRules() {
        if (rules == null) {
            throw new IllegalStateException("weapon has not been configured");
        }
        return rules;
    }

    private Transform3d requiredViewTransform() {
        return viewTransform.orElseThrow(() -> new IllegalStateException("view transform has not been bound"));
    }

    /** Requires endpoint binding before the active world accepts input. */
    private RuntimeSignal requiredFiredSignal() {
        if (fired == null) {
            throw new IllegalStateException("weapon fired signal has not been bound");
        }
        return fired;
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
}
