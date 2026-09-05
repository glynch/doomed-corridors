/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.runtime;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import io.github.glynch.jscene3d.physics.PhysicsWorld;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntime;
import io.github.glynch.jscene3d.project.runtime.scene3d.JScene3dRuntimeExtension;
import java.nio.file.Path;
import java.util.List;

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
        JScene3dRuntimeExtension scene3d = JScene3dRuntimeExtension.headless();
        PhysicsWorld physicsWorld = scene3d.physicsWorld();
        try (ProjectRuntime runtime =
                DoomedCorridorsRuntimeLoader.load(projectDirectory, List.of(scene3d))) {
            runtime.start();
            if (!(runtime.root().children().getFirst().object() instanceof DoomLevel3d level)) {
                throw new IllegalStateException("entry scene does not contain a Doom level");
            }
            DoomMap map = level.map();
            if (!(runtime.root()
                    .children()
                    .getFirst()
                    .children()
                    .getFirst()
                    .controller()
                    .orElseThrow() instanceof DoomPlayerController player)) {
                throw new IllegalStateException("Doom level does not contain its player controller");
            }
            System.out.printf(
                    "Resolved imported %s through the project runtime: %,d things, %,d linedefs, "
                            + "%,d sectors, %,d graphical surfaces, static collision "
                            + "(bodies: %,d; colliders: %,d), and %,d supported doors%n",
                    map.name(),
                    map.things().size(),
                    map.linedefs().size(),
                    map.sectors().size(),
                    level.surfaceCount(),
                    physicsWorld.collisionObjectCount(),
                    physicsWorld.colliderCount(),
                    player.doors().size());
        }
        if (physicsWorld.collisionObjectCount() != 0 || physicsWorld.colliderCount() != 0) {
            throw new IllegalStateException("project runtime did not release imported static collision");
        }
    }
}
