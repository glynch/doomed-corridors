/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsDescriptors;
import io.github.glynch.jscene3d.audio.AudioCategory;
import io.github.glynch.jscene3d.game.presentation.PcmAudioResource;
import io.github.glynch.jscene3d.game.presentation.PositionalSound;
import io.github.glynch.jscene3d.game.presentation.PositionalSoundAttenuation;
import io.github.glynch.jscene3d.game.presentation.PresentationWorldModule;
import io.github.glynch.jscene3d.project.runtime.FrameUpdateContext;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpointBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceResolver;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentUpdateCallbacks;
import io.github.glynch.jscene3d.project.spatial3d.BillboardRenderer3d;
import io.github.glynch.jscene3d.project.spatial3d.Transform3d;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.joml.Vector3f;

/** Presents descriptor-connected movement, attack, pain, and death states for one combatant. */
final class DoomCombatantPresentation
        implements ComponentReferenceBinder, ComponentEndpointBinder, ComponentUpdateCallbacks, AutoCloseable {
    private final List<PositionalSound> sightSounds;
    private final PositionalSound attackSound;
    private final PositionalSound painSound;
    private final List<PositionalSound> deathSounds;
    private final Duration frameDuration;
    private final RandomGenerator random;
    private final Vector3f soundPosition = new Vector3f();
    private Transform3d transform;
    private BillboardRenderer3d idleFrame;
    private List<BillboardRenderer3d> walkFrames = List.of();
    private List<BillboardRenderer3d> attackFrames = List.of();
    private List<BillboardRenderer3d> painFrames = List.of();
    private List<BillboardRenderer3d> deathFrames = List.of();
    private List<BillboardRenderer3d> activeFrames = List.of();
    private Duration frameElapsed = Duration.ZERO;
    private int frameIndex = -1;
    private Animation animation = Animation.IDLE;
    private boolean moving;
    private boolean dead;
    private boolean closed;

    /** Acquires independent positional playback handles for every authored combatant sound role. */
    DoomCombatantPresentation(
            long randomSeed,
            PresentationWorldModule presentation,
            AudioResources audio,
            Duration frameDuration,
            PositionalSoundAttenuation attenuation) {
        PresentationWorldModule validPresentation = Objects.requireNonNull(presentation, "presentation");
        this.frameDuration = requirePositive(frameDuration);
        CombatantSounds sounds =
                CombatantSounds.create(validPresentation, Objects.requireNonNull(audio, "audio"), attenuation);
        this.sightSounds = sounds.sight();
        this.attackSound = sounds.attack();
        this.painSound = sounds.pain();
        this.deathSounds = sounds.death();
        random = new Random(randomSeed);
    }

    @Override
    public void bindReferences(ComponentReferenceResolver references) {
        ComponentReferenceResolver validReferences = Objects.requireNonNull(references, "references");
        transform =
                validReferences.component(DoomedCorridorsDescriptors.COMBATANT_TRANSFORM_PROPERTY, Transform3d.class);
        idleFrame = validReferences.component(
                DoomedCorridorsDescriptors.COMBATANT_IDLE_FRAME_PROPERTY, BillboardRenderer3d.class);
        walkFrames = List.copyOf(validReferences.components(
                DoomedCorridorsDescriptors.COMBATANT_WALK_FRAMES_PROPERTY, BillboardRenderer3d.class));
        attackFrames = List.copyOf(validReferences.components(
                DoomedCorridorsDescriptors.COMBATANT_ATTACK_FRAMES_PROPERTY, BillboardRenderer3d.class));
        painFrames = List.copyOf(validReferences.components(
                DoomedCorridorsDescriptors.COMBATANT_PAIN_FRAMES_PROPERTY, BillboardRenderer3d.class));
        deathFrames = List.copyOf(validReferences.components(
                DoomedCorridorsDescriptors.COMBATANT_DEATH_FRAMES_PROPERTY, BillboardRenderer3d.class));
        if (walkFrames.isEmpty() || attackFrames.isEmpty() || painFrames.isEmpty() || deathFrames.isEmpty()) {
            throw new IllegalArgumentException("combatant animation frame sequences must not be empty");
        }
        showIdle();
    }

    @Override
    public void bindEndpoints(ComponentEndpoints endpoints) {
        ComponentEndpoints validEndpoints = Objects.requireNonNull(endpoints, "endpoints");
        validEndpoints.action(DoomedCorridorsDescriptors.RECEIVE_COMBATANT_ALERTED_ACTION, this::receiveAlerted);
        validEndpoints.action(
                DoomedCorridorsDescriptors.RECEIVE_COMBATANT_MOVEMENT_STARTED_ACTION, this::receiveMovementStarted);
        validEndpoints.action(
                DoomedCorridorsDescriptors.RECEIVE_COMBATANT_MOVEMENT_STOPPED_ACTION, this::receiveMovementStopped);
        validEndpoints.action(DoomedCorridorsDescriptors.RECEIVE_COMBATANT_ATTACKED_ACTION, this::receiveAttacked);
        validEndpoints.action(DoomedCorridorsDescriptors.RECEIVE_COMBATANT_HURT_ACTION, this::receiveHurt);
        validEndpoints.action(DoomedCorridorsDescriptors.RECEIVE_COMBATANT_DIED_ACTION, this::receiveDied);
    }

    @Override
    public void onFrameUpdate(FrameUpdateContext update) {
        Objects.requireNonNull(update, "update");
        if (animation == Animation.IDLE || animation == Animation.DEATH && frameIndex == activeFrames.size() - 1) {
            return;
        }
        frameElapsed = frameElapsed.plus(update.elapsed());
        while (animation != Animation.IDLE && frameElapsed.compareTo(frameDuration) >= 0) {
            frameElapsed = frameElapsed.minus(frameDuration);
            advanceFrame();
        }
    }

    /** Returns the currently visible animation frame, or the idle frame outside an animation. */
    BillboardRenderer3d currentFrame() {
        return animation == Animation.IDLE ? requiredIdleFrame() : activeFrames.get(frameIndex);
    }

    /** Returns whether the terminal death sequence has begun. */
    boolean isDead() {
        return dead;
    }

    /** Releases only the sound handles owned by this behavior component. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        sightSounds.forEach(PositionalSound::close);
        attackSound.close();
        painSound.close();
        deathSounds.forEach(PositionalSound::close);
    }

    /** Plays one deterministic sight-sound variant when behavior first acquires the player. */
    private void receiveAlerted() {
        requireOpen();
        if (!dead) {
            sightSounds.get(random.nextInt(sightSounds.size())).restart(worldPosition());
        }
    }

    /** Records movement and starts its looping animation when no higher-priority state is active. */
    private void receiveMovementStarted() {
        requireOpen();
        if (!dead) {
            moving = true;
            if (animation == Animation.IDLE) {
                start(Animation.WALK, walkFrames);
            }
        }
    }

    /** Records the movement stop and restores idle when walking is the visible state. */
    private void receiveMovementStopped() {
        requireOpen();
        moving = false;
        if (animation == Animation.WALK) {
            showIdle();
        }
    }

    /** Starts one attack sequence and positional sound unless pain or death has visual priority. */
    private void receiveAttacked() {
        requireOpen();
        if (!dead) {
            attackSound.restart(worldPosition());
            if (animation != Animation.PAIN) {
                start(Animation.ATTACK, attackFrames);
            }
        }
    }

    /** Starts one non-fatal visual and positional-audio reaction. */
    private void receiveHurt() {
        requireOpen();
        if (!dead) {
            start(Animation.PAIN, painFrames);
            painSound.restart(worldPosition());
        }
    }

    /** Starts the terminal visual sequence and one deterministic death-sound variant. */
    private void receiveDied() {
        requireOpen();
        if (!dead) {
            dead = true;
            moving = false;
            start(Animation.DEATH, deathFrames);
            deathSounds.get(random.nextInt(deathSounds.size())).restart(worldPosition());
        }
    }

    /** Replaces the current visible state with the first frame of one animation. */
    private void start(Animation nextAnimation, List<BillboardRenderer3d> frames) {
        hideAllFrames();
        animation = Objects.requireNonNull(nextAnimation, "nextAnimation");
        activeFrames = frames;
        frameIndex = 0;
        frameElapsed = Duration.ZERO;
        activeFrames.getFirst().setVisible(true);
    }

    /** Advances one animation according to its loop, transient, or terminal completion policy. */
    private void advanceFrame() {
        activeFrames.get(frameIndex).setVisible(false);
        if (frameIndex + 1 < activeFrames.size()) {
            frameIndex++;
            activeFrames.get(frameIndex).setVisible(true);
        } else {
            switch (animation) {
                case WALK -> {
                    frameIndex = 0;
                    activeFrames.getFirst().setVisible(true);
                }
                case ATTACK, PAIN -> showMovementOrIdle();
                case DEATH -> {
                    activeFrames.get(frameIndex).setVisible(true);
                    frameElapsed = Duration.ZERO;
                }
                case IDLE -> throw new IllegalStateException("idle presentation has no active frames");
            }
        }
    }

    /** Restores the latest behavior-driven base state after a transient animation finishes. */
    private void showMovementOrIdle() {
        if (moving) {
            start(Animation.WALK, walkFrames);
        } else {
            showIdle();
        }
    }

    /** Restores the idle frame and clears transient animation state. */
    private void showIdle() {
        hideAllFrames();
        requiredIdleFrame().setVisible(true);
        animation = Animation.IDLE;
        activeFrames = List.of();
        frameIndex = -1;
        frameElapsed = Duration.ZERO;
    }

    /** Hides every explicitly targeted billboard before making one state visible. */
    private void hideAllFrames() {
        if (idleFrame != null) {
            idleFrame.setVisible(false);
        }
        walkFrames.forEach(frame -> frame.setVisible(false));
        attackFrames.forEach(frame -> frame.setVisible(false));
        painFrames.forEach(frame -> frame.setVisible(false));
        deathFrames.forEach(frame -> frame.setVisible(false));
    }

    /** Copies the current transform translation into the reusable audio position. */
    private Vector3f worldPosition() {
        Transform3d current = transform;
        if (current == null) {
            throw new IllegalStateException("combatant transform has not been bound");
        }
        return current.worldMatrix().getTranslation(soundPosition);
    }

    /** Returns the explicitly targeted idle frame after reference binding. */
    private BillboardRenderer3d requiredIdleFrame() {
        BillboardRenderer3d current = idleFrame;
        if (current == null) {
            throw new IllegalStateException("combatant idle frame has not been bound");
        }
        return current;
    }

    /** Requires a positive frame duration. */
    private static Duration requirePositive(Duration value) {
        Duration duration = Objects.requireNonNull(value, "frameDuration");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("frameDuration must be positive");
        }
        return duration;
    }

    /** Rejects event handling after component cleanup. */
    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("combatant presentation is closed");
        }
    }

    /** Animation roles ordered by explicit event priority rather than component or hierarchy order. */
    private enum Animation {
        IDLE,
        WALK,
        ATTACK,
        PAIN,
        DEATH
    }

    /** Immutable resolved PCM resources grouped by their authored combatant sound role. */
    record AudioResources(
            List<PcmAudioResource> sight,
            PcmAudioResource attack,
            PcmAudioResource pain,
            List<PcmAudioResource> death) {
        AudioResources {
            sight = List.copyOf(Objects.requireNonNull(sight, "sight"));
            Objects.requireNonNull(attack, "attack");
            Objects.requireNonNull(pain, "pain");
            death = List.copyOf(Objects.requireNonNull(death, "death"));
        }
    }

    /** Independently owned positional handles acquired atomically for one presentation component. */
    private record CombatantSounds(
            List<PositionalSound> sight, PositionalSound attack, PositionalSound pain, List<PositionalSound> death) {
        private static CombatantSounds create(
                PresentationWorldModule presentation,
                AudioResources resources,
                PositionalSoundAttenuation attenuation) {
            List<PositionalSound> owned = new ArrayList<>();
            try {
                List<PositionalSound> sight =
                        createSounds(presentation, resources.sight(), attenuation, "sightSounds", owned);
                PositionalSound attack =
                        presentation.createPositionalSound(resources.attack(), AudioCategory.EFFECTS, attenuation);
                owned.add(attack);
                PositionalSound pain =
                        presentation.createPositionalSound(resources.pain(), AudioCategory.EFFECTS, attenuation);
                owned.add(pain);
                List<PositionalSound> death =
                        createSounds(presentation, resources.death(), attenuation, "deathSounds", owned);
                return new CombatantSounds(sight, attack, pain, death);
            } catch (RuntimeException failure) {
                owned.forEach(PositionalSound::close);
                throw failure;
            }
        }

        /** Creates one required sound list using caller-owned compensation on partial failure. */
        private static List<PositionalSound> createSounds(
                PresentationWorldModule presentation,
                List<PcmAudioResource> resources,
                PositionalSoundAttenuation attenuation,
                String name,
                List<PositionalSound> owned) {
            List<PcmAudioResource> validResources = List.copyOf(Objects.requireNonNull(resources, name));
            if (validResources.isEmpty()) {
                throw new IllegalArgumentException(name + " must not be empty");
            }
            List<PositionalSound> sounds = new ArrayList<>(validResources.size());
            for (PcmAudioResource resource : validResources) {
                PositionalSound sound =
                        presentation.createPositionalSound(resource, AudioCategory.EFFECTS, attenuation);
                sounds.add(sound);
                owned.add(sound);
            }
            return List.copyOf(sounds);
        }
    }
}
