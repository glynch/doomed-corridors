package io.github.glynch.doomedcorridors.wad;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Exercises material import against independently inspected Freedoom MAP01 values. */
final class FreedoomMaterialImporterTest {
    private static final String FREEDOOM_SHA256 =
            "a8772e088847032510d97ba2312406a6998f21cbab44d4ff10696faa9c0ecd4b";

    /** Imports exactly the wall textures and flats referenced by the pinned MAP01. */
    @Test
    void importsPinnedMap01Materials() {
        Path source = Path.of("assets/freedoom2.wad");
        Assumptions.assumeTrue(Files.isRegularFile(source), "pinned Freedoom WAD is not installed");
        WadArchive archive = new WadLoader()
                .load(source, Optional.of(FREEDOOM_SHA256))
                .archive()
                .orElseThrow();
        DoomMap map = new DoomMapDecoder().decode(archive, "MAP01").map().orElseThrow();

        DoomMaterialImportResult result = new DoomMaterialImporter().importMap(archive, map);

        assertThat(result.diagnostics()).isEmpty();
        DoomMapMaterials materials = result.materials().orElseThrow();
        assertThat(materials.wallTextures())
                .hasSize(51)
                .containsKeys("A-BRICK3", "DOOR3", "PLANET1", "SKY1", "WFALL1");
        assertThat(materials.flats())
                .hasSize(28)
                .containsKeys("AQF001", "CEIL5_1", "FWATER1", "RROCK19", "TLITE6_5");
        assertThat(materials.wallTextures().get("A-BRICK3").image().width()).isEqualTo(128);
        assertThat(materials.wallTextures().get("A-BRICK3").image().height()).isEqualTo(128);
        assertThat(materials.wallTextures().get("DOOR3").image().width()).isEqualTo(64);
        assertThat(materials.wallTextures().get("DOOR3").image().height()).isEqualTo(72);
        assertThat(materials.wallTextures().get("PLANET1").image().width()).isEqualTo(256);
        assertThat(materials.wallTextures().get("PLANET1").image().height()).isEqualTo(128);
        assertThat(materials.flats().get("AQF001").image().rgba(0, 0)).isEqualTo(0x5f4b37ff);
        assertThat(materials.flats().get("AQF001").image().rgba(10, 10)).isEqualTo(0x473323ff);
        assertThat(materials.flats().get("AQF001").image().rgba(63, 63)).isEqualTo(0x000000ff);
    }
}
