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
import java.time.Duration;
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
        assertThat(state.maximumPlayerHealth()).isEqualTo(200);
        assertThat(state.bullets()).isEqualTo(50);
        assertThat(state.maximumBullets()).isEqualTo(200);
        assertThat(state.collectedPickupThingIndices()).isEmpty();
        assertThat(state.primaryWeaponId()).isEqualTo("pistol");
        assertThat(state.combatants()).singleElement().satisfies(combatant -> {
            assertThat(combatant.thingIndex()).isEqualTo(1);
            assertThat(combatant.actorId()).isEqualTo("zombieman");
            assertThat(combatant.health()).isEqualTo(20);
            assertThat(combatant.radius()).isEqualTo(20.0F / 32.0F);
            assertThat(combatant.height()).isEqualTo(56.0F / 32.0F);
            assertThat(combatant.status()).isEqualTo(DoomCombatantStatus.ALIVE);
            assertThat(combatant.activity()).isEqualTo(DoomCombatantActivity.DORMANT);
        });
    }

    /** Alerts a visible distant enemy and advances it through map collision toward the player. */
    @Test
    void alertsAndPursuesVisiblePlayer() {
        DoomCombatSession session = session(
                openRoom(), List.of(zombieman(1, 7.0F)), 0L);

        DoomCombatUpdate update =
                session.advance(player(0.0F, 0.0F), Duration.ofMillis(200));

        assertThat(update.events())
                .extracting(DoomCombatEvent::type)
                .containsExactly(DoomCombatEvent.Type.COMBATANT_ALERTED);
        assertThat(update.state().combatant(1)).hasValueSatisfying(combatant -> {
            assertThat(combatant.activity()).isEqualTo(DoomCombatantActivity.PURSUING);
            assertThat(combatant.x()).isLessThan(7.0F);
            assertThat(combatant.floorHeight()).isZero();
        });
    }

    /** Attacks after the configured reaction delay and applies deterministic player damage. */
    @Test
    void attacksAndDamagesVisiblePlayer() {
        DoomCombatSession session = session(
                openRoom(), List.of(zombieman(1, 6.0F)), 0L);

        DoomCombatUpdate update =
                session.advance(player(0.0F, 0.0F), Duration.ofMillis(400));

        assertThat(update.state().playerHealth()).isBetween(85, 97);
        assertThat(update.state().combatant(1)).hasValueSatisfying(combatant ->
                assertThat(combatant.activity()).isEqualTo(DoomCombatantActivity.ATTACKING));
        assertThat(update.events())
                .extracting(DoomCombatEvent::type)
                .containsExactly(
                        DoomCombatEvent.Type.COMBATANT_ALERTED,
                        DoomCombatEvent.Type.COMBATANT_ATTACKED,
                        DoomCombatEvent.Type.PLAYER_DAMAGED);
    }

    /** Keeps a dormant enemy from seeing or attacking through a solid wall. */
    @Test
    void blocksEnemySightAtSolidWall() {
        DoomCombatSession session = session(
                dividedRoom(false), List.of(zombieman(1, 6.0F)), 0L);

        DoomCombatUpdate update =
                session.advance(player(0.0F, 0.0F), Duration.ofSeconds(2));

        assertThat(update.events()).isEmpty();
        assertThat(update.state().playerHealth()).isEqualTo(100);
        assertThat(update.state().combatant(1)).hasValueSatisfying(combatant ->
                assertThat(combatant.activity()).isEqualTo(DoomCombatantActivity.DORMANT));
    }

    /** Stops all enemy behavior after repeated attacks reach terminal player death. */
    @Test
    void stopsEnemyBehaviorAfterPlayerDeath() {
        DoomCombatSession session = session(
                openRoom(), List.of(zombieman(1, 6.0F)), 0L);

        DoomCombatUpdate fatalSequence =
                session.advance(player(0.0F, 0.0F), Duration.ofSeconds(40));
        DoomCombatUpdate afterDeath =
                session.advance(player(0.0F, 0.0F), Duration.ofSeconds(2));

        assertThat(fatalSequence.state().isPlayerDead()).isTrue();
        assertThat(fatalSequence.events())
                .extracting(DoomCombatEvent::type)
                .contains(DoomCombatEvent.Type.PLAYER_KILLED);
        assertThat(afterDeath.events()).isEmpty();
        assertThat(afterDeath.state().playerHealth()).isZero();
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

    /** Hits a centered combatant at the closest non-overlapping player separation. */
    @Test
    void hitsCenteredCombatantAtCloseRange() {
        DoomCombatSession session = session(
                openRoom(), List.of(zombieman(1, 3.125F)), 0L);

        DoomCombatUpdate update = session.firePrimary(player(0.0F, 0.0F));

        assertThat(update.state().combatant(1)).hasValueSatisfying(target ->
                assertThat(target.health()).isLessThan(20));
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

    /** Vertically auto-aims at a combatant centered on the player's horizontal bearing. */
    @Test
    void autoAimsVerticallyAtCenteredCombatant() {
        DoomCombatSession session = session(
                openRoom(), List.of(zombieman(1, 6.0F)), 0L);

        DoomCombatUpdate update = session.firePrimary(player(0.0F, 0.75F));

        assertThat(update.state().combatant(1)).hasValueSatisfying(target ->
                assertThat(target.health()).isLessThan(20));
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

    /** Collects one overlapping ammunition actor once and reports the applied amount. */
    @Test
    void collectsAmmunitionOnce() {
        DoomCombatSession session = session(
                openRoom(), List.of(pickup(7, "ammunition-clip", DoomActorCategory.AMMUNITION, 0.0F)), 0L);

        DoomCombatUpdate collected =
                session.advance(player(0.0F, 0.0F), Duration.ofMillis(30));
        DoomCombatUpdate repeated =
                session.advance(player(0.0F, 0.0F), Duration.ofMillis(30));

        assertThat(collected.state().bullets()).isEqualTo(60);
        assertThat(collected.state().collectedPickupThingIndices()).containsExactly(7);
        assertThat(collected.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(DoomCombatEvent.Type.AMMUNITION_PICKED_UP);
            assertThat(event.thingIndex()).isEqualTo(7);
            assertThat(event.amount()).isEqualTo(10);
        });
        assertThat(repeated.state().bullets()).isEqualTo(60);
        assertThat(repeated.events()).isEmpty();
    }

    /** Leaves a capped health pickup present until player damage makes it useful. */
    @Test
    void collectsHealthOnlyWhenUseful() {
        DoomCombatSession session = session(
                openRoom(), List.of(pickup(8, "stimpack", DoomActorCategory.HEALTH, 0.0F)), 0L);

        DoomCombatUpdate atCapacity =
                session.advance(player(0.0F, 0.0F), Duration.ofMillis(30));
        session.damagePlayer(30);
        DoomCombatUpdate collected =
                session.advance(player(0.0F, 0.0F), Duration.ofMillis(30));

        assertThat(atCapacity.state().playerHealth()).isEqualTo(100);
        assertThat(atCapacity.state().isPickupCollected(8)).isFalse();
        assertThat(collected.state().playerHealth()).isEqualTo(80);
        assertThat(collected.state().isPickupCollected(8)).isTrue();
        assertThat(collected.events()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(DoomCombatEvent.Type.HEALTH_PICKED_UP);
            assertThat(event.amount()).isEqualTo(10);
        });
    }

    /** Allows bonus health to exceed the ordinary medical-item limit up to 200. */
    @Test
    void collectsBonusHealthAboveOneHundred() {
        DoomCombatSession session = session(
                openRoom(), List.of(pickup(9, "health-bonus", DoomActorCategory.HEALTH, 0.0F)), 0L);

        DoomCombatUpdate collected =
                session.advance(player(0.0F, 0.0F), Duration.ofMillis(30));

        assertThat(collected.state().playerHealth()).isEqualTo(101);
        assertThat(collected.state().isPickupCollected(9)).isTrue();
    }

    /** Collects a health bonus resting one full classic player height above the player's feet. */
    @Test
    void collectsHealthBonusAtMaximumVerticalReach() {
        DoomCombatSession session = session(
                openRoom(), List.of(pickup(14, "health-bonus", DoomActorCategory.HEALTH, 56.0F / 32.0F)), 0L);

        DoomCombatUpdate collected =
                session.advance(player(0.0F, 0.0F), Duration.ofMillis(30));

        assertThat(collected.state().playerHealth()).isEqualTo(101);
        assertThat(collected.state().isPickupCollected(14)).isTrue();
    }

    /** Rejects a health bonus immediately above the classic player's vertical reach. */
    @Test
    void rejectsHealthBonusAboveMaximumVerticalReach() {
        DoomCombatSession session = session(
                openRoom(), List.of(pickup(15, "health-bonus", DoomActorCategory.HEALTH, 57.0F / 32.0F)), 0L);

        DoomCombatUpdate update =
                session.advance(player(0.0F, 0.0F), Duration.ofMillis(30));

        assertThat(update.state().playerHealth()).isEqualTo(100);
        assertThat(update.state().isPickupCollected(15)).isFalse();
    }

    /** Accepts an item eight units below the player but rejects one unit farther below. */
    @Test
    void appliesClassicLowerPickupReach() {
        DoomCombatSession reachable = session(
                openRoom(), List.of(pickup(16, "health-bonus", DoomActorCategory.HEALTH, -8.0F / 32.0F)), 0L);
        DoomCombatSession unreachable = session(
                openRoom(), List.of(pickup(17, "health-bonus", DoomActorCategory.HEALTH, -9.0F / 32.0F)), 0L);

        DoomCombatState collected =
                reachable.advance(player(0.0F, 0.0F), Duration.ofMillis(30)).state();
        DoomCombatState ignored =
                unreachable.advance(player(0.0F, 0.0F), Duration.ofMillis(30)).state();

        assertThat(collected.isPickupCollected(16)).isTrue();
        assertThat(ignored.isPickupCollected(17)).isFalse();
    }

    /** Preserves a later bullet box when earlier pickups fill the ammunition capacity. */
    @Test
    void leavesAmmunitionAtCapacity() {
        DoomCombatSession session = session(
                openRoom(),
                List.of(
                        pickup(10, "box-of-bullets", DoomActorCategory.AMMUNITION, 0.0F),
                        pickup(11, "box-of-bullets", DoomActorCategory.AMMUNITION, 0.0F),
                        pickup(12, "box-of-bullets", DoomActorCategory.AMMUNITION, 0.0F),
                        pickup(13, "box-of-bullets", DoomActorCategory.AMMUNITION, 0.0F)),
                0L);

        DoomCombatUpdate collected =
                session.advance(player(0.0F, 0.0F), Duration.ofMillis(30));

        assertThat(collected.state().bullets()).isEqualTo(200);
        assertThat(collected.state().collectedPickupThingIndices())
                .containsExactly(10, 11, 12);
        assertThat(collected.state().isPickupCollected(13)).isFalse();
        assertThat(collected.events())
                .extracting(DoomCombatEvent::amount)
                .containsExactly(50, 50, 50);
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

    private static DoomActor pickup(
            int thingIndex, String id, DoomActorCategory category, float floorHeight) {
        DoomActorDefinition definition = new DoomActorDefinition(
                20_000 + thingIndex,
                id,
                id,
                category,
                Optional.of("TESTA"));
        return new DoomActor(thingIndex, definition, 2.0F, floorHeight, -2.0F, 0.0F);
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
        List<DoomMap.Seg> segs = List.of(
                seg(0, 1, 0), seg(1, 2, 1), seg(2, 3, 2), seg(3, 0, 3));
        return new DoomMap(
                "MAP01",
                List.of(),
                new DoomMap.Geometry(vertices, lines, sides, List.of(sector())),
                new DoomMap.Bsp(segs, List.of(new DoomMap.Subsector(4, 0)), List.of()),
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

    private static DoomMap.Seg seg(int start, int end, int linedef) {
        return new DoomMap.Seg(start, end, 0, linedef, 0, 0);
    }
}
