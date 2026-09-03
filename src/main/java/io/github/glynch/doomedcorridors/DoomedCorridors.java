/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.input.DoomPlayerInputAdapter;
import io.github.glynch.doomedcorridors.combat.DoomCombatSession;
import io.github.glynch.doomedcorridors.combat.DoomCombatUpdate;
import io.github.glynch.doomedcorridors.presentation.DoomCombatAudio;
import io.github.glynch.doomedcorridors.presentation.DoomCombatOverlay;
import io.github.glynch.doomedcorridors.presentation.DoomCombatPresentationState;
import io.github.glynch.doomedcorridors.presentation.DoomMapPresentation;
import io.github.glynch.doomedcorridors.world.DoomGameSession;
import io.github.glynch.doomedcorridors.world.DoomPlayerState;
import io.github.glynch.jscene3d.platform.CursorMode;
import io.github.glynch.jscene3d.platform.Key;
import io.github.glynch.jscene3d.platform.MouseButton;
import io.github.glynch.jscene3d.platform.Window;
import io.github.glynch.jscene3d.render.Renderer;
import java.nio.file.Path;
import java.time.Duration;

/** Standalone graphical entry point for Doomed Corridors. */
public final class DoomedCorridors {
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;
    private static final long MAXIMUM_FRAME_NANOS = 250_000_000L;

    private DoomedCorridors() {
        throw new AssertionError("DoomedCorridors cannot be instantiated");
    }

    /**
     * Loads the project-selected Doom map and presents its static geometry until closed.
     *
     * @param arguments optional project directory; defaults to the working directory
     */
    public static void main(String[] arguments) {
        Path projectDirectory = Path.of(arguments.length == 0 ? "." : arguments[0]);
        DoomStartup startup = DoomStartup.load(projectDirectory);
        DoomGameSession movement = DoomGameSession.create(startup.map(), startup.geometry().playerStart());
        DoomCombatSession combat = DoomCombatSession.create(
                startup.map(), startup.combat().rules(), startup.actors().actors(), 0L);
        DoomCombatPresentationState combatPresentation = new DoomCombatPresentationState(
                startup.combat().assets().rules(), combat.state());
        DoomCombatOverlay overlay = new DoomCombatOverlay(startup.combat().assets(), combatPresentation);
        DoomPlayerInputAdapter input = new DoomPlayerInputAdapter();
        try (Window window = Window.create(WINDOW_WIDTH, WINDOW_HEIGHT, startup.project().identity().name());
                Renderer renderer = Renderer.create(window);
                DoomMapPresentation presentation = DoomMapPresentation.create(
                        startup.geometry(),
                        startup.materials(),
                        startup.actors().actors(),
                        startup.sprites(),
                        startup.combat().assets(),
                        window.framebufferAspectRatio());
                DoomCombatAudio audio = DoomCombatAudio.create(startup.combat().assets())) {
            window.show();
            long previousNanos = System.nanoTime();
            while (!window.shouldClose()) {
                Window.pollEvents();
                boolean fireRequested = handlePointer(window);
                resizeIfNeeded(window, presentation);
                long nowNanos = System.nanoTime();
                long elapsedNanos = Math.clamp(nowNanos - previousNanos, 0L, MAXIMUM_FRAME_NANOS);
                previousNanos = nowNanos;
                Duration elapsed = Duration.ofNanos(elapsedNanos);
                boolean pointerLocked = window.cursorMode() == CursorMode.DISABLED;
                DoomPlayerState player = movement.advance(
                        input.sample(window.input(), pointerLocked), elapsed);
                if (fireRequested) {
                    DoomCombatUpdate update = combat.firePrimary(player);
                    combatPresentation.apply(update);
                    audio.apply(update);
                }
                combatPresentation.advance(elapsed);
                presentation.applyCombatState(combatPresentation);
                presentation.applyPlayerState(player);
                audio.applyPlayerState(player);
                renderer.render(presentation.scene(), presentation.camera());
                renderer.render(overlay);
                window.swapBuffers();
            }
        }
    }

    /** Manages pointer capture and reports a captured left-click as primary fire. */
    private static boolean handlePointer(Window window) {
        if (window.input().wasKeyPressed(Key.ESCAPE)) {
            if (window.cursorMode() == CursorMode.DISABLED) {
                window.setCursorMode(CursorMode.NORMAL);
            } else {
                window.requestClose();
            }
        }
        if (window.cursorMode() == CursorMode.NORMAL
                && window.input().wasMouseButtonPressed(MouseButton.LEFT)) {
            window.setCursorMode(CursorMode.DISABLED);
            if (window.isRawMouseMotionSupported()) {
                window.setRawMouseMotionEnabled(true);
            }
            return false;
        }
        return window.cursorMode() == CursorMode.DISABLED
                && window.input().wasMouseButtonPressed(MouseButton.LEFT);
    }

    /** Keeps the perspective projection synchronized with drawable framebuffer changes. */
    private static void resizeIfNeeded(Window window, DoomMapPresentation presentation) {
        if (window.framebufferSizeChanged() && window.framebufferWidth() > 0 && window.framebufferHeight() > 0) {
            presentation.resize(window.framebufferAspectRatio());
        }
    }
}
