/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.jscene3d.audio.AudioCategory;
import io.github.glynch.jscene3d.game.presentation.LocalSound;
import io.github.glynch.jscene3d.game.presentation.OverlayRegistration;
import io.github.glynch.jscene3d.game.presentation.PcmAudioResource;
import io.github.glynch.jscene3d.game.presentation.PositionalSound;
import io.github.glynch.jscene3d.game.presentation.PositionalSoundAttenuation;
import io.github.glynch.jscene3d.game.presentation.PresentationWorldModule;
import io.github.glynch.jscene3d.render.Overlay;
import io.github.glynch.jscene3d.render.Renderer;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3fc;

/** Records presentation activity without requiring OpenGL or an OpenAL playback device. */
final class TestPresentationWorldModule implements PresentationWorldModule {
    private final List<Overlay> overlays = new ArrayList<>();
    private int restarts;
    private int positionalRestarts;
    private int positionalSoundsCreated;
    private int positionalSoundsClosed;
    private boolean soundClosed;
    private boolean positionalSoundClosed;
    private final List<PositionalSoundAttenuation> positionalAttenuations = new ArrayList<>();

    @Override
    public OverlayRegistration registerOverlay(Overlay registered) {
        overlays.add(registered);
        return () -> overlays.remove(registered);
    }

    @Override
    public LocalSound createLocalSound(PcmAudioResource audio, AudioCategory category) {
        return new LocalSound() {
            @Override
            public void restart() {
                restarts++;
            }

            @Override
            public void close() {
                soundClosed = true;
            }
        };
    }

    @Override
    public PositionalSound createPositionalSound(
            PcmAudioResource audio, AudioCategory category, PositionalSoundAttenuation attenuation) {
        positionalAttenuations.add(attenuation);
        positionalSoundsCreated++;
        return new PositionalSound() {
            private boolean closed;

            @Override
            public void restart(Vector3fc position) {
                positionalRestarts++;
            }

            @Override
            public void close() {
                if (closed) {
                    return;
                }
                closed = true;
                positionalSoundsClosed++;
                positionalSoundClosed = true;
            }
        };
    }

    @Override
    public void setListenerTransform(Vector3fc position, Vector3fc forward, Vector3fc up) {
        // The headless host has no active audio listener.
    }

    @Override
    public void renderOverlays(Renderer renderer) {
        throw new AssertionError("headless integration tests do not render");
    }

    @Override
    public void close() {
        overlays.clear();
    }

    /** Returns the number of currently registered overlays. */
    int overlayCount() {
        return overlays.size();
    }

    /** Returns the number of accepted local-sound restart requests. */
    int restarts() {
        return restarts;
    }

    /** Returns the number of accepted world-positioned sound restart requests. */
    int positionalRestarts() {
        return positionalRestarts;
    }

    /** Returns the number of independently created world-positioned sound handles. */
    int positionalSoundsCreated() {
        return positionalSoundsCreated;
    }

    /** Returns the number of independently closed world-positioned sound handles. */
    int positionalSoundsClosed() {
        return positionalSoundsClosed;
    }

    /** Returns whether the component-owned sound handle was closed. */
    boolean soundClosed() {
        return soundClosed;
    }

    /** Returns whether at least one component-owned positional sound handle was closed. */
    boolean positionalSoundClosed() {
        return positionalSoundClosed;
    }

    /** Returns every attenuation supplied for a positional sound in creation order. */
    List<PositionalSoundAttenuation> positionalAttenuations() {
        return List.copyOf(positionalAttenuations);
    }
}
