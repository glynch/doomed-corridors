/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.input;

import io.github.glynch.doomedcorridors.world.DoomPlayerCommand;
import io.github.glynch.jscene3d.game.input.ActionSnapshot;
import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.game.input.InputCapture;
import io.github.glynch.jscene3d.game.input.InputMap;
import io.github.glynch.jscene3d.platform.InputState;
import io.github.glynch.jscene3d.platform.Key;
import java.util.Objects;

/** Adapts native keyboard and relative-pointer state to one headless player command. */
public final class DoomPlayerInputAdapter {
    private static final InputAction MOVE_FORWARD = new InputAction("move-forward");
    private static final InputAction MOVE_BACKWARD = new InputAction("move-backward");
    private static final InputAction STRAFE_LEFT = new InputAction("strafe-left");
    private static final InputAction STRAFE_RIGHT = new InputAction("strafe-right");
    private static final InputAction TURN_LEFT = new InputAction("turn-left");
    private static final InputAction TURN_RIGHT = new InputAction("turn-right");
    private static final float POINTER_SENSITIVITY = 0.002F;

    private final InputMap inputMap = InputMap.builder()
            .bind(MOVE_FORWARD, Key.W)
            .bind(MOVE_FORWARD, Key.UP)
            .bind(MOVE_BACKWARD, Key.S)
            .bind(MOVE_BACKWARD, Key.DOWN)
            .bind(STRAFE_LEFT, Key.A)
            .bind(STRAFE_RIGHT, Key.D)
            .bind(TURN_LEFT, Key.LEFT)
            .bind(TURN_RIGHT, Key.RIGHT)
            .build();

    /**
     * Samples held movement keys and captured relative-pointer motion.
     *
     * @param input current native input snapshot
     * @param pointerLocked whether relative pointer movement belongs to gameplay
     * @return one GUI-independent player command
     */
    public DoomPlayerCommand sample(InputState input, boolean pointerLocked) {
        InputCapture capture = new InputCapture(false, !pointerLocked);
        ActionSnapshot actions = inputMap.sample(Objects.requireNonNull(input, "input"), capture);
        float forward = actions.axis(MOVE_BACKWARD, MOVE_FORWARD);
        float strafe = actions.axis(STRAFE_LEFT, STRAFE_RIGHT);
        float turn = actions.axis(TURN_RIGHT, TURN_LEFT);
        float yawDelta = -(float) actions.pointerDeltaX() * POINTER_SENSITIVITY;
        float pitchDelta = -(float) actions.pointerDeltaY() * POINTER_SENSITIVITY;
        return new DoomPlayerCommand(forward, strafe, turn, yawDelta, pitchDelta);
    }
}
