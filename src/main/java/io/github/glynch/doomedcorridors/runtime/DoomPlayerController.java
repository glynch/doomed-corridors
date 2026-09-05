/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.runtime;

import io.github.glynch.doomedcorridors.world.DoomGameSession;
import io.github.glynch.doomedcorridors.world.DoomPlayerCommand;
import io.github.glynch.doomedcorridors.world.DoomPlayerState;
import io.github.glynch.doomedcorridors.presentation.DoomPlayerCamera;
import io.github.glynch.jscene3d.cameras.PerspectiveCamera;
import io.github.glynch.jscene3d.game.FrameUpdate;
import io.github.glynch.jscene3d.game.input.ActionSnapshot;
import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.project.runtime.FrameUpdateParticipant;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntimeObject;
import io.github.glynch.jscene3d.project.runtime.RuntimeNode;
import io.github.glynch.jscene3d.project.runtime.extension.NodeControllerContext;
import io.github.glynch.jscene3d.project.runtime.extension.ProjectValues;
import io.github.glynch.jscene3d.project.runtime.lwjgl.Scene3dRuntimeObject;
import java.util.Objects;

/** Game-owned player movement controller driven exclusively by semantic project input. */
final class DoomPlayerController implements ProjectRuntimeObject, FrameUpdateParticipant {
    private static final InputAction MOVE_FORWARD = new InputAction("move-forward");
    private static final InputAction MOVE_BACKWARD = new InputAction("move-backward");
    private static final InputAction STRAFE_LEFT = new InputAction("strafe-left");
    private static final InputAction STRAFE_RIGHT = new InputAction("strafe-right");
    private static final InputAction TURN_LEFT = new InputAction("turn-left");
    private static final InputAction TURN_RIGHT = new InputAction("turn-right");

    private final RuntimeNode node;
    private final String cameraNodeId;
    private final float pointerSensitivity;
    private final DoomGameSession session;
    private PerspectiveCamera camera;
    private boolean started;
    private boolean closed;

    /** Stores the authored controller configuration and creates its headless player session. */
    private DoomPlayerController(
            RuntimeNode node,
            String cameraNodeId,
            float pointerSensitivity,
            DoomGameSession session) {
        this.node = node;
        this.cameraNodeId = cameraNodeId;
        this.pointerSensitivity = pointerSensitivity;
        this.session = session;
    }

    /** Creates one controller for a Doom level and its declared camera child. */
    static DoomPlayerController create(NodeControllerContext context) {
        RuntimeNode node = context.node();
        if (!(node.object() instanceof DoomLevel3d level)) {
            throw new IllegalArgumentException("doom-player-controller requires a doom-level-3d node");
        }
        String cameraNode = ProjectValues.text(context.properties(), "camera-node");
        float sensitivity = ProjectValues.finiteFloat(context.properties(), "pointer-sensitivity");
        if (sensitivity <= 0.0F) {
            throw new IllegalArgumentException("pointer-sensitivity must be greater than zero");
        }
        DoomGameSession session = DoomGameSession.create(level.map(), level.playerStart());
        return new DoomPlayerController(node, cameraNode, sensitivity, session);
    }

    @Override
    public void start() {
        requireOpen();
        if (started) {
            throw new IllegalStateException("Doom player controller has already started");
        }
        camera = findCamera();
        DoomPlayerCamera.apply(camera, session.player());
        started = true;
    }

    @Override
    public void update(FrameUpdate update) {
        Objects.requireNonNull(update, "update");
        requireRunning();
        ActionSnapshot input = update.input();
        DoomPlayerCommand command = new DoomPlayerCommand(
                input.axis(MOVE_BACKWARD, MOVE_FORWARD),
                input.axis(STRAFE_LEFT, STRAFE_RIGHT),
                input.axis(TURN_RIGHT, TURN_LEFT),
                -(float) input.pointerDeltaX() * pointerSensitivity,
                -(float) input.pointerDeltaY() * pointerSensitivity);
        DoomPlayerCamera.apply(camera, session.advance(command, update.elapsed()));
    }

    @Override
    public void close() {
        closed = true;
        camera = null;
    }

    /** Returns current player state for deterministic application tests. */
    DoomPlayerState player() {
        requireOpen();
        return session.player();
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
}
