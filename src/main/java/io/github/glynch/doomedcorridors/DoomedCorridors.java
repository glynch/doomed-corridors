/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.input.DoomPlayerInputAdapter;
import io.github.glynch.doomedcorridors.presentation.DoomMapPresentation;
import io.github.glynch.doomedcorridors.world.DoomGameSession;
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
        DoomGameSession session = DoomGameSession.create(startup.map(), startup.geometry().playerStart());
        DoomPlayerInputAdapter input = new DoomPlayerInputAdapter();
        try (Window window = Window.create(WINDOW_WIDTH, WINDOW_HEIGHT, startup.project().identity().name());
                Renderer renderer = Renderer.create(window);
                DoomMapPresentation presentation = DoomMapPresentation.create(
                        startup.geometry(),
                        startup.materials(),
                        startup.actors().actors(),
                        startup.sprites(),
                        window.framebufferAspectRatio())) {
            window.show();
            long previousNanos = System.nanoTime();
            while (!window.shouldClose()) {
                Window.pollEvents();
                handlePointer(window);
                resizeIfNeeded(window, presentation);
                long nowNanos = System.nanoTime();
                long elapsedNanos = Math.clamp(nowNanos - previousNanos, 0L, MAXIMUM_FRAME_NANOS);
                previousNanos = nowNanos;
                boolean pointerLocked = window.cursorMode() == CursorMode.DISABLED;
                presentation.applyPlayerState(session.advance(
                        input.sample(window.input(), pointerLocked), Duration.ofNanos(elapsedNanos)));
                renderer.render(presentation.scene(), presentation.camera());
                window.swapBuffers();
            }
        }
    }

    /** Captures the pointer on click and makes Escape release it before closing the game. */
    private static void handlePointer(Window window) {
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
        }
    }

    /** Keeps the perspective projection synchronized with drawable framebuffer changes. */
    private static void resizeIfNeeded(Window window, DoomMapPresentation presentation) {
        if (window.framebufferSizeChanged() && window.framebufferWidth() > 0 && window.framebufferHeight() > 0) {
            presentation.resize(window.framebufferAspectRatio());
        }
    }
}
