/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Immutable provider rules needed to initialize the first headless combat loop. */
public final class DoomCombatRules {
    private final PlayerDefinition player;
    private final WeaponDefinition primaryWeapon;
    private final Map<String, WeaponDefinition> weapons;
    private final Map<String, CombatantDefinition> combatants;
    private final Map<String, PickupDefinition> pickups;

    /** Validates player, weapon, combatant, and pickup rules while building lookup indexes. */
    DoomCombatRules(
            PlayerDefinition player,
            List<WeaponDefinition> weapons,
            List<CombatantDefinition> combatants,
            List<PickupDefinition> pickups) {
        this.player = Objects.requireNonNull(player, "player");
        this.weapons = indexWeapons(weapons);
        primaryWeapon = this.weapons.get(player.startingWeapon());
        if (primaryWeapon == null) {
            throw new IllegalArgumentException(
                    "startingWeapon does not name a defined weapon: " + player.startingWeapon());
        }
        this.combatants = indexCombatants(combatants);
        this.pickups = indexPickups(pickups, player, this.weapons);
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

    /** Returns the player's initial shell count. */
    public int startingShells() {
        return player.startingShells();
    }

    /** Returns the player's shell-ammunition capacity. */
    public int maximumShells() {
        return player.maximumShells();
    }

    /** Returns the player's initial armour points. */
    public int startingArmor() {
        return player.startingArmor();
    }

    /** Returns the player's armour-point capacity. */
    public int maximumArmor() {
        return player.maximumArmor();
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

    /** Returns one combatant's configured initial health. */
    public int combatantStartingHealth(String actorId) {
        return requireCombatant(actorId).health();
    }

    /** Returns whether the stable identity names a configured weapon. */
    public boolean hasWeapon(String weaponId) {
        return weapons.containsKey(Objects.requireNonNull(weaponId, "weaponId"));
    }

    /** Returns the bullet cost of firing one configured weapon. */
    public int weaponAmmoPerShot(String weaponId) {
        return requireWeapon(weaponId).ammoPerShot();
    }

    /** Returns the ammunition pool consumed by one configured weapon. */
    public Ammunition weaponAmmunition(String weaponId) {
        return requireWeapon(weaponId).ammunition();
    }

    /** Returns the number of independently damaged pellets in one shot. */
    public int weaponPelletCount(String weaponId) {
        return requireWeapon(weaponId).pelletCount();
    }

    /** Returns the minimum elapsed simulation time between accepted shots. */
    public int weaponRefireMilliseconds(String weaponId) {
        return requireWeapon(weaponId).refireMilliseconds();
    }

    /** Returns configured weapon IDs in declaration order. */
    public Set<String> weaponIds() {
        return weapons.keySet();
    }

    /** Returns one configured weapon's maximum distance in Doom map units. */
    public int weaponRange(String weaponId) {
        return requireWeapon(weaponId).range();
    }

    /** Returns one weapon's symmetric horizontal auto-aim half-angle in degrees. */
    public float weaponAutoAimAngleDegrees(String weaponId) {
        return requireWeapon(weaponId).autoAimAngleDegrees();
    }

    /** Returns one weapon's maximum absolute vertical auto-aim slope. */
    public float weaponAutoAimMaximumSlope(String weaponId) {
        return requireWeapon(weaponId).autoAimMaximumSlope();
    }

    /** Rolls one configured weapon's inclusive discrete damage sequence. */
    public int rollWeaponDamage(String weaponId, RandomGenerator random) {
        WeaponDefinition weapon = requireWeapon(weaponId);
        int valueIndex = Objects.requireNonNull(random, "random").nextInt(weapon.damageValueCount());
        return weapon.damageMinimum() + valueIndex * weapon.damageStep();
    }

    /** Finds the provider-defined solid collision bounds for one combatant actor identity. */
    public Optional<CombatantBounds> findCombatantBounds(String actorId) {
        CombatantDefinition definition = combatants.get(Objects.requireNonNull(actorId, "actorId"));
        return definition == null
                ? Optional.empty()
                : Optional.of(new CombatantBounds(definition.radius(), definition.height()));
    }

    /** Returns validated awareness and movement rules for one configured enemy actor. */
    public EnemyBehavior enemyBehavior(String actorId) {
        return requireCombatant(actorId).behavior();
    }

    /** Rolls one configured enemy's inclusive discrete attack-damage sequence. */
    public int rollEnemyDamage(String actorId, RandomGenerator random) {
        EnemyBehavior behavior = requireCombatant(actorId).behavior();
        DamageDefinition damage = behavior.damage();
        int valueIndex = Objects.requireNonNull(random, "random").nextInt(behavior.damageValueCount());
        return damage.minimum() + valueIndex * damage.step();
    }

    /** Requires matching combatant rules for a descriptor-validated actor identity. */
    private CombatantDefinition requireCombatant(String actorId) {
        String validActorId = Objects.requireNonNull(actorId, "actorId");
        CombatantDefinition combatant = combatants.get(validActorId);
        if (combatant == null) {
            throw new IllegalArgumentException("unknown combatant actor: " + validActorId);
        }
        return combatant;
    }

    /** Requires matching weapon rules for a descriptor-validated weapon identity. */
    private WeaponDefinition requireWeapon(String weaponId) {
        String validWeaponId = Objects.requireNonNull(weaponId, "weaponId");
        WeaponDefinition weapon = weapons.get(validWeaponId);
        if (weapon == null) {
            throw new IllegalArgumentException("unknown weapon: " + validWeaponId);
        }
        return weapon;
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

    /** Finds the provider rules for one collectable actor identity. */
    public Optional<PickupDefinition> findPickup(String actorId) {
        return Optional.ofNullable(pickups.get(Objects.requireNonNull(actorId, "actorId")));
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
    private static Map<String, CombatantDefinition> indexCombatants(List<CombatantDefinition> definitions) {
        Map<String, CombatantDefinition> indexed = new LinkedHashMap<>();
        for (CombatantDefinition definition : List.copyOf(Objects.requireNonNull(definitions, "combatants"))) {
            CombatantDefinition validDefinition = Objects.requireNonNull(definition, "combatant");
            CombatantDefinition previous = indexed.putIfAbsent(validDefinition.actorId(), validDefinition);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate combatant actor id: " + validDefinition.actorId());
            }
        }
        return Map.copyOf(indexed);
    }

    /** Indexes pickup definitions and checks each per-item limit against player capacity. */
    private static Map<String, PickupDefinition> indexPickups(
            List<PickupDefinition> definitions, PlayerDefinition player, Map<String, WeaponDefinition> weapons) {
        Map<String, PickupDefinition> indexed = new LinkedHashMap<>();
        for (PickupDefinition definition : List.copyOf(Objects.requireNonNull(definitions, "pickups"))) {
            PickupDefinition validDefinition = Objects.requireNonNull(definition, "pickup");
            int capacity =
                    switch (validDefinition.resource()) {
                        case HEALTH -> player.maximumHealth();
                        case ARMOR -> player.maximumArmor();
                        case BULLETS -> player.maximumBullets();
                        case SHELLS -> player.maximumShells();
                    };
            if (validDefinition.limit() > capacity) {
                throw new IllegalArgumentException(
                        "Pickup limit exceeds player capacity: " + validDefinition.actorId());
            }
            validDefinition.grantedWeapon().ifPresent(weaponId -> {
                if (!weapons.containsKey(weaponId)) {
                    throw new IllegalArgumentException("Pickup grants an unknown weapon: " + validDefinition.actorId());
                }
            });
            PickupDefinition previous = indexed.putIfAbsent(validDefinition.actorId(), validDefinition);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate pickup actor id: " + validDefinition.actorId());
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
            int startingArmor,
            int maximumArmor,
            int startingBullets,
            int maximumBullets,
            int startingShells,
            int maximumShells,
            String startingWeapon) {
        /** Validates resource ranges and the selected weapon identity. */
        PlayerDefinition {
            if (startingHealth <= 0 || maximumHealth < startingHealth) {
                throw new IllegalArgumentException("health values must satisfy 0 < startingHealth <= maximumHealth");
            }
            if (startingBullets < 0 || maximumBullets < startingBullets) {
                throw new IllegalArgumentException("bullet values must satisfy 0 <= startingBullets <= maximumBullets");
            }
            if (startingShells < 0 || maximumShells < startingShells) {
                throw new IllegalArgumentException("shell values must satisfy 0 <= startingShells <= maximumShells");
            }
            if (startingArmor < 0 || maximumArmor < startingArmor) {
                throw new IllegalArgumentException("armour values must satisfy 0 <= startingArmor <= maximumArmor");
            }
            requireId(startingWeapon, "startingWeapon");
        }
    }

    /** Validated rules for one hitscan weapon. */
    record WeaponDefinition(
            String id,
            Ammunition ammunition,
            int ammoPerShot,
            int pelletCount,
            int refireMilliseconds,
            int range,
            float autoAimAngleDegrees,
            float autoAimMaximumSlope,
            int damageMinimum,
            int damageMaximum,
            int damageStep) {
        /** Validates the discrete damage sequence and positive weapon dimensions. */
        WeaponDefinition {
            requireId(id, "weapon id");
            Objects.requireNonNull(ammunition, "ammunition");
            if (ammoPerShot <= 0
                    || pelletCount <= 0
                    || refireMilliseconds <= 0
                    || range <= 0
                    || damageMinimum <= 0
                    || damageStep <= 0) {
                throw new IllegalArgumentException("weapon numeric values must be positive");
            }
            if (!Float.isFinite(autoAimAngleDegrees) || autoAimAngleDegrees <= 0.0F || autoAimAngleDegrees > 45.0F) {
                throw new IllegalArgumentException("weapon auto-aim angle must be finite and in (0, 45]");
            }
            if (!Float.isFinite(autoAimMaximumSlope) || autoAimMaximumSlope <= 0.0F) {
                throw new IllegalArgumentException("weapon auto-aim maximum slope must be finite and positive");
            }
            if (damageMaximum < damageMinimum || (damageMaximum - damageMinimum) % damageStep != 0) {
                throw new IllegalArgumentException("weapon damage range must contain complete damage steps");
            }
        }

        /** Returns the number of equally likely discrete damage values. */
        int damageValueCount() {
            return (damageMaximum - damageMinimum) / damageStep + 1;
        }
    }

    /** Validated collision, health, and behavior rules for one actor definition. */
    record CombatantDefinition(String actorId, int health, int radius, int height, EnemyBehavior behavior) {
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
    public record PickupDefinition(
            String actorId,
            PickupResource resource,
            int amount,
            int limit,
            int radius,
            Optional<String> grantedWeapon,
            int armorProtectionPercent) {
        /** Validates the provider actor identity and positive effect values. */
        public PickupDefinition {
            requireId(actorId, "pickup actor");
            Objects.requireNonNull(resource, "resource");
            grantedWeapon = Objects.requireNonNull(grantedWeapon, "grantedWeapon")
                    .map(value -> requireId(value, "grantedWeapon"));
            if (amount <= 0 || limit <= 0 || radius <= 0) {
                throw new IllegalArgumentException("pickup numeric values must be positive");
            }
            if (armorProtectionPercent < 0 || armorProtectionPercent > 100) {
                throw new IllegalArgumentException("armorProtectionPercent must be in [0, 100]");
            }
            if ((resource == PickupResource.ARMOR) != (armorProtectionPercent > 0)) {
                throw new IllegalArgumentException("only armour pickups require armorProtectionPercent");
            }
        }
    }

    /** Player resource modified by one collectable actor. */
    public enum PickupResource {
        HEALTH,
        ARMOR,
        BULLETS,
        SHELLS
    }

    /** Ammunition pools consumed by currently supported weapons. */
    public enum Ammunition {
        BULLETS,
        SHELLS
    }

    /** Provider-authored cylindrical collision dimensions for one solid combatant. */
    public record CombatantBounds(int radius, int height) {
        /** Retains only positive source-unit dimensions. */
        public CombatantBounds {
            if (radius <= 0 || height <= 0) {
                throw new IllegalArgumentException("combatant collision dimensions must be positive");
            }
        }
    }

    /** Validated awareness, movement, timing, and hitscan damage for one enemy. */
    public record EnemyBehavior(
            int sightRange,
            int attackRange,
            int preferredRange,
            int moveSpeed,
            int reactionMilliseconds,
            int attackIntervalMilliseconds,
            DamageDefinition damage) {
        /** Validates positive timing, distances, speed, and discrete damage values. */
        public EnemyBehavior {
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
            if (minimum <= 0 || step <= 0 || maximum < minimum || (maximum - minimum) % step != 0) {
                throw new IllegalArgumentException("enemy damage range must contain positive complete damage steps");
            }
        }

        /** Returns the number of equally likely values in this range. */
        int valueCount() {
            return (maximum - minimum) / step + 1;
        }
    }
}
