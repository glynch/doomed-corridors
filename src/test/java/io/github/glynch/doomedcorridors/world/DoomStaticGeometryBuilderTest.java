/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.material.DoomMaterial;
import io.github.glynch.doomedcorridors.material.RgbaImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Specifies renderer-independent construction of classic Doom static geometry. */
final class DoomStaticGeometryBuilderTest {
    /** Builds convex subsector planes, one-sided walls, and the player view in world units. */
    @Test
    void buildsClosedRoom() {
        DoomGeometryBuildResult result = new DoomStaticGeometryBuilder().build(closedRoom(), materials());

        assertThat(result.diagnostics()).isEmpty();
        DoomStaticGeometry geometry = result.geometry().orElseThrow();
        assertThat(geometry.surfaces()).hasSize(6);
        assertThat(geometry.surfaces())
                .extracting(DoomSurface::type)
                .containsExactlyInAnyOrder(
                        DoomSurface.Type.FLOOR,
                        DoomSurface.Type.CEILING,
                        DoomSurface.Type.MIDDLE_WALL,
                        DoomSurface.Type.MIDDLE_WALL,
                        DoomSurface.Type.MIDDLE_WALL,
                        DoomSurface.Type.MIDDLE_WALL);

        DoomSurface floor = surface(geometry, DoomSurface.Type.FLOOR);
        assertThat(floor.materialName()).isEqualTo("FLOOR");
        assertThat(floor.mesh().vertexCount()).isEqualTo(4);
        assertThat(floor.mesh().triangleCount()).isEqualTo(2);
        assertThat(floor.mesh().position(0)).containsExactly(0.0F, 0.0F, 0.0F);
        assertThat(floor.mesh().normal(0)).containsExactly(0.0F, 1.0F, 0.0F);

        DoomSurface ceiling = surface(geometry, DoomSurface.Type.CEILING);
        assertThat(ceiling.materialName()).isEqualTo("CEILING");
        assertThat(ceiling.mesh().position(0)[1]).isEqualTo(4.0F);
        assertThat(ceiling.mesh().normal(0)).containsExactly(0.0F, -1.0F, 0.0F);

        assertThat(geometry.playerStart().x()).isEqualTo(2.0F);
        assertThat(geometry.playerStart().eyeHeight()).isEqualTo(41.0F / 32.0F);
        assertThat(geometry.playerStart().z()).isEqualTo(-2.0F);
        assertThat(geometry.playerStart().yawRadians()).isEqualTo(0.0F);
    }

