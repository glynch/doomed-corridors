/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.material.DoomMaterial;

/** Builds immutable static surfaces from decoded classic Doom map records. */
public final class DoomStaticGeometryBuilder {
    private static final int PLAYER_ONE_THING_TYPE = 1;
    private static final float PLAYER_VIEW_HEIGHT = 41.0F;
    private static final int DONT_PEG_TOP = 0x0008;
    private static final int DONT_PEG_BOTTOM = 0x0010;
    private static final String NO_TEXTURE = "-";
    private static final String SKY_FLAT = "F_SKY1";

    /**
     * Builds all supported static planes and wall spans referenced by a decoded map.
     *
     * @param map validated classic Doom map
     * @param materials imported materials referenced by the map
     * @return geometry when no error diagnostics were produced
     */
    public DoomGeometryBuildResult build(DoomMap map, DoomMapMaterials materials) {
        DoomMap validMap = Objects.requireNonNull(map, "map");
        DoomMapMaterials validMaterials = Objects.requireNonNull(materials, "materials");
        BuildState state = new BuildState(validMap, validMaterials);
        addPlanes(state);
        addWalls(state);
        DoomPlayerStart playerStart = findPlayerStart(state);
        if (state.hasErrors() || playerStart == null) {
            return new DoomGeometryBuildResult(Optional.empty(), state.diagnostics);
        }
        return new DoomGeometryBuildResult(
                Optional.of(new DoomStaticGeometry(state.surfaces, playerStart)), state.diagnostics);
    }

    /** Converts BSP subsectors into convex floor and ceiling fans. */
    private static void addPlanes(BuildState state) {
        List<List<PlanarPoint>> polygons = subsectorPolygons(state.map);
        for (int subsectorIndex = 0; subsectorIndex < state.map.subsectors().size(); subsectorIndex++) {
            List<PlanarPoint> vertices = polygons.get(subsectorIndex);
            if (vertices.size() < 3) {
                continue;
            }
            int sectorIndex = sectorForSubsector(state.map, subsectorIndex);
            DoomMap.Sector sector = state.map.sectors().get(sectorIndex);
            if (!SKY_FLAT.equals(sector.floorTexture())) {
                addPlane(state, vertices, sectorIndex, sector.floorHeight(), sector.floorTexture(), true);
            }
            if (!SKY_FLAT.equals(sector.ceilingTexture())) {
                addPlane(state, vertices, sectorIndex, sector.ceilingHeight(), sector.ceilingTexture(), false);
            }
        }
    }

    /** Creates one horizontal surface after resolving its source flat. */
    private static void addPlane(
            BuildState state,
            List<PlanarPoint> vertices,
            int sectorIndex,
            int height,
            String materialName,
            boolean floor) {
        DoomMaterial material = state.materials.flats().get(materialName);
        if (material == null) {
            state.error(
                    "doom.geometry.material",
                    "/sectors/" + sectorIndex + (floor ? "/floor" : "/ceiling"),
                    "Imported flat is missing: " + materialName);
            return;
        }
        int vertexCount = vertices.size();
        float[] positions = new float[vertexCount * 3];
        float[] normals = new float[vertexCount * 3];
        float[] textureCoordinates = new float[vertexCount * 2];
        for (int index = 0; index < vertexCount; index++) {
            PlanarPoint vertex = vertices.get(index);
            int positionOffset = index * 3;
            positions[positionOffset] = DoomUnits.toWorld((float) vertex.x());
            positions[positionOffset + 1] = DoomUnits.toWorld(height);
            positions[positionOffset + 2] = DoomUnits.yToWorldZ(vertex.y());
            normals[positionOffset + 1] = floor ? 1.0F : -1.0F;
            int textureOffset = index * 2;
            textureCoordinates[textureOffset] = (float) (vertex.x() / material.image().width());
            textureCoordinates[textureOffset + 1] = (float) (-vertex.y() / material.image().height());
        }
        int[] indices = planeIndices(vertices, floor);
        DoomSurface.Type type = floor ? DoomSurface.Type.FLOOR : DoomSurface.Type.CEILING;
        state.surfaces.add(new DoomSurface(
                type,
                materialName,
                sectorIndex,
                new DoomMeshData(positions, normals, textureCoordinates, indices),
                floor ? OptionalInt.empty() : OptionalInt.of(sectorIndex)));
    }

