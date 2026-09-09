/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsRuntimeTypes;
import io.github.glynch.jscene3d.audio.AudioCategory;
import io.github.glynch.jscene3d.game.presentation.PcmAudioResource;
import io.github.glynch.jscene3d.game.presentation.PositionalSound;
import io.github.glynch.jscene3d.game.presentation.PositionalSoundAttenuation;
import io.github.glynch.jscene3d.game.presentation.PresentationWorldModule;
import io.github.glynch.jscene3d.project.runtime.Entity;
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

/** Presents descriptor-connected non-fatal pain and terminal death reactions for one combatant. */
final class DoomCombatantPresentation
        implements ComponentReferenceBinder, ComponentEndpointBinder, ComponentUpdateCallbacks, AutoCloseable {
    private final PositionalSound painSound;
    private final List<PositionalSound> deathSounds;
    private final Duration frameDuration;
    private final RandomGenerator random;
    private final Vector3f soundPosition = new Vector3f();
    private Transform3d transform;
    private BillboardRenderer3d idleFrame;
    private List<BillboardRenderer3d> painFrames = List.of();
    private List<BillboardRenderer3d> deathFrames = List.of();
    private List<BillboardRenderer3d> activeFrames = List.of();
    private Duration frameElapsed = Duration.ZERO;
    private int frameIndex = -1;
    private boolean dead;
    private boolean closed;

    /** Acquires independent positional playback handles for the authored reaction sounds. */
    DoomCombatantPresentation(
            Entity owner,
            PresentationWorldModule presentation,
            PcmAudioResource painSound,
            List<PcmAudioResource> deathSounds,
            Duration frameDuration,
            PositionalSoundAttenuation attenuation) {
        Entity validOwner = Objects.requireNonNull(owner, "owner");
        PresentationWorldModule validPresentation = Objects.requireNonNull(presentation, "presentation");
        this.frameDuration = requirePositive(frameDuration);
        this.painSound = validPresentation.createPositionalSound(
                Objects.requireNonNull(painSound, "painSound"), AudioCategory.EFFECTS, attenuation);
        try {
            this.deathSounds = createSounds(validPresentation, deathSounds, attenuation);
        } catch (RuntimeException failure) {
            this.painSound.close();
            throw failure;
        }
        random = new Random(validOwner.authoredId().value().getMostSignificantBits()
                ^ validOwner.authoredId().value().getLeastSignificantBits());
    }

    @Override
    public void bindReferences(ComponentReferenceResolver references) {
        ComponentReferenceResolver validReferences = Objects.requireNonNull(references, "references");
        transform =
                validReferences.component(DoomedCorridorsRuntimeTypes.COMBATANT_TRANSFORM_PROPERTY, Transform3d.class);
        idleFrame = validReferences.component(
                DoomedCorridorsRuntimeTypes.COMBATANT_IDLE_FRAME_PROPERTY, BillboardRenderer3d.class);
        painFrames = List.copyOf(validReferences.components(
                DoomedCorridorsRuntimeTypes.COMBATANT_PAIN_FRAMES_PROPERTY, BillboardRenderer3d.class));
        deathFrames = List.copyOf(validReferences.components(
                DoomedCorridorsRuntimeTypes.COMBATANT_DEATH_FRAMES_PROPERTY, BillboardRenderer3d.class));
        if (painFrames.isEmpty() || deathFrames.isEmpty()) {
            throw new IllegalArgumentException("combatant reaction frame sequences must not be empty");
        }
        showIdle();
    }

    @Override
    public void bindEndpoints(ComponentEndpoints endpoints) {
        ComponentEndpoints validEndpoints = Objects.requireNonNull(endpoints, "endpoints");
        validEndpoints.action(DoomedCorridorsRuntimeTypes.RECEIVE_COMBATANT_HURT_ACTION, this::receiveHurt);
        validEndpoints.action(DoomedCorridorsRuntimeTypes.RECEIVE_COMBATANT_DIED_ACTION, this::receiveDied);
    }

    @Override
    public void onFrameUpdate(FrameUpdateContext update) {
        Objects.requireNonNull(update, "update");
        if (frameIndex < 0 || dead && frameIndex == activeFrames.size() - 1) {
            return;
        }
        frameElapsed = frameElapsed.plus(update.elapsed());
        while (frameIndex >= 0 && frameElapsed.compareTo(frameDuration) >= 0) {
            frameElapsed = frameElapsed.minus(frameDuration);
            advanceFrame();
        }
    }

    /** Returns the currently visible reaction frame, or the idle frame outside a reaction. */
    BillboardRenderer3d currentFrame() {
        return frameIndex < 0 ? requiredIdleFrame() : activeFrames.get(frameIndex);
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
        painSound.close();
        deathSounds.forEach(PositionalSound::close);
    }

    /** Starts one non-fatal visual and positional-audio reaction. */
    private void receiveHurt() {
        requireOpen();
        if (!dead) {
            start(painFrames);
            painSound.restart(worldPosition());
        }
    }

    /** Starts the terminal visual sequence and one deterministic death-sound variant. */
    private void receiveDied() {
        requireOpen();
        if (!dead) {
            dead = true;
            start(deathFrames);
            deathSounds.get(random.nextInt(deathSounds.size())).restart(worldPosition());
        }
    }

    /** Replaces any current reaction with the first frame of the supplied sequence. */
    private void start(List<BillboardRenderer3d> frames) {
        hideAllFrames();
        activeFrames = frames;
        frameIndex = 0;
        frameElapsed = Duration.ZERO;
        activeFrames.getFirst().setVisible(true);
    }

    /** Advances one reaction, returning non-fatal reactions to idle and retaining the final death frame. */
    private void advanceFrame() {
        activeFrames.get(frameIndex).setVisible(false);
        if (frameIndex + 1 < activeFrames.size()) {
            frameIndex++;
            activeFrames.get(frameIndex).setVisible(true);
        } else if (dead) {
            activeFrames.get(frameIndex).setVisible(true);
            frameElapsed = Duration.ZERO;
        } else {
            showIdle();
        }
    }

    /** Restores the idle frame and clears transient animation state. */
    private void showIdle() {
        hideAllFrames();
        requiredIdleFrame().setVisible(true);
        activeFrames = List.of();
        frameIndex = -1;
        frameElapsed = Duration.ZERO;
    }

    /** Hides every explicitly targeted billboard before making one state visible. */
    private void hideAllFrames() {
        if (idleFrame != null) {
            idleFrame.setVisible(false);
        }
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

    /** Creates all required death-sound variants with compensation on partial failure. */
    private static List<PositionalSound> createSounds(
            PresentationWorldModule presentation,
            List<PcmAudioResource> resources,
            PositionalSoundAttenuation attenuation) {
        List<PcmAudioResource> validResources = List.copyOf(Objects.requireNonNull(resources, "deathSounds"));
        if (validResources.isEmpty()) {
            throw new IllegalArgumentException("deathSounds must not be empty");
        }
        List<PositionalSound> sounds = new ArrayList<>(validResources.size());
        try {
            validResources.forEach(resource ->
                    sounds.add(presentation.createPositionalSound(resource, AudioCategory.EFFECTS, attenuation)));
            return List.copyOf(sounds);
        } catch (RuntimeException failure) {
            sounds.forEach(PositionalSound::close);
            throw failure;
        }
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
}
