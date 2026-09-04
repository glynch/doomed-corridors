package io.github.glynch.doomedcorridors.wad;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Decodes classic Doom binary map lumps without rendering or applying game rules. */
public final class DoomMapDecoder {
    private static final List<String> CLASSIC_LUMP_NAMES = List.of(
            "THINGS",
            "LINEDEFS",
            "SIDEDEFS",
            "VERTEXES",
            "SEGS",
            "SSECTORS",
            "NODES",
            "SECTORS",
            "REJECT",
            "BLOCKMAP");

    /** Decodes one named classic map through the validated WAD archive interface. */
    public DoomMapDecodeResult decode(WadArchive archive, String mapName) {
        Objects.requireNonNull(archive, "archive");
        Objects.requireNonNull(mapName, "mapName");
        String normalizedName = mapName.toUpperCase(Locale.ROOT);
        List<WadDiagnostic> diagnostics = new ArrayList<>();
        List<WadLump> mapLumps = findMapLumps(archive, normalizedName, diagnostics);
        if (mapLumps.isEmpty()) {
            return new DoomMapDecodeResult(Optional.empty(), diagnostics);
        }

        try {
            List<DoomMap.Thing> things = parseThings(read(archive, mapLumps, 0));
            List<DoomMap.Linedef> linedefs = parseLinedefs(read(archive, mapLumps, 1));
            List<DoomMap.Sidedef> sidedefs = parseSidedefs(read(archive, mapLumps, 2));
            List<DoomMap.Vertex> vertices = parseVertices(read(archive, mapLumps, 3));
            List<DoomMap.Seg> segs = parseSegs(read(archive, mapLumps, 4));
            List<DoomMap.Subsector> subsectors = parseSubsectors(read(archive, mapLumps, 5));
            List<DoomMap.Node> nodes = parseNodes(read(archive, mapLumps, 6));
            List<DoomMap.Sector> sectors = parseSectors(read(archive, mapLumps, 7));
            List<Integer> rejectBytes = unsignedBytes(read(archive, mapLumps, 8));
            DoomMap.Blockmap blockmap = parseBlockmap(read(archive, mapLumps, 9));
            validateLinedefs(linedefs, vertices.size(), sidedefs.size());
            validateSidedefs(sidedefs, sectors.size());
            validateSegs(segs, vertices.size(), linedefs.size());
            validateSubsectors(subsectors, segs.size());
            validateNodes(nodes, subsectors.size());
            validateReject(rejectBytes, sectors.size());
            validateBlockmap(blockmap, linedefs.size());
            DoomMap map = new DoomMap(
                    normalizedName,
                    things,
                    new DoomMap.Geometry(vertices, linedefs, sidedefs, sectors),
                    new DoomMap.Bsp(segs, subsectors, nodes),
                    rejectBytes,
                    blockmap);
            return new DoomMapDecodeResult(Optional.of(map), diagnostics);
        } catch (DecodeFailure failure) {
            diagnostics.add(new WadDiagnostic(
                    WadDiagnostic.Severity.ERROR,
                    failure.code(),
                    archive.source(),
                    "/maps/" + normalizedName + "/" + failure.location(),
                    failure.getMessage()));
            return new DoomMapDecodeResult(Optional.empty(), diagnostics);
        } catch (IOException | RuntimeException exception) {
            diagnostics.add(new WadDiagnostic(
                    WadDiagnostic.Severity.ERROR,
                    "doom.map.data",
                    archive.source(),
                    "/maps/" + normalizedName,
                    "Cannot decode classic map data: " + exception.getMessage()));
            return new DoomMapDecodeResult(Optional.empty(), diagnostics);
        }
    }

