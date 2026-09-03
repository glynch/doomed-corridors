package io.github.glynch.doomedcorridors.material;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.doomedcorridors.wad.WadLump;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Specifies the manually inspectable material contact-sheet output. */
final class DoomMaterialContactSheetTest {
    @TempDir
    Path temporaryDirectory;

    /** Writes a readable PNG containing every imported material image. */
    @Test
    void writesMaterialImagesToPng() throws IOException {
        DoomMaterial wall = material("WALL", DoomMaterial.Kind.WALL_TEXTURE, (byte) 0xff, (byte) 0, (byte) 0);
        DoomMaterial flat = material("FLAT", DoomMaterial.Kind.FLAT, (byte) 0, (byte) 0xff, (byte) 0);
        DoomMapMaterials materials = new DoomMapMaterials(Map.of("WALL", wall), Map.of("FLAT", flat));
        Path output = temporaryDirectory.resolve("nested/materials.png");

        new DoomMaterialContactSheet().write(materials, output);

        BufferedImage image = ImageIO.read(output.toFile());
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isPositive();
        assertThat(image.getHeight()).isPositive();
        assertThat(containsRgb(image, 0xff0000)).isTrue();
        assertThat(containsRgb(image, 0x00ff00)).isTrue();
    }

    private static DoomMaterial material(
            String name, DoomMaterial.Kind kind, byte red, byte green, byte blue) {
        RgbaImage image = new RgbaImage(1, 1, new byte[] {red, green, blue, (byte) 0xff});
        return new DoomMaterial(name, kind, image, List.of(new WadLump(0, name, 0, 1)));
    }

    private static boolean containsRgb(BufferedImage image, int rgb) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xffffff) == rgb) {
                    return true;
                }
            }
        }
        return false;
    }
}