    /** Triangulates one convex polygon while preserving the requested visible face. */
    private static int[] planeIndices(List<PlanarPoint> vertices, boolean floor) {
        boolean counterClockwise = signedArea(vertices) > 0.0;
        boolean forward = floor == counterClockwise;
        int[] indices = new int[(vertices.size() - 2) * 3];
        for (int triangle = 0; triangle < vertices.size() - 2; triangle++) {
            int offset = triangle * 3;
            indices[offset] = 0;
            indices[offset + 1] = forward ? triangle + 1 : triangle + 2;
            indices[offset + 2] = forward ? triangle + 2 : triangle + 1;
        }
        return indices;
    }

    /** Adds the visible wall spans from both sides of every linedef. */
    private static void addWalls(BuildState state) {
        for (int linedefIndex = 0; linedefIndex < state.map.linedefs().size(); linedefIndex++) {
            DoomMap.Linedef linedef = state.map.linedefs().get(linedefIndex);
            addWallSide(state, linedefIndex, linedef, WallSide.RIGHT);
            if (linedef.leftSidedef() >= 0) {
                addWallSide(state, linedefIndex, linedef, WallSide.LEFT);
            }
        }
    }

    /** Adds the opaque or portal wall spans visible from one linedef side. */
    private static void addWallSide(
            BuildState state, int linedefIndex, DoomMap.Linedef linedef, WallSide side) {
        int sidedefIndex = side.sidedefIndex(linedef);
        int neighborIndex = side.neighborSidedefIndex(linedef);
        DoomMap.Sidedef sidedef = state.map.sidedefs().get(sidedefIndex);
        DoomMap.Sector sector = state.map.sectors().get(sidedef.sector());
        String sideLocation = "/linedefs/" + linedefIndex + '/' + side.locationSegment();
        if (neighborIndex < 0) {
            addWallSpan(
                    state,
                    new WallSpan(
                            linedef,
                            sidedef,
                            sideLocation,
                            side,
                            new WallRange(
                                    sector.floorHeight(), sector.ceilingHeight(), sector.ceilingHeight()),
                            new WallAppearance(
                                    sidedef.middleTexture(), DoomSurface.Type.MIDDLE_WALL),
                            OptionalInt.empty()),
                    sidedef.sector());
            return;
        }
        int neighborSectorIndex = state.map.sidedefs().get(neighborIndex).sector();
        DoomMap.Sector neighbor = state.map.sectors().get(neighborSectorIndex);
        addPortalSpans(
                state,
                new PortalWallSide(
                        linedef,
                        sidedef,
                        new SectorReference(sidedef.sector(), sector),
                        new SectorReference(neighborSectorIndex, neighbor),
                        sideLocation,
                        side));
    }

