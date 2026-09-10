/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.doomedcorridors.actor.DoomActorCatalog;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalogLoader;
import io.github.glynch.doomedcorridors.combat.DoomCombatRules;
import io.github.glynch.doomedcorridors.combat.DoomCombatRulesLoader;
import io.github.glynch.doomedcorridors.internal.DoomedCorridorsDescriptors;
import io.github.glynch.jscene3d.project.component.EndpointId;
import io.github.glynch.jscene3d.project.runtime.RuntimeAction;
import io.github.glynch.jscene3d.project.runtime.RuntimePayload;
import io.github.glynch.jscene3d.project.runtime.RuntimePayloadAction;
import io.github.glynch.jscene3d.project.runtime.RuntimeSignal;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentEndpoints;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies descriptor-backed player damage and terminal signal semantics. */
final class DoomPlayerStateTest {
    /** Emits non-fatal and terminal signals once while clamping accepted damage to current health. */
    @Test
    void appliesDamageAndEmitsStateSignals() {
        RecordingEndpoints endpoints = new RecordingEndpoints();
        DoomPlayerState state =
                new DoomPlayerState(ResourceReference.asset("actors"), ResourceReference.asset("combat"));
        state.bindEndpoints(endpoints);
        state.configure(rules());

        int first = state.damage(30);
        int fatal = state.damage(100);
        int afterDeath = state.damage(1);

        assertThat(first).isEqualTo(30);
        assertThat(fatal).isEqualTo(70);
        assertThat(afterDeath).isZero();
        assertThat(state.health()).isZero();
        assertThat(endpoints.hurt.emissions).isEqualTo(1);
        assertThat(endpoints.died.emissions).isEqualTo(1);
    }

    /** Keeps combat active while a selected local playtest profile suppresses accepted player damage. */
    @Test
    void ignoresDamageWhilePlaytestInvulnerabilityIsEnabled() {
        RecordingEndpoints endpoints = new RecordingEndpoints();
        DoomPlayerState state =
                new DoomPlayerState(ResourceReference.asset("actors"), ResourceReference.asset("combat"));
        state.bindEndpoints(endpoints);
        state.setInvulnerable(true);
        state.configure(rules());

        assertThat(state.damage(30)).isZero();
        assertThat(state.health()).isEqualTo(100);
        assertThat(endpoints.hurt.emissions).isZero();
        assertThat(endpoints.died.emissions).isZero();
    }

    private static DoomCombatRules rules() {
        DoomActorCatalog actors = new DoomActorCatalogLoader()
                .load(Path.of("game/actors.json"))
                .catalog()
                .orElseThrow();
        return new DoomCombatRulesLoader()
                .load(Path.of("game/combat.json"), actors)
                .rules()
                .orElseThrow();
    }

    /** Supplies the two descriptor-declared player-state signals. */
    private static final class RecordingEndpoints implements ComponentEndpoints {
        private final RecordingSignal hurt = new RecordingSignal();
        private final RecordingSignal died = new RecordingSignal();

        @Override
        public RuntimeSignal signal(EndpointId endpoint) {
            if (endpoint.equals(DoomedCorridorsDescriptors.HURT_SIGNAL)) {
                return hurt;
            }
            if (endpoint.equals(DoomedCorridorsDescriptors.DIED_SIGNAL)) {
                return died;
            }
            throw new AssertionError("unexpected endpoint: " + endpoint);
        }

        @Override
        public void action(EndpointId endpoint, RuntimeAction implementation) {
            throw new AssertionError("player state has no action");
        }

        @Override
        public void action(EndpointId endpoint, RuntimePayloadAction implementation) {
            throw new AssertionError("player state has no payload action");
        }
    }

    /** Counts payload-free signal emissions. */
    private static final class RecordingSignal implements RuntimeSignal {
        private int emissions;

        @Override
        public void emit() {
            emissions++;
        }

        @Override
        public void emit(RuntimePayload payload) {
            throw new AssertionError("player state signals have no payload");
        }
    }
}
