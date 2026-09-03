/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.doomedcorridors.actor.DoomActor;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalogLoader;
import io.github.glynch.doomedcorridors.actor.DoomActorCategory;
import io.github.glynch.doomedcorridors.actor.DoomActorDefinition;
import io.github.glynch.doomedcorridors.map.DoomMap;
import io.github.glynch.doomedcorridors.world.DoomPlayerState;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Specifies deterministic pistol combat through the headless combat-session seam. */
final class DoomCombatSessionTest {
    private static final float EYE_HEIGHT = 41.0F / 32.0F;

    /** Initializes player resources and only configured combatant actors. */
    @Test
    void initializesConfiguredCombatants() {
        DoomCombatSession session = session(
                openRoom(), List.of(zombieman(1, 6.0F), decoration(2, 5.0F)), 0L);

        DoomCombatState state = session.state();

        assertThat(state.playerHealth()).isEqualTo(100);
        assertThat(state.maximumPlayerHealth()).isEqualTo(100);
        assertThat(state.bullets()).isEqualTo(50);
        assertThat(state.primaryWeaponId()).isEqualTo("pistol");
        assertThat(state.combatants()).singleElement().satisfies(combatant -> {
            assertThat(combatant.thingIndex()).isEqualTo(1);
            assertThat(combatant.actorId()).isEqualTo("zombieman");
            assertThat(combatant.health()).isEqualTo(20);
            assertThat(combatant.radius()).isEqualTo(20.0F / 32.0F);
            assertThat(combatant.height()).isEqualTo(56.0F / 32.0F);
            assertThat(combatant.status()).isEqualTo(DoomCombatantStatus.ALIVE);
        });
    }

    /** Damages the nearest living combatant intersected by the pistol ray. */
    @Test
    void hitsNearestCombatant() {
        DoomCombatSession session = session(
                openRoom(), List.of(zombieman(1, 4.0F), zombieman(2, 6.0F)), 0L);

        DoomCombatUpdate update = session.firePrimary(player(0.0F, 0.0F));

        assertThat(update.state().bullets()).isEqualTo(49);
        assertThat(update.state().combatant(1)).hasValueSatisfying(target ->
                assertThat(target.health()).isBetween(5, 15));
        assertThat(update.state().combatant(2)).hasValueSatisfying(target ->
                assertThat(target.health()).isEqualTo(20));
        assertThat(update.events())
                .extracting(DoomCombatEvent::type)
                .containsExactly(DoomCombatEvent.Type.WEAPON_FIRED, DoomCombatEvent.Type.COMBATANT_DAMAGED);
    }

    /** Stops a shot at a one-sided linedef before an otherwise aligned combatant. */
    @Test
    void blocksHitscanAtSolidWall() {
        DoomCombatSession session = session(
                dividedRoom(false), List.of(zombieman(1, 6.0F)), 0L);

        DoomCombatUpdate update = session.firePrimary(player(0.0F, 0.0F));

        assertThat(update.state().combatant(1)).hasValueSatisfying(target ->
                assertThat(target.health()).isEqualTo(20));
        assertThat(update.events())
                .extracting(DoomCombatEvent::type)
                .containsExactly(DoomCombatEvent.Type.WEAPON_FIRED);
    }

    /** Allows a shot through a two-sided linedef with an open vertical span. */
    @Test
    void passesHitscanThroughOpenPortal() {
        DoomCombatSession session = session(
                dividedRoom(true), List.of(zombieman(1, 6.0F)), 0L);

        DoomCombatUpdate update = session.firePrimary(player(0.0F, 0.0F));

        assertThat(update.state().combatant(1)).hasValueSatisfying(target ->
                assertThat(target.health()).isLessThan(20));
    }

    /** Leaves an actor unharmed when vertical aim misses its collision cylinder. */
    @Test
    void missesCombatantAboveItsHeight() {
        DoomCombatSession session = session(
                openRoom(), List.of(zombieman(1, 6.0F)), 0L);

        DoomCombatUpdate update = session.firePrimary(player(0.0F, 0.75F));

        assertThat(update.state().combatant(1)).hasValueSatisfying(target ->
                assertThat(target.health()).isEqualTo(20));
    }

    /** Marks a zero-health combatant dead and lets later shots pass through it. */
    @Test
    void killsCombatantAndSkipsItsCylinder() {
        DoomCombatSession session = session(
                openRoom(), List.of(zombieman(1, 4.0F), zombieman(2, 6.0F)), 0L);
        DoomCombatantState first = session.state().combatant(1).orElseThrow();
        for (int shot = 0; shot < 4 && first.status() == DoomCombatantStatus.ALIVE; shot++) {
            first = session.firePrimary(player(0.0F, 0.0F)).state().combatant(1).orElseThrow();
        }

        DoomCombatUpdate update = session.firePrimary(player(0.0F, 0.0F));

        assertThat(first.health()).isZero();
        assertThat(first.status()).isEqualTo(DoomCombatantStatus.DEAD);
        assertThat(update.state().combatant(2)).hasValueSatisfying(target ->
                assertThat(target.health()).isLessThan(20));
    }

