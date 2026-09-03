/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.material.DoomMaterialContactSheet;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/** Headless project, WAD, material, and geometry inspection entry point. */
public final class DoomedCorridorsInspector {
    private DoomedCorridorsInspector() {
        throw new AssertionError("DoomedCorridorsInspector cannot be instantiated");
    }

    /**
     * Loads the project pipeline and writes a deterministic material contact sheet.
     *
     * @param arguments optional project directory; defaults to the working directory
     */
    public static void main(String[] arguments) {
        Path projectDirectory = Path.of(arguments.length == 0 ? "." : arguments[0]);
        DoomStartup startup = DoomStartup.load(projectDirectory);
        Path contactSheet = startup.project()
                .root()
                .resolve("target/smoke/"
                        + startup.map().name().toLowerCase(Locale.ROOT)
                        + "-materials.png");
        try {
            new DoomMaterialContactSheet().write(startup.materials(), contactSheet);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write material contact sheet", exception);
        }
        System.out.println("Wrote material contact sheet to " + contactSheet);
    }
}
