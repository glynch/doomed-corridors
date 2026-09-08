/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.jscene3d.game.input.ActionSnapshot;
import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.game.input.InputVector2;
import io.github.glynch.jscene3d.game.input.InputWorldModule;
import io.github.glynch.jscene3d.project.component.ComponentId;
import io.github.glynch.jscene3d.project.component.ComponentType;
import io.github.glynch.jscene3d.project.component.PropertyId;
import io.github.glynch.jscene3d.project.physics3d.CharacterBody3d;
import io.github.glynch.jscene3d.project.runtime.FixedUpdateContext;
import io.github.glynch.jscene3d.project.runtime.FrameUpdateContext;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceResolver;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentUpdateCallbacks;
import io.github.glynch.jscene3d.project.spatial3d.Transform3d;
import java.util.Objects;
import java.util.Optional;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Converts authored semantic movement and look actions into character-body and view-transform changes. */
final class DoomPlayerController implements ComponentReferenceBinder, ComponentUpdateCallbacks {
    static final ComponentId COMPONENT_ID = ComponentId.from("486f49a3-fe97-4a6c-b92d-533a1995493c");
    static final ComponentType TYPE = ComponentType.of(DoomedCorridorsRuntimeExtension.ID + "/player-controller", 2);
    static final PropertyId BODY_PROPERTY = new PropertyId("body");
    static final PropertyId VIEW_TRANSFORM_PROPERTY = new PropertyId("view-transform");
    static final PropertyId MOVE_ACTION_PROPERTY = new PropertyId("move-action");
    static final PropertyId LOOK_ACTION_PROPERTY = new PropertyId("look-action");
    static final PropertyId TURN_LEFT_ACTION_PROPERTY = new PropertyId("turn-left-action");
    static final PropertyId TURN_RIGHT_ACTION_PROPERTY = new PropertyId("turn-right-action");
    static final PropertyId MOVE_SPEED_PROPERTY = new PropertyId("move-speed");
    static final PropertyId TURN_SPEED_DEGREES_PROPERTY = new PropertyId("turn-speed-degrees");
    static final PropertyId POINTER_SENSITIVITY_PROPERTY = new PropertyId("pointer-sensitivity");
    static final PropertyId MAXIMUM_PITCH_DEGREES_PROPERTY = new PropertyId("maximum-pitch-degrees");

    private final InputWorldModule input;
    private final InputAction moveAction;
    private final InputAction lookAction;
    private final InputAction turnLeftAction;
    private final InputAction turnRightAction;
    private final float moveSpeed;
    private final float turnSpeed;
    private final float pointerSensitivity;
    private final float maximumPitch;
    private Optional<CharacterBody3d> characterBody = Optional.empty();
    private Optional<Transform3d> viewTransform = Optional.empty();
    private float yaw;
    private float pitch;
    private boolean pointerLookConsumed;

    /** Stores descriptor-validated configuration independently of physical input bindings. */
    DoomPlayerController(InputWorldModule input, Actions actions, Tuning tuning) {
        this.input = Objects.requireNonNull(input, "input");
        Actions validActions = Objects.requireNonNull(actions, "actions");
        moveAction = validActions.move();
        lookAction = validActions.look();
        turnLeftAction = validActions.turnLeft();
        turnRightAction = validActions.turnRight();
        Tuning validTuning = Objects.requireNonNull(tuning, "tuning");
        moveSpeed = requirePositive(validTuning.moveSpeed(), "moveSpeed");
        turnSpeed = (float) Math.toRadians(requirePositive(validTuning.turnSpeedDegrees(), "turnSpeedDegrees"));
        pointerSensitivity = requirePositive(validTuning.pointerSensitivity(), "pointerSensitivity");
        maximumPitch = (float) Math.toRadians(requirePitch(validTuning.maximumPitchDegrees()));
    }

    /** Resolves the explicitly authored body and child view identities, then adopts the authored view orientation. */
    @Override
    public void bindReferences(ComponentReferenceResolver references) {
        Objects.requireNonNull(references, "references");
        characterBody = Optional.of(references.component(BODY_PROPERTY, CharacterBody3d.class));
        Transform3d resolvedView = references.component(VIEW_TRANSFORM_PROPERTY, Transform3d.class);
        viewTransform = Optional.of(resolvedView);
        Vector3f angles = resolvedView.orientation().getEulerAnglesYXZ(new Vector3f());
        pitch = Math.clamp(angles.x, -maximumPitch, maximumPitch);
        yaw = angles.y;
    }

