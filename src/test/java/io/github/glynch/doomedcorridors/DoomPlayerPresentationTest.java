/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsDescriptors;
import io.github.glynch.jscene3d.audio.PcmAudio;
import io.github.glynch.jscene3d.game.presentation.PcmAudioResource;
import io.github.glynch.jscene3d.project.component.EndpointId;
import io.github.glynch.jscene3d.project.component.PropertyId;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.FrameUpdateContext;
import io.github.glynch.jscene3d.project.runtime.RuntimeAction;
import io.github.glynch.jscene3d.project.runtime.RuntimePayloadAction;
import io.github.glynch.jscene3d.project.runtime.RuntimeSignal;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceResolver;
import io.github.glynch.jscene3d.project.spatial3d.Transform3d;
import java.time.Duration;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

/** Exercises descriptor-connected player damage and terminal presentation without native devices. */
final class DoomPlayerPresentationTest {
    /** Presents transient pain, replaces it with terminal death, and ignores later reactions. */
    @Test
    void presentsPainThenTerminalDeath() {
        TestPresentationWorldModule presentationWorld = new TestPresentationWorldModule();
        RecordingEndpoints endpoints = new RecordingEndpoints();
        TestTransform view = new TestTransform(0.40625F);
        PcmAudioResource pain = audio((short) 1);
        PcmAudioResource death = audio((short) 2);
        DoomPlayerPresentation presentation = new DoomPlayerPresentation(
                presentationWorld,
                pain,
                death,
                new DoomPlayerPresentation.Flash(Duration.ofMillis(200), 0.4F),
                new DoomPlayerPresentation.Flash(Duration.ofMillis(500), 0.6F),
                new DoomPlayerPresentation.ViewDrop(Duration.ofSeconds(1), 1.0F),
                0.2F);
        presentation.bindReferences(new RecordingReferences(view));
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
        assertThat(view.position().y()).isEqualTo(-0.09375F);
        presentation.onFrameUpdate(new FrameUpdateContext(Duration.ofMillis(500), Duration.ZERO, 0.0F));
        assertThat(view.position().y()).isEqualTo(-0.59375F);

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

    /** Resolves the one camera transform targeted by player presentation. */
    private record RecordingReferences(Transform3d view) implements ComponentReferenceResolver {
        @Override
        public Entity entity(PropertyId property) {
            throw new AssertionError("player presentation has no entity target");
        }

        @Override
        public <T> T component(PropertyId property, Class<T> valueType) {
            assertThat(property).isEqualTo(DoomedCorridorsDescriptors.PLAYER_VIEW_TRANSFORM_PROPERTY);
            return valueType.cast(view);
        }

        @Override
        public <T> List<T> components(PropertyId property, Class<T> valueType) {
            throw new AssertionError("player presentation has no component-target array");
        }
    }

    /** Minimal mutable transform used to verify the terminal camera transition. */
    private static final class TestTransform implements Transform3d {
        private final Vector3f position;
        private final Quaternionf orientation = new Quaternionf();
        private final Vector3f scale = new Vector3f(1.0F);

        private TestTransform(float y) {
            position = new Vector3f(0.0F, y, 0.0F);
        }

        @Override
        public Vector3fc position() {
            return position;
        }

        @Override
        public Quaternionfc orientation() {
            return orientation;
        }

        @Override
        public Vector3fc scale() {
            return scale;
        }

        @Override
        public void setPosition(float x, float y, float z) {
            position.set(x, y, z);
        }

        @Override
        public void setOrientation(float x, float y, float z, float w) {
            orientation.set(x, y, z, w).normalize();
        }

        @Override
        public void setWorldPose(Vector3fc worldPosition, Quaternionfc worldOrientation) {
            position.set(worldPosition);
            orientation.set(worldOrientation);
        }

        @Override
        public void setScale(float x, float y, float z) {
            scale.set(x, y, z);
        }

        @Override
        public Matrix4fc localMatrix() {
            return new Matrix4f().translationRotateScale(position, orientation, scale);
        }

        @Override
        public Matrix4fc worldMatrix() {
            return localMatrix();
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {
            // The component under test does not own its referenced transform.
        }
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
            if (endpoint.equals(DoomedCorridorsDescriptors.RECEIVE_PLAYER_HURT_ACTION)) {
                hurt = implementation;
            } else if (endpoint.equals(DoomedCorridorsDescriptors.RECEIVE_PLAYER_DIED_ACTION)) {
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
