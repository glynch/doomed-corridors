/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Verifies runtime and branding content in the generic application-directory export. */
final class ExportedApplicationDirectoryIT {
    private static final String APPLICATION_DIRECTORY_PROPERTY = "doomedCorridors.applicationDirectory";

    /** Proves that project-owned launch and menu assets survive runtime-only export selection. */
    @Test
    void containsRuntimeAndBrandingContent() throws IOException {
        Path application = requiredPath(APPLICATION_DIRECTORY_PROPERTY);
        List<String> paths = relativePaths(application);
        List<String> entries = jarEntries(applicationJar(application.resolve("lib")));

        assertThat(paths)
                .contains(
                        "bin/doomed-corridors",
                        "project/jscene3d.json",
                        "project/worlds/main-menu.world.json",
                        "project/worlds/map01.world.json",
                        "project/resources/main-menu-background.resource.json",
                        "project/resources/main-menu-title.resource.json",
                        "project/application/branding/images/corridor-background.png",
                        "project/application/branding/images/doomed-corridors-title.png",
                        "project/application/branding/images/powered-by-jscene3d.png")
                .doesNotContain(
                        "project/assets/freedoom2.wad",
                        "project/application/branding/source/doomed-corridors-title.svg",
                        "project/application/branding/source/powered-by-jscene3d.svg")
                .noneMatch(path -> path.endsWith(".java") || path.contains("jscene3d-project-export"));
        assertThat(entries)
                .contains("io/github/glynch/doomedcorridors/DoomedCorridorsRuntimeExtension.class")
                .noneMatch(path -> path.endsWith("Test.class") || path.endsWith("IT.class"));
    }

    /** Returns every exported path with portable separators. */
    private static List<String> relativePaths(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> !path.equals(root))
                    .map(root::relativize)
                    .map(Path::toString)
                    .map(path -> path.replace('\\', '/'))
                    .toList();
        }
    }

    /** Locates the game JAR among exported runtime artifacts. */
    private static Path applicationJar(Path libraryDirectory) throws IOException {
        try (Stream<Path> libraries = Files.list(libraryDirectory)) {
            return libraries
                    .filter(path -> path.getFileName().toString().startsWith("doomed-corridors-"))
                    .filter(path -> path.getFileName().toString().endsWith(".jar"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("exported Doomed Corridors JAR is absent"));
        }
    }

    /** Reads one application artifact index without loading its classes. */
    private static List<String> jarEntries(Path applicationJar) throws IOException {
        try (JarFile jar = new JarFile(applicationJar.toFile())) {
            return jar.stream().map(JarEntry::getName).toList();
        }
    }

    /** Resolves one Maven-supplied required path. */
    private static Path requiredPath(String property) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("required system property is absent: " + property);
        }
        return Path.of(value).toAbsolutePath().normalize();
    }
}
