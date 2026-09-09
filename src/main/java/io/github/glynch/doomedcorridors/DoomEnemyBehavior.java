/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.combat.DoomCombatRules;
import io.github.glynch.doomedcorridors.internal.DoomedCorridorsRuntimeTypes;
import io.github.glynch.jscene3d.doom.geometry.DoomUnits;
import io.github.glynch.jscene3d.project.physics3d.CharacterBody3d;
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
import io.github.glynch.jscene3d.project.spatial3d.descriptor.Spatial3dDescriptors;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import java.time.Duration;
import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.joml.Vector3f;

/** Descriptor-backed awareness and collision-aware pursuit for one configured Doom enemy. */
final class DoomEnemyBehavior
        implements DoomRuleConsumer, ComponentReferenceBinder, ComponentEndpointBinder, ComponentUpdateCallbacks {
    private static final float SELF_HIT_ADVANCE = 1.0E-4F;
    private static final float POSITION_TOLERANCE = 1.0E-5F;

    private final Entity owner;
    private final Physics3dWorldModule physics;
    private final ResourceReference actorCatalog;
    private final ResourceReference combatRules;
    private final String actorId;
    private final RandomGenerator random;
    private Entity targetProvider;
    private DoomCombatantState state;
    private CharacterBody3d body;
    private Transform3d transform;
    private float sightRange;
    private DoomEnemyPursuit pursuit;
    private DoomEnemyAttack attack;
    private DoomCombatRules rules;
    private RuntimeSignal alertedSignal;
    private RuntimeSignal movementStartedSignal;
    private RuntimeSignal movementStoppedSignal;
    private RuntimeSignal attackedSignal;
    private boolean moving;
    private boolean configured;

    /** Retains authored rule identities and the world physics seam used for sight and movement. */
    DoomEnemyBehavior(
            Entity owner,
            Physics3dWorldModule physics,
            ResourceReference actorCatalog,
            ResourceReference combatRules,
            String actorId) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.physics = Objects.requireNonNull(physics, "physics");
        this.actorCatalog = requireSourceAsset(actorCatalog, "actor-catalog");
        this.combatRules = requireSourceAsset(combatRules, "combat-rules");
        this.actorId = requireText(actorId, "actor-id");
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
    public void bindReferences(ComponentReferenceResolver references) {
        ComponentReferenceResolver validReferences = Objects.requireNonNull(references, "references");
        targetProvider = validReferences.entity(DoomedCorridorsRuntimeTypes.ENEMY_TARGET_PROVIDER_PROPERTY);
        state = validReferences.component(DoomedCorridorsRuntimeTypes.ENEMY_STATE_PROPERTY, DoomCombatantState.class);
        body = validReferences.component(DoomedCorridorsRuntimeTypes.COMBATANT_BODY_PROPERTY, CharacterBody3d.class);
    }

    @Override
    public void bindEndpoints(ComponentEndpoints endpoints) {
        ComponentEndpoints validEndpoints = Objects.requireNonNull(endpoints, "endpoints");
        alertedSignal = validEndpoints.signal(DoomedCorridorsRuntimeTypes.ENEMY_ALERTED_SIGNAL);
        movementStartedSignal = validEndpoints.signal(DoomedCorridorsRuntimeTypes.ENEMY_MOVEMENT_STARTED_SIGNAL);
        movementStoppedSignal = validEndpoints.signal(DoomedCorridorsRuntimeTypes.ENEMY_MOVEMENT_STOPPED_SIGNAL);
        attackedSignal = validEndpoints.signal(DoomedCorridorsRuntimeTypes.ENEMY_ATTACKED_SIGNAL);
    }

    @Override
    public void configure(DoomCombatRules rules) {
        if (configured) {
            throw new IllegalStateException("enemy behavior is already configured");
        }
        this.rules = Objects.requireNonNull(rules, "rules");
        DoomCombatRules.EnemyBehavior behavior = this.rules.enemyBehavior(actorId);
        sightRange = DoomUnits.toWorld(behavior.sightRange());
        pursuit = new DoomEnemyPursuit(
                DoomUnits.toWorld(behavior.preferredRange()),
                DoomUnits.toWorld(behavior.moveSpeed()),
                Duration.ofMillis(behavior.reactionMilliseconds()));
        attack = new DoomEnemyAttack(
                DoomUnits.toWorld(behavior.attackRange()), Duration.ofMillis(behavior.attackIntervalMilliseconds()));
        transform = owner.capability(Spatial3dDescriptors.spatialCapability(), Transform3d.class)
                .orElseThrow(() -> new IllegalStateException("enemy entity has no spatial-3d capability"));
        configured = true;
    }

    @Override
    public void onBeforePhysics(FixedUpdateContext update) {
        FixedUpdateContext validUpdate = Objects.requireNonNull(update, "update");
        requireConfigured();
        if (requiredState().health() == 0) {
            updateMovement(false);
            return;
        }
        DoomEnemyTarget target = requiredTarget();
        Entity player = target.player();
        DoomDamageable playerState = target.damageable();
        Vector3f enemyPosition = requiredTransform().worldMatrix().getTranslation(new Vector3f());
        Vector3f playerPosition = playerTransform(player).worldMatrix().getTranslation(new Vector3f());
        float horizontalDistance = horizontalDistance(enemyPosition, playerPosition);
        boolean visible = horizontalDistance <= sightRange && hasLineOfSight(player, playerPosition);
        boolean wasAlerted = requiredPursuit().isAlerted();
        Vector3f velocity = requiredPursuit().advance(enemyPosition, playerPosition, visible, validUpdate.step());
        if (!wasAlerted && requiredPursuit().isAlerted()) {
            requiredAlertedSignal().emit();
        }
        updateMovement(velocity.lengthSquared() > POSITION_TOLERANCE * POSITION_TOLERANCE);
        if (requiredPursuit().isAlerted()) {
            requiredBody().move(velocity, validUpdate.step());
        }
        if (requiredAttack()
                .advance(
                        requiredPursuit().isReady(),
                        visible,
                        playerState.health() > 0,
                        horizontalDistance,
                        validUpdate.step())) {
            requiredAttackedSignal().emit();
            playerState.damage(requiredRules().rollEnemyDamage(actorId, random));
        }
    }

    /** Returns whether this enemy has observed its target during the current lifetime. */
    boolean isAlerted() {
        return requiredPursuit().isAlerted();
    }

    /** Returns whether the latest behavior step requested collision-aware movement. */
    boolean isMoving() {
        return moving;
    }

    /** Emits only genuine transitions between stationary and movement-requesting behavior. */
    private void updateMovement(boolean nextMoving) {
        if (moving == nextMoving) {
            return;
        }
        moving = nextMoving;
        if (moving) {
            requiredMovementStartedSignal().emit();
        } else {
            requiredMovementStoppedSignal().emit();
        }
    }

    /** Determines visibility when the first solid beyond the enemy's own collision radius belongs to the player. */
    private boolean hasLineOfSight(Entity player, Vector3f playerPosition) {
        Vector3f origin = requiredState().aimPoint(new Vector3f());
        Vector3f direction = new Vector3f(playerPosition).sub(origin);
        float remaining = direction.length();
        if (remaining <= POSITION_TOLERANCE) {
            return true;
        }
        direction.div(remaining);
        float horizontalDirection = (float) Math.hypot(direction.x, direction.z);
        if (horizontalDirection <= POSITION_TOLERANCE) {
            return false;
        }
        float selfClearance = requiredState().aimRadius() / horizontalDirection + SELF_HIT_ADVANCE;
        if (selfClearance >= remaining) {
            return true;
        }
        origin.fma(selfClearance, direction);
        remaining -= selfClearance;
        return physics.raycast(origin, direction, remaining)
                .filter(hit -> hit.object().owner() == player)
                .isPresent();
    }

    /** Resolves the player's authoritative spatial capability on the exact authored target. */
    private static Transform3d playerTransform(Entity player) {
        return player.capability(Spatial3dDescriptors.spatialCapability(), Transform3d.class)
                .orElseThrow(() -> new IllegalStateException("enemy player target has no spatial-3d capability"));
    }

    /** Computes planar distance without allowing vertical separation to affect provider sight range. */
    private static float horizontalDistance(Vector3f first, Vector3f second) {
        return (float) Math.hypot(second.x - first.x, second.z - first.z);
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
            throw new IllegalStateException("enemy behavior has not been configured");
        }
    }

    private DoomEnemyTarget requiredTarget() {
        if (targetProvider == null) {
            throw new IllegalStateException("enemy target provider has not been bound");
        }
        return targetProvider
                .capability(DoomedCorridorsRuntimeTypes.ENEMY_TARGET_CAPABILITY, DoomEnemyTarget.class)
                .orElseThrow(() -> new IllegalStateException("target provider has no enemy-target capability"));
    }

    private DoomCombatantState requiredState() {
        if (state == null) {
            throw new IllegalStateException("enemy state has not been bound");
        }
        return state;
    }

    private CharacterBody3d requiredBody() {
        if (body == null) {
            throw new IllegalStateException("enemy body has not been bound");
        }
        return body;
    }

    private Transform3d requiredTransform() {
        if (transform == null) {
            throw new IllegalStateException("enemy transform has not been configured");
        }
        return transform;
    }

    private DoomEnemyPursuit requiredPursuit() {
        if (pursuit == null) {
            throw new IllegalStateException("enemy pursuit has not been configured");
        }
        return pursuit;
    }

    private DoomEnemyAttack requiredAttack() {
        if (attack == null) {
            throw new IllegalStateException("enemy attack has not been configured");
        }
        return attack;
    }

    private DoomCombatRules requiredRules() {
        if (rules == null) {
            throw new IllegalStateException("enemy combat rules have not been configured");
        }
        return rules;
    }

    private RuntimeSignal requiredAttackedSignal() {
        if (attackedSignal == null) {
            throw new IllegalStateException("enemy attacked signal has not been bound");
        }
        return attackedSignal;
    }

    private RuntimeSignal requiredAlertedSignal() {
        if (alertedSignal == null) {
            throw new IllegalStateException("enemy alerted signal has not been bound");
        }
        return alertedSignal;
    }

    private RuntimeSignal requiredMovementStartedSignal() {
        if (movementStartedSignal == null) {
            throw new IllegalStateException("enemy movement-started signal has not been bound");
        }
        return movementStartedSignal;
    }

    private RuntimeSignal requiredMovementStoppedSignal() {
        if (movementStoppedSignal == null) {
            throw new IllegalStateException("enemy movement-stopped signal has not been bound");
        }
        return movementStoppedSignal;
    }
}