    /** Reports an empty weapon without firing or consuming negative ammunition. */
    @Test
    void reportsEmptyPistol() {
        DoomCombatSession session = session(openRoom(), List.of(), 0L);
        for (int shot = 0; shot < 50; shot++) {
            session.firePrimary(player(0.0F, 0.0F));
        }

        DoomCombatUpdate update = session.firePrimary(player(0.0F, 0.0F));

        assertThat(update.state().bullets()).isZero();
        assertThat(update.events()).singleElement().satisfies(event ->
                assertThat(event.type()).isEqualTo(DoomCombatEvent.Type.WEAPON_EMPTY));
    }

    /** Applies incoming damage once and clamps player death at zero health. */
    @Test
    void damagesAndKillsPlayer() {
        DoomCombatSession session = session(openRoom(), List.of(), 0L);

        DoomCombatUpdate damaged = session.damagePlayer(40);
        DoomCombatUpdate killed = session.damagePlayer(Integer.MAX_VALUE);

        assertThat(damaged.state().playerHealth()).isEqualTo(60);
        assertThat(damaged.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(DoomCombatEvent.Type.PLAYER_DAMAGED);
            assertThat(event.amount()).isEqualTo(40);
        });
        assertThat(killed.state().playerHealth()).isZero();
        assertThat(killed.state().isPlayerDead()).isTrue();
        assertThat(killed.events())
                .extracting(DoomCombatEvent::type)
                .containsExactly(DoomCombatEvent.Type.PLAYER_DAMAGED, DoomCombatEvent.Type.PLAYER_KILLED);
    }

    /** Produces the same damage sequence for the same explicit session seed. */
    @Test
    void usesDeterministicDamageSequence() {
        DoomActor target = zombieman(1, 6.0F);
        DoomCombatSession first = session(openRoom(), List.of(target), 1234L);
        DoomCombatSession second = session(openRoom(), List.of(target), 1234L);

        int firstHealth = first.firePrimary(player(0.0F, 0.0F))
                .state()
                .combatant(1)
                .orElseThrow()
                .health();
        int secondHealth = second.firePrimary(player(0.0F, 0.0F))
                .state()
                .combatant(1)
                .orElseThrow()
                .health();

        assertThat(secondHealth).isEqualTo(firstHealth);
    }

    private static DoomCombatSession session(DoomMap map, List<DoomActor> actors, long seed) {
        return DoomCombatSession.create(map, rules(), actors, seed);
    }

    private static DoomCombatRules rules() {
        var catalog = new DoomActorCatalogLoader()
                .load(Path.of("game/actors.json"))
                .catalog()
                .orElseThrow();
        return new DoomCombatRulesLoader()
                .load(Path.of("game/combat.json"), catalog)
                .rules()
                .orElseThrow();
    }

    private static DoomPlayerState player(float yaw, float pitch) {
        return new DoomPlayerState(2.0F, EYE_HEIGHT, -2.0F, yaw, pitch);
    }

    private static DoomActor zombieman(int thingIndex, float x) {
        DoomActorDefinition definition = new DoomActorDefinition(
                3004,
                "zombieman",
                "Zombieman",
                DoomActorCategory.ENEMY,
                Optional.of("POSSA"));
        return new DoomActor(thingIndex, definition, x, 0.0F, -2.0F, 0.0F);
    }

    private static DoomActor decoration(int thingIndex, float x) {
        DoomActorDefinition definition = new DoomActorDefinition(
                43,
                "burnt-tree",
                "Burnt Tree",
                DoomActorCategory.DECORATION,
                Optional.of("TRE1A"));
        return new DoomActor(thingIndex, definition, x, 0.0F, -2.0F, 0.0F);
    }

    private static DoomMap openRoom() {
        return map(false, false);
    }

    private static DoomMap dividedRoom(boolean portal) {
        return map(true, portal);
    }

    private static DoomMap map(boolean divided, boolean portal) {
        List<DoomMap.Vertex> vertices = new ArrayList<>(List.of(
                new DoomMap.Vertex(0, 0),
                new DoomMap.Vertex(0, 128),
                new DoomMap.Vertex(256, 128),
                new DoomMap.Vertex(256, 0)));
        List<DoomMap.Linedef> lines = new ArrayList<>(List.of(
                line(0, 1, 0, -1),
                line(1, 2, 1, -1),
                line(2, 3, 2, -1),
                line(3, 0, 3, -1)));
        List<DoomMap.Sidedef> sides = new ArrayList<>(List.of(side(), side(), side(), side()));
        if (divided) {
            vertices.add(new DoomMap.Vertex(128, 0));
            vertices.add(new DoomMap.Vertex(128, 128));
            sides.add(side());
            int leftSide = -1;
            if (portal) {
                leftSide = sides.size();
                sides.add(side());
            }
            lines.add(line(4, 5, 4, leftSide));
        }
        return new DoomMap(
                "MAP01",
                List.of(),
                new DoomMap.Geometry(vertices, lines, sides, List.of(sector())),
                new DoomMap.Bsp(List.of(), List.of(), List.of()),
                List.of(),
                new DoomMap.Blockmap(0, 0, 1, 1, List.of(List.of())));
    }

    private static DoomMap.Linedef line(int start, int end, int right, int left) {
        return new DoomMap.Linedef(start, end, 0, 0, 0, right, left);
    }

    private static DoomMap.Sidedef side() {
        return new DoomMap.Sidedef(0, 0, "-", "-", "WALL", 0);
    }

    private static DoomMap.Sector sector() {
        return new DoomMap.Sector(0, 128, "FLOOR", "CEILING", 160, 0, 0);
    }
}
