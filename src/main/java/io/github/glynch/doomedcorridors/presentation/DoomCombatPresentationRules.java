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
    private final Weapon weapon;
    private final Map<String, Combatant> combatants;
    private final Hud hud;

    /** Indexes one weapon, combatant bindings, and HUD patch names. */
    DoomCombatPresentationRules(Weapon weapon, List<Combatant> combatants, Hud hud) {
        this.weapon = Objects.requireNonNull(weapon, "weapon");
        this.combatants = indexCombatants(combatants);
        this.hud = Objects.requireNonNull(hud, "hud");
    }

    /** Returns the selected weapon presentation. */
    public Weapon weapon() {
        return weapon;
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
        names.add(weapon.readyFrame());
        names.addAll(weapon.fireFrames());
        for (Combatant combatant : combatants.values()) {
            names.addAll(combatant.painFrames());
            names.addAll(combatant.deathFrames());
        }
        names.addAll(hud.digits());
        names.add(hud.percent());
        return Set.copyOf(names);
    }

    /** Returns all exact DMX sound lump names needed by this presentation. */
    public Set<String> soundLumps() {
        Set<String> names = new LinkedHashSet<>();
        names.add(weapon.fireSound());
        for (Combatant combatant : combatants.values()) {
            names.add(combatant.painSound());
            names.addAll(combatant.deathSounds());
        }
        return Set.copyOf(names);
    }

    /** Returns configured combatant actor IDs in declaration order. */
    Set<String> combatantActorIds() {
        return combatants.keySet();
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
            String id,
            String readyFrame,
            List<String> fireFrames,
            Duration frameDuration,
            String fireSound) {
        /** Validates exact image/sound lump names and positive frame timing. */
        public Weapon {
            Objects.requireNonNull(id, "id");
            requireLump(readyFrame, "readyFrame");
            fireFrames = requireLumps(fireFrames, "fireFrames");
            requirePositive(frameDuration, "frameDuration");
            requireLump(fireSound, "fireSound");
        }
    }

    /** Presentation binding for one combatant actor identity. */
    public record Combatant(
            String actorId,
            List<String> painFrames,
            List<String> deathFrames,
            Duration frameDuration,
            String painSound,
            List<String> deathSounds) {
        /** Validates exact image/sound lump names and positive frame timing. */
        public Combatant {
            Objects.requireNonNull(actorId, "actorId");
            painFrames = requireLumps(painFrames, "painFrames");
            deathFrames = requireLumps(deathFrames, "deathFrames");
            requirePositive(frameDuration, "frameDuration");
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