    private static List<WadLump> findMapLumps(
            WadArchive archive, String mapName, List<WadDiagnostic> diagnostics) {
        int markerIndex = -1;
        for (WadLump lump : archive.lumps()) {
            if (lump.name().equalsIgnoreCase(mapName)) {
                markerIndex = lump.index();
            }
        }
        if (markerIndex < 0) {
            diagnostics.add(new WadDiagnostic(
                    WadDiagnostic.Severity.ERROR,
                    "doom.map.missing",
                    archive.source(),
                    "/maps/" + mapName,
                    "Map marker is not present: " + mapName));
            return List.of();
        }
        if (markerIndex + 1 < archive.lumps().size()
                && archive.lumps().get(markerIndex + 1).name().equals("TEXTMAP")) {
            diagnostics.add(new WadDiagnostic(
                    WadDiagnostic.Severity.ERROR,
                    "doom.map.format.udmf",
                    archive.source(),
                    "/maps/" + mapName + "/TEXTMAP",
                    "UDMF maps are outside the classic Doom II compatibility target"));
            return List.of();
        }
        if (markerIndex + CLASSIC_LUMP_NAMES.size() >= archive.lumps().size()) {
            diagnostics.add(new WadDiagnostic(
                    WadDiagnostic.Severity.ERROR,
                    "doom.map.layout",
                    archive.source(),
                    "/maps/" + mapName,
                    "Map does not contain the complete classic lump sequence"));
            return List.of();
        }

        List<WadLump> result = new ArrayList<>(CLASSIC_LUMP_NAMES.size());
        for (int offset = 0; offset < CLASSIC_LUMP_NAMES.size(); offset++) {
            WadLump lump = archive.lumps().get(markerIndex + offset + 1);
            String expected = CLASSIC_LUMP_NAMES.get(offset);
            if (!lump.name().equals(expected)) {
                diagnostics.add(new WadDiagnostic(
                        WadDiagnostic.Severity.ERROR,
                        "doom.map.layout",
                        archive.source(),
                        "/maps/" + mapName + "/" + expected,
                        "Expected " + expected + " after map marker, found " + lump.name()));
                return List.of();
            }
            result.add(lump);
        }
        int followingIndex = markerIndex + CLASSIC_LUMP_NAMES.size() + 1;
        if (followingIndex < archive.lumps().size()
                && archive.lumps().get(followingIndex).name().equals("BEHAVIOR")) {
            diagnostics.add(new WadDiagnostic(
                    WadDiagnostic.Severity.ERROR,
                    "doom.map.format.hexen",
                    archive.source(),
                    "/maps/" + mapName + "/BEHAVIOR",
                    "Hexen-format maps are outside the classic Doom II compatibility target"));
            return List.of();
        }
        return List.copyOf(result);
    }

    private static byte[] read(WadArchive archive, List<WadLump> lumps, int index) throws IOException {
        return archive.read(lumps.get(index));
    }

    private static List<DoomMap.Thing> parseThings(byte[] data) {
        ByteBuffer input = records(data, 10, "THINGS");
        List<DoomMap.Thing> result = new ArrayList<>(data.length / 10);
        while (input.hasRemaining()) {
            result.add(new DoomMap.Thing(
                    input.getShort(), input.getShort(), unsigned(input), unsigned(input), unsigned(input)));
        }
        return List.copyOf(result);
    }

    private static List<DoomMap.Linedef> parseLinedefs(byte[] data) {
        ByteBuffer input = records(data, 14, "LINEDEFS");
        List<DoomMap.Linedef> result = new ArrayList<>(data.length / 14);
        while (input.hasRemaining()) {
            result.add(new DoomMap.Linedef(
                    unsigned(input),
                    unsigned(input),
                    unsigned(input),
                    unsigned(input),
                    unsigned(input),
                    indexOrMissing(input),
                    indexOrMissing(input)));
        }
        return List.copyOf(result);
    }

