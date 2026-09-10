/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.combat.DoomCombatRules;
import io.github.glynch.doomedcorridors.internal.DoomedCorridorsDescriptors;
import io.github.glynch.jscene3d.doom.geometry.DoomUnits;
import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.game.input.InputWorldModule;
import io.github.glynch.jscene3d.project.physics3d.CollisionRaycastHit3d;
import io.github.glynch.jscene3d.project.physics3d.Physics3dWorldModule;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.FixedUpdateContext;
import io.github.glynch.jscene3d.project.runtime.RuntimePayload;
import io.github.glynch.jscene3d.project.runtime.RuntimeSignal;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpointBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceResolver;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentUpdateCallbacks;
import io.github.glynch.jscene3d.project.spatial3d.PerspectiveCamera3d;
import io.github.glynch.jscene3d.project.spatial3d.Transform3d;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.joml.Vector3f;

/** Input-driven controller which fires the player's selected Doom hitscan weapon from the authored view. */
final class DoomHitscanWeapon
        implements DoomRuleConsumer, ComponentReferenceBinder, ComponentEndpointBinder, ComponentUpdateCallbacks {
    private static final float SELF_HIT_ADVANCE = 1.0E-4F;

    private final Entity owner;
    private final InputWorldModule input;
    private final Physics3dWorldModule physics;
    private final ResourceReference actorCatalog;
    private final ResourceReference combatRules;
    private final InputAction fireAction;
    private final RandomGenerator random;
    private Optional<Transform3d> viewTransform = Optional.empty();
    private Optional<PerspectiveCamera3d> viewCamera = Optional.empty();
    private RuntimeSignal fired;
    private RuntimeSignal hitSignal;
    private int ammunitionPerShot;
    private int pelletCount;
    private long refireIntervalNanos;
    private long remainingRecoveryNanos;
    private DoomCombatRules.Ammunition ammunition;
    private String firingWeaponId;
    private float range;
    private float autoAimAngle;
    private float autoAimMaximumSlope;
    private DoomCombatRules rules;

    /** Retains authored configuration and derives a stable per-entity damage sequence. */
    DoomHitscanWeapon(
            Entity owner,
            InputWorldModule input,
            Physics3dWorldModule physics,
            ResourceReference actorCatalog,
            ResourceReference combatRules,
            String fireAction) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.input = Objects.requireNonNull(input, "input");
        this.physics = Objects.requireNonNull(physics, "physics");
        this.actorCatalog = requireSourceAsset(actorCatalog, "actor-catalog");
        this.combatRules = requireSourceAsset(combatRules, "combat-rules");
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
        rules = validRules;
    }

    @Override
    public void bindReferences(ComponentReferenceResolver references) {
        ComponentReferenceResolver validReferences = Objects.requireNonNull(references, "references");
        viewTransform = Optional.of(
                validReferences.component(DoomedCorridorsDescriptors.VIEW_TRANSFORM_PROPERTY, Transform3d.class));
        viewCamera = Optional.of(
                validReferences.component(DoomedCorridorsDescriptors.VIEW_CAMERA_PROPERTY, PerspectiveCamera3d.class));
    }

    /** Binds the descriptor-declared successful-shot signal. */
    @Override
    public void bindEndpoints(ComponentEndpoints endpoints) {
        ComponentEndpoints validEndpoints = Objects.requireNonNull(endpoints, "endpoints");
        fired = validEndpoints.signal(DoomedCorridorsDescriptors.WEAPON_FIRED_SIGNAL);
        hitSignal = validEndpoints.signal(DoomedCorridorsDescriptors.WEAPON_HIT_SIGNAL);
    }

    @Override
    public void onAfterPhysics(FixedUpdateContext update) {
        advanceRecovery(Objects.requireNonNull(update, "update").step());
        if (remainingRecoveryNanos > 0L || !input.snapshot().wasPressed(fireAction)) {
            return;
        }
        DoomPlayerState player = owner.capability(
                        DoomedCorridorsDescriptors.PLAYER_RESOURCES_CAPABILITY, DoomPlayerState.class)
                .orElseThrow(() -> new IllegalStateException("weapon owner has no player resources"));
        if (player.health() == 0) {
            return;
        }
        selectFiringRules(player.activeWeapon());
        if (!player.spendAmmunition(ammunition, ammunitionPerShot)) {
            return;
        }
        remainingRecoveryNanos = refireIntervalNanos;
        Optional<DoomWeaponHitLocation> resolvedHit = fire();
        requiredFiredSignal().emit();
        resolvedHit.ifPresent(weaponHit -> requiredHitSignal()
                .emit(new RuntimePayload(DoomedCorridorsDescriptors.WEAPON_HIT_PAYLOAD_TYPE, weaponHit)));
    }

    /** Selects the provider rules for the player's currently active owned weapon. */
    private void selectFiringRules(String selectedWeaponId) {
        DoomCombatRules configuredRules = requireRules();
        if (!configuredRules.hasWeapon(selectedWeaponId)) {
            throw new IllegalStateException("player selected an undefined weapon: " + selectedWeaponId);
        }
        firingWeaponId = selectedWeaponId;
        ammunitionPerShot = configuredRules.weaponAmmoPerShot(selectedWeaponId);
        ammunition = configuredRules.weaponAmmunition(selectedWeaponId);
        pelletCount = configuredRules.weaponPelletCount(selectedWeaponId);
        refireIntervalNanos = Duration.ofMillis(configuredRules.weaponRefireMilliseconds(selectedWeaponId))
                .toNanos();
        range = DoomUnits.toWorld(configuredRules.weaponRange(selectedWeaponId));
        autoAimAngle = (float) Math.toRadians(configuredRules.weaponAutoAimAngleDegrees(selectedWeaponId));
        autoAimMaximumSlope = configuredRules.weaponAutoAimMaximumSlope(selectedWeaponId);
    }

    /** Advances the active weapon's recovery without coupling gameplay cadence to presentation frames. */
    private void advanceRecovery(Duration step) {
        remainingRecoveryNanos = Math.max(0L, remainingRecoveryNanos - step.toNanos());
    }

    /** Traces the exact authored view ray before applying the configured Doom-style auto-aim cone. */
    private Optional<DoomWeaponHitLocation> fire() {
        DoomCombatRules configuredRules = requireRules();
        Vector3f direction = requiredViewTransform()
                .worldMatrix()
                .transformDirection(new Vector3f(0.0F, 0.0F, -1.0F))
                .normalize();
        Vector3f origin = requiredViewTransform().worldMatrix().getTranslation(new Vector3f());
        Optional<DoomWeaponHitLocation> resolvedHit = damageFirstTarget(origin, direction, range, configuredRules);
        if (resolvedHit.isEmpty()) {
            resolvedHit = autoAim(origin, direction, configuredRules);
        }
        return resolvedHit;
    }

    /** Selects the nearest visible damageable entity whose horizontal bounds intersect the authored aim cone. */
    private Optional<DoomWeaponHitLocation> autoAim(
            Vector3f origin, Vector3f viewDirection, DoomCombatRules configuredRules) {
        float forwardLength = (float) Math.hypot(viewDirection.x, viewDirection.z);
        if (forwardLength == 0.0F) {
            return Optional.empty();
        }
        float forwardX = viewDirection.x / forwardLength;
        float forwardZ = viewDirection.z / forwardLength;
        List<AimCandidate> candidates = new ArrayList<>();
        for (Entity root : owner.world().roots()) {
            collectCandidates(root, origin, forwardX, forwardZ, candidates);
        }
        candidates.sort(Comparator.comparingDouble(AimCandidate::distanceSquared));
        for (AimCandidate candidate : candidates) {
            Optional<DoomWeaponHitLocation> resolvedHit = damageCandidate(origin, candidate, configuredRules);
            if (resolvedHit.isPresent()) {
                return resolvedHit;
            }
        }
        return Optional.empty();
    }

    /** Collects descriptor-selected targets recursively without depending on authored hierarchy position. */
    private void collectCandidates(
            Entity entity, Vector3f origin, float forwardX, float forwardZ, List<AimCandidate> destination) {
        entity.capability(DoomedCorridorsDescriptors.HITSCAN_TARGET_CAPABILITY, DoomHitscanTarget.class)
                .filter(target -> target.owner() != owner)
                .flatMap(target -> candidate(target, origin, forwardX, forwardZ))
                .ifPresent(destination::add);
        entity.children().forEach(child -> collectCandidates(child, origin, forwardX, forwardZ, destination));
    }

    /** Projects one target against the configured horizontal angle and vertical slope window. */
    private Optional<AimCandidate> candidate(
            DoomHitscanTarget target, Vector3f origin, float forwardX, float forwardZ) {
        Vector3f direction = target.aimPoint(new Vector3f()).sub(origin);
        float horizontalDistance = (float) Math.hypot(direction.x, direction.z);
        float distanceSquared = direction.lengthSquared();
        if (horizontalDistance == 0.0F || distanceSquared > range * range) {
            return Optional.empty();
        }
        float targetX = direction.x / horizontalDistance;
        float targetZ = direction.z / horizontalDistance;
        float dot = Math.clamp(forwardX * targetX + forwardZ * targetZ, -1.0F, 1.0F);
        float centerAngle = (float) Math.acos(dot);
        float edgeAngle = (float) Math.asin(Math.min(1.0F, target.aimRadius() / horizontalDistance));
        float verticalSlope = Math.abs(direction.y / horizontalDistance);
        if (centerAngle > autoAimAngle + edgeAngle || verticalSlope > autoAimMaximumSlope) {
            return Optional.empty();
        }
        return Optional.of(new AimCandidate(target, direction.normalize(), distanceSquared));
    }

    /** Confirms that physics reaches the selected target before applying one damage roll. */
    private Optional<DoomWeaponHitLocation> damageCandidate(
            Vector3f origin, AimCandidate candidate, DoomCombatRules configuredRules) {
        Optional<CollisionRaycastHit3d> raycastHit = firstExternalHit(origin, candidate.direction(), range);
        if (raycastHit
                .map(result -> result.object().owner() == candidate.target().owner())
                .orElse(false)) {
            int appliedDamage = candidate.target().damage(rollShotDamage(configuredRules));
            return appliedDamage > 0 ? Optional.of(weaponHit(candidate.direction())) : Optional.empty();
        }
        return Optional.empty();
    }

    /** Applies damage to the first non-owner solid reached by one fully specified ray. */
    private Optional<DoomWeaponHitLocation> damageFirstTarget(
            Vector3f origin, Vector3f direction, float maximumDistance, DoomCombatRules configuredRules) {
        return firstExternalHit(origin, direction, maximumDistance)
                .flatMap(result -> result.object()
                        .owner()
                        .capability(DoomedCorridorsDescriptors.DAMAGEABLE_CAPABILITY, DoomDamageable.class))
                .filter(target -> target.damage(rollShotDamage(configuredRules)) > 0)
                .map(target -> weaponHit(direction));
    }

    /** Rolls every configured pellet after their shared unspread trajectory resolves one target. */
    private int rollShotDamage(DoomCombatRules configuredRules) {
        int damage = 0;
        for (int pellet = 0; pellet < pelletCount; pellet++) {
            damage += configuredRules.rollWeaponDamage(firingWeaponId, random);
        }
        return damage;
    }

    /** Captures one successful ray in the perspective coordinates used by the firing view. */
    private DoomWeaponHitLocation weaponHit(Vector3f worldDirection) {
        return DoomWeaponHitLocation.fromWorldDirection(
                worldDirection,
                requiredViewTransform().worldMatrix(),
                requiredViewCamera().fieldOfViewDegrees());
    }

    /** Finds one ray's first non-owner solid while tolerating an origin inside the player's own body. */
    private Optional<CollisionRaycastHit3d> firstExternalHit(
            Vector3f originalOrigin, Vector3f direction, float maximumDistance) {
        Vector3f origin = new Vector3f(originalOrigin);
        float remaining = maximumDistance;
        while (remaining > 0.0F) {
            Optional<CollisionRaycastHit3d> result = physics.raycast(origin, direction, remaining);
            if (result.isEmpty()) {
                return Optional.empty();
            }
            CollisionRaycastHit3d raycastHit = result.orElseThrow();
            if (raycastHit.object().owner() != owner) {
                return Optional.of(raycastHit);
            }
            float advance = raycastHit.distance() + SELF_HIT_ADVANCE;
            origin.fma(advance, direction);
            remaining -= advance;
        }
        return Optional.empty();
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

    private PerspectiveCamera3d requiredViewCamera() {
        return viewCamera.orElseThrow(() -> new IllegalStateException("view camera has not been bound"));
    }

    /** Requires endpoint binding before the active world accepts input. */
    private RuntimeSignal requiredFiredSignal() {
        if (fired == null) {
            throw new IllegalStateException("weapon fired signal has not been bound");
        }
        return fired;
    }

    private RuntimeSignal requiredHitSignal() {
        if (hitSignal == null) {
            throw new IllegalStateException("weapon hit signal has not been bound");
        }
        return hitSignal;
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

    /** One descriptor-selected target and its normalized aim ray. */
    private record AimCandidate(DoomHitscanTarget target, Vector3f direction, float distanceSquared) {
        private AimCandidate {
            Objects.requireNonNull(target, "target");
            direction = new Vector3f(Objects.requireNonNull(direction, "direction"));
        }
    }
}