    /** Selects upper, lower, and masked-middle spans for a two-sided portal. */
    private static void addPortalSpans(BuildState state, PortalWallSide wall) {
        DoomMap.Linedef linedef = wall.linedef();
        DoomMap.Sidedef sidedef = wall.sidedef();
        DoomMap.Sector sector = wall.sector().value();
        DoomMap.Sector neighbor = wall.neighbor().value();
        if (neighbor.ceilingHeight() < sector.ceilingHeight()) {
            int textureTop = (linedef.flags() & DONT_PEG_TOP) != 0
                    ? sector.ceilingHeight()
                    : neighbor.ceilingHeight() + textureHeight(state, sidedef.upperTexture());
            addWallSpan(
                    state,
                    new WallSpan(
                            linedef,
                            sidedef,
                            wall.location(),
                            wall.side(),
                            new WallRange(
                                    neighbor.ceilingHeight(), sector.ceilingHeight(), textureTop),
                            new WallAppearance(
                                    sidedef.upperTexture(), DoomSurface.Type.UPPER_WALL),
                            OptionalInt.of(wall.neighbor().index())),
                    wall.sector().index());
        }
        if (neighbor.floorHeight() > sector.floorHeight()) {
            int textureTop = (linedef.flags() & DONT_PEG_BOTTOM) != 0
                    ? sector.ceilingHeight()
                    : neighbor.floorHeight();
            addWallSpan(
                    state,
                    new WallSpan(
                            linedef,
                            sidedef,
                            wall.location(),
                            wall.side(),
                            new WallRange(
                                    sector.floorHeight(), neighbor.floorHeight(), textureTop),
                            new WallAppearance(
                                    sidedef.lowerTexture(), DoomSurface.Type.LOWER_WALL),
                            OptionalInt.empty()),
                    wall.sector().index());
        }
        int openingBottom = Math.max(sector.floorHeight(), neighbor.floorHeight());
        int openingTop = Math.min(sector.ceilingHeight(), neighbor.ceilingHeight());
        if (!NO_TEXTURE.equals(sidedef.middleTexture()) && openingTop > openingBottom) {
            int textureTop = (linedef.flags() & DONT_PEG_BOTTOM) != 0
                    ? openingBottom + textureHeight(state, sidedef.middleTexture())
                    : openingTop;
            addWallSpan(
                    state,
                    new WallSpan(
                            linedef,
                            sidedef,
                            wall.location(),
                            wall.side(),
                            new WallRange(openingBottom, openingTop, textureTop),
                            new WallAppearance(
                                    sidedef.middleTexture(), DoomSurface.Type.MASKED_MIDDLE_WALL),
                            OptionalInt.empty()),
                    wall.sector().index());
        }
    }

    /** Creates one quad or reports its unresolved wall material. */
    private static void addWallSpan(BuildState state, WallSpan span, int sectorIndex) {
        String role = switch (span.appearance.type) {
            case UPPER_WALL -> "upper";
            case LOWER_WALL -> "lower";
            case MIDDLE_WALL, MASKED_MIDDLE_WALL -> "middle";
            default -> throw new IllegalArgumentException("not a wall type: " + span.appearance.type);
        };
        DoomMaterial material = state.materials.wallTextures().get(span.appearance.materialName);
        if (NO_TEXTURE.equals(span.appearance.materialName) || material == null) {
            state.error(
                    "doom.geometry.material",
                    span.location + "/" + role,
                    "Imported wall texture is missing: " + span.appearance.materialName);
            return;
        }
        DoomMap.Vertex first = state.map.vertices().get(span.linedef.startVertex());
        DoomMap.Vertex second = state.map.vertices().get(span.linedef.endVertex());
        if (span.side == WallSide.LEFT) {
            DoomMap.Vertex replacement = first;
            first = second;
            second = replacement;
        }
        float[] positions = wallPositions(first, second, span.range.bottom, span.range.top);
        float[] normals = wallNormals(positions);
        double deltaX = (double) second.x() - first.x();
        double deltaY = (double) second.y() - first.y();
        float length = (float) Math.hypot(deltaX, deltaY);
        float startU = (float) span.sidedef.xOffset() / material.image().width();
        float endU = startU + length / material.image().width();
        float textureTop = (float) span.range.textureTop + span.sidedef.yOffset();
        float topV = (textureTop - span.range.top) / material.image().height();
        float bottomV = (textureTop - span.range.bottom) / material.image().height();
        float[] textureCoordinates = {startU, bottomV, endU, bottomV, endU, topV, startU, topV};
        state.surfaces.add(new DoomSurface(
                span.appearance.type,
                span.appearance.materialName,
                sectorIndex,
                new DoomMeshData(positions, normals, textureCoordinates, new int[] {0, 1, 2, 0, 2, 3}),
                span.movingCeilingSector));
    }

