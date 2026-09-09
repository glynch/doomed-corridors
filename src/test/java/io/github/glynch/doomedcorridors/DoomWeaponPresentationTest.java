/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsRuntimeTypes;
import io.github.glynch.jscene3d.audio.AudioCategory;
import io.github.glynch.jscene3d.audio.PcmAudio;
import io.github.glynch.jscene3d.game.presentation.LocalSound;
import io.github.glynch.jscene3d.game.presentation.OverlayImageResource;
import io.github.glynch.jscene3d.game.presentation.OverlayRegistration;
import io.github.glynch.jscene3d.game.presentation.PcmAudioResource;
import io.github.glynch.jscene3d.game.presentation.PresentationWorldModule;
import io.github.glynch.jscene3d.project.component.EndpointId;
import io.github.glynch.jscene3d.project.runtime.FrameUpdateContext;
import io.github.glynch.jscene3d.project.runtime.RuntimeAction;
import io.github.glynch.jscene3d.project.runtime.RuntimePayload;
import io.github.glynch.jscene3d.project.runtime.RuntimePayloadAction;
import io.github.glynch.jscene3d.project.runtime.RuntimeSignal;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import io.github.glynch.jscene3d.render.Overlay;
import io.github.glynch.jscene3d.render.OverlayImage;
import io.github.glynch.jscene3d.render.Renderer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Exercises the descriptor-connected first-person weapon presentation without native host facilities. */
final class DoomWeaponPresentationTest {
    /** Restarts sound and advances the authored firing frames before returning to the ready frame. */
    @Test
    void presentsSuccessfulShotSequence() {
        RecordingPresentation presentation = new RecordingPresentation();
        RecordingEndpoints endpoints = new RecordingEndpoints();
        OverlayImageResource ready = image((byte) 1);
        OverlayImageResource first = image((byte) 2);
        OverlayImageResource second = image((byte) 3);
        PcmAudioResource sound = PcmAudioResource.owning(PcmAudio.mono16(11_025, new short[] {1, 2}));

        DoomWeaponPresentation component = new DoomWeaponPresentation(
                presentation, ready, List.of(first, second), sound, Duration.ofMillis(70), Duration.ofMillis(120));
        component.bindEndpoints(endpoints);
        var readyImage = component.currentFrame();

        endpoints.fired.execute();

        assertThat(component.isFiring()).isTrue();
        assertThat(component.currentFrame()).isNotSameAs(readyImage);
        assertThat(presentation.restarts).isEqualTo(1);
        component.onFrameUpdate(new FrameUpdateContext(Duration.ofMillis(70), Duration.ZERO, 0.0F));
        assertThat(component.currentFrame()).isNotSameAs(readyImage);
        component.onFrameUpdate(new FrameUpdateContext(Duration.ofMillis(70), Duration.ZERO, 0.0F));
        assertThat(component.isFiring()).isFalse();
        assertThat(component.currentFrame()).isSameAs(readyImage);

        endpoints.hit.execute(new RuntimePayload(
                DoomedCorridorsRuntimeTypes.WEAPON_HIT_PAYLOAD_TYPE, new DoomWeaponHit(0.0F, 0.0F, 74.0F)));
        assertThat(component.isHitIndicatorVisible()).isTrue();
        component.onFrameUpdate(new FrameUpdateContext(Duration.ofMillis(120), Duration.ZERO, 0.0F));
        assertThat(component.isHitIndicatorVisible()).isFalse();

        component.close();
        component.close();
        assertThat(presentation.overlay).isNull();
        assertThat(presentation.soundClosed).isTrue();
        ready.close();
        first.close();
        second.close();
        sound.close();
    }

    /** Creates one one-pixel sRGB overlay-image resource with distinct content. */
    private static OverlayImageResource image(byte value) {
        return OverlayImageResource.owning(OverlayImage.srgbRgba(1, 1, new byte[] {value, value, value, (byte) 0xff}));
    }

    /** Captures the one action implemented by the component. */
    private static final class RecordingEndpoints implements ComponentEndpoints {
        private RuntimeAction fired;
        private RuntimePayloadAction hit;

        @Override
        public RuntimeSignal signal(EndpointId endpoint) {
            throw new AssertionError("weapon presentation has no signal");
        }

        @Override
        public void action(EndpointId endpoint, RuntimeAction implementation) {
            if (endpoint.equals(DoomedCorridorsRuntimeTypes.RECEIVE_WEAPON_FIRED_ACTION)) {
                fired = implementation;
            } else {
                throw new AssertionError("unexpected endpoint: " + endpoint);
            }
        }

        @Override
        public void action(EndpointId endpoint, RuntimePayloadAction implementation) {
            if (endpoint.equals(DoomedCorridorsRuntimeTypes.RECEIVE_WEAPON_HIT_ACTION)) {
                hit = implementation;
            } else {
                throw new AssertionError("unexpected endpoint: " + endpoint);
            }
        }
    }

    /** Records overlay and sound activity without opening OpenGL or OpenAL. */
    private static final class RecordingPresentation implements PresentationWorldModule {
        private Overlay overlay;
        private int restarts;
        private boolean soundClosed;

        @Override
        public OverlayRegistration registerOverlay(Overlay registered) {
            overlay = registered;
            return () -> overlay = null;
        }

        @Override
        public LocalSound createLocalSound(PcmAudioResource audio, AudioCategory category) {
            assertThat(audio.audio()).isNotNull();
            assertThat(category).isEqualTo(AudioCategory.EFFECTS);
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
            throw new AssertionError("unit test does not render");
        }

        @Override
        public void close() {
            overlay = null;
        }
    }
}
