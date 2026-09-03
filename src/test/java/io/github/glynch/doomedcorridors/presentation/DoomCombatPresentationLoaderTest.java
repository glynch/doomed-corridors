/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.doomedcorridors.actor.DoomActorCatalog;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalogLoader;
import io.github.glynch.doomedcorridors.combat.DoomCombatRules;
import io.github.glynch.doomedcorridors.combat.DoomCombatRulesLoader;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Specifies loading the provider-authored combat presentation document. */
final class DoomCombatPresentationLoaderTest {
    /** Loads WAD patch, sound, timing, and HUD bindings from the checked-in project data. */
    @Test
    void loadsProjectCombatPresentation() {
        DoomCombatPresentationLoadResult result = new DoomCombatPresentationLoader()
                .load(Path.of("game/combat-presentation.json"), combatRules());

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        DoomCombatPresentationRules rules = result.rules().orElseThrow();
        assertThat(rules.weapon().id()).isEqualTo("pistol");
        assertThat(rules.weapon().readyFrame()).isEqualTo("PISGA0");
        assertThat(rules.weapon().fireFrames())
                .containsExactly("PISGB0", "PISGC0", "PISGD0", "PISGE0");
        assertThat(rules.combatant("zombieman").deathFrames())
                .containsExactly("POSSH0", "POSSI0", "POSSJ0", "POSSK0", "POSSL0");
        assertThat(rules.hud().digits()).hasSize(10);
        assertThat(rules.imageLumps()).hasSize(22);
        assertThat(rules.soundLumps()).hasSize(5);
    }

    /** Loads the companion combat rules used for cross-document validation. */
    private static DoomCombatRules combatRules() {
        DoomActorCatalog actors = new DoomActorCatalogLoader()
                .load(Path.of("game/actors.json"))
                .catalog()
                .orElseThrow();
        return new DoomCombatRulesLoader()
                .load(Path.of("game/combat.json"), actors)
                .rules()
                .orElseThrow();
    }
}
