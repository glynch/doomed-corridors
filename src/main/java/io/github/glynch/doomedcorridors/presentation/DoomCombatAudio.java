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
    private final AudioSource weaponSource;
    private final Map<String, AudioSource> playerSources;
    private final Map<String, AudioSource> worldSources;

    /** Retains a fully initialized engine and sound-source index. */
    private DoomCombatAudio(
            DoomCombatPresentationRules rules,
            AudioEngine engine,
            AudioSource weaponSource,
            Map<String, AudioSource> playerSources,
            Map<String, AudioSource> worldSources) {
        this.rules = rules;
        this.engine = engine;
        this.weaponSource = weaponSource;
        this.playerSources = playerSources;
        this.worldSources = worldSources;
    }

    /** Creates one buffered source for every imported WAD effect. */
    public static DoomCombatAudio create(DoomCombatAssets assets) {
        DoomCombatAssets validAssets = Objects.requireNonNull(assets, "assets");
        AudioEngine engine = AudioEngine.create();
        try {
            Map<String, AudioClip> clips = new LinkedHashMap<>();
            for (Map.Entry<String, PcmAudio> entry : validAssets.sounds().entrySet()) {
                clips.put(entry.getKey(), engine.createClip(entry.getValue()));
            }
            AudioSource weaponSource = source(
                    engine, clips, validAssets.rules().weapon().fireSound(), true);
            Map<String, AudioSource> playerSources = new LinkedHashMap<>();
            addSource(engine, clips, playerSources, validAssets.rules().player().painSound(), true);
            addSource(engine, clips, playerSources, validAssets.rules().player().deathSound(), true);
            Map<String, AudioSource> worldSources = new LinkedHashMap<>();
            for (String name : validAssets.rules().soundLumps()) {
                addSource(engine, clips, worldSources, name, false);
            }
            return new DoomCombatAudio(
                    validAssets.rules(),
                    engine,
                    weaponSource,
                    Map.copyOf(playerSources),
                    Map.copyOf(worldSources));
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
        boolean playerKilled = validUpdate.events().stream()
                .anyMatch(event -> event.type() == DoomCombatEvent.Type.PLAYER_KILLED);
        for (DoomCombatEvent event : validUpdate.events()) {
            switch (event) {
                case DoomCombatEvent weaponFired
                        when weaponFired.type() == DoomCombatEvent.Type.WEAPON_FIRED ->
                    restart(weaponSource);
                case DoomCombatEvent combatantAlerted
                        when combatantAlerted.type() == DoomCombatEvent.Type.COMBATANT_ALERTED ->
                    playCombatantSound(
                            combatantAlerted.thingIndex(),
                            validUpdate.state(),
                            CombatantSound.SIGHT);
                case DoomCombatEvent combatantAttacked
                        when combatantAttacked.type() == DoomCombatEvent.Type.COMBATANT_ATTACKED ->
                    playCombatantSound(
                            combatantAttacked.thingIndex(),
                            validUpdate.state(),
                            CombatantSound.ATTACK);
                case DoomCombatEvent combatantDamaged
                        when combatantDamaged.type() == DoomCombatEvent.Type.COMBATANT_DAMAGED
                                && !killed.contains(combatantDamaged.thingIndex()) ->
                    playCombatantSound(
                            combatantDamaged.thingIndex(),
                            validUpdate.state(),
                            CombatantSound.PAIN);
                case DoomCombatEvent combatantKilled
                        when combatantKilled.type() == DoomCombatEvent.Type.COMBATANT_KILLED ->
                    playCombatantSound(
                            combatantKilled.thingIndex(),
                            validUpdate.state(),
                            CombatantSound.DEATH);
                case DoomCombatEvent playerDamaged
                        when playerDamaged.type() == DoomCombatEvent.Type.PLAYER_DAMAGED
                                && !playerKilled ->
                    playPlayerSound(rules.player().painSound());
                case DoomCombatEvent playerDeath
                        when playerDeath.type() == DoomCombatEvent.Type.PLAYER_KILLED ->
                    playPlayerSound(rules.player().deathSound());
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

    /** Adds one named configured source unless that role already contains the sound. */
    private static void addSource(
            AudioEngine engine,
            Map<String, AudioClip> clips,
            Map<String, AudioSource> sources,
            String name,
            boolean relative) {
        sources.computeIfAbsent(name, ignored -> source(engine, clips, name, relative));
    }

    /** Creates one configured source for a required decoded sound clip. */
    private static AudioSource source(
            AudioEngine engine,
            Map<String, AudioClip> clips,
            String name,
            boolean relative) {
        AudioClip clip = Objects.requireNonNull(clips.get(name), "clip " + name);
        AudioSource source = engine.createSource(clip, AudioCategory.EFFECTS);
        configureSource(source, relative);
        return source;
    }

    /** Resolves one combatant event sound and plays it in world space. */
    private void playCombatantSound(
            int thingIndex,
            DoomCombatState state,
            CombatantSound eventSound) {
        DoomCombatantState combatant = state.combatant(thingIndex).orElse(null);
        if (combatant == null) {
            return;
        }
        DoomCombatPresentationRules.Combatant actorRules = rules.combatant(combatant.actorId());
        if (actorRules == null) {
            return;
        }
        String sound = combatantSound(actorRules.sounds(), eventSound, thingIndex);
        AudioSource source = requiredSource(worldSources, sound);
        source.setPosition(new Vector3f(
                combatant.x(),
                combatant.floorHeight() + combatant.height() * 0.5F,
                combatant.z()));
        restart(source);
    }

    /** Resolves an event role to an exact sound, including deterministic variants. */
    private static String combatantSound(
            DoomCombatPresentationRules.CombatantSounds sounds,
            CombatantSound eventSound,
            int thingIndex) {
        return switch (eventSound) {
            case SIGHT -> variant(sounds.sightSounds(), thingIndex);
            case ATTACK -> sounds.attackSound();
            case PAIN -> sounds.painSound();
            case DEATH -> variant(sounds.deathSounds(), thingIndex);
        };
    }

    /** Selects a stable variant without requiring mutable random state in presentation. */
    private static String variant(List<String> sounds, int thingIndex) {
        return sounds.get(Math.floorMod(thingIndex, sounds.size()));
    }

    /** Restarts one named listener-relative player effect. */
    private void playPlayerSound(String sound) {
        restart(requiredSource(playerSources, sound));
    }

    /** Returns one required source from a role-specific source index. */
    private static AudioSource requiredSource(
            Map<String, AudioSource> sources, String sound) {
        return Objects.requireNonNull(sources.get(sound), "sound source " + sound);
    }

    /** Rewinds before play so rapid consecutive events retrigger predictably. */
    private static void restart(AudioSource source) {
        source.rewind();
        source.play();
    }

    /** Role used to select one combatant presentation sound. */
    private enum CombatantSound {
        SIGHT,
        ATTACK,
        PAIN,
        DEATH
    }
}
