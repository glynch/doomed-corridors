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
    void decodesStartupMap() {
        Assumptions.assumeTrue(
                Files.isRegularFile(Path.of("assets/freedoom2.wad")),
                "pinned Freedoom WAD is not installed");
        PrintStream originalOutput = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
            System.setOut(output);

            DoomedCorridors.main(new String[] {"."});
        } finally {
            System.setOut(originalOutput);
        }

        assertThat(captured.toString(StandardCharsets.UTF_8))
                .contains("Decoded MAP01: 200 things, 1,274 linedefs, 2,041 sidedefs, "
                        + "1,189 vertices, and 211 sectors");
    }
}