    private static List<DoomMap.Sidedef> parseSidedefs(byte[] data) {
        ByteBuffer input = records(data, 30, "SIDEDEFS");
        List<DoomMap.Sidedef> result = new ArrayList<>(data.length / 30);
        while (input.hasRemaining()) {
            result.add(new DoomMap.Sidedef(
                    input.getShort(),
                    input.getShort(),
                    name(input),
                    name(input),
                    name(input),
                    unsigned(input)));
        }
        return List.copyOf(result);
    }

    private static List<DoomMap.Vertex> parseVertices(byte[] data) {
        ByteBuffer input = records(data, 4, "VERTEXES");
        List<DoomMap.Vertex> result = new ArrayList<>(data.length / 4);
        while (input.hasRemaining()) {
            result.add(new DoomMap.Vertex(input.getShort(), input.getShort()));
        }
        return List.copyOf(result);
    }

    private static List<DoomMap.Seg> parseSegs(byte[] data) {
        ByteBuffer input = records(data, 12, "SEGS");
        List<DoomMap.Seg> result = new ArrayList<>(data.length / 12);
        while (input.hasRemaining()) {
            result.add(new DoomMap.Seg(
                    unsigned(input),
                    unsigned(input),
                    unsigned(input),
                    unsigned(input),
                    unsigned(input),
                    unsigned(input)));
        }
        return List.copyOf(result);
    }

    private static List<DoomMap.Subsector> parseSubsectors(byte[] data) {
        ByteBuffer input = records(data, 4, "SSECTORS");
        List<DoomMap.Subsector> result = new ArrayList<>(data.length / 4);
        while (input.hasRemaining()) {
            result.add(new DoomMap.Subsector(unsigned(input), unsigned(input)));
        }
        return List.copyOf(result);
    }

    private static List<DoomMap.Node> parseNodes(byte[] data) {
        ByteBuffer input = records(data, 28, "NODES");
        List<DoomMap.Node> result = new ArrayList<>(data.length / 28);
        while (input.hasRemaining()) {
            DoomMap.Partition partition = new DoomMap.Partition(
                    input.getShort(), input.getShort(), input.getShort(), input.getShort());
            DoomMap.BoundingBox right = boundingBox(input);
            DoomMap.BoundingBox left = boundingBox(input);
            DoomMap.NodeChild rightChild = child(input);
            DoomMap.NodeChild leftChild = child(input);
            result.add(new DoomMap.Node(
                    partition,
                    new DoomMap.NodeSide(right, rightChild),
                    new DoomMap.NodeSide(left, leftChild)));
        }
        return List.copyOf(result);
    }

    private static List<DoomMap.Sector> parseSectors(byte[] data) {
        ByteBuffer input = records(data, 26, "SECTORS");
        List<DoomMap.Sector> result = new ArrayList<>(data.length / 26);
        while (input.hasRemaining()) {
            result.add(new DoomMap.Sector(
                    input.getShort(),
                    input.getShort(),
                    name(input),
                    name(input),
                    input.getShort(),
                    unsigned(input),
                    unsigned(input)));
        }
        return List.copyOf(result);
    }

    private static DoomMap.Blockmap parseBlockmap(byte[] data) {
        ByteBuffer input = blockmapInput(data);
        int originX = input.getShort();
        int originY = input.getShort();
        int columns = unsigned(input);
        int rows = unsigned(input);
        int[] offsets = blockmapOffsets(input, blockmapCellCount(columns, rows));
        List<List<Integer>> cells = new ArrayList<>(offsets.length);
        for (int index = 0; index < offsets.length; index++) {
            cells.add(blockmapCell(input, data.length, offsets[index], index));
        }
        return new DoomMap.Blockmap(originX, originY, columns, rows, cells);
    }

