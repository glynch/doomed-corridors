/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.doomedcorridors.actor.DoomActorSprites;
import io.github.glynch.doomedcorridors.presentation.DoomMapPresentation;
import io.github.glynch.jscene3d.doom.geometry.DoomGeometryBuildResult;
import io.github.glynch.jscene3d.doom.geometry.DoomPlayerStart;
import io.github.glynch.jscene3d.doom.geometry.DoomStaticGeometry;
import io.github.glynch.jscene3d.doom.geometry.DoomStaticGeometryBuilder;
import io.github.glynch.jscene3d.doom.geometry.DoomSurface;
import io.github.glynch.jscene3d.doom.map.DoomMap;
import io.github.glynch.jscene3d.doom.map.DoomMapDecoder;
import io.github.glynch.jscene3d.doom.material.DoomMapMaterials;
import io.github.glynch.jscene3d.doom.material.DoomMaterialImporter;
import io.github.glynch.jscene3d.wad.WadArchive;
import io.github.glynch.jscene3d.wad.WadLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Exercises static geometry construction against the independently pinned Freedoom release. */
final class FreedoomStaticGeometryBuilderTest {
    private static final String FREEDOOM_SHA256 = "a8772e088847032510d97ba2312406a6998f21cbab44d4ff10696faa9c0ecd4b";

    /** Builds every non-sky MAP01 floor and resolves its WAD-defined player-one start. */
    @Test
    void buildsPinnedMap01() {
        Path source = Path.of("assets/freedoom2.wad");
        Assumptions.assumeTrue(Files.isRegularFile(source), "pinned Freedoom WAD is not installed");
        WadArchive archive = WadLoader.load(source, FREEDOOM_SHA256).archive().orElseThrow();
        DoomMap map = new DoomMapDecoder().decode(archive, "MAP01").map().orElseThrow();
        DoomMapMaterials materials =
                new DoomMaterialImporter().importMap(archive, map).materials().orElseThrow();

        DoomGeometryBuildResult result = new DoomStaticGeometryBuilder().build(map, materials);

        assertThat(result.diagnostics()).isEmpty();
        DoomStaticGeometry geometry = result.geometry().orElseThrow();
        assertThat(geometry.surfaces())
                .filteredOn(surface -> surface.type() == DoomSurface.Type.FLOOR)
                .hasSize(693);
        assertThat(geometry.surfaces())
                .filteredOn(surface -> surface.type() == DoomSurface.Type.MIDDLE_WALL)
                .isNotEmpty();
        assertThat(geometry.playerStart()).isEqualTo(new DoomPlayerStart(-6.0F, 41.0F / 32.0F, 6.0F, 0.0F));
        DoomGameSession session = DoomGameSession.create(map, geometry.playerStart());
        DoomPlayerState moved =
                session.advance(new DoomPlayerCommand(1.0F, 0.0F, 0.0F, 0.0F, 0.0F), Duration.ofMillis(100));
        assertThat(moved.x()).isGreaterThan(geometry.playerStart().x());
        try (DoomMapPresentation presentation = DoomMapPresentation.create(
                geometry, materials, List.of(), new DoomActorSprites(java.util.Map.of()), 16.0F / 9.0F)) {
            assertThat(presentation.scene().children()).hasSameSizeAs(geometry.surfaces());
        }
    }
}
