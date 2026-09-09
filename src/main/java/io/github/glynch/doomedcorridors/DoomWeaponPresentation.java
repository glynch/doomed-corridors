/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsRuntimeTypes;
import io.github.glynch.jscene3d.audio.AudioCategory;
import io.github.glynch.jscene3d.game.presentation.LocalSound;
import io.github.glynch.jscene3d.game.presentation.OverlayRegistration;
import io.github.glynch.jscene3d.game.presentation.PcmAudioResource;
import io.github.glynch.jscene3d.game.presentation.PresentationWorldModule;
import io.github.glynch.jscene3d.math.Color;
import io.github.glynch.jscene3d.project.runtime.FrameUpdateContext;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpointBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentUpdateCallbacks;
import io.github.glynch.jscene3d.project.spatial3d.Texture3dResource;
import io.github.glynch.jscene3d.render.Overlay;
import io.github.glynch.jscene3d.render.OverlayCanvas;
import io.github.glynch.jscene3d.render.OverlayImage;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Presents one descriptor-configured first-person weapon through a screen overlay and local sound. */
final class DoomWeaponPresentation
        implements Overlay, ComponentEndpointBinder, ComponentUpdateCallbacks, AutoCloseable {
    private static final float REFERENCE_WIDTH = 320.0F;
    private static final float REFERENCE_HEIGHT = 200.0F;

    private final OverlayImage readyFrame;
    private final List<OverlayImage> fireFrames;
    private final Duration frameDuration;
    private final LocalSound fireSound;
    private final OverlayRegistration overlayRegistration;
    private Duration frameElapsed = Duration.ZERO;
    private int fireFrameIndex = -1;
    private boolean closed;

    /** Converts shared texture resources and acquires component-owned host presentation handles. */
    DoomWeaponPresentation(
            PresentationWorldModule presentation,
            Texture3dResource readyFrame,
            List<Texture3dResource> fireFrames,
            PcmAudioResource fireSound,
            Duration frameDuration) {
        PresentationWorldModule validPresentation = Objects.requireNonNull(presentation, "presentation");
        this.readyFrame = Objects.requireNonNull(readyFrame, "readyFrame").overlayImage();
        this.fireFrames = List.copyOf(Objects.requireNonNull(fireFrames, "fireFrames")).stream()
                .map(Texture3dResource::overlayImage)
                .toList();
        if (this.fireFrames.isEmpty()) {
            throw new IllegalArgumentException("fireFrames must not be empty");
        }
        this.frameDuration = Objects.requireNonNull(frameDuration, "frameDuration");
        if (frameDuration.isZero() || frameDuration.isNegative()) {
            throw new IllegalArgumentException("frameDuration must be positive");
        }
        this.fireSound = validPresentation.createLocalSound(
                Objects.requireNonNull(fireSound, "fireSound"), AudioCategory.EFFECTS);
        try {
            overlayRegistration = validPresentation.registerOverlay(this);
        } catch (RuntimeException failure) {
            this.fireSound.close();
            throw failure;
        }
    }

    /** Binds the descriptor-declared successful-shot receiver. */
    @Override
    public void bindEndpoints(ComponentEndpoints endpoints) {
        Objects.requireNonNull(endpoints, "endpoints")
                .action(DoomedCorridorsRuntimeTypes.RECEIVE_WEAPON_FIRED_ACTION, this::receiveFired);
    }

    /** Advances the active firing sequence using real presentation time. */
    @Override
    public void onFrameUpdate(FrameUpdateContext update) {
        Objects.requireNonNull(update, "update");
        if (fireFrameIndex < 0) {
            return;
        }
        frameElapsed = frameElapsed.plus(update.elapsed());
        while (fireFrameIndex >= 0 && frameElapsed.compareTo(frameDuration) >= 0) {
            frameElapsed = frameElapsed.minus(frameDuration);
            fireFrameIndex++;
            if (fireFrameIndex == fireFrames.size()) {
                fireFrameIndex = -1;
                frameElapsed = Duration.ZERO;
            }
        }
    }

    /** Draws the current frame centered against the viewport's lower edge. */
    @Override
    public void paint(OverlayCanvas canvas, int width, int height) {
        Objects.requireNonNull(canvas, "canvas");
        OverlayImage frame = currentFrame();
        float scale = Math.min(width / REFERENCE_WIDTH, height / REFERENCE_HEIGHT);
        scale = Math.max(1.0F, scale);
        float frameWidth = frame.width() * scale;
        float frameHeight = frame.height() * scale;
        canvas.image(
                frame.fullRegion(),
                (width - frameWidth) * 0.5F,
                height - frameHeight,
                frameWidth,
                frameHeight,
                Color.WHITE,
                1.0F);
    }

    /** Returns the image currently selected by the firing animation. */
    OverlayImage currentFrame() {
        return fireFrameIndex < 0 ? readyFrame : fireFrames.get(fireFrameIndex);
    }

    /** Returns whether the firing frame sequence is currently active. */
    boolean isFiring() {
        return fireFrameIndex >= 0;
    }

    /** Releases the component-owned overlay and local audio handles. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        overlayRegistration.close();
        fireSound.close();
    }

    /** Restarts both the authored frame sequence and firing sound. */
    private void receiveFired() {
        if (closed) {
            throw new IllegalStateException("weapon presentation is closed");
        }
        fireFrameIndex = 0;
        frameElapsed = Duration.ZERO;
        fireSound.restart();
    }
}
