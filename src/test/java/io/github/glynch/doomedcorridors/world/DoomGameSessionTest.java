/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.glynch.doomedcorridors.map.DoomMap;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Specifies deterministic player movement through the headless game-session seam. */
final class DoomGameSessionTest {
    /** Stops the player radius at a one-sided wall. */
    @Test
    void blocksSolidWall() {
        DoomGameSession session = DoomGameSession.create(
                room(128, 108, 64), new DoomPlayerStart(108.0F / 32.0F, 41.0F / 32.0F, -2.0F, 0.0F));

        DoomPlayerState player =
                session.advance(new DoomPlayerCommand(1.0F, 0.0F, 0.0F, 0.0F, 0.0F), Duration.ofMillis(30));

        assertThat(player.x()).isBetween(3.48F, 3.501F);
        assertThat(player.z()).isEqualTo(-2.0F);
    }

    /** Crosses a two-sided portal when its step and vertical opening fit the player. */
    @Test
    void crossesPassablePortalAndStepsUp() {
        DoomGameSession session = DoomGameSession.create(
                twoRooms(16, 128), new DoomPlayerStart(2.0F, 41.0F / 32.0F, -2.0F, 0.0F));

        DoomPlayerState player =
                session.advance(new DoomPlayerCommand(1.0F, 0.0F, 0.0F, 0.0F, 0.0F), Duration.ofMillis(300));

        assertThat(player.x()).isGreaterThan(4.0F);
        assertThat(player.eyeHeight()).isEqualTo(57.0F / 32.0F);
    }

    /** Rejects a portal whose destination floor exceeds the Doom step height. */
    @Test
    void blocksStepAboveTwentyFourMapUnits() {
        DoomGameSession session = DoomGameSession.create(
                twoRooms(32, 128), new DoomPlayerStart(2.0F, 41.0F / 32.0F, -2.0F, 0.0F));

        DoomPlayerState player =
                session.advance(new DoomPlayerCommand(1.0F, 0.0F, 0.0F, 0.0F, 0.0F), Duration.ofMillis(300));

        assertThat(player.x()).isLessThanOrEqualTo(3.501F);
    }

    /** Rejects a portal whose opening is shorter than the Doom player. */
    @Test
    void blocksOpeningWithoutPlayerClearance() {
        DoomGameSession session = DoomGameSession.create(
                twoRooms(16, 64), new DoomPlayerStart(2.0F, 41.0F / 32.0F, -2.0F, 0.0F));

        DoomPlayerState player =
                session.advance(new DoomPlayerCommand(1.0F, 0.0F, 0.0F, 0.0F, 0.0F), Duration.ofMillis(300));

        assertThat(player.x()).isLessThanOrEqualTo(3.501F);
    }

    /** Preserves tangential movement when a diagonal command reaches a wall. */
    @Test
    void slidesAlongWall() {
        DoomGameSession session = DoomGameSession.create(
                room(128, 108, 64), new DoomPlayerStart(108.0F / 32.0F, 41.0F / 32.0F, -2.0F, 0.0F));

        DoomPlayerState player =
                session.advance(new DoomPlayerCommand(1.0F, 1.0F, 0.0F, 0.0F, 0.0F), Duration.ofMillis(100));

        assertThat(player.x()).isLessThanOrEqualTo(3.501F);
        assertThat(player.z()).isGreaterThan(-2.0F);
    }

    /** Produces the same fixed-step result regardless of render-frame grouping. */
    @Test
    void advancesAtAStableFixedStep() {
        DoomMap map = room(1024, 512, 512);
        DoomPlayerStart start = new DoomPlayerStart(16.0F, 41.0F / 32.0F, -16.0F, 0.0F);
        DoomGameSession singleFrame = DoomGameSession.create(map, start);
        DoomGameSession splitFrames = DoomGameSession.create(map, start);
        DoomPlayerCommand command = new DoomPlayerCommand(1.0F, 0.0F, 0.0F, 0.0F, 0.0F);

        DoomPlayerState expected = singleFrame.advance(command, Duration.ofMillis(100));
        DoomPlayerState actual = splitFrames.player();
        for (int frame = 0; frame < 10; frame++) {
            actual = splitFrames.advance(command, Duration.ofMillis(10));
        }

        assertThat(actual).isEqualTo(expected);
    }

    /** Applies frame-relative mouse look even when no fixed movement step is due. */
    @Test
    void updatesViewWithoutMovementStep() {
        DoomGameSession session = DoomGameSession.create(
                room(128, 64, 64), new DoomPlayerStart(2.0F, 41.0F / 32.0F, -2.0F, 0.0F));

        DoomPlayerState player =
                session.advance(new DoomPlayerCommand(0.0F, 0.0F, 0.0F, 0.25F, -0.1F), Duration.ZERO);

        assertThat(player.yawRadians()).isEqualTo(0.25F);
        assertThat(player.pitchRadians()).isEqualTo(-0.1F);
        assertThat(player.x()).isEqualTo(2.0F);
        assertThat(player.z()).isEqualTo(-2.0F);
    }

