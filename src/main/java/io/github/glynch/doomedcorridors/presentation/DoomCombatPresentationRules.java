/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable provider-authored bindings from combat identities to WAD presentation assets. */
public final class DoomCombatPresentationRules {
    private final List<Weapon> weapons;
    private final Map<String, Weapon> weaponsById;
    private final Player player;
    private final Pickups pickups;
    private final Doors doors;
    private final Map<String, Combatant> combatants;
    private final Hud hud;

    /** Indexes one weapon, combatant bindings, and HUD patch names. */
    DoomCombatPresentationRules(
            List<Weapon> weapons, Player player, Pickups pickups, Doors doors, List<Combatant> combatants, Hud hud) {
        this.weapons = List.copyOf(Objects.requireNonNull(weapons, "weapons"));
        this.weaponsById = indexWeapons(this.weapons);
        this.player = Objects.requireNonNull(player, "player");
        this.pickups = Objects.requireNonNull(pickups, "pickups");
        this.doors = Objects.requireNonNull(doors, "doors");
        this.combatants = indexCombatants(combatants);
        this.hud = Objects.requireNonNull(hud, "hud");
    }

    /** Returns the selected weapon presentation. */
    public Weapon weapon() {
        return weapons.getFirst();
    }

    /** Returns all weapon presentation bindings in declaration order. */
    public List<Weapon> weapons() {
        return weapons;
    }

    /** Returns player pain and death sound bindings. */
    public Player player() {
        return player;
    }

    /** Returns listener-relative pickup feedback bindings. */
    public Pickups pickups() {
        return pickups;
    }

    /** Returns normal and blaze door movement-sound bindings. */
    public Doors doors() {
        return doors;
    }

    /** Returns the HUD patch bindings. */
    public Hud hud() {
        return hud;
    }

    /** Returns presentation rules for an actor identity, or {@code null} when it is inert. */
    public Combatant combatant(String actorId) {
        return combatants.get(Objects.requireNonNull(actorId, "actorId"));
    }

    /** Returns all exact patch lump names needed by this presentation. */
    public Set<String> imageLumps() {
        Set<String> names = new LinkedHashSet<>();
        for (Weapon weapon : weapons) {
            names.add(weapon.readyFrame());
            names.addAll(weapon.fireFrames());
        }
        for (Combatant combatant : combatants.values()) {
            CombatantAnimations animations = combatant.animations();
            names.addAll(animations.walkFrames());
            names.addAll(animations.attackFrames());
            names.addAll(animations.painFrames());
            names.addAll(animations.deathFrames());
        }
        names.addAll(hud.digits());
        names.add(hud.percent());
        return Set.copyOf(names);
    }

    /** Returns all exact DMX sound lump names needed by this presentation. */
    public Set<String> soundLumps() {
        Set<String> names = new LinkedHashSet<>();
        weapons.forEach(weapon -> names.add(weapon.fireSound()));
        names.add(player.painSound());
        names.add(player.deathSound());
        names.add(pickups.collectSound());
        names.add(doors.normalOpeningSound());
        names.add(doors.normalClosingSound());
        names.add(doors.blazeOpeningSound());
        names.add(doors.blazeClosingSound());
        for (Combatant combatant : combatants.values()) {
            CombatantSounds sounds = combatant.sounds();
            names.addAll(sounds.sightSounds());
            names.add(sounds.attackSound());
            names.add(sounds.painSound());
            names.addAll(sounds.deathSounds());
        }
        return Set.copyOf(names);
    }

    /** Returns configured combatant actor IDs in declaration order. */
    Set<String> combatantActorIds() {
        return combatants.keySet();
    }

    /** Returns configured weapon IDs in declaration order. */
    Set<String> weaponIds() {
        return weaponsById.keySet();
    }