    private static ByteBuffer blockmapInput(byte[] data) {
        if (data.length < 8 || data.length % Short.BYTES != 0) {
            throw new DecodeFailure(
                    "doom.map.blockmap", "BLOCKMAP", "BLOCKMAP must contain an eight-byte header and whole words");
        }
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static int blockmapCellCount(int columns, int rows) {
        try {
            return Math.multiplyExact(columns, rows);
        } catch (ArithmeticException exception) {
            throw new DecodeFailure(
                    "doom.map.blockmap", "BLOCKMAP", "BLOCKMAP dimensions exceed the supported range");
        }
    }

    private static int[] blockmapOffsets(ByteBuffer input, int cellCount) {
        if (cellCount > input.remaining() / Short.BYTES) {
            throw new DecodeFailure(
                    "doom.map.blockmap", "BLOCKMAP", "BLOCKMAP does not contain its complete cell-offset table");
        }
        int[] offsets = new int[cellCount];
        for (int index = 0; index < cellCount; index++) {
            offsets[index] = unsigned(input);
        }
        return offsets;
    }

    private static List<Integer> blockmapCell(
            ByteBuffer input, int dataLength, int offset, int index) {
        int byteOffset = offset * Short.BYTES;
        if (byteOffset > dataLength - 2 * Short.BYTES) {
            throw new DecodeFailure(
                    "doom.map.blockmap",
                    "BLOCKMAP/cells/" + index,
                    "BLOCKMAP cell list offset is outside the lump: " + offset);
        }
        ByteBuffer cell = input.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        cell.position(byteOffset);
        cell.getShort();
        List<Integer> linedefs = new ArrayList<>();
        while (cell.remaining() >= Short.BYTES) {
            int value = unsigned(cell);
            if (value == 0xffff) {
                return List.copyOf(linedefs);
            }
            linedefs.add(value);
        }
        throw new DecodeFailure(
                "doom.map.blockmap",
                "BLOCKMAP/cells/" + index,
                "BLOCKMAP cell list is not terminated");
    }

    private static ByteBuffer records(byte[] data, int recordSize, String name) {
        if (data.length % recordSize != 0) {
            throw new DecodeFailure(
                    "doom.map.record-size",
                    name,
                    name + " size " + data.length + " is not divisible by " + recordSize);
        }
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static int unsigned(ByteBuffer input) {
        return Short.toUnsignedInt(input.getShort());
    }

    private static int indexOrMissing(ByteBuffer input) {
        int value = unsigned(input);
        return value == 0xffff ? -1 : value;
    }

    private static String name(ByteBuffer input) {
        byte[] bytes = new byte[8];
        input.get(bytes);
        int length = 0;
        while (length < bytes.length && bytes[length] != 0) {
            length++;
        }
        return new String(bytes, 0, length, StandardCharsets.US_ASCII).toUpperCase(Locale.ROOT);
    }

    private static DoomMap.BoundingBox boundingBox(ByteBuffer input) {
        return new DoomMap.BoundingBox(
                input.getShort(), input.getShort(), input.getShort(), input.getShort());
    }

    private static DoomMap.NodeChild child(ByteBuffer input) {
        int value = unsigned(input);
        return new DoomMap.NodeChild((value & 0x8000) != 0, value & 0x7fff);
    }

    private static List<Integer> unsignedBytes(byte[] data) {
        List<Integer> result = new ArrayList<>(data.length);
        for (byte value : data) {
            result.add(Byte.toUnsignedInt(value));
        }
        return List.copyOf(result);
    }

    private static void validateLinedefs(
            List<DoomMap.Linedef> linedefs, int vertexCount, int sidedefCount) {
        for (int index = 0; index < linedefs.size(); index++) {
            DoomMap.Linedef linedef = linedefs.get(index);
            requireIndex(linedef.startVertex(), vertexCount, "LINEDEFS/" + index + "/startVertex");
            requireIndex(linedef.endVertex(), vertexCount, "LINEDEFS/" + index + "/endVertex");
            requireIndex(linedef.rightSidedef(), sidedefCount, "LINEDEFS/" + index + "/rightSidedef");
            if (linedef.leftSidedef() != -1) {
                requireIndex(linedef.leftSidedef(), sidedefCount, "LINEDEFS/" + index + "/leftSidedef");
            }
        }
    }

    private static void validateSidedefs(List<DoomMap.Sidedef> sidedefs, int sectorCount) {
        for (int index = 0; index < sidedefs.size(); index++) {
            requireIndex(sidedefs.get(index).sector(), sectorCount, "SIDEDEFS/" + index + "/sector");
        }
    }

    private static void validateBlockmap(DoomMap.Blockmap blockmap, int linedefCount) {
        for (int cellIndex = 0; cellIndex < blockmap.cells().size(); cellIndex++) {
            List<Integer> cell = blockmap.cells().get(cellIndex);
            for (int entryIndex = 0; entryIndex < cell.size(); entryIndex++) {
                requireIndex(
                        cell.get(entryIndex),
                        linedefCount,
                        "BLOCKMAP/cells/" + cellIndex + "/" + entryIndex);
            }
        }
    }

    private static void validateReject(List<Integer> rejectBytes, int sectorCount) {
        long bits = (long) sectorCount * sectorCount;
        long requiredBytes = (bits + Byte.SIZE - 1) / Byte.SIZE;
        if (rejectBytes.size() < requiredBytes) {
            throw new DecodeFailure(
                    "doom.map.reject-size",
                    "REJECT",
                    "REJECT contains " + rejectBytes.size() + " bytes; at least " + requiredBytes + " required");
        }
    }

    private static void validateSegs(List<DoomMap.Seg> segs, int vertexCount, int linedefCount) {
        for (int index = 0; index < segs.size(); index++) {
            DoomMap.Seg seg = segs.get(index);
            requireIndex(seg.startVertex(), vertexCount, "SEGS/" + index + "/startVertex");
            requireIndex(seg.endVertex(), vertexCount, "SEGS/" + index + "/endVertex");
            requireIndex(seg.linedef(), linedefCount, "SEGS/" + index + "/linedef");
            if (seg.direction() > 1) {
                throw new DecodeFailure(
                        "doom.map.value", "SEGS/" + index + "/direction", "Seg direction must be 0 or 1");
            }
        }
    }

    private static void validateSubsectors(List<DoomMap.Subsector> subsectors, int segCount) {
        for (int index = 0; index < subsectors.size(); index++) {
            DoomMap.Subsector subsector = subsectors.get(index);
            if (subsector.firstSeg() > segCount || subsector.segCount() > segCount - subsector.firstSeg()) {
                throw new DecodeFailure(
                        "doom.map.reference",
                        "SSECTORS/" + index + "/segs",
                        "Subsector seg range extends beyond the seg table");
            }
        }
    }

    private static void validateNodes(List<DoomMap.Node> nodes, int subsectorCount) {
        for (int index = 0; index < nodes.size(); index++) {
            DoomMap.Node node = nodes.get(index);
            validateNodeChild(node.right().child(), nodes.size(), subsectorCount, "NODES/" + index + "/rightChild");
            validateNodeChild(node.left().child(), nodes.size(), subsectorCount, "NODES/" + index + "/leftChild");
        }
    }

    private static void validateNodeChild(
            DoomMap.NodeChild child, int nodeCount, int subsectorCount, String location) {
        int size = child.subsector() ? subsectorCount : nodeCount;
        requireIndex(child.index(), size, location);
    }

    private static void requireIndex(int value, int size, String location) {
        if (value < 0 || value >= size) {
            throw new DecodeFailure(
                    "doom.map.reference", location, "Index " + value + " is outside range 0.." + (size - 1));
        }
    }

    private static final class DecodeFailure extends RuntimeException {
        private final String code;
        private final String location;

        private DecodeFailure(String code, String location, String message) {
            super(message);
            this.code = code;
            this.location = location;
        }

        private String code() {
            return code;
        }

        private String location() {
            return location;
        }
    }
}
