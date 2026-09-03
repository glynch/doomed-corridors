/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.doomedcorridors.actor.DoomActorCatalog;
import io.github.glynch.doomedcorridors.actor.DoomActorCategory;
import io.github.glynch.doomedcorridors.actor.DoomActorDefinition;
import io.github.glynch.doomedcorridors.actor.DoomActorResolution;
import io.github.glynch.doomedcorridors.actor.DoomSkillLevel;
import io.github.glynch.doomedcorridors.map.DoomMap;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Specifies classic thing filtering and headless actor placement. */
final class DoomActorResolverTest {
    /** Resolves normal single-player actors and reports selected unknown types. */
    @Test
    void resolvesSelectedVisibleActors() {
        DoomMap map = map(List.of(
                new DoomMap.Thing(64, 32, 90, 3004, 2),
                new DoomMap.Thing(96, 32, 0, 999, 2),
                new DoomMap.Thing(128, 32, 0, 2001, 4),
                new DoomMap.Thing(160, 32, 0, 11, 2),
                new DoomMap.Thing(192, 32, 0, 3004, 18)));
        DoomActorCatalog catalog = new DoomActorCatalog(List.of(
                definition(3004, "zombieman", DoomActorCategory.ENEMY, "POSSA"),
                definition(2001, "shotgun", DoomActorCategory.WEAPON, "SHOTA"),
                definition(11, "deathmatch-start", DoomActorCategory.MARKER, null)));

        DoomActorResolution result =
                new DoomActorResolver().resolve(Path.of("map.wad"), map, catalog, DoomSkillLevel.NORMAL);

        assertThat(result.actors()).singleElement().satisfies(actor -> {
            assertThat(actor.thingIndex()).isZero();
            assertThat(actor.definition().id()).isEqualTo("zombieman");
            assertThat(actor.x()).isEqualTo(2.0F);
            assertThat(actor.floorHeight()).isEqualTo(0.5F);
            assertThat(actor.z()).isEqualTo(-1.0F);
            assertThat(actor.yawRadians()).isEqualTo((float) Math.PI / 2.0F);
        });
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.actor.thing-unsupported");
            assertThat(diagnostic.location()).isEqualTo("/things/1");
            assertThat(diagnostic.message()).endsWith("999");
        });
    }

    /** Uses the requested classic skill group instead of merging duplicate placements. */
    @Test
    void selectsRequestedSkillGroup() {
        DoomMap map = map(List.of(
                new DoomMap.Thing(64, 32, 0, 3004, 2),
                new DoomMap.Thing(128, 32, 0, 2001, 4)));
        DoomActorCatalog catalog = new DoomActorCatalog(List.of(
                definition(3004, "zombieman", DoomActorCategory.ENEMY, "POSSA"),
                definition(2001, "shotgun", DoomActorCategory.WEAPON, "SHOTA")));

        DoomActorResolution result =
                new DoomActorResolver().resolve(Path.of("map.wad"), map, catalog, DoomSkillLevel.HARD);

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.actors()).singleElement().satisfies(actor -> {
            assertThat(actor.thingIndex()).isEqualTo(1);
            assertThat(actor.definition().id()).isEqualTo("shotgun");
        });
    }

    private static DoomActorDefinition definition(
            int thingType, String id, DoomActorCategory category, String spriteFrame) {
        return new DoomActorDefinition(
                thingType, id, id, category, Optional.ofNullable(spriteFrame));
    }

    private static DoomMap map(List<DoomMap.Thing> things) {
        List<DoomMap.Vertex> vertices = List.of(
                new DoomMap.Vertex(0, 0),
                new DoomMap.Vertex(0, 256),
                new DoomMap.Vertex(256, 256),
                new DoomMap.Vertex(256, 0));
        List<DoomMap.Linedef> lines = List.of(
                line(0, 1, 0), line(1, 2, 1), line(2, 3, 2), line(3, 0, 3));
        return new DoomMap(
                "MAP01",
                things,
                new DoomMap.Geometry(
                        vertices,
                        lines,
                        List.of(side(), side(), side(), side()),
                        List.of(new DoomMap.Sector(16, 128, "FLOOR", "CEILING", 160, 0, 0))),
                new DoomMap.Bsp(
                        List.of(seg(0, 1, 0), seg(1, 2, 1), seg(2, 3, 2), seg(3, 0, 3)),
                        List.of(new DoomMap.Subsector(4, 0)),
                        List.of()),
                List.of(0),
                new DoomMap.Blockmap(0, 0, 1, 1, List.of(List.of())));
    }

    private static DoomMap.Linedef line(int start, int end, int side) {
        return new DoomMap.Linedef(start, end, 0, 0, 0, side, -1);
    }

    private static DoomMap.Sidedef side() {
        return new DoomMap.Sidedef(0, 0, "-", "-", "WALL", 0);
    }

    private static DoomMap.Seg seg(int start, int end, int line) {
        return new DoomMap.Seg(start, end, 0, line, 0, 0);
    }
}
