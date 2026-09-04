package io.github.glynch.doomedcorridors.wad;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Exercises map decoding against the independently pinned Freedoom release. */
final class FreedoomMapDecoderTest {
    private static final String FREEDOOM_SHA256 =
            "a8772e088847032510d97ba2312406a6998f21cbab44d4ff10696faa9c0ecd4b";

    /** Decodes the actual MAP01 directory sequence and representative source values. */
    @Test
    void decodesPinnedMap01() {
        Path source = Path.of("assets/freedoom2.wad");
        Assumptions.assumeTrue(Files.isRegularFile(source), "pinned Freedoom WAD is not installed");
        WadArchive archive = new WadLoader()
                .load(source, Optional.of(FREEDOOM_SHA256))
                .archive()
                .orElseThrow();

        DoomMapDecodeResult result = new DoomMapDecoder().decode(archive, "MAP01");

        assertThat(result.diagnostics()).isEmpty();
        DoomMap map = result.map().orElseThrow();
        assertThat(map.things()).hasSize(200).first().isEqualTo(new DoomMap.Thing(-192, -192, 0, 1, 7));
        assertThat(map.linedefs()).hasSize(1_274);
        assertThat(map.sidedefs()).hasSize(2_041);
        assertThat(map.vertices()).hasSize(1_189).startsWith(
                new DoomMap.Vertex(-224, -288),
                new DoomMap.Vertex(-224, -224),
                new DoomMap.Vertex(400, -464),
                new DoomMap.Vertex(400, -728));
        assertThat(map.segs()).hasSize(2_233);
        assertThat(map.subsectors()).hasSize(698);
        assertThat(map.nodes()).hasSize(697);
        assertThat(map.sectors()).hasSize(211);
        assertThat(map.rejectBytes()).hasSize(5_566);
        assertThat(map.blockmap().originX()).isEqualTo(-256);
        assertThat(map.blockmap().originY()).isEqualTo(-1_808);
        assertThat(map.blockmap().columns()).isEqualTo(20);
        assertThat(map.blockmap().rows()).isEqualTo(27);
        assertThat(map.blockmap().cells()).hasSize(540);
    }
}
