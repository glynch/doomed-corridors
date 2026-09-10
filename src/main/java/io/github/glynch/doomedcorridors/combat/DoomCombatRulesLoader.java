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
    private static final int SCHEMA_VERSION = 5;

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    /** Loads one combat document against the actor catalog used by the same project. */
    public DoomCombatRulesLoadResult load(Path source, DoomActorCatalog actorCatalog) {
        Path normalizedSource =
                Objects.requireNonNull(source, "source").toAbsolutePath().normalize();
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
        List<RawCombatant> rawCombatants = Objects.requireNonNull(raw.combatants(), "combatants are required");
        List<RawPickup> rawPickups = Objects.requireNonNull(raw.pickups(), "pickups are required");
        List<DoomCombatRules.WeaponDefinition> weapons = new ArrayList<>(rawWeapons.size());
        for (RawWeapon weapon : rawWeapons) {
            RawWeapon value = Objects.requireNonNull(weapon, "weapon must be an object");
            weapons.add(new DoomCombatRules.WeaponDefinition(
                    value.id(),
                    ammunition(value.ammunition()),
                    value.ammoPerShot(),
                    value.pelletCount(),
                    value.range(),
                    value.autoAimAngleDegrees(),
                    value.autoAimMaximumSlope(),
                    value.damageMinimum(),
                    value.damageMaximum(),
                    value.damageStep()));
        }
        List<DoomCombatRules.CombatantDefinition> combatants = new ArrayList<>(rawCombatants.size());
        for (RawCombatant combatant : rawCombatants) {
            RawCombatant value = Objects.requireNonNull(combatant, "combatant must be an object");
            RawBehavior behavior = Objects.requireNonNull(value.behavior(), "combatant behavior is required");
            RawDamage damage = Objects.requireNonNull(behavior.damage(), "combatant behavior damage is required");
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
                            new DoomCombatRules.DamageDefinition(damage.minimum(), damage.maximum(), damage.step()))));
        }
        List<DoomCombatRules.PickupDefinition> pickups = new ArrayList<>(rawPickups.size());
        for (RawPickup pickup : rawPickups) {
            RawPickup value = Objects.requireNonNull(pickup, "pickup must be an object");
            pickups.add(new DoomCombatRules.PickupDefinition(
                    value.actor(),
                    pickupResource(value.resource()),
                    value.amount(),
                    value.limit(),
                    value.radius(),
                    Optional.ofNullable(value.grantedWeapon()),
                    value.armorProtectionPercent()));
        }
        return new DoomCombatRules(
                new DoomCombatRules.PlayerDefinition(
                        player.startingHealth(),
                        player.maximumHealth(),
                        player.startingArmor(),
                        player.maximumArmor(),
                        player.startingBullets(),
                        player.maximumBullets(),
                        player.startingShells(),
                        player.maximumShells(),
                        player.startingWeapon()),
                weapons,
                combatants,
                pickups);
    }

    /** Parses one lower-case provider resource name into the internal closed set. */
    private static DoomCombatRules.PickupResource pickupResource(String resource) {
        return switch (Objects.requireNonNull(resource, "pickup resource is required")) {
            case "health" -> DoomCombatRules.PickupResource.HEALTH;
            case "armor" -> DoomCombatRules.PickupResource.ARMOR;
            case "bullets" -> DoomCombatRules.PickupResource.BULLETS;
            case "shells" -> DoomCombatRules.PickupResource.SHELLS;
            default -> throw new IllegalArgumentException("Unsupported pickup resource: " + resource);
        };
    }

    /** Parses one lower-case ammunition-pool name into the internal closed set. */
    private static DoomCombatRules.Ammunition ammunition(String ammunition) {
        return switch (Objects.requireNonNull(ammunition, "weapon ammunition is required")) {
            case "bullets" -> DoomCombatRules.Ammunition.BULLETS;
            case "shells" -> DoomCombatRules.Ammunition.SHELLS;
            default -> throw new IllegalArgumentException("Unsupported weapon ammunition: " + ammunition);
        };
    }

    /** Requires combatant and pickup rules to name compatible companion actor definitions. */
    private static void validateActorReferences(DoomCombatRules rules, DoomActorCatalog actors) {
        for (DoomActorDefinition actor : actors.definitions()) {
            validateCombatantReference(rules, actor);
            validatePickupReference(rules, actor);
        }
        validateDefinedActors(rules.combatantActorIds(), actors, "Combatant");
        validateDefinedActors(rules.pickupActorIds(), actors, "Pickup");
    }

    /** Requires one configured combatant to be an equivalently shaped enemy actor. */
    private static void validateCombatantReference(DoomCombatRules rules, DoomActorDefinition actor) {
        DoomCombatRules.CombatantDefinition combatant = rules.combatant(actor.id());
        if (combatant == null) {
            return;
        }
        if (actor.category() != DoomActorCategory.ENEMY) {
            throw new IllegalArgumentException("Combatant actor is not an enemy: " + actor.id());
        }
        var bounds = actor.collisionBounds()
                .orElseThrow(() -> new IllegalArgumentException("Combatant actor is not solid: " + actor.id()));
        if (bounds.radius() != combatant.radius() || bounds.height() != combatant.height()) {
            throw new IllegalArgumentException("Combatant collision does not match actor catalog: " + actor.id());
        }
    }

    /** Requires one configured pickup resource to agree with its actor's broad category. */
    private static void validatePickupReference(DoomCombatRules rules, DoomActorDefinition actor) {
        DoomCombatRules.PickupDefinition pickup = rules.pickup(actor.id());
        if (pickup != null && !isCompatible(actor.category(), pickup)) {
            throw new IllegalArgumentException("Pickup actor category does not match its resource: " + actor.id());
        }
    }

    /** Requires every rule-owned actor identity to exist in the provider actor catalog. */
    private static void validateDefinedActors(Iterable<String> actorIds, DoomActorCatalog actors, String ruleKind) {
        for (String actorId : actorIds) {
            if (actors.definition(actorId).isEmpty()) {
                throw new IllegalArgumentException(ruleKind + " actor is not defined: " + actorId);
            }
        }
    }

    /** Reports whether an actor's broad catalog category matches the configured resource. */
    private static boolean isCompatible(DoomActorCategory category, DoomCombatRules.PickupDefinition pickup) {
        if (pickup.grantedWeapon().isPresent()) {
            return category == DoomActorCategory.WEAPON;
        }
        return switch (pickup.resource()) {
            case HEALTH -> category == DoomActorCategory.HEALTH;
            case ARMOR -> category == DoomActorCategory.ARMOR;
            case BULLETS, SHELLS -> category == DoomActorCategory.AMMUNITION;
        };
    }

    /** Returns one failed load result with a stable diagnostic identity. */
    private static DoomCombatRulesLoadResult error(Path source, String code, String location, String message) {
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
            List<RawCombatant> combatants,
            List<RawPickup> pickups) {}

    /** Direct JSON player binding retained only for conversion and validation. */
    private record RawPlayer(
            int startingHealth,
            int maximumHealth,
            int startingArmor,
            int maximumArmor,
            int startingBullets,
            int maximumBullets,
            int startingShells,
            int maximumShells,
            String startingWeapon) {}

    /** Direct JSON weapon binding retained only for conversion and validation. */
    private record RawWeapon(
            String id,
            String ammunition,
            int ammoPerShot,
            int pelletCount,
            int range,
            float autoAimAngleDegrees,
            float autoAimMaximumSlope,
            int damageMinimum,
            int damageMaximum,
            int damageStep) {}

    /** Direct JSON combatant binding retained only for conversion and validation. */
    private record RawCombatant(String actor, int health, int radius, int height, RawBehavior behavior) {}

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

    /** Direct JSON pickup binding retained only for conversion and validation. */
    private record RawPickup(
            String actor,
            String resource,
            int amount,
            int limit,
            int radius,
            String grantedWeapon,
            int armorProtectionPercent) {}
}
