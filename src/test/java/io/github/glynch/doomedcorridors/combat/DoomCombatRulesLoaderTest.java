/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.doomedcorridors.actor.DoomActorCatalog;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalogLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Specifies loading and cross-catalog validation of provider combat rules. */
final class DoomCombatRulesLoaderTest {
    @TempDir
    Path temporaryDirectory;

    /** Loads the checked-in pistol, player, and zombieman rules. */
    @Test
    void loadsProjectCombatRules() {
        DoomCombatRulesLoadResult result =
                new DoomCombatRulesLoader().load(Path.of("game/combat.json"), actorCatalog());

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.rules()).hasValueSatisfying(rules -> {
            assertThat(rules.startingHealth()).isEqualTo(100);
            assertThat(rules.startingBullets()).isEqualTo(50);
            assertThat(rules.primaryWeaponId()).isEqualTo("pistol");
            assertThat(rules.combatantDefinitionCount()).isEqualTo(1);
        });
    }

    /** Rejects combat rules that refer to an unknown actor definition. */
    @Test
    void rejectsUnknownCombatantActor() throws IOException {
        Path source = temporaryDirectory.resolve("combat.json");
        Files.writeString(
                source,
                """
                {
                  "schemaVersion": 1,
                  "player": {
                    "startingHealth": 100,
                    "startingBullets": 50,
                    "startingWeapon": "pistol"
                  },
                  "weapons": [{
                    "id": "pistol",
                    "ammoPerShot": 1,
                    "range": 2048,
                    "damageMinimum": 5,
                    "damageMaximum": 15,
                    "damageStep": 5
                  }],
                  "combatants": [{
                    "actor": "unknown-enemy",
                    "health": 20,
                    "radius": 20,
                    "height": 56
                  }]
                }
                """);

        DoomCombatRulesLoadResult result =
                new DoomCombatRulesLoader().load(source, actorCatalog());

        assertThat(result.rules()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.combat.rules-invalid");
            assertThat(diagnostic.message()).contains("unknown-enemy");
        });
    }

    /** Loads the provider actor catalog used by combat cross-reference checks. */
    private static DoomActorCatalog actorCatalog() {
        return new DoomActorCatalogLoader()
                .load(Path.of("game/actors.json"))
                .catalog()
                .orElseThrow();
    }
}
