/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import java.time.Duration;
import java.util.Objects;

/** Deterministic headless game session for Doom-style player movement and map collision. */
public final class DoomGameSession {
    private static final long FIXED_STEP_NANOS = 1_000_000_000L / 35L;
    private static final float FIXED_STEP_SECONDS = 1.0F / 35.0F;
    private static final float MOVE_SPEED = 8.0F;
    private static final float TURN_SPEED = (float) Math.PI;
    private static final float MAXIMUM_PITCH = (float) Math.toRadians(85.0);

    private final DoomCollisionWorld collision;
    private DoomPlayerState player;
    private long accumulatedNanos;

    /** Creates a session at the WAD-defined player start. */
    private DoomGameSession(DoomMap map, DoomPlayerStart start) {
        collision = new DoomCollisionWorld(map);
        float eyeHeight = collision.floorHeight(start.x(), start.z()) + DoomCollisionWorld.PLAYER_EYE_HEIGHT;
        player = new DoomPlayerState(start.x(), eyeHeight, start.z(), start.yawRadians(), 0.0F);
    }

    /**
     * Creates a headless session from decoded map data and its resolved player-one start.
     *
     * @param map decoded classic Doom map
     * @param start player-one start in world coordinates
     * @return a new deterministic session
     */
    public static DoomGameSession create(DoomMap map, DoomPlayerStart start) {
        return new DoomGameSession(
                Objects.requireNonNull(map, "map"), Objects.requireNonNull(start, "start"));
    }

    /** Returns the current immutable player state. */
    public DoomPlayerState player() {
        return player;
    }

    /**
     * Applies one frame command and advances whole 35 Hz simulation steps from elapsed time.
     *
     * <p>Pointer-view deltas are consumed once per call. Movement and keyboard-turn axes remain
     * active for every fixed step produced by that call; any partial step is retained for the next
     * call.
     *
     * @param command normalized movement and frame-relative view input
     * @param elapsed non-negative elapsed time
     * @return the resulting immutable player state
     */
    public DoomPlayerState advance(DoomPlayerCommand command, Duration elapsed) {
        DoomPlayerCommand validCommand = Objects.requireNonNull(command, "command");
        Duration validElapsed = Objects.requireNonNull(elapsed, "elapsed");
        if (validElapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must not be negative");
        }
        applyView(validCommand);
        accumulatedNanos = Math.addExact(accumulatedNanos, validElapsed.toNanos());
        while (accumulatedNanos >= FIXED_STEP_NANOS) {
            turnPlayer(validCommand);
            movePlayer(validCommand);
            accumulatedNanos -= FIXED_STEP_NANOS;
        }
        return player;
    }

    /** Applies a frame-relative yaw and pitch change independently of simulation cadence. */
    private void applyView(DoomPlayerCommand command) {
        float yaw = normalizeAngle(player.yawRadians() + command.yawDelta());
        float pitch = Math.clamp(
                player.pitchRadians() + command.pitchDelta(), -MAXIMUM_PITCH, MAXIMUM_PITCH);
        player = new DoomPlayerState(player.x(), player.eyeHeight(), player.z(), yaw, pitch);
    }

    /** Applies one held keyboard-turn command at the fixed simulation rate. */
    private void turnPlayer(DoomPlayerCommand command) {
        float yaw = normalizeAngle(player.yawRadians() + command.turn() * TURN_SPEED * FIXED_STEP_SECONDS);
        player = new DoomPlayerState(
                player.x(), player.eyeHeight(), player.z(), yaw, player.pitchRadians());
    }

    /** Applies one normalized movement command through the collision world. */
    private void movePlayer(DoomPlayerCommand command) {
        float yaw = player.yawRadians();
        float forwardX = (float) Math.cos(yaw);
        float forwardZ = -(float) Math.sin(yaw);
        float rightX = (float) Math.sin(yaw);
        float rightZ = (float) Math.cos(yaw);
        float deltaX = forwardX * command.forward() + rightX * command.strafe();
        float deltaZ = forwardZ * command.forward() + rightZ * command.strafe();
        float length = (float) Math.hypot(deltaX, deltaZ);
        if (length > 1.0F) {
            deltaX /= length;
            deltaZ /= length;
        }
        float distance = MOVE_SPEED * FIXED_STEP_SECONDS;
        DoomCollisionWorld.Position position =
                collision.move(player.x(), player.z(), deltaX * distance, deltaZ * distance);
        player = new DoomPlayerState(
                position.x(),
                position.floorHeight() + DoomCollisionWorld.PLAYER_EYE_HEIGHT,
                position.z(),
                player.yawRadians(),
                player.pitchRadians());
    }

    /** Normalizes yaw to the negative-pi through positive-pi interval. */
    private static float normalizeAngle(float angle) {
        float fullTurn = (float) (Math.PI * 2.0);
        float normalized = angle % fullTurn;
        if (normalized > Math.PI) {
            normalized -= fullTurn;
        } else if (normalized < -Math.PI) {
            normalized += fullTurn;
        }
        return normalized;
    }
}
