/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsDescriptors;
import io.github.glynch.jscene3d.doom.runtime.DoomDoor;
import io.github.glynch.jscene3d.doom.runtime.DoomDoorDescriptors;
import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.game.input.InputWorldModule;
import io.github.glynch.jscene3d.project.physics3d.CollisionRaycastHit3d;
import io.github.glynch.jscene3d.project.physics3d.Physics3dWorldModule;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.FixedUpdateContext;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceResolver;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentUpdateCallbacks;
import io.github.glynch.jscene3d.project.spatial3d.Transform3d;
import java.util.Objects;
import java.util.Optional;
import org.joml.Vector3f;

/** Uses the authored interaction action to activate the first unobstructed Doom door in front of the player. */
final class DoomDoorInteractor implements ComponentReferenceBinder, ComponentUpdateCallbacks {
    private static final float SELF_HIT_ADVANCE = 1.0E-4F;

    private final InputWorldModule input;
    private final Physics3dWorldModule physics;
    private final InputAction action;
    private final float maximumDistance;
    private Optional<Transform3d> viewTransform = Optional.empty();
    private Optional<Entity> ignoredEntity = Optional.empty();

    /** Retains authored input and range configuration against the current World modules. */
    DoomDoorInteractor(InputWorldModule input, Physics3dWorldModule physics, String action, float maximumDistance) {
        this.input = Objects.requireNonNull(input, "input");
        this.physics = Objects.requireNonNull(physics, "physics");
        this.action = new InputAction(requireText(action, "action"));
        if (!Float.isFinite(maximumDistance) || maximumDistance <= 0.0F) {
            throw new IllegalArgumentException("maximumDistance must be finite and positive");
        }
        this.maximumDistance = maximumDistance;
    }

    /** Resolves the descriptor-declared view transform and collision entity to ignore. */
    @Override
    public void bindReferences(ComponentReferenceResolver references) {
        ComponentReferenceResolver validReferences = Objects.requireNonNull(references, "references");
        viewTransform = Optional.of(validReferences.component(
                DoomedCorridorsDescriptors.INTERACTION_VIEW_TRANSFORM_PROPERTY, Transform3d.class));
        ignoredEntity =
                Optional.of(validReferences.entity(DoomedCorridorsDescriptors.INTERACTION_IGNORED_ENTITY_PROPERTY));
    }

    /** Activates only the nearest solid reached by a newly pressed authored interaction action. */
    @Override
    public void onBeforePhysics(FixedUpdateContext update) {
        Objects.requireNonNull(update, "update");
        if (!input.snapshot().wasPressed(action)) {
            return;
        }
        Transform3d view = requiredViewTransform();
        Vector3f origin = view.worldMatrix().getTranslation(new Vector3f());
        Vector3f direction = view.worldMatrix()
                .transformDirection(new Vector3f(0.0F, 0.0F, -1.0F))
                .normalize();
        firstExternalHit(origin, direction).flatMap(this::door).ifPresent(DoomDoor::activate);
    }

    /** Skips a ray hit on the explicitly authored player entity and stops at every other solid. */
    private Optional<CollisionRaycastHit3d> firstExternalHit(Vector3f originalOrigin, Vector3f direction) {
        Vector3f origin = new Vector3f(originalOrigin);
        float remaining = maximumDistance;
        while (remaining > 0.0F) {
            Optional<CollisionRaycastHit3d> result = physics.raycast(origin, direction, remaining);
            if (result.isEmpty()) {
                return Optional.empty();
            }
            CollisionRaycastHit3d hit = result.orElseThrow();
            if (hit.object().owner() != requiredIgnoredEntity()) {
                return Optional.of(hit);
            }
            float advance = hit.distance() + SELF_HIT_ADVANCE;
            origin.fma(advance, direction);
            remaining -= advance;
        }
        return Optional.empty();
    }

    /** Resolves door participation exclusively through the hit entity's declared capability. */
    private Optional<DoomDoor> door(CollisionRaycastHit3d hit) {
        return hit.object().owner().capability(DoomDoorDescriptors.DOOR_CAPABILITY, DoomDoor.class);
    }

    private Transform3d requiredViewTransform() {
        return viewTransform.orElseThrow(() -> new IllegalStateException("view transform has not been bound"));
    }

    private Entity requiredIgnoredEntity() {
        return ignoredEntity.orElseThrow(() -> new IllegalStateException("ignored entity has not been bound"));
    }

    private static String requireText(String value, String name) {
        String validValue = Objects.requireNonNull(value, name);
        if (validValue.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return validValue;
    }
}
