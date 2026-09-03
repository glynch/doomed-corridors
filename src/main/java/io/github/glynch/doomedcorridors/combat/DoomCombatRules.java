/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable provider rules needed to initialize the first headless combat loop. */
public final class DoomCombatRules {
    private final PlayerDefinition player;
    private final WeaponDefinition primaryWeapon;
    private final Map<String, CombatantDefinition> combatants;
    private final Map<String, PickupDefinition> pickups;

    /** Validates player, weapon, combatant, and pickup rules while building lookup indexes. */
    DoomCombatRules(
            PlayerDefinition player,
            List<WeaponDefinition> weapons,
            List<CombatantDefinition> combatants,
            List<PickupDefinition> pickups) {
        this.player = Objects.requireNonNull(player, "player");
        Map<String, WeaponDefinition> weaponsById = indexWeapons(weapons);
        primaryWeapon = weaponsById.get(player.startingWeapon());
        if (primaryWeapon == null) {
            throw new IllegalArgumentException(
                    "startingWeapon does not name a defined weapon: " + player.startingWeapon());
        }
        this.combatants = indexCombatants(combatants);
        this.pickups = indexPickups(pickups, player);
    }

    /** Returns the player's initial health. */
    public int startingHealth() {
        return player.startingHealth();
    }

    /** Returns the absolute player-health ceiling, including over-health pickups. */
    public int maximumHealth() {
        return player.maximumHealth();
    }

    /** Returns the player's initial bullet count. */
    public int startingBullets() {
        return player.startingBullets();
    }

    /** Returns the player's bullet-ammunition capacity. */
    public int maximumBullets() {
        return player.maximumBullets();
    }

    /** Returns the stable identifier of the initially selected weapon. */
    public String primaryWeaponId() {
        return primaryWeapon.id();
    }

    /** Returns the number of actor definitions that participate in combat. */
    public int combatantDefinitionCount() {
        return combatants.size();
    }

    /** Returns the number of configured collectable actor definitions. */
    public int pickupDefinitionCount() {
        return pickups.size();
    }

    /** Returns whether the actor identity participates in configured combat. */
    public boolean hasCombatant(String actorId) {
        return combatants.containsKey(Objects.requireNonNull(actorId, "actorId"));
    }

    /** Returns the initial weapon rules to the combat implementation. */
    WeaponDefinition primaryWeapon() {
        return primaryWeapon;
    }

    /** Returns matching combatant rules or {@code null} for an inert actor definition. */
    CombatantDefinition combatant(String actorId) {
        return combatants.get(actorId);
    }

    /** Returns matching pickup rules or {@code null} for a non-collectable actor definition. */
    PickupDefinition pickup(String actorId) {
        return pickups.get(actorId);
    }

    /** Returns configured actor identifiers for cross-catalog validation. */
    Set<String> combatantActorIds() {
        return combatants.keySet();
    }

    /** Returns configured pickup actor identifiers for cross-catalog validation. */
    Set<String> pickupActorIds() {
        return pickups.keySet();
    }

