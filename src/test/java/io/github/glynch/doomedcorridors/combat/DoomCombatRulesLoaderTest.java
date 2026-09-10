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
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Specifies loading and cross-catalog validation of provider combat rules. */
final class DoomCombatRulesLoaderTest {
    @TempDir
    Path temporaryDirectory;

    /** Loads the checked-in player resource and weapon rules. */
    @Test
    void loadsProjectCombatRules() {
        DoomCombatRulesLoadResult result =
                new DoomCombatRulesLoader().load(Path.of("game/combat.json"), actorCatalog());

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.rules()).hasValueSatisfying(rules -> {
            assertThat(rules.startingHealth()).isEqualTo(100);
            assertThat(rules.maximumHealth()).isEqualTo(200);
            assertThat(rules.startingArmor()).isZero();
            assertThat(rules.maximumArmor()).isEqualTo(200);
            assertThat(rules.startingBullets()).isEqualTo(50);
            assertThat(rules.maximumBullets()).isEqualTo(200);
            assertThat(rules.startingShells()).isZero();
            assertThat(rules.maximumShells()).isEqualTo(50);
            assertThat(rules.primaryWeaponId()).isEqualTo("pistol");
            assertThat(rules.hasWeapon("pistol")).isTrue();
            assertThat(rules.hasWeapon("shotgun")).isTrue();
            assertThat(rules.weaponAmmunition("shotgun")).isEqualTo(DoomCombatRules.Ammunition.SHELLS);
            assertThat(rules.weaponPelletCount("shotgun")).isEqualTo(7);
            assertThat(rules.weaponAmmoPerShot("pistol")).isEqualTo(1);
            assertThat(rules.weaponRange("pistol")).isEqualTo(2048);
            assertThat(rules.weaponAutoAimAngleDegrees("pistol")).isEqualTo(5.625F);
            assertThat(rules.weaponAutoAimMaximumSlope("pistol")).isEqualTo(0.625F);
            assertThat(rules.rollWeaponDamage("pistol", new Random(0L))).isIn(5, 10, 15);
        });
    }

    /** Loads the checked-in combatant and pickup rules. */
    @Test
    void loadsProjectCombatantAndPickupRules() {
        DoomCombatRulesLoadResult result =
                new DoomCombatRulesLoader().load(Path.of("game/combat.json"), actorCatalog());

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.rules()).hasValueSatisfying(rules -> {
            assertThat(rules.combatantDefinitionCount()).isEqualTo(1);
            assertThat(rules.combatantStartingHealth("zombieman")).isEqualTo(20);
            assertThat(rules.rollEnemyDamage("zombieman", new Random(0L))).isIn(3, 6, 9, 12, 15);
            assertThat(rules.pickupDefinitionCount()).isEqualTo(11);
            assertThat(rules.findCombatantBounds("zombieman")).contains(new DoomCombatRules.CombatantBounds(20, 56));
            assertThat(rules.findCombatantBounds("stimpack")).isEmpty();
        });
    }

    /** Rejects combat rules that refer to an unknown actor definition. */
    @Test
    void rejectsUnknownCombatantActor() throws IOException {
        Path source = temporaryDirectory.resolve("combat.json");
        Files.writeString(source, """
                {
                  "schemaVersion": 5,
                  "player": {
                    "startingHealth": 100,
                    "maximumHealth": 200,
                    "startingArmor": 0,
                    "maximumArmor": 200,
                    "startingBullets": 50,
                    "maximumBullets": 200,
                    "startingShells": 0,
                    "maximumShells": 50,
                    "startingWeapon": "pistol"
                  },
                  "weapons": [{
                    "id": "pistol",
                    "ammunition": "bullets",
                    "ammoPerShot": 1,
                    "pelletCount": 1,
                    "range": 2048,
                    "autoAimAngleDegrees": 5.625,
                    "autoAimMaximumSlope": 0.625,
                    "damageMinimum": 5,
                    "damageMaximum": 15,
                    "damageStep": 5
                  }],
                  "combatants": [{
                    "actor": "unknown-enemy",
                    "health": 20,
                    "radius": 20,
                    "height": 56,
                    "behavior": {
                      "sightRange": 2048,
                      "attackRange": 2048,
                      "preferredRange": 96,
                      "moveSpeed": 96,
                      "reactionMilliseconds": 300,
                      "attackIntervalMilliseconds": 1000,
                      "damage": { "minimum": 3, "maximum": 15, "step": 3 }
                    }
                  }],
                  "pickups": []
                }
                """);

        DoomCombatRulesLoadResult result = new DoomCombatRulesLoader().load(source, actorCatalog());

        assertThat(result.rules()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.combat.rules-invalid");
            assertThat(diagnostic.message()).contains("unknown-enemy");
        });
    }

    /** Rejects a health effect assigned to an ammunition-category actor. */
    @Test
    void rejectsPickupResourceCategoryMismatch() throws IOException {
        Path source = temporaryDirectory.resolve("combat.json");
        String mismatched = Files.readString(Path.of("game/combat.json"))
                .replace("\"actor\": \"stimpack\"", "\"actor\": \"chainsaw\"");
        Files.writeString(source, mismatched);

        DoomCombatRulesLoadResult result = new DoomCombatRulesLoader().load(source, actorCatalog());

        assertThat(result.rules()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.combat.rules-invalid");
            assertThat(diagnostic.message()).contains("chainsaw", "category");
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
