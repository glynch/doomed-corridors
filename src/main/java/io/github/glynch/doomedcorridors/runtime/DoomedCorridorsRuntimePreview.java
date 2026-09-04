/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.runtime;

import io.github.glynch.jscene3d.game.GameRuntime;
import io.github.glynch.jscene3d.game.input.ActionSnapshot;
import io.github.glynch.jscene3d.platform.Key;
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
            ProjectRuntime project = DoomedCorridorsRuntimeLoader.load(
                    projectDirectory,
                    List.of(new JScene3dRuntimeExtension(window, renderer)));
            try (GameRuntime runtime = new GameRuntime(project)) {
                runtime.start();
                window.show();
                run(window, runtime);
            }
        }
    }

    /** Drives the generic runtime until the preview window closes. */
    private static void run(Window window, GameRuntime runtime) {
        long previousNanos = System.nanoTime();
        while (!window.shouldClose()) {
            Window.pollEvents();
            if (window.input().wasKeyPressed(Key.ESCAPE)) {
                window.requestClose();
            }
            long nowNanos = System.nanoTime();
            long elapsedNanos = Math.clamp(nowNanos - previousNanos, 0L, MAXIMUM_FRAME_NANOS);
            previousNanos = nowNanos;
            runtime.advance(Duration.ofNanos(elapsedNanos), ActionSnapshot.empty());
            runtime.render();
            window.swapBuffers();
        }
    }
}
