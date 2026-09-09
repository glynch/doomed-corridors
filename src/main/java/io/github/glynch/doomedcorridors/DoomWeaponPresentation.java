/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsRuntimeTypes;
import io.github.glynch.jscene3d.audio.AudioCategory;
import io.github.glynch.jscene3d.game.presentation.LocalSound;
import io.github.glynch.jscene3d.game.presentation.OverlayImageResource;
import io.github.glynch.jscene3d.game.presentation.OverlayRegistration;
import io.github.glynch.jscene3d.game.presentation.PcmAudioResource;
import io.github.glynch.jscene3d.game.presentation.PresentationWorldModule;
import io.github.glynch.jscene3d.math.Color;
import io.github.glynch.jscene3d.project.runtime.FrameUpdateContext;
import io.github.glynch.jscene3d.project.runtime.RuntimePayload;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpointBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentUpdateCallbacks;
import io.github.glynch.jscene3d.render.Overlay;
import io.github.glynch.jscene3d.render.OverlayCanvas;
import io.github.glynch.jscene3d.render.OverlayImage;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.joml.Vector2f;

/** Presents one descriptor-configured first-person weapon through a screen overlay and local sound. */
final class DoomWeaponPresentation
        implements Overlay, ComponentEndpointBinder, ComponentUpdateCallbacks, AutoCloseable {
    private static final float REFERENCE_WIDTH = 320.0F;
    private static final float REFERENCE_HEIGHT = 200.0F;

    private final OverlayImage readyFrame;
    private final List<OverlayImage> fireFrames;
    private final Duration frameDuration;
    private final Duration hitIndicatorDuration;
    private final LocalSound fireSound;
    private final OverlayRegistration overlayRegistration;
    private Duration frameElapsed = Duration.ZERO;
    private Duration hitIndicatorRemaining = Duration.ZERO;
    private Optional<DoomWeaponHit> hitIndicator = Optional.empty();
    private int fireFrameIndex = -1;
    private boolean closed;

    /** Converts shared texture resources and acquires component-owned host presentation handles. */
    DoomWeaponPresentation(
            PresentationWorldModule presentation,
            OverlayImageResource readyFrame,
            List<OverlayImageResource> fireFrames,
            PcmAudioResource fireSound,
            Duration frameDuration,
            Duration hitIndicatorDuration) {
        PresentationWorldModule validPresentation = Objects.requireNonNull(presentation, "presentation");
        this.readyFrame = Objects.requireNonNull(readyFrame, "readyFrame").image();
        this.fireFrames = List.copyOf(Objects.requireNonNull(fireFrames, "fireFrames")).stream()
                .map(OverlayImageResource::image)
                .toList();
        if (this.fireFrames.isEmpty()) {
            throw new IllegalArgumentException("fireFrames must not be empty");
        }
        this.frameDuration = Objects.requireNonNull(frameDuration, "frameDuration");
        if (frameDuration.isZero() || frameDuration.isNegative()) {
            throw new IllegalArgumentException("frameDuration must be positive");
        }
        this.hitIndicatorDuration = Objects.requireNonNull(hitIndicatorDuration, "hitIndicatorDuration");
        if (hitIndicatorDuration.isZero() || hitIndicatorDuration.isNegative()) {
            throw new IllegalArgumentException("hitIndicatorDuration must be positive");
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
        ComponentEndpoints validEndpoints = Objects.requireNonNull(endpoints, "endpoints");
        validEndpoints.action(DoomedCorridorsRuntimeTypes.RECEIVE_WEAPON_FIRED_ACTION, this::receiveFired);
        validEndpoints.action(DoomedCorridorsRuntimeTypes.RECEIVE_WEAPON_HIT_ACTION, this::receiveHit);
    }

    /** Advances the active firing sequence using real presentation time. */
    @Override
    public void onFrameUpdate(FrameUpdateContext update) {
        Objects.requireNonNull(update, "update");
        if (fireFrameIndex >= 0) {
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
        hitIndicatorRemaining = subtractFloorZero(hitIndicatorRemaining, update.elapsed());
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
        if (isHitIndicatorVisible()) {
            paintHitIndicator(canvas, width, height, hitIndicator.orElseThrow());
        }
    }

    /** Returns the image currently selected by the firing animation. */
    OverlayImage currentFrame() {
        return fireFrameIndex < 0 ? readyFrame : fireFrames.get(fireFrameIndex);
    }

    /** Returns whether the firing frame sequence is currently active. */
    boolean isFiring() {
        return fireFrameIndex >= 0;
    }

    /** Returns whether the short successful-hit confirmation is currently visible. */
    boolean isHitIndicatorVisible() {
        return !hitIndicatorRemaining.isZero() && hitIndicator.isPresent();
    }

    /** Returns the current successful-hit marker position for deterministic presentation verification. */
    Vector2f hitIndicatorPosition(int width, int height) {
        if (!isHitIndicatorVisible()) {
            throw new IllegalStateException("hit indicator is not visible");
        }
        return hitIndicator.orElseThrow().project(width, height);
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

    /** Restarts the visual confirmation emitted only for a shot which removed target health. */
    private void receiveHit(RuntimePayload payload) {
        if (closed) {
            throw new IllegalStateException("weapon presentation is closed");
        }
        RuntimePayload validPayload = Objects.requireNonNull(payload, "payload");
        if (!validPayload.type().equals(DoomedCorridorsRuntimeTypes.WEAPON_HIT_PAYLOAD_TYPE)
                || !(validPayload.value() instanceof DoomWeaponHit weaponHit)) {
            throw new IllegalArgumentException("receive-hit requires the declared Doom weapon-hit payload");
        }
        hitIndicator = Optional.of(weaponHit);
        hitIndicatorRemaining = hitIndicatorDuration;
    }

    /** Draws a compact red X at the perspective-projected successful-hit location. */
    private static void paintHitIndicator(OverlayCanvas canvas, int width, int height, DoomWeaponHit hit) {
        Vector2f position = hit.project(width, height);
        float viewportExtent = Math.min(width, height);
        float arm = Math.max(6.0F, viewportExtent * 0.012F);
        float thickness = Math.max(2.0F, arm * 0.18F);
        canvas.line(position.x - arm, position.y - arm, position.x + arm, position.y + arm, thickness, Color.RED, 0.9F);
        canvas.line(position.x + arm, position.y - arm, position.x - arm, position.y + arm, thickness, Color.RED, 0.9F);
    }

    /** Subtracts elapsed presentation time without permitting a negative duration. */
    private static Duration subtractFloorZero(Duration remaining, Duration elapsed) {
        return remaining.compareTo(elapsed) <= 0 ? Duration.ZERO : remaining.minus(elapsed);
    }
}
