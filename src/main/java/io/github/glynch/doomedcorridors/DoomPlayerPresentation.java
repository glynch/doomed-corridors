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
import io.github.glynch.jscene3d.render.Overlay;
import io.github.glynch.jscene3d.render.OverlayCanvas;
import java.time.Duration;
import java.util.Objects;

/** Presents descriptor-connected player pain and terminal death through local audio and screen color. */
final class DoomPlayerPresentation
        implements Overlay, ComponentEndpointBinder, ComponentUpdateCallbacks, AutoCloseable {
    private final Flash painFlash;
    private final Flash deathFlash;
    private final float terminalShadeOpacity;
    private final LocalSound painSound;
    private final LocalSound deathSound;
    private final OverlayRegistration overlayRegistration;
    private Duration painFlashRemaining = Duration.ZERO;
    private Duration deathFlashRemaining = Duration.ZERO;
    private boolean dead;
    private boolean closed;

    /** Acquires the two local sounds and one overlay using descriptor-supplied presentation values. */
    DoomPlayerPresentation(
            PresentationWorldModule presentation,
            PcmAudioResource painSound,
            PcmAudioResource deathSound,
            Flash painFlash,
            Flash deathFlash,
            float terminalShadeOpacity) {
        PresentationWorldModule validPresentation = Objects.requireNonNull(presentation, "presentation");
        this.painFlash = Objects.requireNonNull(painFlash, "painFlash");
        this.deathFlash = Objects.requireNonNull(deathFlash, "deathFlash");
        this.terminalShadeOpacity = requireUnitInterval(terminalShadeOpacity, "terminalShadeOpacity");
        Handles handles = Handles.acquire(
                validPresentation,
                Objects.requireNonNull(painSound, "painSound"),
                Objects.requireNonNull(deathSound, "deathSound"),
                this);
        this.painSound = handles.painSound();
        this.deathSound = handles.deathSound();
        overlayRegistration = handles.overlayRegistration();
    }

    /** Binds the descriptor-declared player-state reactions. */
    @Override
    public void bindEndpoints(ComponentEndpoints endpoints) {
        ComponentEndpoints validEndpoints = Objects.requireNonNull(endpoints, "endpoints");
        validEndpoints.action(DoomedCorridorsRuntimeTypes.RECEIVE_PLAYER_HURT_ACTION, this::receiveHurt);
        validEndpoints.action(DoomedCorridorsRuntimeTypes.RECEIVE_PLAYER_DIED_ACTION, this::receiveDied);
    }

    /** Advances the currently active finite red response. */
    @Override
    public void onFrameUpdate(FrameUpdateContext update) {
        Objects.requireNonNull(update, "update");
        if (dead) {
            deathFlashRemaining = subtractFloorZero(deathFlashRemaining, update.elapsed());
        } else {
            painFlashRemaining = subtractFloorZero(painFlashRemaining, update.elapsed());
        }
    }

    /** Draws the subtle terminal shade below any active red response. */
    @Override
    public void paint(OverlayCanvas canvas, int width, int height) {
        Objects.requireNonNull(canvas, "canvas");
        float shadeOpacity = currentShadeOpacity();
        if (shadeOpacity > 0.0F) {
            canvas.rectangle(0.0F, 0.0F, width, height, Color.BLACK, shadeOpacity);
        }
        float redOpacity = currentRedOpacity();
        if (redOpacity > 0.0F) {
            canvas.rectangle(0.0F, 0.0F, width, height, Color.RED, redOpacity);
        }
    }

    /** Returns whether terminal player presentation has been received. */
    boolean isDead() {
        return dead;
    }

    /** Returns the current red-response opacity. */
    float currentRedOpacity() {
        if (dead) {
            return opacity(deathFlashRemaining, deathFlash);
        }
        return opacity(painFlashRemaining, painFlash);
    }

    /** Returns the current terminal dark-shade opacity. */
    float currentShadeOpacity() {
        return dead ? terminalShadeOpacity : 0.0F;
    }

    /** Releases every component-owned presentation handle exactly once. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        overlayRegistration.close();
        deathSound.close();
        painSound.close();
    }

    /** Starts one authored non-fatal response unless death is already terminal. */
    private void receiveHurt() {
        requireOpen();
        if (!dead) {
            painFlashRemaining = painFlash.duration();
            painSound.restart();
        }
    }

    /** Replaces any transient response with the authored terminal presentation. */
    private void receiveDied() {
        requireOpen();
        if (!dead) {
            dead = true;
            painFlashRemaining = Duration.ZERO;
            deathFlashRemaining = deathFlash.duration();
            deathSound.restart();
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("player presentation is closed");
        }
    }

    private static Duration subtractFloorZero(Duration value, Duration elapsed) {
        if (elapsed.compareTo(value) >= 0) {
            return Duration.ZERO;
        }
        return value.minus(elapsed);
    }

    private static float opacity(Duration remaining, Flash flash) {
        if (remaining.isZero()) {
            return 0.0F;
        }
        double fraction = (double) remaining.toNanos() / flash.duration().toNanos();
        return flash.opacity() * (float) Math.clamp(fraction, 0.0, 1.0);
    }

    private static float requireUnitInterval(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0F || value > 1.0F) {
            throw new IllegalArgumentException(name + " must be finite and in [0, 1]");
        }
        return value;
    }

    /** One finite red response with its maximum opacity. */
    record Flash(Duration duration, float opacity) {
        Flash {
            requirePositive(duration, "duration");
            requireOpacity(opacity, "opacity");
        }

        /** Rejects null, zero, and negative flash durations. */
        private static void requirePositive(Duration value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
        }

        /** Rejects non-finite or out-of-range flash opacity. */
        private static void requireOpacity(float value, String name) {
            if (!Float.isFinite(value) || value <= 0.0F || value > 1.0F) {
                throw new IllegalArgumentException(name + " must be finite and in (0, 1]");
            }
        }
    }

    /** Fully acquired presentation handles, with compensation kept beside acquisition. */
    private record Handles(LocalSound painSound, LocalSound deathSound, OverlayRegistration overlayRegistration) {
        private static Handles acquire(
                PresentationWorldModule presentation,
                PcmAudioResource painAudio,
                PcmAudioResource deathAudio,
                Overlay overlay) {
            LocalSound pain = presentation.createLocalSound(painAudio, AudioCategory.EFFECTS);
            try {
                return acquireDeathAndOverlay(presentation, pain, deathAudio, overlay);
            } catch (RuntimeException failure) {
                pain.close();
                throw failure;
            }
        }

        private static Handles acquireDeathAndOverlay(
                PresentationWorldModule presentation, LocalSound pain, PcmAudioResource deathAudio, Overlay overlay) {
            LocalSound death = presentation.createLocalSound(deathAudio, AudioCategory.EFFECTS);
            try {
                return new Handles(pain, death, presentation.registerOverlay(overlay));
            } catch (RuntimeException failure) {
                death.close();
                throw failure;
            }
        }
    }
}