    /** Indexes validated weapon definitions by stable provider ID. */
    private static Map<String, WeaponDefinition> indexWeapons(List<WeaponDefinition> definitions) {
        Map<String, WeaponDefinition> indexed = new LinkedHashMap<>();
        for (WeaponDefinition definition : List.copyOf(Objects.requireNonNull(definitions, "weapons"))) {
            WeaponDefinition validDefinition = Objects.requireNonNull(definition, "weapon");
            WeaponDefinition previous = indexed.putIfAbsent(validDefinition.id(), validDefinition);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate weapon id: " + validDefinition.id());
            }
        }
        return Map.copyOf(indexed);
    }

    /** Indexes validated combatant definitions by actor ID. */
    private static Map<String, CombatantDefinition> indexCombatants(
            List<CombatantDefinition> definitions) {
        Map<String, CombatantDefinition> indexed = new LinkedHashMap<>();
        for (CombatantDefinition definition :
                List.copyOf(Objects.requireNonNull(definitions, "combatants"))) {
            CombatantDefinition validDefinition = Objects.requireNonNull(definition, "combatant");
            CombatantDefinition previous = indexed.putIfAbsent(validDefinition.actorId(), validDefinition);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate combatant actor id: " + validDefinition.actorId());
            }
        }
        return Map.copyOf(indexed);
    }

    /** Indexes pickup definitions and checks each per-item limit against player capacity. */
    private static Map<String, PickupDefinition> indexPickups(
            List<PickupDefinition> definitions, PlayerDefinition player) {
        Map<String, PickupDefinition> indexed = new LinkedHashMap<>();
        for (PickupDefinition definition :
                List.copyOf(Objects.requireNonNull(definitions, "pickups"))) {
            PickupDefinition validDefinition = Objects.requireNonNull(definition, "pickup");
            int capacity = switch (validDefinition.resource()) {
                case HEALTH -> player.maximumHealth();
                case BULLETS -> player.maximumBullets();
            };
            if (validDefinition.limit() > capacity) {
                throw new IllegalArgumentException(
                        "Pickup limit exceeds player capacity: " + validDefinition.actorId());
            }
            PickupDefinition previous = indexed.putIfAbsent(
                    validDefinition.actorId(), validDefinition);
            if (previous != null) {
                throw new IllegalArgumentException(
                        "Duplicate pickup actor id: " + validDefinition.actorId());
            }
        }
        return Map.copyOf(indexed);
    }

    /** Requires one lower-case, hyphen-separated provider identifier. */
    private static String requireId(String value, String name) {
        String id = Objects.requireNonNull(value, name);
        boolean valid = !id.isEmpty() && isLowercaseLetter(id.charAt(0));
        boolean previousHyphen = false;
        for (int index = 1; valid && index < id.length(); index++) {
            char character = id.charAt(index);
            boolean hyphen = character == '-';
            valid = isLowercaseLetter(character)
                    || isDigit(character)
                    || (hyphen && !previousHyphen && index + 1 < id.length());
            previousHyphen = hyphen;
        }
        if (!valid) {
            throw new IllegalArgumentException(name + " has an invalid value: " + id);
        }
        return id;
    }

    /** Reports whether one character is an ASCII lower-case letter. */
    private static boolean isLowercaseLetter(char character) {
        return character >= 'a' && character <= 'z';
    }

    /** Reports whether one character is an ASCII digit. */
    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    /** Validated starting resources and absolute capacities for the player. */
    record PlayerDefinition(
            int startingHealth,
            int maximumHealth,
            int startingBullets,
            int maximumBullets,
            String startingWeapon) {
        /** Validates resource ranges and the selected weapon identity. */
        PlayerDefinition {
            if (startingHealth <= 0 || maximumHealth < startingHealth) {
                throw new IllegalArgumentException(
                        "health values must satisfy 0 < startingHealth <= maximumHealth");
            }
            if (startingBullets < 0 || maximumBullets < startingBullets) {
                throw new IllegalArgumentException(
                        "bullet values must satisfy 0 <= startingBullets <= maximumBullets");
            }
            requireId(startingWeapon, "startingWeapon");
        }
    }

    /** Validated rules for one hitscan weapon. */
    record WeaponDefinition(
            String id,
            int ammoPerShot,
            int range,
            int damageMinimum,
            int damageMaximum,
            int damageStep) {
        /** Validates the discrete damage sequence and positive weapon dimensions. */
        WeaponDefinition {
            requireId(id, "weapon id");
            if (ammoPerShot <= 0 || range <= 0 || damageMinimum <= 0 || damageStep <= 0) {
                throw new IllegalArgumentException("weapon numeric values must be positive");
            }
            if (damageMaximum < damageMinimum
                    || (damageMaximum - damageMinimum) % damageStep != 0) {
                throw new IllegalArgumentException("weapon damage range must contain complete damage steps");
            }
        }

        /** Returns the number of equally likely discrete damage values. */
        int damageValueCount() {
            return (damageMaximum - damageMinimum) / damageStep + 1;
        }
    }

    /** Validated collision, health, and behavior rules for one actor definition. */
    record CombatantDefinition(
            String actorId,
            int health,
            int radius,
            int height,
            EnemyBehavior behavior) {
        /** Validates positive combatant dimensions and health. */
        CombatantDefinition {
            requireId(actorId, "combatant actor");
            if (health <= 0 || radius <= 0 || height <= 0) {
                throw new IllegalArgumentException("combatant numeric values must be positive");
            }
            Objects.requireNonNull(behavior, "behavior");
        }
    }

    /** Validated resource effect and contact radius for one collectable actor identity. */
    record PickupDefinition(
            String actorId, PickupResource resource, int amount, int limit, int radius) {
        /** Validates the provider actor identity and positive effect values. */
        PickupDefinition {
            requireId(actorId, "pickup actor");
            Objects.requireNonNull(resource, "resource");
            if (amount <= 0 || limit <= 0 || radius <= 0) {
                throw new IllegalArgumentException("pickup numeric values must be positive");
            }
        }
    }

    /** Player resource modified by one collectable actor. */
    enum PickupResource {
        HEALTH,
        BULLETS
    }

    /** Validated awareness, movement, timing, and hitscan damage for one enemy. */
    record EnemyBehavior(
            int sightRange,
            int attackRange,
            int preferredRange,
            int moveSpeed,
            int reactionMilliseconds,
            int attackIntervalMilliseconds,
            DamageDefinition damage) {
        /** Validates positive timing, distances, speed, and discrete damage values. */
        EnemyBehavior {
            if (sightRange <= 0
                    || attackRange <= 0
                    || preferredRange <= 0
                    || moveSpeed <= 0
                    || reactionMilliseconds <= 0
                    || attackIntervalMilliseconds <= 0) {
                throw new IllegalArgumentException("enemy behavior values must be positive");
            }
            if (preferredRange > attackRange || attackRange > sightRange) {
                throw new IllegalArgumentException(
                        "enemy ranges must satisfy preferredRange <= attackRange <= sightRange");
            }
            Objects.requireNonNull(damage, "damage");
        }

        /** Returns the number of equally likely discrete attack-damage values. */
        int damageValueCount() {
            return damage.valueCount();
        }
    }

    /** Validated discrete damage sequence for one enemy attack. */
    record DamageDefinition(int minimum, int maximum, int step) {
        /** Validates a positive, evenly stepped inclusive range. */
        DamageDefinition {
            if (minimum <= 0 || step <= 0 || maximum < minimum
                    || (maximum - minimum) % step != 0) {
                throw new IllegalArgumentException(
                        "enemy damage range must contain positive complete damage steps");
            }
        }

        /** Returns the number of equally likely values in this range. */
        int valueCount() {
            return (maximum - minimum) / step + 1;
        }
    }
}
