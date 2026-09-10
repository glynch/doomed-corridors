/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsDescriptors;
import io.github.glynch.jscene3d.audio.PcmAudio;
import io.github.glynch.jscene3d.game.presentation.PcmAudioResource;
import io.github.glynch.jscene3d.game.presentation.PositionalSoundAttenuation;
import io.github.glynch.jscene3d.objects.BillboardAlignment;
import io.github.glynch.jscene3d.project.component.EndpointId;
import io.github.glynch.jscene3d.project.component.PropertyId;
import io.github.glynch.jscene3d.project.runtime.FrameUpdateContext;
import io.github.glynch.jscene3d.project.runtime.RuntimeAction;
import io.github.glynch.jscene3d.project.runtime.RuntimePayloadAction;
import io.github.glynch.jscene3d.project.runtime.RuntimeSignal;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceResolver;
import io.github.glynch.jscene3d.project.spatial3d.BillboardRenderer3d;
import io.github.glynch.jscene3d.project.spatial3d.Material3dResource;
import io.github.glynch.jscene3d.project.spatial3d.Transform3d;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector2f;
import org.joml.Vector2fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

/** Verifies descriptor-driven combatant presentation independently of native rendering and audio. */
final class DoomCombatantPresentationTest {
    private static final Duration FRAME_DURATION = Duration.ofMillis(140);

    /** Applies explicit behavior and damage actions using the required animation priority and completion policies. */
    @Test
    void presentsBehaviorAndDamageStateTransitions() {
        TestPresentationWorldModule presentation = new TestPresentationWorldModule();
        RecordingEndpoints endpoints = new RecordingEndpoints();
        RecordingBillboard idle = new RecordingBillboard(true);
        List<RecordingBillboard> walk = frames(4);
        List<RecordingBillboard> attack = frames(3);
        List<RecordingBillboard> pain = frames(1);
        List<RecordingBillboard> death = frames(5);
        List<PcmAudioResource> resources = sounds(7);
        DoomCombatantPresentation component = new DoomCombatantPresentation(
                42L,
                presentation,
                new DoomCombatantPresentation.AudioResources(
                        resources.subList(0, 3), resources.get(3), resources.get(4), resources.subList(5, 7)),
                FRAME_DURATION,
                new PositionalSoundAttenuation(5.0F, 37.5F, 1.0F));
        component.bindReferences(new RecordingReferences(idle, walk, attack, pain, death));
        component.bindEndpoints(endpoints);

        endpoints.execute(DoomedCorridorsDescriptors.RECEIVE_COMBATANT_ALERTED_ACTION);
        endpoints.execute(DoomedCorridorsDescriptors.RECEIVE_COMBATANT_MOVEMENT_STARTED_ACTION);

        assertThat(presentation.positionalRestarts()).isEqualTo(1);
        assertThat(component.currentFrame()).isSameAs(walk.getFirst());
        component.onFrameUpdate(frameUpdate(FRAME_DURATION));
        assertThat(component.currentFrame()).isSameAs(walk.get(1));

        endpoints.execute(DoomedCorridorsDescriptors.RECEIVE_COMBATANT_ATTACKED_ACTION);
        assertThat(component.currentFrame()).isSameAs(attack.getFirst());
        endpoints.execute(DoomedCorridorsDescriptors.RECEIVE_COMBATANT_MOVEMENT_STOPPED_ACTION);
        component.onFrameUpdate(frameUpdate(FRAME_DURATION.multipliedBy(3)));
        assertThat(component.currentFrame()).isSameAs(idle);

        endpoints.execute(DoomedCorridorsDescriptors.RECEIVE_COMBATANT_MOVEMENT_STARTED_ACTION);
        endpoints.execute(DoomedCorridorsDescriptors.RECEIVE_COMBATANT_HURT_ACTION);
        endpoints.execute(DoomedCorridorsDescriptors.RECEIVE_COMBATANT_ATTACKED_ACTION);
        assertThat(component.currentFrame()).isSameAs(pain.getFirst());
        assertThat(presentation.positionalRestarts()).isEqualTo(4);
        component.onFrameUpdate(frameUpdate(FRAME_DURATION));
        assertThat(component.currentFrame()).isSameAs(walk.getFirst());

        endpoints.execute(DoomedCorridorsDescriptors.RECEIVE_COMBATANT_MOVEMENT_STOPPED_ACTION);
        assertThat(component.currentFrame()).isSameAs(idle);
        endpoints.execute(DoomedCorridorsDescriptors.RECEIVE_COMBATANT_DIED_ACTION);
        endpoints.execute(DoomedCorridorsDescriptors.RECEIVE_COMBATANT_MOVEMENT_STOPPED_ACTION);
        component.onFrameUpdate(frameUpdate(FRAME_DURATION.multipliedBy(5)));
        assertThat(component.isDead()).isTrue();
        assertThat(component.currentFrame()).isSameAs(death.getLast());
        assertThat(death.getLast().isVisible()).isTrue();

        component.close();
        component.close();
        assertThat(presentation.positionalSoundClosed()).isTrue();
        resources.forEach(PcmAudioResource::close);
    }

