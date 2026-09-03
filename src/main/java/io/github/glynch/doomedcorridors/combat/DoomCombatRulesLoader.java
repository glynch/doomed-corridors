/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalog;
import io.github.glynch.doomedcorridors.actor.DoomActorCategory;
import io.github.glynch.doomedcorridors.actor.DoomActorDefinition;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Loads provider-authored combat rules and validates actor-catalog references. */
public final class DoomCombatRulesLoader {
    private static final int SCHEMA_VERSION = 2;

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    /** Loads one combat document against the actor catalog used by the same project. */
    public DoomCombatRulesLoadResult load(Path source, DoomActorCatalog actorCatalog) {
        Path normalizedSource = Objects.requireNonNull(source, "source")
                .toAbsolutePath()
                .normalize();
        DoomActorCatalog validActors = Objects.requireNonNull(actorCatalog, "actorCatalog");
        try {
            RawCatalog raw = mapper.readValue(normalizedSource.toFile(), RawCatalog.class);
            if (raw.schemaVersion() != SCHEMA_VERSION) {
                return error(
                        normalizedSource,
                        "doom.combat.rules-version",
                        "/schemaVersion",
                        "Unsupported combat rules schemaVersion: " + raw.schemaVersion());
            }
            DoomCombatRules rules = toRules(raw);
            validateActorReferences(rules, validActors);
            return new DoomCombatRulesLoadResult(Optional.of(rules), List.of());
        } catch (IOException | IllegalArgumentException exception) {
            return error(
                    normalizedSource,
                    "doom.combat.rules-invalid",
                    "/",
                    "Cannot load combat rules: " + exception.getMessage());
        }
    }

    /** Converts nullable JSON bindings into validated rules. */
    private static DoomCombatRules toRules(RawCatalog raw) {
        RawPlayer player = Objects.requireNonNull(raw.player(), "player is required");
        List<RawWeapon> rawWeapons = Objects.requireNonNull(raw.weapons(), "weapons are required");
        List<RawCombatant> rawCombatants =
                Objects.requireNonNull(raw.combatants(), "combatants are required");
        List<DoomCombatRules.WeaponDefinition> weapons = new ArrayList<>(rawWeapons.size());
        for (RawWeapon weapon : rawWeapons) {
            RawWeapon value = Objects.requireNonNull(weapon, "weapon must be an object");
            weapons.add(new DoomCombatRules.WeaponDefinition(
                    value.id(),
                    value.ammoPerShot(),
                    value.range(),
                    value.damageMinimum(),
                    value.damageMaximum(),
                    value.damageStep()));
        }
        List<DoomCombatRules.CombatantDefinition> combatants =
                new ArrayList<>(rawCombatants.size());
        for (RawCombatant combatant : rawCombatants) {
            RawCombatant value = Objects.requireNonNull(combatant, "combatant must be an object");
            RawBehavior behavior = Objects.requireNonNull(
                    value.behavior(), "combatant behavior is required");
            RawDamage damage = Objects.requireNonNull(
                    behavior.damage(), "combatant behavior damage is required");
            combatants.add(new DoomCombatRules.CombatantDefinition(
                    value.actor(),
                    value.health(),
                    value.radius(),
                    value.height(),
                    new DoomCombatRules.EnemyBehavior(
                            behavior.sightRange(),
                            behavior.attackRange(),
                            behavior.preferredRange(),
                            behavior.moveSpeed(),
                            behavior.reactionMilliseconds(),
                            behavior.attackIntervalMilliseconds(),
                            new DoomCombatRules.DamageDefinition(
                                    damage.minimum(), damage.maximum(), damage.step()))));
        }
        return new DoomCombatRules(
                player.startingHealth(),
                player.startingBullets(),
                player.startingWeapon(),
                weapons,
                combatants);
    }

    /** Requires every combatant rule to name one enemy in the companion actor catalog. */
    private static void validateActorReferences(DoomCombatRules rules, DoomActorCatalog actors) {
        for (DoomActorDefinition actor : actors.definitions()) {
            DoomCombatRules.CombatantDefinition combatant = rules.combatant(actor.id());
            if (combatant != null && actor.category() != DoomActorCategory.ENEMY) {
                throw new IllegalArgumentException("Combatant actor is not an enemy: " + actor.id());
            }
        }
        for (String actorId : rules.combatantActorIds()) {
            boolean defined = actors.definitions().stream().anyMatch(actor -> actor.id().equals(actorId));
            if (!defined) {
                throw new IllegalArgumentException("Combatant actor is not defined: " + actorId);
            }
        }
    }

    /** Returns one failed load result with a stable diagnostic identity. */
    private static DoomCombatRulesLoadResult error(
            Path source, String code, String location, String message) {
        return new DoomCombatRulesLoadResult(
                Optional.empty(),
                List.of(new DoomCombatDiagnostic(
                        DoomCombatDiagnostic.Severity.ERROR, code, source, location, message)));
    }

    /** Direct JSON root binding retained only for conversion and validation. */
    private record RawCatalog(
            @JsonProperty("$schema") String schema,
            int schemaVersion,
            RawPlayer player,
            List<RawWeapon> weapons,
            List<RawCombatant> combatants) {}

    /** Direct JSON player binding retained only for conversion and validation. */
    private record RawPlayer(int startingHealth, int startingBullets, String startingWeapon) {}

    /** Direct JSON weapon binding retained only for conversion and validation. */
    private record RawWeapon(
            String id,
            int ammoPerShot,
            int range,
            int damageMinimum,
            int damageMaximum,
            int damageStep) {}

    /** Direct JSON combatant binding retained only for conversion and validation. */
    private record RawCombatant(
            String actor, int health, int radius, int height, RawBehavior behavior) {}

    /** Direct JSON enemy-behavior binding retained only for conversion and validation. */
    private record RawBehavior(
            int sightRange,
            int attackRange,
            int preferredRange,
            int moveSpeed,
            int reactionMilliseconds,
            int attackIntervalMilliseconds,
            RawDamage damage) {}

    /** Direct JSON enemy-damage binding retained only for conversion and validation. */
    private record RawDamage(int minimum, int maximum, int step) {}
}
