package io.github.glynch.doomedcorridors.map;

import java.util.List;
import java.util.Objects;

/** Immutable, renderer-independent data decoded from one classic Doom map. */
public final class DoomMap {
    private final String name;
    private final List<Thing> things;
    private final Geometry geometry;
    private final Bsp bsp;
    private final List<Integer> rejectBytes;
    private final Blockmap blockmap;

    /** Creates a decoded map from immutable domain groups. */
    public DoomMap(
            String name,
            List<Thing> things,
            Geometry geometry,
            Bsp bsp,
            List<Integer> rejectBytes,
            Blockmap blockmap) {
        this.name = Objects.requireNonNull(name, "name");
        this.things = List.copyOf(things);
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.bsp = Objects.requireNonNull(bsp, "bsp");
        this.rejectBytes = List.copyOf(rejectBytes);
        this.blockmap = Objects.requireNonNull(blockmap, "blockmap");
    }

    /** Returns the normalized map marker. */
    public String name() {
        return name;
    }

    /** Returns map things in lump order. */
    public List<Thing> things() {
        return things;
    }

    /** Returns linedefs in lump order. */
    public List<Linedef> linedefs() {
        return geometry.linedefs();
    }

    /** Returns sidedefs in lump order. */
    public List<Sidedef> sidedefs() {
        return geometry.sidedefs();
    }

    /** Returns vertices in lump order. */
    public List<Vertex> vertices() {
        return geometry.vertices();
    }

    /** Returns segs in lump order. */
    public List<Seg> segs() {
        return bsp.segs();
    }

    /** Returns subsectors in lump order. */
    public List<Subsector> subsectors() {
        return bsp.subsectors();
    }

    /** Returns BSP nodes in lump order. */
    public List<Node> nodes() {
        return bsp.nodes();
    }

    /** Returns sectors in lump order. */
    public List<Sector> sectors() {
        return geometry.sectors();
    }

    /** Returns unsigned REJECT-table bytes in source order. */
    public List<Integer> rejectBytes() {
        return rejectBytes;
    }

    /** Returns the parsed collision blockmap. */
    public Blockmap blockmap() {
        return blockmap;
    }

    /** Geometry and sector records used to construct the map. */
    public record Geometry(
            List<Vertex> vertices, List<Linedef> linedefs, List<Sidedef> sidedefs, List<Sector> sectors) {
        /** Creates an immutable geometry group. */
        public Geometry {
            vertices = List.copyOf(vertices);
            linedefs = List.copyOf(linedefs);
            sidedefs = List.copyOf(sidedefs);
            sectors = List.copyOf(sectors);
        }
    }

    /** BSP records used to construct the map. */
    public record Bsp(List<Seg> segs, List<Subsector> subsectors, List<Node> nodes) {
        /** Creates an immutable BSP group. */
        public Bsp {
            segs = List.copyOf(segs);
            subsectors = List.copyOf(subsectors);
            nodes = List.copyOf(nodes);
        }
    }

    /** A map thing placement. */
    public record Thing(int x, int y, int angle, int type, int flags) {}

    /** A boundary between two vertices with right and optional left sidedefs. */
    public record Linedef(
            int startVertex,
            int endVertex,
            int flags,
            int special,
            int tag,
            int rightSidedef,
            int leftSidedef) {}

    /** Material offsets and sector reference for one side of a linedef. */
    public record Sidedef(
            int xOffset,
            int yOffset,
            String upperTexture,
            String lowerTexture,
            String middleTexture,
            int sector) {
        /** Creates a sidedef. */
        public Sidedef {
            Objects.requireNonNull(upperTexture, "upperTexture");
            Objects.requireNonNull(lowerTexture, "lowerTexture");
            Objects.requireNonNull(middleTexture, "middleTexture");
        }
    }

    /** A signed two-dimensional Doom map coordinate. */
    public record Vertex(int x, int y) {}

    /** A BSP segment derived from a linedef. */
    public record Seg(int startVertex, int endVertex, int angle, int linedef, int direction, int offset) {}

    /** A contiguous range of BSP segments. */
    public record Subsector(int segCount, int firstSeg) {}

    /** One BSP partition node and its two bounded child branches. */
    public record Node(Partition partition, NodeSide right, NodeSide left) {
        /** Creates a BSP node. */
        public Node {
            Objects.requireNonNull(partition, "partition");
            Objects.requireNonNull(right, "right");
            Objects.requireNonNull(left, "left");
        }
    }

    /** Origin and direction vector of a BSP partition line. */
    public record Partition(int x, int y, int deltaX, int deltaY) {}

    /** Bounding box and child reference for one side of a BSP node. */
    public record NodeSide(BoundingBox bounds, NodeChild child) {
        /** Creates a bounded BSP branch. */
        public NodeSide {
            Objects.requireNonNull(bounds, "bounds");
            Objects.requireNonNull(child, "child");
        }
    }

    /** Top, bottom, left, and right bounds stored for a BSP child. */
    public record BoundingBox(int top, int bottom, int left, int right) {}

    /** A decoded BSP child reference. */
    public record NodeChild(boolean subsector, int index) {}

    /** Heights, materials, lighting, and behavior tag for a convex map region. */
    public record Sector(
            int floorHeight,
            int ceilingHeight,
            String floorTexture,
            String ceilingTexture,
            int lightLevel,
            int special,
            int tag) {
        /** Creates a sector. */
        public Sector {
            Objects.requireNonNull(floorTexture, "floorTexture");
            Objects.requireNonNull(ceilingTexture, "ceilingTexture");
        }
    }

    /** Grid of linedef indexes used by classic Doom collision queries. */
    public record Blockmap(int originX, int originY, int columns, int rows, List<List<Integer>> cells) {
        /** Creates an immutable blockmap. */
        public Blockmap {
            cells = cells.stream().map(List::copyOf).toList();
        }

        /** Returns the linedef indexes for one grid cell. */
        public List<Integer> cell(int column, int row) {
            if (column < 0 || column >= columns || row < 0 || row >= rows) {
                throw new IndexOutOfBoundsException("blockmap cell is outside the grid");
            }
            return cells.get(row * columns + column);
        }
    }
}
