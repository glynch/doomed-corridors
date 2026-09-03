/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.doomedcorridors.actor.DoomActor;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalog;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalogLoader;
import io.github.glynch.doomedcorridors.actor.DoomActorCategory;
import io.github.glynch.doomedcorridors.actor.DoomActorDefinition;
import io.github.glynch.doomedcorridors.presentation.DoomCombatPresentationLoader;
import io.github.glynch.doomedcorridors.presentation.DoomCombatPresentationRules;
import io.github.glynch.doomedcorridors.presentation.DoomCombatPresentationState;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Specifies timed visual state independently of rendering and native audio. */
final class DoomCombatPresentationStateTest {
    private static final int THING_INDEX = 12;

    /** Animates pistol frames after fire while updating the HUD snapshot atomically. */
    @Test
    void animatesWeaponAndResources() {
        DoomCombatPresentationState presentation = new DoomCombatPresentationState(
                presentationRules(), state(aliveCombatant(), 100, 50));
        DoomCombatUpdate fired = new DoomCombatUpdate(
                state(aliveCombatant(), 100, 49),
                List.of(new DoomCombatEvent(DoomCombatEvent.Type.WEAPON_FIRED, DoomCombatEvent.PLAYER, 0)));

        presentation.apply(fired);
        assertThat(presentation.health()).isEqualTo(100);
        assertThat(presentation.bullets()).isEqualTo(49);
        assertThat(presentation.weaponFrame()).isEqualTo("PISGB0");

        presentation.advance(Duration.ofMillis(70));
        assertThat(presentation.weaponFrame()).isEqualTo("PISGC0");
        presentation.advance(Duration.ofMillis(210));
        assertThat(presentation.weaponFrame()).isEqualTo("PISGA0");
    }

    /** Returns pain to idle but retains the final death patch after the terminal sequence. */
    @Test
    void animatesPainAndTerminalDeath() {
        DoomCombatantState alive = aliveCombatant();
        DoomCombatPresentationState presentation = new DoomCombatPresentationState(
                presentationRules(), state(alive, 100, 50));
        DoomCombatantState hurt = alive.withHealth(10);

        presentation.apply(new DoomCombatUpdate(
                state(hurt, 100, 49),
                List.of(new DoomCombatEvent(DoomCombatEvent.Type.COMBATANT_DAMAGED, THING_INDEX, 10))));
        assertThat(presentation.actorFrame(THING_INDEX)).contains("POSSG1");
        presentation.advance(Duration.ofMillis(140));
        assertThat(presentation.actorFrame(THING_INDEX)).isEmpty();

        DoomCombatantState dead = hurt.withHealth(0);
        presentation.apply(new DoomCombatUpdate(
                state(dead, 100, 48),
                List.of(
                        new DoomCombatEvent(DoomCombatEvent.Type.COMBATANT_DAMAGED, THING_INDEX, 10),
                        new DoomCombatEvent(DoomCombatEvent.Type.COMBATANT_KILLED, THING_INDEX, 0))));
        assertThat(presentation.actorFrame(THING_INDEX)).contains("POSSH0");
        presentation.advance(Duration.ofMillis(560));
        assertThat(presentation.actorFrame(THING_INDEX)).contains("POSSL0");
        presentation.advance(Duration.ofSeconds(10));
        assertThat(presentation.actorFrame(THING_INDEX)).contains("POSSL0");
    }

    /** Animates pursuit and attack while exposing player pain and terminal death presentation. */
    @Test
    void presentsEnemyBehaviorAndPlayerDamage() {
        DoomCombatantState alive = aliveCombatant();
        DoomCombatPresentationState presentation = new DoomCombatPresentationState(
                presentationRules(), state(alive, 100, 50));
        DoomCombatantState pursuing = alive.withPose(
                3.0F, 0.0F, 0.0F, DoomCombatantActivity.PURSUING);

        presentation.apply(new DoomCombatUpdate(state(pursuing, 100, 50), List.of()));
        assertThat(presentation.actorFrame(THING_INDEX)).contains("POSSA1");
        presentation.advance(Duration.ofMillis(140));
        assertThat(presentation.actorFrame(THING_INDEX)).contains("POSSB1");

        DoomCombatantState attacking = pursuing.withActivity(DoomCombatantActivity.ATTACKING);
        presentation.apply(new DoomCombatUpdate(
                state(attacking, 91, 50),
                List.of(
                        new DoomCombatEvent(DoomCombatEvent.Type.COMBATANT_ATTACKED, THING_INDEX, 0),
                        new DoomCombatEvent(DoomCombatEvent.Type.PLAYER_DAMAGED, DoomCombatEvent.PLAYER, 9))));
        assertThat(presentation.actorFrame(THING_INDEX)).contains("POSSE1");
        assertThat(presentation.health()).isEqualTo(91);
        assertThat(presentation.damageFlashAlpha()).isEqualTo(0.45F);

        presentation.apply(new DoomCombatUpdate(
                state(attacking, 0, 50),
                List.of(new DoomCombatEvent(
                        DoomCombatEvent.Type.PLAYER_KILLED, DoomCombatEvent.PLAYER, 0))));
        assertThat(presentation.isPlayerDead()).isTrue();
    }

    /** Builds one package-visible combat snapshot for the presentation boundary. */
    private static DoomCombatState state(
            DoomCombatantState combatant, int health, int bullets) {
        return new DoomCombatState(
                health, 200, bullets, 200, "pistol", List.of(combatant), List.of());
    }

    /** Builds one configured zombieman combatant. */
    private static DoomCombatantState aliveCombatant() {
        DoomActorDefinition definition = new DoomActorDefinition(
                3004,
                "zombieman",
                "Zombieman",
                DoomActorCategory.ENEMY,
                Optional.of("POSSA"));
        DoomActor actor = new DoomActor(THING_INDEX, definition, 4.0F, 0.0F, 0.0F, 0.0F);
        return new DoomCombatantState(actor, 20.0F / 32.0F, 56.0F / 32.0F, 20);
    }

    /** Loads the checked-in combat presentation through both versioned documents. */
    private static DoomCombatPresentationRules presentationRules() {
        DoomActorCatalog actors = new DoomActorCatalogLoader()
                .load(Path.of("game/actors.json"))
                .catalog()
                .orElseThrow();
        DoomCombatRules combat = new DoomCombatRulesLoader()
                .load(Path.of("game/combat.json"), actors)
                .rules()
                .orElseThrow();
        return new DoomCombatPresentationLoader()
                .load(Path.of("game/combat-presentation.json"), combat)
                .rules()
                .orElseThrow();
    }
}