    /** Applies look and asks the physics-owned character body to resolve one fixed movement step. */
    @Override
    public void onBeforePhysics(FixedUpdateContext update) {
        Objects.requireNonNull(update, "update");
        ActionSnapshot snapshot = input.snapshot();
        applyPointerLook(snapshot);
        applyContinuousLook(snapshot, update);
        requiredBody().move(planarVelocity(snapshot.axis2d(moveAction)), update.step());
    }

    /** Allows the next host frame's relative movement to be consumed by one fixed update. */
    @Override
    public void onFrameUpdate(FrameUpdateContext update) {
        Objects.requireNonNull(update, "update");
        pointerLookConsumed = false;
    }

    /** Consumes relative mouse motion once even when a rendered frame runs several fixed updates. */
    private void applyPointerLook(ActionSnapshot snapshot) {
        double horizontal = snapshot.pointerDeltaX();
        double vertical = snapshot.pointerDeltaY();
        if (pointerLookConsumed || horizontal == 0.0 && vertical == 0.0) {
            return;
        }
        yaw -= (float) horizontal * pointerSensitivity;
        pitch = Math.clamp(pitch - (float) vertical * pointerSensitivity, -maximumPitch, maximumPitch);
        pointerLookConsumed = true;
        updateViewOrientation();
    }

    /** Applies held gamepad and authored keyboard turning proportionally to fixed elapsed time. */
    private void applyContinuousLook(ActionSnapshot snapshot, FixedUpdateContext update) {
        InputVector2 look = pointerLookConsumed ? InputVector2.ZERO : snapshot.axis2d(lookAction);
        float keyboardTurn = snapshot.axis(turnLeftAction, turnRightAction);
        float elapsedSeconds = update.step().toNanos() / 1_000_000_000.0F;
        yaw -= (look.x() + keyboardTurn) * turnSpeed * elapsedSeconds;
        pitch = Math.clamp(pitch + look.y() * turnSpeed * elapsedSeconds, -maximumPitch, maximumPitch);
        if (!look.equals(InputVector2.ZERO) || keyboardTurn != 0.0F) {
            updateViewOrientation();
        }
    }

    /** Publishes the accumulated yaw and pitch to the explicitly referenced view transform. */
    private void updateViewOrientation() {
        Quaternionf orientation = new Quaternionf().rotationYXZ(yaw, pitch, 0.0F);
        requiredViewTransform().setOrientation(orientation.x, orientation.y, orientation.z, orientation.w);
    }

    /** Converts the local semantic movement axis into normalized world-space planar velocity. */
    private Vector3f planarVelocity(InputVector2 move) {
        Vector3f velocity = new Vector3f(move.x(), 0.0F, -move.y());
        if (velocity.lengthSquared() > 1.0F) {
            velocity.normalize();
        }
        return velocity.rotateY(yaw).mul(moveSpeed);
    }

    /** Requires the stable body reference to have been bound before fixed updates begin. */
    private CharacterBody3d requiredBody() {
        return characterBody.orElseThrow(() -> new IllegalStateException("player body has not been bound"));
    }

    /** Requires the stable view reference to have been bound before fixed updates begin. */
    private Transform3d requiredViewTransform() {
        return viewTransform.orElseThrow(() -> new IllegalStateException("player view transform has not been bound"));
    }

    /** Rejects invalid positive authored movement values even after descriptor kind validation. */
    private static float requirePositive(float value, String name) {
        if (!Float.isFinite(value) || value <= 0.0F) {
            throw new IllegalArgumentException(name + " must be finite and positive: " + value);
        }
        return value;
    }

    /** Restricts authored pitch to a useful first-person range below ninety degrees. */
    private static float requirePitch(float value) {
        if (!Float.isFinite(value) || value <= 0.0F || value >= 90.0F) {
            throw new IllegalArgumentException("maximumPitchDegrees must be finite and in (0, 90): " + value);
        }
        return value;
    }

    /** Semantic actions consumed by one controller instance. */
    record Actions(InputAction move, InputAction look, InputAction turnLeft, InputAction turnRight) {
        /** Requires every authored action identity. */
        Actions {
            Objects.requireNonNull(move, "move");
            Objects.requireNonNull(look, "look");
            Objects.requireNonNull(turnLeft, "turnLeft");
            Objects.requireNonNull(turnRight, "turnRight");
        }
    }

    /** Authored movement and view tuning values consumed by one controller instance. */
    record Tuning(float moveSpeed, float turnSpeedDegrees, float pointerSensitivity, float maximumPitchDegrees) {}
}
