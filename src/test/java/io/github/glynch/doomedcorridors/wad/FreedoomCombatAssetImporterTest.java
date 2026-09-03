/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.wad;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.doomedcorridors.actor.DoomActorCatalog;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalogLoader;
import io.github.glynch.doomedcorridors.combat.DoomCombatRules;
import io.github.glynch.doomedcorridors.combat.DoomCombatRulesLoader;
import io.github.glynch.doomedcorridors.presentation.DoomCombatAssets;
import io.github.glynch.doomedcorridors.presentation.DoomCombatPresentationLoader;
import io.github.glynch.doomedcorridors.presentation.DoomCombatPresentationRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Exercises combat image and sound import against the pinned Freedoom archive. */
final class FreedoomCombatAssetImporterTest {
    private static final String FREEDOOM_SHA256 =
            "a8772e088847032510d97ba2312406a6998f21cbab44d4ff10696faa9c0ecd4b";

    /** Imports all exact provider-declared patches and DMX effects. */
    @Test
    void importsPinnedCombatAssets() {
        Path source = Path.of("assets/freedoom2.wad");
        Assumptions.assumeTrue(Files.isRegularFile(source), "pinned Freedoom WAD is not installed");
        WadArchive archive = new WadLoader()
                .load(source, Optional.of(FREEDOOM_SHA256))
                .archive()
                .orElseThrow();

        DoomCombatAssetImportResult result = new DoomCombatAssetImporter()
                .importAssets(archive, presentationRules());

        assertThat(result.diagnostics()).isEmpty();
        DoomCombatAssets assets = result.assets().orElseThrow();
        assertThat(assets.images()).hasSize(22).containsKeys("PISGA0", "POSSG1", "POSSL0", "STTNUM0");
        assertThat(assets.sounds()).hasSize(5).containsKeys("DSPISTOL", "DSPOPAIN", "DSPODTH3");
        assertThat(assets.image("PISGA0").image().width()).isPositive();
        assertThat(assets.image("POSSL0").image().height()).isPositive();
        assertThat(assets.sound("DSPISTOL").channels()).isOne();
        assertThat(assets.sound("DSPISTOL").sampleRate()).isEqualTo(22_050);
        assertThat(assets.sound("DSPISTOL").frameCount()).isPositive();
    }

    /** Loads checked-in presentation rules and their companion combat catalog. */
    private static DoomCombatPresentationRules presentationRules() {
        DoomActorCatalog actors = new DoomActorCatalogLoader()
                .load(Path.of("game/actors.json"))
                .catalog()
                .orElseThrow();
        DoomCombatRules combat = new DoomCombatRulesLoader()
                .load(Path.of("game/combat.json"), actors)
                .rules()
                .orElseThrow();
        return new DoomCombatPresentationLoader()
                .load(Path.of("game/combat-presentation.json"), combat)
                .rules()
                .orElseThrow();
    }
}
