/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsRuntimeTypes;
import io.github.glynch.jscene3d.audio.PcmAudio;
import io.github.glynch.jscene3d.game.presentation.PcmAudioResource;
import io.github.glynch.jscene3d.project.component.EndpointId;
import io.github.glynch.jscene3d.project.runtime.FrameUpdateContext;
import io.github.glynch.jscene3d.project.runtime.RuntimeAction;
import io.github.glynch.jscene3d.project.runtime.RuntimePayloadAction;
import io.github.glynch.jscene3d.project.runtime.RuntimeSignal;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Exercises descriptor-connected player damage and terminal presentation without native devices. */
final class DoomPlayerPresentationTest {
    /** Presents transient pain, replaces it with terminal death, and ignores later reactions. */
    @Test
    void presentsPainThenTerminalDeath() {
        TestPresentationWorldModule presentationWorld = new TestPresentationWorldModule();
        RecordingEndpoints endpoints = new RecordingEndpoints();
        PcmAudioResource pain = audio((short) 1);
        PcmAudioResource death = audio((short) 2);
        DoomPlayerPresentation presentation = new DoomPlayerPresentation(
                presentationWorld,
                pain,
                death,
                new DoomPlayerPresentation.Flash(Duration.ofMillis(200), 0.4F),
                new DoomPlayerPresentation.Flash(Duration.ofMillis(500), 0.6F),
                0.2F);
        presentation.bindEndpoints(endpoints);

        endpoints.hurt.execute();
        assertThat(presentation.isDead()).isFalse();
        assertThat(presentation.currentRedOpacity()).isEqualTo(0.4F);
        assertThat(presentation.currentShadeOpacity()).isZero();
        assertThat(presentationWorld.restarts()).isEqualTo(1);

        presentation.onFrameUpdate(new FrameUpdateContext(Duration.ofMillis(100), Duration.ZERO, 0.0F));
        assertThat(presentation.currentRedOpacity()).isCloseTo(0.2F, within(1.0E-6F));
        presentation.onFrameUpdate(new FrameUpdateContext(Duration.ofMillis(100), Duration.ZERO, 0.0F));
        assertThat(presentation.currentRedOpacity()).isZero();

        endpoints.died.execute();
        endpoints.died.execute();
        endpoints.hurt.execute();
        assertThat(presentation.isDead()).isTrue();
        assertThat(presentation.currentRedOpacity()).isEqualTo(0.6F);
        assertThat(presentation.currentShadeOpacity()).isEqualTo(0.2F);
        assertThat(presentationWorld.restarts()).isEqualTo(2);

        presentation.onFrameUpdate(new FrameUpdateContext(Duration.ofMillis(500), Duration.ZERO, 0.0F));
        assertThat(presentation.currentRedOpacity()).isZero();
        assertThat(presentation.currentShadeOpacity()).isEqualTo(0.2F);

        presentation.close();
        presentation.close();
        assertThat(presentationWorld.overlayCount()).isZero();
        assertThat(presentationWorld.soundClosed()).isTrue();
        pain.close();
        death.close();
    }

    private static PcmAudioResource audio(short sample) {
        return PcmAudioResource.owning(PcmAudio.mono16(11_025, new short[] {sample}));
    }

    /** Captures the two payload-free actions declared by player presentation. */
    private static final class RecordingEndpoints implements ComponentEndpoints {
        private RuntimeAction hurt;
        private RuntimeAction died;

        @Override
        public RuntimeSignal signal(EndpointId endpoint) {
            throw new AssertionError("player presentation has no signal");
        }

        @Override
        public void action(EndpointId endpoint, RuntimeAction implementation) {
            if (endpoint.equals(DoomedCorridorsRuntimeTypes.RECEIVE_PLAYER_HURT_ACTION)) {
                hurt = implementation;
            } else if (endpoint.equals(DoomedCorridorsRuntimeTypes.RECEIVE_PLAYER_DIED_ACTION)) {
                died = implementation;
            } else {
                throw new AssertionError("unexpected endpoint: " + endpoint);
            }
        }

        @Override
        public void action(EndpointId endpoint, RuntimePayloadAction implementation) {
            throw new AssertionError("player presentation has no payload action");
        }
    }
}
