/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.runtime;

import io.github.glynch.jscene3d.game.GameRuntime;
import io.github.glynch.jscene3d.game.input.ActionSnapshot;
import io.github.glynch.jscene3d.game.input.InputCapture;
import io.github.glynch.jscene3d.game.input.InputMap;
import io.github.glynch.jscene3d.platform.CursorMode;
import io.github.glynch.jscene3d.platform.Key;
import io.github.glynch.jscene3d.platform.MouseButton;
import io.github.glynch.jscene3d.platform.Window;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntime;
import io.github.glynch.jscene3d.project.runtime.lwjgl.JScene3dRuntimeExtension;
import io.github.glynch.jscene3d.render.Renderer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Graphical proof that renders imported MAP01 through the declarative project runtime. */
public final class DoomedCorridorsRuntimePreview {
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;
    private static final long MAXIMUM_FRAME_NANOS = 250_000_000L;

    /** Prevents construction of this application entry-point namespace. */
    private DoomedCorridorsRuntimePreview() {
        throw new AssertionError("DoomedCorridorsRuntimePreview cannot be instantiated");
    }

    /**
     * Loads the project-selected imported map and renders its static presentation.
     *
     * @param arguments optional project directory; defaults to the working directory
     */
    public static void main(String[] arguments) {
        if (arguments.length > 1) {
            throw new IllegalArgumentException("expected at most one project directory");
        }
        Path projectDirectory = Path.of(arguments.length == 0 ? "." : arguments[0]);
        try (Window window = Window.create(WINDOW_WIDTH, WINDOW_HEIGHT, "Doomed Corridors Runtime Preview");
                Renderer renderer = Renderer.create(window)) {
            runProject(window, renderer, projectDirectory);
        }
    }

    /** Composes and owns the project runtime for one graphical session. */
    private static void runProject(Window window, Renderer renderer, Path projectDirectory) {
        try (ProjectRuntime project = DoomedCorridorsRuntimeLoader.load(
                projectDirectory, List.of(new JScene3dRuntimeExtension(window, renderer)))) {
            runGame(window, project);
        }
    }

    /** Loads project input and transfers the composed project into the game loop. */
    private static void runGame(Window window, ProjectRuntime project) {
        InputMap inputMap = DoomedCorridorsRuntimeLoader.loadInputMap(project.project());
        try (GameRuntime runtime = new GameRuntime(project)) {
            runtime.start();
            window.show();
            run(window, runtime, inputMap);
        }
    }

    /** Drives the generic runtime until the preview window closes. */
    private static void run(Window window, GameRuntime runtime, InputMap inputMap) {
        long previousNanos = System.nanoTime();
        while (!window.shouldClose()) {
            Window.pollEvents();
            handlePointerCapture(window);
            long nowNanos = System.nanoTime();
            long elapsedNanos = Math.clamp(nowNanos - previousNanos, 0L, MAXIMUM_FRAME_NANOS);
            previousNanos = nowNanos;
            ActionSnapshot input = inputMap.sample(
                    window.input(), new InputCapture(false, window.cursorMode() != CursorMode.DISABLED));
            runtime.advance(Duration.ofNanos(elapsedNanos), input);
            runtime.render();
            window.swapBuffers();
        }
    }

    /** Acquires relative mouse input on click and releases it on Escape. */
    private static void handlePointerCapture(Window window) {
        if (window.input().wasKeyPressed(Key.ESCAPE)) {
            if (window.cursorMode() == CursorMode.DISABLED) {
                window.setCursorMode(CursorMode.NORMAL);
            } else {
                window.requestClose();
            }
            return;
        }
        if (window.cursorMode() == CursorMode.NORMAL
                && window.input().wasMouseButtonPressed(MouseButton.LEFT)) {
            window.setCursorMode(CursorMode.DISABLED);
            if (window.isRawMouseMotionSupported()) {
                window.setRawMouseMotionEnabled(true);
            }
        }
    }
}
