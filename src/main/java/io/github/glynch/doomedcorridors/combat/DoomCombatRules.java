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
    private final int startingHealth;
    private final int startingBullets;
    private final WeaponDefinition primaryWeapon;
    private final Map<String, CombatantDefinition> combatants;

    /** Validates player, weapon, and combatant rules while building lookup indexes. */
    DoomCombatRules(
            int startingHealth,
            int startingBullets,
            String startingWeapon,
            List<WeaponDefinition> weapons,
            List<CombatantDefinition> combatants) {
        if (startingHealth <= 0) {
            throw new IllegalArgumentException("startingHealth must be positive");
        }
        if (startingBullets < 0) {
            throw new IllegalArgumentException("startingBullets must not be negative");
        }
        this.startingHealth = startingHealth;
        this.startingBullets = startingBullets;
        Map<String, WeaponDefinition> weaponsById = indexWeapons(weapons);
        primaryWeapon = weaponsById.get(requireId(startingWeapon, "startingWeapon"));
        if (primaryWeapon == null) {
            throw new IllegalArgumentException("startingWeapon does not name a defined weapon: " + startingWeapon);
        }
        this.combatants = indexCombatants(combatants);
    }

    /** Returns the player's initial and maximum health. */
    public int startingHealth() {
        return startingHealth;
    }

    /** Returns the player's initial bullet count. */
    public int startingBullets() {
        return startingBullets;
    }

    /** Returns the stable identifier of the initially selected weapon. */
    public String primaryWeaponId() {
        return primaryWeapon.id();
    }

    /** Returns the number of actor definitions that participate in combat. */
    public int combatantDefinitionCount() {
        return combatants.size();
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

    /** Returns configured actor identifiers for cross-catalog validation. */
    Set<String> combatantActorIds() {
        return combatants.keySet();
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