    /** Builds the four bottom-to-top wall vertices. */
    private static float[] wallPositions(
            DoomMap.Vertex first, DoomMap.Vertex second, int bottom, int top) {
        float firstX = DoomUnits.toWorld(first.x());
        float firstZ = DoomUnits.yToWorldZ(first.y());
        float secondX = DoomUnits.toWorld(second.x());
        float secondZ = DoomUnits.yToWorldZ(second.y());
        float bottomY = DoomUnits.toWorld(bottom);
        float topY = DoomUnits.toWorld(top);
        return new float[] {
            firstX, bottomY, firstZ,
            secondX, bottomY, secondZ,
            secondX, topY, secondZ,
            firstX, topY, firstZ
        };
    }

    /** Calculates the repeated inward normal for a wall quad. */
    private static float[] wallNormals(float[] positions) {
        float deltaX = positions[3] - positions[0];
        float deltaZ = positions[5] - positions[2];
        float inverseLength = 1.0F / (float) Math.hypot(deltaX, deltaZ);
        float normalX = -deltaZ * inverseLength;
        float normalZ = deltaX * inverseLength;
        return new float[] {
            normalX, 0.0F, normalZ,
            normalX, 0.0F, normalZ,
            normalX, 0.0F, normalZ,
            normalX, 0.0F, normalZ
        };
    }

    /** Resolves player one and raises the eye above the containing sector floor. */
    private static DoomPlayerStart findPlayerStart(BuildState state) {
        DoomMap.Thing player = state.map.things().stream()
                .filter(thing -> thing.type() == PLAYER_ONE_THING_TYPE)
                .findFirst()
                .orElse(null);
        if (player == null) {
            state.error("doom.geometry.player-start", "/things", "Map does not contain a player-one start");
            return null;
        }
        int sectorIndex = sectorContaining(state.map, player.x(), player.y());
        int floorHeight = state.map.sectors().get(sectorIndex).floorHeight();
        return new DoomPlayerStart(
                DoomUnits.toWorld(player.x()),
                DoomUnits.toWorld(floorHeight + PLAYER_VIEW_HEIGHT),
                DoomUnits.yToWorldZ(player.y()),
                (float) Math.toRadians(player.angle()));
    }

    /** Locates a point by following the same BSP child convention as the classic renderer. */
    private static int sectorContaining(DoomMap map, int x, int y) {
        if (map.nodes().isEmpty()) {
            return sectorForSubsector(map, 0);
        }
        DoomMap.NodeChild child = new DoomMap.NodeChild(false, map.nodes().size() - 1);
        while (!child.subsector()) {
            DoomMap.Node node = map.nodes().get(child.index());
            double side = partitionSide(node.partition(), x, y);
            child = side < 0.0 ? node.right().child() : node.left().child();
        }
        return sectorForSubsector(map, child.index());
    }

    /** Recovers every convex BSP leaf polygon, including edges implicit in node partitions. */
    private static List<List<PlanarPoint>> subsectorPolygons(DoomMap map) {
        if (map.nodes().isEmpty()) {
            return List.of(segPolygon(map, 0));
        }
        List<List<PlanarPoint>> polygons = new ArrayList<>(map.subsectors().size());
        for (int index = 0; index < map.subsectors().size(); index++) {
            polygons.add(List.of());
        }
        DoomMap.NodeChild root = new DoomMap.NodeChild(false, map.nodes().size() - 1);
        recoverPolygons(map, root, mapBounds(map), polygons);
        return polygons;
    }

    /** Recursively clips one convex region into the node's right and left child regions. */
    private static void recoverPolygons(
            DoomMap map,
            DoomMap.NodeChild child,
            List<PlanarPoint> polygon,
            List<List<PlanarPoint>> polygons) {
        if (child.subsector()) {
            polygons.set(child.index(), compactPolygon(polygon));
            return;
        }
        DoomMap.Node node = map.nodes().get(child.index());
        List<PlanarPoint> right = clip(polygon, node.partition(), true);
        List<PlanarPoint> left = clip(polygon, node.partition(), false);
        recoverPolygons(map, node.right().child(), right, polygons);
        recoverPolygons(map, node.left().child(), left, polygons);
    }

