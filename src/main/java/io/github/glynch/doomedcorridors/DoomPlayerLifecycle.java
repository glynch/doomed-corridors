/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsRuntimeTypes;
import io.github.glynch.jscene3d.game.application.ApplicationCommand;
import io.github.glynch.jscene3d.game.application.ApplicationControl;
import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.game.input.InputWorldModule;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.FixedUpdateContext;
import io.github.glynch.jscene3d.project.runtime.FrameUpdateContext;
import io.github.glynch.jscene3d.project.runtime.World;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpointBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceResolver;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentUpdateCallbacks;
import java.time.Duration;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Applies terminal player lifecycle changes to an explicitly authored controls entity. */
final class DoomPlayerLifecycle implements ComponentReferenceBinder, ComponentEndpointBinder, ComponentUpdateCallbacks {
    private final World world;
    private final InputWorldModule input;
    private final ApplicationControl application;
    private final Duration gameOverDelay;
    private final InputAction returnToMenuAction;
    private @Nullable Entity controls;
    private @Nullable Entity gameOver;
    private Duration deathElapsed = Duration.ZERO;
    private boolean dead;
    private boolean gameOverVisible;

    DoomPlayerLifecycle(
            World world,
            InputWorldModule input,
            ApplicationControl application,
            Duration gameOverDelay,
            String returnToMenuAction) {
        this.world = Objects.requireNonNull(world, "world");
        this.input = Objects.requireNonNull(input, "input");
        this.application = Objects.requireNonNull(application, "application");
        this.gameOverDelay = requirePositive(gameOverDelay, "gameOverDelay");
        this.returnToMenuAction = new InputAction(Objects.requireNonNull(returnToMenuAction, "returnToMenuAction"));
    }

    @Override
    public void bindReferences(ComponentReferenceResolver references) {
        controls = Objects.requireNonNull(references, "references")
                .entity(DoomedCorridorsRuntimeTypes.PLAYER_CONTROL_ENTITY_PROPERTY);
        gameOver = references.entity(DoomedCorridorsRuntimeTypes.PLAYER_GAME_OVER_ENTITY_PROPERTY);
    }

    @Override
    public void bindEndpoints(ComponentEndpoints endpoints) {
        Objects.requireNonNull(endpoints, "endpoints")
                .action(DoomedCorridorsRuntimeTypes.RECEIVE_PLAYER_DIED_ACTION, this::receiveDied);
    }

    /** Returns whether this lifecycle has applied terminal player death. */
    boolean isDead() {
        return dead;
    }

    /** Returns whether the authored terminal menu has been enabled. */
    boolean isGameOverVisible() {
        return gameOverVisible;
    }

    @Override
    public void onBeforePhysics(FixedUpdateContext update) {
        Objects.requireNonNull(update, "update");
        if (dead && input.snapshot().wasPressed(returnToMenuAction)) {
            application.request(ApplicationCommand.RETURN_TO_MENU);
        }
    }

    @Override
    public void onFrameUpdate(FrameUpdateContext update) {
        Objects.requireNonNull(update, "update");
        if (!dead || gameOverVisible) {
            return;
        }
        deathElapsed = deathElapsed.plus(update.elapsed());
        if (deathElapsed.compareTo(gameOverDelay) >= 0) {
            world.enable(Objects.requireNonNull(gameOver, "game-over entity has not been bound"));
            gameOverVisible = true;
        }
    }

    /** Disables the exact authored controls entity once while leaving the view and HUD active. */
    private void receiveDied() {
        if (dead) {
            return;
        }
        Entity target = Objects.requireNonNull(controls, "player controls have not been bound");
        if (target.isLocallyEnabled()) {
            world.disable(target);
        }
        dead = true;
    }

    /** Requires an authored positive presentation delay. */
    private static Duration requirePositive(Duration value, String name) {
        Duration valid = Objects.requireNonNull(value, name);
        if (valid.isZero() || valid.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return valid;
    }
}
