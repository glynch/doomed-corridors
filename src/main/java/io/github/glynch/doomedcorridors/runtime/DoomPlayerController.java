/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.runtime;

import static io.github.glynch.doomedcorridors.internal.Preconditions.requirePositive;

import io.github.glynch.doomedcorridors.presentation.DoomPlayerCamera;
import io.github.glynch.doomedcorridors.world.DoomDoorState;
import io.github.glynch.doomedcorridors.world.DoomPlayerStart;
import io.github.glynch.doomedcorridors.world.DoomPlayerState;
import io.github.glynch.jscene3d.cameras.PerspectiveCamera;
import io.github.glynch.jscene3d.game.FixedUpdate;
import io.github.glynch.jscene3d.game.input.ActionSnapshot;
import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.project.runtime.FixedUpdateParticipant;
import io.github.glynch.jscene3d.project.runtime.FixedUpdatePhase;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntimeObject;
import io.github.glynch.jscene3d.project.runtime.RuntimeNode;
import io.github.glynch.jscene3d.project.runtime.extension.NodeControllerContext;
import io.github.glynch.jscene3d.project.runtime.extension.ProjectValues;
import io.github.glynch.jscene3d.project.runtime.scene3d.CharacterBody3d;
import io.github.glynch.jscene3d.project.runtime.scene3d.Scene3dRuntimeObject;
import java.util.List;
import java.util.Objects;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Supplies Doom-specific movement intent and view rules to a generic character body. */
final class DoomPlayerController implements ProjectRuntimeObject, FixedUpdateParticipant {
    private static final InputAction MOVE_FORWARD = new InputAction("move-forward");
    private static final InputAction MOVE_BACKWARD = new InputAction("move-backward");
    private static final InputAction STRAFE_LEFT = new InputAction("strafe-left");
    private static final InputAction STRAFE_RIGHT = new InputAction("strafe-right");
    private static final InputAction TURN_LEFT = new InputAction("turn-left");
    private static final InputAction TURN_RIGHT = new InputAction("turn-right");
    private static final InputAction INTERACT = new InputAction("interact");
    private static final float CAMERA_OFFSET = 13.0F / 32.0F;

    private final RuntimeNode node;
    private final DoomLevel3d level;
    private final CharacterBody3d characterBody;
    private final String cameraNodeId;
    private final float pointerSensitivity;
    private final float moveSpeed;
    private final float turnSpeed;
    private final float maximumPitch;
    private PerspectiveCamera camera;
    private float yaw;
    private float pitch;
    private boolean started;
    private boolean closed;

    /** Stores the authored controller configuration for one generic character body. */
    private DoomPlayerController(
            RuntimeNode node,
            DoomLevel3d level,
            CharacterBody3d characterBody,
            ControllerSettings settings) {
        this.node = node;
        this.level = level;
        this.characterBody = characterBody;
        cameraNodeId = settings.cameraNodeId();
        pointerSensitivity = settings.pointerSensitivity();
        moveSpeed = settings.moveSpeed();
        turnSpeed = settings.turnSpeed();
        maximumPitch = settings.maximumPitch();
    }

    /** Creates one game controller for a character body nested directly below a Doom level. */
    static DoomPlayerController create(NodeControllerContext context) {
        RuntimeNode node = context.node();
        DoomLevel3d level = node.parent()
                .map(RuntimeNode::object)
                .filter(DoomLevel3d.class::isInstance)
                .map(DoomLevel3d.class::cast)
                .orElseThrow(() -> new IllegalArgumentException(
                        "doom-player-controller requires a player node directly below doom-level-3d"));
        if (!(node.object() instanceof CharacterBody3d characterBody)) {
            throw new IllegalArgumentException("doom-player-controller requires a character-body-3d node");
        }
        ControllerSettings settings = new ControllerSettings(
                ProjectValues.text(context.properties(), "camera-node"),
                requirePositive(
                        ProjectValues.finiteFloat(context.properties(), "pointer-sensitivity"),
                        "pointer-sensitivity"),
                requirePositive(ProjectValues.finiteFloat(context.properties(), "move-speed"), "move-speed"),
                (float) Math.toRadians(requirePositive(
                        ProjectValues.finiteFloat(context.properties(), "turn-speed-degrees"),
                        "turn-speed-degrees")),
                (float) Math.toRadians(requirePositive(
                        ProjectValues.finiteFloat(context.properties(), "maximum-pitch-degrees"),
                        "maximum-pitch-degrees")));
        return new DoomPlayerController(node, level, characterBody, settings);
    }

