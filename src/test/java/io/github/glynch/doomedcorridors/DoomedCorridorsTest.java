/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Exercises the headless application wiring without starting native subsystems. */
final class DoomedCorridorsTest {
    /** Loads and decodes the manifest-selected map from the pinned source asset. */
    @Test
    void decodesStartupMapAndWritesMaterialContactSheet() throws java.io.IOException {
        Assumptions.assumeTrue(
                Files.isRegularFile(Path.of("assets/freedoom2.wad")),
                "pinned Freedoom WAD is not installed");
        Path contactSheet = Path.of("target/smoke/map01-materials.png");
        Path spriteSheet = Path.of("target/smoke/map01-sprites.png");
        Files.deleteIfExists(contactSheet);
        Files.deleteIfExists(spriteSheet);
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setOut(output);

            DoomedCorridorsInspector.main(new String[] {"."});
        } finally {
            System.setOut(originalOutput);
        }

        assertThat(captured.toString(StandardCharsets.UTF_8))
                .contains("Decoded MAP01: 200 things, 1,274 linedefs, 2,041 sidedefs, "
                        + "1,189 vertices, and 211 sectors")
                .contains("Imported MAP01 materials: 51 wall textures and 28 flats")
                .contains("Built MAP01 static geometry:")
                .contains("Loaded 37 provider actor definitions")
                .contains("Loaded combat rules for pistol and 1 combatant definition")
                .contains("Resolved MAP01 actors: 119 visible placements")
                .contains("Imported 21 unique actor sprite frames")
                .contains("Initialized headless combat: pistol, 100 health, 50 bullets, and 11 combatants")
                .contains("Wrote material contact sheet to")
                .contains("Wrote sprite contact sheet to");
        assertThat(contactSheet).isNotEmptyFile();
        assertThat(spriteSheet).isNotEmptyFile();
    }
}