    /** Builds a duplicate-rejecting, non-empty weapon index. */
    private static Map<String, Weapon> indexWeapons(List<Weapon> definitions) {
        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("weapons must not be empty");
        }
        Map<String, Weapon> indexed = new LinkedHashMap<>();
        for (Weapon weapon : definitions) {
            Weapon value = Objects.requireNonNull(weapon, "weapon");
            if (indexed.putIfAbsent(value.id(), value) != null) {
                throw new IllegalArgumentException("Duplicate weapon presentation: " + value.id());
            }
        }
        return Map.copyOf(indexed);
    }

    /** Builds a duplicate-rejecting combatant index. */
    private static Map<String, Combatant> indexCombatants(List<Combatant> definitions) {
        Map<String, Combatant> indexed = new LinkedHashMap<>();
        for (Combatant combatant : List.copyOf(Objects.requireNonNull(definitions, "combatants"))) {
            Combatant value = Objects.requireNonNull(combatant, "combatant");
            if (indexed.putIfAbsent(value.actorId(), value) != null) {
                throw new IllegalArgumentException("Duplicate combat presentation actor: " + value.actorId());
            }
        }
        return Map.copyOf(indexed);
    }

    /** Requires one non-empty sequence and returns an immutable copy. */
    private static List<String> requireLumps(List<String> values, String name) {
        List<String> lumps = List.copyOf(Objects.requireNonNull(values, name));
        if (lumps.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        for (int index = 0; index < lumps.size(); index++) {
            requireLump(lumps.get(index), name + '[' + index + ']');
        }
        return lumps;
    }

    /** Requires an upper-case classic lump identifier of at most eight characters. */
    private static void requireLump(String value, String name) {
        String lump = Objects.requireNonNull(value, name);
        boolean valid = !lump.isEmpty() && lump.length() <= 8;
        for (int index = 0; valid && index < lump.length(); index++) {
            char character = lump.charAt(index);
            valid = character >= 'A' && character <= 'Z' || character >= '0' && character <= '9';
        }
        if (!valid) {
            throw new IllegalArgumentException(name + " is not a classic lump name: " + lump);
        }
    }

    /** Presentation binding for the selected weapon. */
    public record Weapon(
            String id, String readyFrame, List<String> fireFrames, Duration frameDuration, String fireSound) {
        /** Validates exact image/sound lump names and positive frame timing. */
        public Weapon {
            Objects.requireNonNull(id, "id");
            requireLump(readyFrame, "readyFrame");
            fireFrames = requireLumps(fireFrames, "fireFrames");
            requirePositive(frameDuration, "frameDuration");
            requireLump(fireSound, "fireSound");
        }
    }

    /** Player-local sound effects for receiving damage and dying. */
    public record Player(String painSound, String deathSound) {
        /** Validates exact classic sound lump names. */
        public Player {
            requireLump(painSound, "painSound");
            requireLump(deathSound, "deathSound");
        }
    }

    /** Listener-relative sound effect for collecting health or ammunition. */
    public record Pickups(String collectSound) {
        /** Validates the exact classic sound lump name. */
        public Pickups {
            requireLump(collectSound, "collectSound");
        }
    }

    /** Positional movement sounds selected by each imported door's semantic profile. */
    public record Doors(
            String normalOpeningSound, String normalClosingSound, String blazeOpeningSound, String blazeClosingSound) {
        /** Validates every exact classic sound lump name. */
        public Doors {
            requireLump(normalOpeningSound, "normalOpeningSound");
            requireLump(normalClosingSound, "normalClosingSound");
            requireLump(blazeOpeningSound, "blazeOpeningSound");
            requireLump(blazeClosingSound, "blazeClosingSound");
        }
    }

    /** Presentation binding for one combatant actor identity. */
    public record Combatant(String actorId, CombatantAnimations animations, CombatantSounds sounds) {
        /** Validates the actor identity and grouped presentation bindings. */
        public Combatant {
            Objects.requireNonNull(actorId, "actorId");
            Objects.requireNonNull(animations, "animations");
            Objects.requireNonNull(sounds, "sounds");
        }
    }

    /** Frame sequences and common timing for one combatant actor identity. */
    public record CombatantAnimations(
            List<String> walkFrames,
            List<String> attackFrames,
            List<String> painFrames,
            List<String> deathFrames,
            Duration frameDuration) {
        /** Validates non-empty exact frame names and positive common timing. */
        public CombatantAnimations {
            walkFrames = requireLumps(walkFrames, "walkFrames");
            attackFrames = requireLumps(attackFrames, "attackFrames");
            painFrames = requireLumps(painFrames, "painFrames");
            deathFrames = requireLumps(deathFrames, "deathFrames");
            requirePositive(frameDuration, "frameDuration");
        }
    }

    /** Alert, attack, pain, and death sounds for one combatant actor identity. */
    public record CombatantSounds(
            List<String> sightSounds, String attackSound, String painSound, List<String> deathSounds) {
        /** Validates exact sound lump names and non-empty variants. */
        public CombatantSounds {
            sightSounds = requireLumps(sightSounds, "sightSounds");
            requireLump(attackSound, "attackSound");
            requireLump(painSound, "painSound");
            deathSounds = requireLumps(deathSounds, "deathSounds");
        }
    }

    /** Presentation binding for classic numeric health and ammunition readouts. */
    public record Hud(List<String> digits, String percent) {
        /** Requires exactly one patch per decimal digit plus a percent-sign patch. */
        public Hud {
            digits = requireLumps(digits, "digits");
            if (digits.size() != 10) {
                throw new IllegalArgumentException("digits must contain exactly ten patches");
            }
            requireLump(percent, "percent");
        }
    }

    /** Requires one positive duration. */
    private static void requirePositive(Duration value, String name) {
        Duration duration = Objects.requireNonNull(value, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