    /** Clips a convex polygon to one side of a BSP partition. */
    private static List<PlanarPoint> clip(
            List<PlanarPoint> polygon, DoomMap.Partition partition, boolean rightSide) {
        if (polygon.isEmpty()) {
            return polygon;
        }
        List<PlanarPoint> result = new ArrayList<>(polygon.size() + 1);
        PlanarPoint previous = polygon.getLast();
        double previousSide = partitionSide(partition, previous.x(), previous.y());
        boolean previousInside = inside(previousSide, rightSide);
        for (PlanarPoint current : polygon) {
            double currentSide = partitionSide(partition, current.x(), current.y());
            boolean currentInside = inside(currentSide, rightSide);
            if (currentInside != previousInside) {
                double amount = previousSide / (previousSide - currentSide);
                result.add(new PlanarPoint(
                        previous.x() + amount * (current.x() - previous.x()),
                        previous.y() + amount * (current.y() - previous.y())));
            }
            if (currentInside) {
                result.add(current);
            }
            previous = current;
            previousSide = currentSide;
            previousInside = currentInside;
        }
        return compactPolygon(result);
    }

    /** Uses classic side zero for the negative partition half-plane. */
    private static boolean inside(double side, boolean rightSide) {
        return rightSide ? side <= 0.000_001 : side >= -0.000_001;
    }

    /** Calculates the partition cross product used for BSP side selection. */
    private static double partitionSide(DoomMap.Partition partition, double x, double y) {
        return partition.deltaX() * (y - partition.y()) - partition.deltaY() * (x - partition.x());
    }

    /** Returns a finite rectangle enclosing every explicit map vertex. */
    private static List<PlanarPoint> mapBounds(DoomMap map) {
        int minimumX = map.vertices().stream().mapToInt(DoomMap.Vertex::x).min().orElseThrow();
        int maximumX = map.vertices().stream().mapToInt(DoomMap.Vertex::x).max().orElseThrow();
        int minimumY = map.vertices().stream().mapToInt(DoomMap.Vertex::y).min().orElseThrow();
        int maximumY = map.vertices().stream().mapToInt(DoomMap.Vertex::y).max().orElseThrow();
        return List.of(
                new PlanarPoint(minimumX, minimumY),
                new PlanarPoint(maximumX, minimumY),
                new PlanarPoint(maximumX, maximumY),
                new PlanarPoint(minimumX, maximumY));
    }

    /** Resolves the sector referenced by the first seg of a subsector. */
    private static int sectorForSubsector(DoomMap map, int subsectorIndex) {
        DoomMap.Subsector subsector = map.subsectors().get(subsectorIndex);
        DoomMap.Seg seg = map.segs().get(subsector.firstSeg());
        DoomMap.Linedef linedef = map.linedefs().get(seg.linedef());
        int sidedefIndex = seg.direction() == 0 ? linedef.rightSidedef() : linedef.leftSidedef();
        return map.sidedefs().get(sidedefIndex).sector();
    }

    /** Returns stored seg starts when a map contains the single-subsector special case. */
    private static List<PlanarPoint> segPolygon(DoomMap map, int subsectorIndex) {
        DoomMap.Subsector subsector = map.subsectors().get(subsectorIndex);
        List<PlanarPoint> vertices = new ArrayList<>(subsector.segCount());
        for (int offset = 0; offset < subsector.segCount(); offset++) {
            DoomMap.Seg seg = map.segs().get(subsector.firstSeg() + offset);
            DoomMap.Vertex vertex = map.vertices().get(seg.startVertex());
            PlanarPoint point = new PlanarPoint(vertex.x(), vertex.y());
            if (vertices.isEmpty() || !vertices.getLast().equals(point)) {
                vertices.add(point);
            }
        }
        return compactPolygon(vertices);
    }

    /** Removes adjacent coincident vertices left by partition clipping. */
    private static List<PlanarPoint> compactPolygon(List<PlanarPoint> polygon) {
        List<PlanarPoint> compact = new ArrayList<>(polygon.size());
        for (PlanarPoint point : polygon) {
            if (compact.isEmpty() || !samePoint(compact.getLast(), point)) {
                compact.add(point);
            }
        }
        if (compact.size() > 1 && samePoint(compact.getFirst(), compact.getLast())) {
            compact.removeLast();
        }
        return List.copyOf(compact);
    }

