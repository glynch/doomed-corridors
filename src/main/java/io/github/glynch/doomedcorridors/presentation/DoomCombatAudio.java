/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import io.github.glynch.doomedcorridors.combat.DoomCombatEvent;
import io.github.glynch.doomedcorridors.combat.DoomCombatState;
import io.github.glynch.doomedcorridors.combat.DoomCombatUpdate;
import io.github.glynch.doomedcorridors.combat.DoomCombatantState;
import io.github.glynch.doomedcorridors.world.DoomPlayerState;
import io.github.glynch.jscene3d.audio.AudioCategory;
import io.github.glynch.jscene3d.audio.AudioClip;
import io.github.glynch.jscene3d.audio.AudioEngine;
import io.github.glynch.jscene3d.audio.AudioSource;
import io.github.glynch.jscene3d.audio.PcmAudio;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.joml.Vector3f;

/** Owns native playback resources and maps combat events onto imported WAD effects. */
public final class DoomCombatAudio implements AutoCloseable {
    private static final float SOURCE_REFERENCE_DISTANCE = 1.0F;
    private static final float SOURCE_MAXIMUM_DISTANCE = 40.0F;

    private final DoomCombatPresentationRules rules;
    private final AudioEngine engine;
    private final Map<String, AudioSource> sources;

    /** Retains a fully initialized engine and sound-source index. */
    private DoomCombatAudio(
            DoomCombatPresentationRules rules,
            AudioEngine engine,
            Map<String, AudioSource> sources) {
        this.rules = rules;
        this.engine = engine;
        this.sources = sources;
    }

    /** Creates one buffered source for every imported WAD effect. */
    public static DoomCombatAudio create(DoomCombatAssets assets) {
        DoomCombatAssets validAssets = Objects.requireNonNull(assets, "assets");
        AudioEngine engine = AudioEngine.create();
        try {
            Map<String, AudioSource> sources = new LinkedHashMap<>();
            for (Map.Entry<String, PcmAudio> entry : validAssets.sounds().entrySet()) {
                AudioClip clip = engine.createClip(entry.getValue());
                AudioSource source = engine.createSource(clip, AudioCategory.EFFECTS);
                configureSource(source, entry.getKey().equals(validAssets.rules().weapon().fireSound()));
                sources.put(entry.getKey(), source);
            }
            return new DoomCombatAudio(validAssets.rules(), engine, Map.copyOf(sources));
        } catch (RuntimeException failure) {
            engine.close();
            throw failure;
        }
    }

    /** Synchronizes the listener with the same current player pose used by the camera. */
    public void applyPlayerState(DoomPlayerState player) {
        DoomPlayerState value = Objects.requireNonNull(player, "player");
        float horizontal = (float) Math.cos(value.pitchRadians());
        Vector3f position = new Vector3f(value.x(), value.eyeHeight(), value.z());
        Vector3f forward = new Vector3f(
                (float) Math.cos(value.yawRadians()) * horizontal,
                (float) Math.sin(value.pitchRadians()),
                -(float) Math.sin(value.yawRadians()) * horizontal);
        engine.listener().setTransform(position, forward, new Vector3f(0.0F, 1.0F, 0.0F));
    }

    /** Plays the effects selected by one atomic combat update. */
    public void apply(DoomCombatUpdate update) {
        DoomCombatUpdate validUpdate = Objects.requireNonNull(update, "update");
        Set<Integer> killed = validUpdate.events().stream()
                .filter(event -> event.type() == DoomCombatEvent.Type.COMBATANT_KILLED)
                .map(DoomCombatEvent::thingIndex)
                .collect(Collectors.toUnmodifiableSet());
        for (DoomCombatEvent event : validUpdate.events()) {
            switch (event) {
                case DoomCombatEvent weaponFired
                        when weaponFired.type() == DoomCombatEvent.Type.WEAPON_FIRED ->
                    play(rules.weapon().fireSound());
                case DoomCombatEvent combatantDamaged
                        when combatantDamaged.type() == DoomCombatEvent.Type.COMBATANT_DAMAGED
                                && !killed.contains(combatantDamaged.thingIndex()) ->
                    playCombatantSound(
                            combatantDamaged.thingIndex(), validUpdate.state(), false);
                case DoomCombatEvent combatantKilled
                        when combatantKilled.type() == DoomCombatEvent.Type.COMBATANT_KILLED ->
                    playCombatantSound(combatantKilled.thingIndex(), validUpdate.state(), true);
                default -> {
                    // No sound is bound for this event in the first combat presentation.
                }
            }
        }
    }

    /** Releases every source, clip, context, and device through engine ownership. */
    @Override
    public void close() {
        engine.close();
    }

    /** Configures listener-relative weapon playback or positional world playback. */
    private static void configureSource(AudioSource source, boolean relative) {
        source.setRelative(relative);
        if (relative) {
            source.setPosition(new Vector3f());
        } else {
            source.setAttenuation(
                    SOURCE_REFERENCE_DISTANCE, SOURCE_MAXIMUM_DISTANCE, 1.0F);
        }
    }

    /** Resolves a combatant-specific pain or deterministic death sound and plays it in world space. */
    private void playCombatantSound(int thingIndex, DoomCombatState state, boolean death) {
        DoomCombatantState combatant = state.combatant(thingIndex).orElse(null);
        if (combatant == null) {
            return;
        }
        DoomCombatPresentationRules.Combatant actorRules = rules.combatant(combatant.actorId());
        if (actorRules == null) {
            return;
        }
        String sound = death ? deathSound(actorRules.deathSounds(), thingIndex) : actorRules.painSound();
        AudioSource source = sources.get(sound);
        source.setPosition(new Vector3f(
                combatant.x(),
                combatant.floorHeight() + combatant.height() * 0.5F,
                combatant.z()));
        restart(source);
    }

    /** Selects a stable variant without requiring mutable random state in presentation. */
    private static String deathSound(List<String> sounds, int thingIndex) {
        return sounds.get(Math.floorMod(thingIndex, sounds.size()));
    }

    /** Restarts one named listener-relative effect. */
    private void play(String sound) {
        restart(sources.get(sound));
    }

    /** Rewinds before play so rapid consecutive events retrigger predictably. */
    private static void restart(AudioSource source) {
        source.rewind();
        source.play();
    }
}