    /** Builds upper, lower, and masked middle spans for a two-sided boundary. */
    @Test
    void buildsTwoSidedWallSpans() {
        DoomGeometryBuildResult result =
                new DoomStaticGeometryBuilder().build(twoSidedBoundary(), materials());

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.geometry().orElseThrow().surfaces())
                .filteredOn(surface -> surface.type() != DoomSurface.Type.FLOOR
                        && surface.type() != DoomSurface.Type.CEILING)
                .extracting(DoomSurface::type, DoomSurface::materialName)
                .contains(
                        org.assertj.core.groups.Tuple.tuple(DoomSurface.Type.UPPER_WALL, "UPPER"),
                        org.assertj.core.groups.Tuple.tuple(DoomSurface.Type.LOWER_WALL, "LOWER"),
                        org.assertj.core.groups.Tuple.tuple(DoomSurface.Type.MASKED_MIDDLE_WALL, "GRATE"));
    }

    /** Rejects maps that reference material images absent from the imported material set. */
    @Test
    void reportsMissingMaterial() {
        DoomMapMaterials missingWalls =
                new DoomMapMaterials("MAP01", Map.of(), materials().flats());

        DoomGeometryBuildResult result = new DoomStaticGeometryBuilder().build(closedRoom(), missingWalls);

        assertThat(result.geometry()).isEmpty();
        assertThat(result.diagnostics())
                .extracting(DoomGeometryDiagnostic::code, DoomGeometryDiagnostic::location)
                .contains(org.assertj.core.groups.Tuple.tuple("doom.geometry.material", "/linedefs/0/right/middle"));
    }

    private static DoomSurface surface(DoomStaticGeometry geometry, DoomSurface.Type type) {
        return geometry.surfaces().stream()
                .filter(candidate -> candidate.type() == type)
                .findFirst()
                .orElseThrow();
    }

    private static DoomMap closedRoom() {
        List<DoomMap.Vertex> vertices = List.of(
                new DoomMap.Vertex(0, 0),
                new DoomMap.Vertex(0, 128),
                new DoomMap.Vertex(128, 128),
                new DoomMap.Vertex(128, 0));
        List<DoomMap.Linedef> linedefs = List.of(
                new DoomMap.Linedef(0, 1, 0, 0, 0, 0, -1),
                new DoomMap.Linedef(1, 2, 0, 0, 0, 1, -1),
                new DoomMap.Linedef(2, 3, 0, 0, 0, 2, -1),
                new DoomMap.Linedef(3, 0, 0, 0, 0, 3, -1));
        List<DoomMap.Sidedef> sidedefs = List.of(
                side("-", "-", "WALL", 0),
                side("-", "-", "WALL", 0),
                side("-", "-", "WALL", 0),
                side("-", "-", "WALL", 0));
        List<DoomMap.Seg> segs = List.of(
                new DoomMap.Seg(0, 1, 0, 0, 0, 0),
                new DoomMap.Seg(1, 2, 0, 1, 0, 0),
                new DoomMap.Seg(2, 3, 0, 2, 0, 0),
                new DoomMap.Seg(3, 0, 0, 3, 0, 0));
        return map(
                List.of(new DoomMap.Thing(64, 64, 0, 1, 7)),
                vertices,
                linedefs,
                sidedefs,
                List.of(sector(0, 128)),
                segs,
                List.of(new DoomMap.Subsector(4, 0)));
    }

    private static DoomMap twoSidedBoundary() {
        List<DoomMap.Vertex> vertices = List.of(
                new DoomMap.Vertex(0, 0), new DoomMap.Vertex(0, 128), new DoomMap.Vertex(128, 0));
        List<DoomMap.Linedef> linedefs =
                List.of(new DoomMap.Linedef(0, 1, 0, 0, 0, 0, 1));
        List<DoomMap.Sidedef> sidedefs = List.of(
                side("UPPER", "LOWER", "GRATE", 0), side("UPPER", "LOWER", "GRATE", 1));
        List<DoomMap.Seg> segs = List.of(new DoomMap.Seg(0, 1, 0, 0, 0, 0));
        return map(
                List.of(new DoomMap.Thing(0, 0, 0, 1, 7)),
                vertices,
                linedefs,
                sidedefs,
                List.of(sector(0, 128), sector(32, 96)),
                segs,
                List.of(new DoomMap.Subsector(1, 0)));
    }

    private static DoomMap map(
            List<DoomMap.Thing> things,
            List<DoomMap.Vertex> vertices,
            List<DoomMap.Linedef> linedefs,
            List<DoomMap.Sidedef> sidedefs,
            List<DoomMap.Sector> sectors,
            List<DoomMap.Seg> segs,
            List<DoomMap.Subsector> subsectors) {
        return new DoomMap(
                "MAP01",
                things,
                new DoomMap.Geometry(vertices, linedefs, sidedefs, sectors),
                new DoomMap.Bsp(segs, subsectors, List.of()),
                List.of(0),
                new DoomMap.Blockmap(0, 0, 1, 1, List.of(List.of())));
    }

    private static DoomMap.Sidedef side(String upper, String lower, String middle, int sector) {
        return new DoomMap.Sidedef(0, 0, upper, lower, middle, sector);
    }

    private static DoomMap.Sector sector(int floor, int ceiling) {
        return new DoomMap.Sector(floor, ceiling, "FLOOR", "CEILING", 160, 0, 0);
    }

    private static DoomMapMaterials materials() {
        Map<String, DoomMaterial> walls = new LinkedHashMap<>();
        walls.put("WALL", material("WALL", DoomMaterial.Kind.WALL_TEXTURE, 64, 128));
        walls.put("UPPER", material("UPPER", DoomMaterial.Kind.WALL_TEXTURE, 64, 64));
        walls.put("LOWER", material("LOWER", DoomMaterial.Kind.WALL_TEXTURE, 64, 64));
        walls.put("GRATE", material("GRATE", DoomMaterial.Kind.WALL_TEXTURE, 64, 64));
        Map<String, DoomMaterial> flats = new LinkedHashMap<>();
        flats.put("FLOOR", material("FLOOR", DoomMaterial.Kind.FLAT, 64, 64));
        flats.put("CEILING", material("CEILING", DoomMaterial.Kind.FLAT, 64, 64));
        return new DoomMapMaterials("MAP01", walls, flats);
    }

    private static DoomMaterial material(String name, DoomMaterial.Kind kind, int width, int height) {
        byte[] pixels = new byte[width * height * 4];
        java.util.Arrays.fill(pixels, (byte) 0xff);
        return new DoomMaterial(name, kind, new RgbaImage(width, height, pixels), List.of());
    }
}