    /** Applies held keyboard turning at the fixed simulation rate in addition to mouse yaw. */
    @Test
    void combinesKeyboardTurnWithMouseYaw() {
        DoomGameSession session = DoomGameSession.create(
                room(128, 64, 64), new DoomPlayerStart(2.0F, 41.0F / 32.0F, -2.0F, 0.0F));

        DoomPlayerState player = session.advance(
                new DoomPlayerCommand(0.0F, 0.0F, 1.0F, 0.25F, 0.0F),
                Duration.ofNanos(1_000_000_000L / 35L));

        assertThat(player.yawRadians()).isCloseTo(0.25F + (float) Math.PI / 35.0F, within(0.000_001F));
    }

    private static DoomMap room(int size, int playerX, int playerY) {
        List<DoomMap.Vertex> vertices = List.of(
                new DoomMap.Vertex(0, 0),
                new DoomMap.Vertex(0, size),
                new DoomMap.Vertex(size, size),
                new DoomMap.Vertex(size, 0));
        List<DoomMap.Linedef> linedefs = List.of(
                line(0, 1, 0, -1), line(1, 2, 1, -1), line(2, 3, 2, -1), line(3, 0, 3, -1));
        List<DoomMap.Sidedef> sides = List.of(side(0), side(0), side(0), side(0));
        List<DoomMap.Seg> segs = List.of(
                seg(0, 1, 0), seg(1, 2, 1), seg(2, 3, 2), seg(3, 0, 3));
        return map(
                new DoomMap.Thing(playerX, playerY, 0, 1, 7),
                new DoomMap.Geometry(vertices, linedefs, sides, List.of(sector(0, 128))),
                new DoomMap.Bsp(segs, List.of(new DoomMap.Subsector(4, 0)), List.of()));
    }

    private static DoomMap twoRooms(int destinationFloor, int destinationCeiling) {
        List<DoomMap.Vertex> vertices = List.of(
                new DoomMap.Vertex(0, 0),
                new DoomMap.Vertex(0, 128),
                new DoomMap.Vertex(128, 128),
                new DoomMap.Vertex(256, 128),
                new DoomMap.Vertex(256, 0),
                new DoomMap.Vertex(128, 0));
        List<DoomMap.Linedef> linedefs = List.of(
                line(0, 1, 0, -1),
                line(1, 2, 1, -1),
                line(2, 3, 2, -1),
                line(3, 4, 3, -1),
                line(4, 5, 4, -1),
                line(5, 0, 5, -1),
                new DoomMap.Linedef(5, 2, 4, 0, 0, 6, 7));
        List<DoomMap.Sidedef> sides = new ArrayList<>();
        sides.add(side(0));
        sides.add(side(0));
        sides.add(side(1));
        sides.add(side(1));
        sides.add(side(1));
        sides.add(side(0));
        sides.add(side(1));
        sides.add(side(0));
        List<DoomMap.Seg> segs = List.of(seg(0, 1, 0), seg(3, 4, 3));
        DoomMap.BoundingBox bounds = new DoomMap.BoundingBox(128, 0, 0, 256);
        DoomMap.Node node = new DoomMap.Node(
                new DoomMap.Partition(128, 0, 0, 128),
                new DoomMap.NodeSide(bounds, new DoomMap.NodeChild(true, 1)),
                new DoomMap.NodeSide(bounds, new DoomMap.NodeChild(true, 0)));
        return map(
                new DoomMap.Thing(64, 64, 0, 1, 7),
                new DoomMap.Geometry(
                        vertices,
                        linedefs,
                        sides,
                        List.of(sector(0, 128), sector(destinationFloor, destinationCeiling))),
                new DoomMap.Bsp(
                        segs,
                        List.of(new DoomMap.Subsector(1, 0), new DoomMap.Subsector(1, 1)),
                        List.of(node)));
    }

    private static DoomMap map(
            DoomMap.Thing player, DoomMap.Geometry geometry, DoomMap.Bsp bsp) {
        return new DoomMap(
                "MAP01",
                List.of(player),
                geometry,
                bsp,
                List.of(0),
                new DoomMap.Blockmap(0, 0, 1, 1, List.of(List.of())));
    }

    private static DoomMap.Linedef line(int start, int end, int rightSide, int leftSide) {
        return new DoomMap.Linedef(start, end, 0, 0, 0, rightSide, leftSide);
    }

    private static DoomMap.Sidedef side(int sector) {
        return new DoomMap.Sidedef(0, 0, "-", "-", "WALL", sector);
    }

    private static DoomMap.Sector sector(int floor, int ceiling) {
        return new DoomMap.Sector(floor, ceiling, "FLOOR", "CEILING", 160, 0, 0);
    }

    private static DoomMap.Seg seg(int start, int end, int linedef) {
        return new DoomMap.Seg(start, end, 0, linedef, 0, 0);
    }
}
