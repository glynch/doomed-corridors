/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.doomedcorridors.actor.DoomActorCatalog;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalogLoader;
import io.github.glynch.doomedcorridors.actor.DoomActorResolution;
import io.github.glynch.doomedcorridors.actor.DoomActorSprites;
import io.github.glynch.doomedcorridors.actor.DoomSkillLevel;
import io.github.glynch.doomedcorridors.map.DoomMap;
import io.github.glynch.doomedcorridors.wad.DoomMapDecoder;
import io.github.glynch.doomedcorridors.wad.DoomSpriteImportResult;
import io.github.glynch.doomedcorridors.wad.DoomSpriteImporter;
import io.github.glynch.doomedcorridors.wad.WadArchive;
import io.github.glynch.doomedcorridors.wad.WadLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Verifies actor definitions against the pinned real MAP01 thing inventory. */
final class FreedoomActorResolverTest {
    /** Resolves every normal single-player visible placement without unsupported types. */
    @Test
    void resolvesMapOneActors() {
        Path source = Path.of("assets/freedoom2.wad");
        Assumptions.assumeTrue(Files.isRegularFile(source), "pinned Freedoom WAD is not installed");
        WadArchive archive = new WadLoader().load(source).archive().orElseThrow();
        DoomMap map = new DoomMapDecoder().decode(archive, "MAP01").map().orElseThrow();
        DoomActorCatalog catalog = new DoomActorCatalogLoader()
                .load(Path.of("game/actors.json"))
                .catalog()
                .orElseThrow();

        DoomActorResolution result =
                new DoomActorResolver().resolve(source, map, catalog, DoomSkillLevel.NORMAL);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.actors()).hasSize(119);
        assertThat(result.actors())
                .extracting(actor -> actor.definition().id())
                .contains("zombieman", "shotgun-guy", "imp", "shotgun", "stimpack");

        DoomSpriteImportResult spriteResult = new DoomSpriteImporter().importActors(archive, result.actors());
        assertThat(spriteResult.diagnostics()).isEmpty();
        DoomActorSprites sprites = spriteResult.sprites().orElseThrow();
        assertThat(sprites.byFrame()).hasSize(21);
        assertThat(sprites.sprite("POSSA")).hasValueSatisfying(sprite -> {
            assertThat(sprite.lumpName()).isEqualTo("POSSA1");
            assertThat(sprite.image().width()).isPositive();
            assertThat(sprite.image().height()).isPositive();
        });
    }
}