    /** Compares clipping coordinates with a small numerical tolerance. */
    private static boolean samePoint(PlanarPoint first, PlanarPoint second) {
        return Math.abs(first.x() - second.x()) < 0.000_001
                && Math.abs(first.y() - second.y()) < 0.000_001;
    }

    /** Calculates twice the signed polygon area. */
    private static double signedArea(List<PlanarPoint> vertices) {
        double area = 0.0;
        for (int index = 0; index < vertices.size(); index++) {
            PlanarPoint first = vertices.get(index);
            PlanarPoint second = vertices.get((index + 1) % vertices.size());
            area += first.x() * second.y() - second.x() * first.y();
        }
        return area;
    }

    /** Returns a wall texture height, or zero when it cannot be resolved. */
    private static int textureHeight(BuildState state, String materialName) {
        DoomMaterial material = state.materials.wallTextures().get(materialName);
        return material == null ? 0 : material.image().height();
    }

    /** Mutable implementation state hidden behind the single public build operation. */
    private static final class BuildState {
        private final DoomMap map;
        private final DoomMapMaterials materials;
        private final List<DoomSurface> surfaces = new ArrayList<>();
        private final List<DoomGeometryDiagnostic> diagnostics = new ArrayList<>();

        private BuildState(DoomMap map, DoomMapMaterials materials) {
            this.map = map;
            this.materials = materials;
        }

        private void error(String code, String location, String message) {
            diagnostics.add(new DoomGeometryDiagnostic(
                    DoomGeometryDiagnostic.Severity.ERROR, code, location, message));
        }

        private boolean hasErrors() {
            return diagnostics.stream()
                    .anyMatch(diagnostic -> diagnostic.severity() == DoomGeometryDiagnostic.Severity.ERROR);
        }
    }

    /** One renderer-independent point used only while recovering BSP polygons. */
    private record PlanarPoint(double x, double y) {}

    /** Identifies one directed linedef side without an ambiguous boolean argument. */
    private enum WallSide {
        RIGHT("right"),
        LEFT("left");

        private final String locationSegment;

        WallSide(String locationSegment) {
            this.locationSegment = locationSegment;
        }

        private int sidedefIndex(DoomMap.Linedef linedef) {
            return this == RIGHT ? linedef.rightSidedef() : linedef.leftSidedef();
        }

        private int neighborSidedefIndex(DoomMap.Linedef linedef) {
            return this == RIGHT ? linedef.leftSidedef() : linedef.rightSidedef();
        }

        private String locationSegment() {
            return locationSegment;
        }
    }

    /** One indexed sector selected while interpreting a directed wall side. */
    private record SectorReference(int index, DoomMap.Sector value) {}

    /** Related inputs needed to select the visible spans of one portal wall side. */
    private record PortalWallSide(
            DoomMap.Linedef linedef,
            DoomMap.Sidedef sidedef,
            SectorReference sector,
            SectorReference neighbor,
            String location,
            WallSide side) {}

    /** Parameters describing one selected wall span before it becomes a quad. */
    private static final class WallSpan {
        private final DoomMap.Linedef linedef;
        private final DoomMap.Sidedef sidedef;
        private final String location;
        private final WallSide side;
        private final WallRange range;
        private final WallAppearance appearance;
        private final OptionalInt movingCeilingSector;

        private WallSpan(
                DoomMap.Linedef linedef,
                DoomMap.Sidedef sidedef,
                String location,
                WallSide side,
                WallRange range,
                WallAppearance appearance,
                OptionalInt movingCeilingSector) {
            this.linedef = linedef;
            this.sidedef = sidedef;
            this.location = location;
            this.side = side;
            this.range = range;
            this.appearance = appearance;
            this.movingCeilingSector = movingCeilingSector;
        }
    }

    /** Vertical bounds and texture origin for one wall span. */
    private record WallRange(int bottom, int top, int textureTop) {}

    /** Texture and semantic role of one wall span. */
    private record WallAppearance(String materialName, DoomSurface.Type type) {}
}