    @Override
    public void start() {
        requireOpen();
        if (started) {
            throw new IllegalStateException("Doom player controller has already started");
        }
        camera = findCamera();
        DoomPlayerStart start = level.playerStart();
        yaw = start.yawRadians();
        characterBody.teleport(
                new Vector3f(start.x(), start.eyeHeight() - CAMERA_OFFSET, start.z()),
                new Quaternionf().rotationY(yaw));
        applyCamera();
        started = true;
    }

    @Override
    public FixedUpdatePhase fixedUpdatePhase() {
        return FixedUpdatePhase.BEFORE_PHYSICS;
    }

    @Override
    public void fixedUpdate(FixedUpdate update) {
        Objects.requireNonNull(update, "update");
        requireRunning();
        float fixedSeconds = update.step().toNanos() / 1_000_000_000.0F;
        ActionSnapshot input = update.input();
        yaw += input.axis(TURN_RIGHT, TURN_LEFT) * turnSpeed * fixedSeconds
                - (float) input.pointerDeltaX() * pointerSensitivity;
        pitch = Math.clamp(
                pitch - (float) input.pointerDeltaY() * pointerSensitivity,
                -maximumPitch,
                maximumPitch);
        if (input.wasPressed(INTERACT)) {
            Vector3f position = characterBody.controller().body().position(new Vector3f());
            level.interact(position.x, position.z, yaw);
        }
        characterBody.controller().move(movementVelocity(input), fixedSeconds);
        characterBody.controller().body().setTransform(
                characterBody.controller().body().position(new Vector3f()),
                new Quaternionf().rotationY(yaw));
        applyCamera();
        level.advanceDoors();
    }

    @Override
    public void close() {
        closed = true;
        camera = null;
    }

    /** Returns current player state for deterministic application tests. */
    DoomPlayerState player() {
        requireOpen();
        Vector3f position = characterBody.controller().body().position(new Vector3f());
        return new DoomPlayerState(position.x, position.y + CAMERA_OFFSET, position.z, yaw, pitch);
    }

    /** Returns immutable door snapshots for headless inspection and play-debug tooling. */
    List<DoomDoorState> doors() {
        requireOpen();
        return level.doors();
    }

    /** Converts semantic player actions into normalized world-space planar velocity. */
    private Vector3f movementVelocity(ActionSnapshot input) {
        float forward = input.axis(MOVE_BACKWARD, MOVE_FORWARD);
        float strafe = input.axis(STRAFE_LEFT, STRAFE_RIGHT);
        float velocityX = (float) Math.cos(yaw) * forward + (float) Math.sin(yaw) * strafe;
        float velocityZ = -(float) Math.sin(yaw) * forward + (float) Math.cos(yaw) * strafe;
        Vector3f velocity = new Vector3f(velocityX, 0.0F, velocityZ);
        if (velocity.lengthSquared() > 1.0F) {
            velocity.normalize();
        }
        return velocity.mul(moveSpeed);
    }

    /** Applies local eye height and pitch while parent-body orientation supplies yaw. */
    private void applyCamera() {
        DoomPlayerCamera.applyLocalView(camera, CAMERA_OFFSET, pitch);
    }

    /** Finds the declared camera among the controlled node's authored children. */
    private PerspectiveCamera findCamera() {
        RuntimeNode cameraNode = node.children().stream()
                .filter(child -> child.definition().id().equals(cameraNodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "camera-node does not identify a child of " + node.definition().id() + ": " + cameraNodeId));
        if (cameraNode.object() instanceof Scene3dRuntimeObject spatial
                && spatial.object3d() instanceof PerspectiveCamera perspectiveCamera) {
            return perspectiveCamera;
        }
        throw new IllegalArgumentException("camera-node must identify a perspective-camera-3d child: " + cameraNodeId);
    }

    /** Requires a started controller that has not been closed. */
    private void requireRunning() {
        requireOpen();
        if (!started) {
            throw new IllegalStateException("Doom player controller has not started");
        }
    }

    /** Rejects access after terminal lifecycle closure. */
    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Doom player controller is closed");
        }
    }

    /** Authored movement and view settings converted to runtime units. */
    private record ControllerSettings(
            String cameraNodeId,
            float pointerSensitivity,
            float moveSpeed,
            float turnSpeed,
            float maximumPitch) {}
}