    /** Creates one frame-update context with no interpolation-specific state. */
    private static FrameUpdateContext frameUpdate(Duration elapsed) {
        return new FrameUpdateContext(elapsed, Duration.ZERO, 0.0F);
    }

    /** Creates distinct mutable billboard test doubles in authored order. */
    private static List<RecordingBillboard> frames(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(ignored -> new RecordingBillboard(false))
                .toList();
    }

    /** Creates independently owned one-sample PCM resources for every sound role. */
    private static List<PcmAudioResource> sounds(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> PcmAudioResource.owning(PcmAudio.mono16(11_025, new short[] {(short) index})))
                .toList();
    }

    /** Retains no-payload action callbacks by their descriptor endpoint identity. */
    private static final class RecordingEndpoints implements ComponentEndpoints {
        private final Map<EndpointId, RuntimeAction> actions = new LinkedHashMap<>();

        @Override
        public RuntimeSignal signal(EndpointId endpoint) {
            throw new AssertionError("combatant presentation declares no signals");
        }

        @Override
        public void action(EndpointId endpoint, RuntimeAction implementation) {
            actions.put(endpoint, implementation);
        }

        @Override
        public void action(EndpointId endpoint, RuntimePayloadAction implementation) {
            throw new AssertionError("combatant presentation declares no payload actions");
        }

        private void execute(EndpointId endpoint) {
            actions.get(endpoint).execute();
        }
    }

    /** Resolves the exact authored transform and billboard collections expected by the component. */
    private record RecordingReferences(
            RecordingBillboard idle,
            List<RecordingBillboard> walk,
            List<RecordingBillboard> attack,
            List<RecordingBillboard> pain,
            List<RecordingBillboard> death)
            implements ComponentReferenceResolver {
        @Override
        public io.github.glynch.jscene3d.project.runtime.Entity entity(PropertyId property) {
            throw new AssertionError("combatant presentation declares no entity target");
        }

        @Override
        public <T> T component(PropertyId property, Class<T> valueType) {
            Object value = property.equals(DoomedCorridorsDescriptors.COMBATANT_TRANSFORM_PROPERTY)
                    ? new FixedTransform()
                    : idle;
            return valueType.cast(value);
        }

        @Override
        public <T> List<T> components(PropertyId property, Class<T> valueType) {
            List<? extends BillboardRenderer3d> values =
                    switch (property.value()) {
                        case "walk-frames" -> walk;
                        case "attack-frames" -> attack;
                        case "pain-frames" -> pain;
                        case "death-frames" -> death;
                        default -> throw new AssertionError("unexpected component targets: " + property);
                    };
            return values.stream().map(valueType::cast).toList();
        }
    }

    /** Minimal visible-state billboard used to verify animation selection. */
    private static final class RecordingBillboard implements BillboardRenderer3d {
        private boolean visible;

        private RecordingBillboard(boolean visible) {
            this.visible = visible;
        }

        @Override
        public Material3dResource material() {
            throw new AssertionError("animation selection does not inspect materials");
        }

        @Override
        public Vector2fc size() {
            return new Vector2f(1.0F);
        }

        @Override
        public Vector2fc anchor() {
            return new Vector2f(0.5F);
        }

        @Override
        public BillboardAlignment alignment() {
            return BillboardAlignment.CYLINDRICAL;
        }

        @Override
        public boolean isVisible() {
            return visible;
        }

        @Override
        public void setVisible(boolean value) {
            visible = value;
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {
            // The owning spatial module, not the presentation behavior, owns billboard lifetime.
        }
    }

    /** Fixed world transform used only to supply positional-sound coordinates. */
    private static final class FixedTransform implements Transform3d {
        private final Vector3f position = new Vector3f(2.0F, 3.0F, 4.0F);
        private final Quaternionf orientation = new Quaternionf();
        private final Vector3f scale = new Vector3f(1.0F);
        private final Matrix4f matrix = new Matrix4f().translation(position);

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
            throw new AssertionError("presentation does not mutate transforms");
        }

        @Override
        public void setOrientation(float x, float y, float z, float w) {
            throw new AssertionError("presentation does not mutate transforms");
        }

        @Override
        public void setWorldPose(Vector3fc worldPosition, Quaternionfc worldOrientation) {
            throw new AssertionError("presentation does not mutate transforms");
        }

        @Override
        public void setScale(float x, float y, float z) {
            throw new AssertionError("presentation does not mutate transforms");
        }

        @Override
        public Matrix4fc localMatrix() {
            return matrix;
        }

        @Override
        public Matrix4fc worldMatrix() {
            return matrix;
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {
            // The owning spatial module, not the presentation behavior, owns transform lifetime.
        }
    }
}
