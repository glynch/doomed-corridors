/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.glynch.doomedcorridors.combat.DoomCombatDiagnostic;
import io.github.glynch.doomedcorridors.combat.DoomCombatRules;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Loads provider-authored combat asset and timing bindings. */
public final class DoomCombatPresentationLoader {
    private static final int SCHEMA_VERSION = 3;

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    /** Loads one presentation document and cross-validates its combat identities. */
    public DoomCombatPresentationLoadResult load(Path source, DoomCombatRules combatRules) {
        Path normalizedSource = Objects.requireNonNull(source, "source")
                .toAbsolutePath()
                .normalize();
        DoomCombatRules validCombatRules = Objects.requireNonNull(combatRules, "combatRules");
        try {
            RawPresentation raw = mapper.readValue(normalizedSource.toFile(), RawPresentation.class);
            if (raw.schemaVersion() != SCHEMA_VERSION) {
                return error(normalizedSource, "doom.presentation.rules-version", "/schemaVersion",
                        "Unsupported combat presentation schemaVersion: " + raw.schemaVersion());
            }
            DoomCombatPresentationRules rules = toRules(raw);
            validateCombatReferences(rules, validCombatRules);
            return new DoomCombatPresentationLoadResult(Optional.of(rules), List.of());
        } catch (IOException | IllegalArgumentException exception) {
            return error(normalizedSource, "doom.presentation.rules-invalid", "/",
                    "Cannot load combat presentation: " + exception.getMessage());
        }
    }

    /** Converts nullable JSON bindings into validated presentation rules. */
    private static DoomCombatPresentationRules toRules(RawPresentation raw) {
        RawWeapon rawWeapon = Objects.requireNonNull(raw.weapon(), "weapon is required");
        DoomCombatPresentationRules.Weapon weapon = new DoomCombatPresentationRules.Weapon(
                rawWeapon.id(),
                rawWeapon.readyFrame(),
                rawWeapon.fireFrames(),
                Duration.ofMillis(rawWeapon.frameMilliseconds()),
                rawWeapon.fireSound());
        RawPlayer rawPlayer = Objects.requireNonNull(raw.player(), "player is required");
        DoomCombatPresentationRules.Player player = new DoomCombatPresentationRules.Player(
                rawPlayer.painSound(), rawPlayer.deathSound());
        RawPickups rawPickups = Objects.requireNonNull(raw.pickups(), "pickups are required");
        DoomCombatPresentationRules.Pickups pickups =
                new DoomCombatPresentationRules.Pickups(rawPickups.collectSound());
        List<RawCombatant> rawCombatants = Objects.requireNonNull(raw.combatants(), "combatants are required");
        List<DoomCombatPresentationRules.Combatant> combatants = new ArrayList<>(rawCombatants.size());
        for (RawCombatant rawCombatant : rawCombatants) {
            RawCombatant value = Objects.requireNonNull(rawCombatant, "combatant must be an object");
            RawAnimations animations = Objects.requireNonNull(
                    value.animations(), "combatant animations are required");
            RawSounds sounds = Objects.requireNonNull(
                    value.sounds(), "combatant sounds are required");
            combatants.add(new DoomCombatPresentationRules.Combatant(
                    value.actor(),
                    new DoomCombatPresentationRules.CombatantAnimations(
                            animations.walkFrames(),
                            animations.attackFrames(),
                            animations.painFrames(),
                            animations.deathFrames(),
                            Duration.ofMillis(animations.frameMilliseconds())),
                    new DoomCombatPresentationRules.CombatantSounds(
                            sounds.sightSounds(),
                            sounds.attackSound(),
                            sounds.painSound(),
                            sounds.deathSounds())));
        }
        RawHud rawHud = Objects.requireNonNull(raw.hud(), "hud is required");
        DoomCombatPresentationRules.Hud hud =
                new DoomCombatPresentationRules.Hud(rawHud.digits(), rawHud.percent());
        return new DoomCombatPresentationRules(weapon, player, pickups, combatants, hud);
    }

    /** Requires all bindings to name combat identities from the companion rules. */
    private static void validateCombatReferences(
            DoomCombatPresentationRules presentation, DoomCombatRules combat) {
        if (!presentation.weapon().id().equals(combat.primaryWeaponId())) {
            throw new IllegalArgumentException(
                    "Presented weapon is not the primary combat weapon: " + presentation.weapon().id());
        }
        for (String actorId : presentation.combatantActorIds()) {
            if (!combat.hasCombatant(actorId)) {
                throw new IllegalArgumentException("Presented actor is not a combatant: " + actorId);
            }
        }
    }

    /** Returns one failed load result with a stable diagnostic identity. */
    private static DoomCombatPresentationLoadResult error(
            Path source, String code, String location, String message) {
        DoomCombatDiagnostic diagnostic = new DoomCombatDiagnostic(
                DoomCombatDiagnostic.Severity.ERROR, code, source, location, message);
        return new DoomCombatPresentationLoadResult(Optional.empty(), List.of(diagnostic));
    }

    /** Direct JSON root binding retained only during conversion. */
    private record RawPresentation(
            @JsonProperty("$schema") String schema,
            int schemaVersion,
            RawWeapon weapon,
            RawPlayer player,
            RawPickups pickups,
            List<RawCombatant> combatants,
            RawHud hud) {}

    /** Direct JSON weapon binding retained only during conversion. */
    private record RawWeapon(
            String id,
            String readyFrame,
            List<String> fireFrames,
            int frameMilliseconds,
            String fireSound) {}

    /** Direct JSON player binding retained only during conversion. */
    private record RawPlayer(String painSound, String deathSound) {}

    /** Direct JSON pickup feedback binding retained only during conversion. */
    private record RawPickups(String collectSound) {}

    /** Direct JSON combatant binding retained only during conversion. */
    private record RawCombatant(String actor, RawAnimations animations, RawSounds sounds) {}

    /** Direct JSON combatant-animation binding retained only during conversion. */
    private record RawAnimations(
            List<String> walkFrames,
            List<String> attackFrames,
            List<String> painFrames,
            List<String> deathFrames,
            int frameMilliseconds) {}

    /** Direct JSON combatant-sound binding retained only during conversion. */
    private record RawSounds(
            List<String> sightSounds,
            String attackSound,
            String painSound,
            List<String> deathSounds) {}

    /** Direct JSON HUD binding retained only during conversion. */
    private record RawHud(List<String> digits, String percent) {}
}
