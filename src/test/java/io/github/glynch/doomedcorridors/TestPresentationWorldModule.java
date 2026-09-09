/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.jscene3d.audio.AudioCategory;
import io.github.glynch.jscene3d.game.presentation.LocalSound;
import io.github.glynch.jscene3d.game.presentation.OverlayRegistration;
import io.github.glynch.jscene3d.game.presentation.PcmAudioResource;
import io.github.glynch.jscene3d.game.presentation.PresentationWorldModule;
import io.github.glynch.jscene3d.render.Overlay;
import io.github.glynch.jscene3d.render.Renderer;
import java.util.ArrayList;
import java.util.List;

/** Records presentation activity without requiring OpenGL or an OpenAL playback device. */
final class TestPresentationWorldModule implements PresentationWorldModule {
    private final List<Overlay> overlays = new ArrayList<>();
    private int restarts;
    private boolean soundClosed;

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

    /** Returns whether the component-owned sound handle was closed. */
    boolean soundClosed() {
        return soundClosed;
    }
}
