/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.runtime;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntime;
import java.nio.file.Path;

/** Headless executable proving the project-selected imported map reaches application runtime code. */
public final class DoomedCorridorsRuntimeInspector {
    /** Prevents construction of this application entry point. */
    private DoomedCorridorsRuntimeInspector() {
        throw new AssertionError("DoomedCorridorsRuntimeInspector cannot be instantiated");
    }

    /**
     * Loads, imports, composes, starts, and closes the declarative project runtime.
     *
     * @param arguments optional project directory; defaults to the working directory
     */
    public static void main(String[] arguments) {
        if (arguments.length > 1) {
            throw new IllegalArgumentException("expected at most one project directory");
        }
        Path projectDirectory = Path.of(arguments.length == 0 ? "." : arguments[0]);
        try (ProjectRuntime runtime = DoomedCorridorsRuntimeLoader.load(projectDirectory)) {
            runtime.start();
            if (!(runtime.root().children().getFirst().object() instanceof DoomLevel3d level)) {
                throw new IllegalStateException("entry scene does not contain a Doom level");
            }
            DoomMap map = level.map();
            System.out.printf(
                    "Resolved imported %s through the project runtime: %,d things, %,d linedefs, "
                            + "%,d sectors, and %,d graphical surfaces%n",
                    map.name(),
                    map.things().size(),
                    map.linedefs().size(),
                    map.sectors().size(),
                    level.surfaceCount());
        }
    }
}
