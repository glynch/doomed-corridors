/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.presentation.DoomMapPresentation;
import io.github.glynch.jscene3d.platform.Key;
import io.github.glynch.jscene3d.platform.Window;
import io.github.glynch.jscene3d.render.Renderer;
import java.nio.file.Path;

/** Standalone graphical entry point for Doomed Corridors. */
public final class DoomedCorridors {
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 720;

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
        try (Window window = Window.create(WINDOW_WIDTH, WINDOW_HEIGHT, startup.project().identity().name());
                Renderer renderer = Renderer.create(window);
                DoomMapPresentation presentation = DoomMapPresentation.create(
                        startup.geometry(), startup.materials(), window.framebufferAspectRatio())) {
            window.show();
            while (!window.shouldClose()) {
                Window.pollEvents();
                if (window.input().wasKeyPressed(Key.ESCAPE)) {
                    window.requestClose();
                }
                resizeIfNeeded(window, presentation);
                renderer.render(presentation.scene(), presentation.camera());
                window.swapBuffers();
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
