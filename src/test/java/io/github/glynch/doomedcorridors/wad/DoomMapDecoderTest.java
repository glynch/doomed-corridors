package io.github.glynch.doomedcorridors.wad;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Specifies classic Doom map decoding through the public WAD adapter seam. */
final class DoomMapDecoderTest {
    @TempDir
    Path temporaryDirectory;

    /** Decodes each classic map lump into immutable renderer-independent values. */
    @Test
    void decodesClassicMap() throws IOException {
        WadArchive archive = writeAndLoadMap(
                new TestLump("THINGS", shorts(64, -32, 90, 1, 7)),
                new TestLump("LINEDEFS", shorts(0, 1, 1, 0, 0, 0, 0xffff)),
                new TestLump("SIDEDEFS", sidedef(8, -4, "UPPER", "-", "MIDDLE", 0)),
                new TestLump("VERTEXES", shorts(0, 0, 128, 0)),
                new TestLump("SEGS", shorts(0, 1, 0, 0, 0, 0)),
                new TestLump("SSECTORS", shorts(1, 0)),
                new TestLump("NODES", new byte[0]),
                new TestLump("SECTORS", sector(0, 128, "FLOOR0_1", "CEIL1_1", 160, 0, 0)),
                new TestLump("REJECT", new byte[] {0}),
                new TestLump("BLOCKMAP", shorts(0, 0, 1, 1, 5, 0, 0, 0xffff)));

        DoomMapDecodeResult result = new DoomMapDecoder().decode(archive, "map01");

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        DoomMap map = result.map().orElseThrow();
        assertThat(map.name()).isEqualTo("MAP01");
        assertThat(map.things()).containsExactly(new DoomMap.Thing(64, -32, 90, 1, 7));
        assertThat(map.linedefs()).containsExactly(new DoomMap.Linedef(0, 1, 1, 0, 0, 0, -1));
        assertThat(map.sidedefs())
                .containsExactly(new DoomMap.Sidedef(8, -4, "UPPER", "-", "MIDDLE", 0));
        assertThat(map.vertices()).containsExactly(new DoomMap.Vertex(0, 0), new DoomMap.Vertex(128, 0));
        assertThat(map.segs()).containsExactly(new DoomMap.Seg(0, 1, 0, 0, 0, 0));
        assertThat(map.subsectors()).containsExactly(new DoomMap.Subsector(1, 0));
        assertThat(map.nodes()).isEmpty();
        assertThat(map.sectors())
                .containsExactly(new DoomMap.Sector(0, 128, "FLOOR0_1", "CEIL1_1", 160, 0, 0));
        assertThat(map.rejectBytes()).containsExactly(0);
        assertThat(map.blockmap())
                .isEqualTo(new DoomMap.Blockmap(0, 0, 1, 1, List.of(List.of(0))));
    }

    /** Reports a stable lump-level diagnostic when fixed-size records are truncated. */
    @Test
    void rejectsTruncatedThingRecord() throws IOException {
        WadArchive archive = writeAndLoadMap(
                new TestLump("THINGS", new byte[] {1}),
                new TestLump("LINEDEFS", shorts(0, 1, 1, 0, 0, 0, 0xffff)),
                new TestLump("SIDEDEFS", sidedef(8, -4, "UPPER", "-", "MIDDLE", 0)),
                new TestLump("VERTEXES", shorts(0, 0, 128, 0)),
                new TestLump("SEGS", shorts(0, 1, 0, 0, 0, 0)),
                new TestLump("SSECTORS", shorts(1, 0)),
                new TestLump("NODES", new byte[0]),
                new TestLump("SECTORS", sector(0, 128, "FLOOR0_1", "CEIL1_1", 160, 0, 0)),
                new TestLump("REJECT", new byte[] {0}),
                new TestLump("BLOCKMAP", shorts(0, 0, 1, 1, 5, 0, 0, 0xffff)));

        DoomMapDecodeResult result = new DoomMapDecoder().decode(archive, "MAP01");

        assertThat(result.map()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.map.record-size");
            assertThat(diagnostic.location()).isEqualTo("/maps/MAP01/THINGS");
        });
    }

    /** Rejects geometry indexes that cannot resolve inside the decoded map. */
    @Test
    void rejectsLinedefWithMissingVertex() throws IOException {
        WadArchive archive = writeAndLoadMapReplacing(
                "LINEDEFS", shorts(0, 2, 1, 0, 0, 0, 0xffff));

        DoomMapDecodeResult result = new DoomMapDecoder().decode(archive, "MAP01");

        assertThat(result.map()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.map.reference");
            assertThat(diagnostic.location()).isEqualTo("/maps/MAP01/LINEDEFS/0/endVertex");
        });
    }

    /** Rejects a linedef that points outside the sidedef table. */
    @Test
    void rejectsLinedefWithMissingSidedef() throws IOException {
        WadArchive archive = writeAndLoadMapReplacing(
                "LINEDEFS", shorts(0, 1, 1, 0, 0, 1, 0xffff));

        DoomMapDecodeResult result = new DoomMapDecoder().decode(archive, "MAP01");

        assertThat(result.map()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.map.reference");
            assertThat(diagnostic.location()).isEqualTo("/maps/MAP01/LINEDEFS/0/rightSidedef");
        });
    }

    /** Rejects a sidedef that points outside the sector table. */
    @Test
    void rejectsSidedefWithMissingSector() throws IOException {
        WadArchive archive = writeAndLoadMapReplacing(
                "SIDEDEFS", sidedef(8, -4, "UPPER", "-", "MIDDLE", 1));

        DoomMapDecodeResult result = new DoomMapDecoder().decode(archive, "MAP01");

        assertThat(result.map()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.map.reference");
            assertThat(diagnostic.location()).isEqualTo("/maps/MAP01/SIDEDEFS/0/sector");
        });
    }

    /** Rejects a BSP segment that points outside the linedef table. */
    @Test
    void rejectsSegWithMissingLinedef() throws IOException {
        WadArchive archive = writeAndLoadMapReplacing("SEGS", shorts(0, 1, 0, 1, 0, 0));

        DoomMapDecodeResult result = new DoomMapDecoder().decode(archive, "MAP01");

        assertThat(result.map()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.map.reference");
            assertThat(diagnostic.location()).isEqualTo("/maps/MAP01/SEGS/0/linedef");
        });
    }

    /** Rejects a subsector whose contiguous seg range exceeds the seg table. */
    @Test
    void rejectsSubsectorWithMissingSegs() throws IOException {
        WadArchive archive = writeAndLoadMapReplacing("SSECTORS", shorts(2, 0));

        DoomMapDecodeResult result = new DoomMapDecoder().decode(archive, "MAP01");

        assertThat(result.map()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.map.reference");
            assertThat(diagnostic.location()).isEqualTo("/maps/MAP01/SSECTORS/0/segs");
        });
    }

    /** Rejects a BSP node child that points outside the subsector table. */
    @Test
    void rejectsNodeWithMissingSubsector() throws IOException {
        byte[] node = shorts(
                0,
                0,
                1,
                0,
                10,
                -10,
                -10,
                10,
                10,
                -10,
                -10,
                10,
                0x8001,
                0x8000);
        WadArchive archive = writeAndLoadMapReplacing("NODES", node);

        DoomMapDecodeResult result = new DoomMapDecoder().decode(archive, "MAP01");

        assertThat(result.map()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.map.reference");
            assertThat(diagnostic.location()).isEqualTo("/maps/MAP01/NODES/0/rightChild");
        });
    }

    /** Reports an invalid block-list offset at its owning blockmap cell. */
    @Test
    void rejectsBlockmapOffsetOutsideLump() throws IOException {
        WadArchive archive = writeAndLoadMapReplacing("BLOCKMAP", shorts(0, 0, 1, 1, 100));

        DoomMapDecodeResult result = new DoomMapDecoder().decode(archive, "MAP01");

        assertThat(result.map()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.map.blockmap");
            assertThat(diagnostic.location()).isEqualTo("/maps/MAP01/BLOCKMAP/cells/0");
        });
    }

    /** Rejects a blockmap cell that points outside the linedef table. */
    @Test
    void rejectsBlockmapCellWithMissingLinedef() throws IOException {
        WadArchive archive = writeAndLoadMapReplacing(
                "BLOCKMAP", shorts(0, 0, 1, 1, 5, 0, 1, 0xffff));

        DoomMapDecodeResult result = new DoomMapDecoder().decode(archive, "MAP01");

        assertThat(result.map()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.map.reference");
            assertThat(diagnostic.location()).isEqualTo("/maps/MAP01/BLOCKMAP/cells/0/0");
        });
    }

    /** Rejects a REJECT table too short to contain one bit per sector pair. */
    @Test
    void rejectsTruncatedRejectTable() throws IOException {
        WadArchive archive = writeAndLoadMapReplacing("REJECT", new byte[0]);

        DoomMapDecodeResult result = new DoomMapDecoder().decode(archive, "MAP01");

        assertThat(result.map()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.map.reject-size");
            assertThat(diagnostic.location()).isEqualTo("/maps/MAP01/REJECT");
        });
    }

    /** Distinguishes unsupported UDMF maps from corrupt classic lump ordering. */
    @Test
    void rejectsUdmfMapExplicitly() throws IOException {
        WadArchive archive = writeAndLoadMap(
                new TestLump("TEXTMAP", "namespace=\"zdoom\";".getBytes(StandardCharsets.US_ASCII)));

        DoomMapDecodeResult result = new DoomMapDecoder().decode(archive, "MAP01");

        assertThat(result.map()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.map.format.udmf");
            assertThat(diagnostic.location()).isEqualTo("/maps/MAP01/TEXTMAP");
        });
    }

    /** Detects the BEHAVIOR marker used by unsupported Hexen-format maps. */
    @Test
    void rejectsHexenFormatMapExplicitly() throws IOException {
        List<TestLump> lumps = validMapLumps();
        lumps.add(new TestLump("BEHAVIOR", new byte[] {0, 0, 0, 0}));
        WadArchive archive = writeAndLoadMap(lumps.toArray(TestLump[]::new));

        DoomMapDecodeResult result = new DoomMapDecoder().decode(archive, "MAP01");

        assertThat(result.map()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.map.format.hexen");
            assertThat(diagnostic.location()).isEqualTo("/maps/MAP01/BEHAVIOR");
        });
    }

    private WadArchive writeAndLoadMapReplacing(String name, byte[] content) throws IOException {
        List<TestLump> lumps = validMapLumps();
        for (int index = 0; index < lumps.size(); index++) {
            if (lumps.get(index).name().equals(name)) {
                lumps.set(index, new TestLump(name, content));
                return writeAndLoadMap(lumps.toArray(TestLump[]::new));
            }
        }
        throw new IllegalArgumentException("unknown classic map lump: " + name);
    }

    private static List<TestLump> validMapLumps() {
        return new java.util.ArrayList<>(List.of(
                new TestLump("THINGS", shorts(64, -32, 90, 1, 7)),
                new TestLump("LINEDEFS", shorts(0, 1, 1, 0, 0, 0, 0xffff)),
                new TestLump("SIDEDEFS", sidedef(8, -4, "UPPER", "-", "MIDDLE", 0)),
                new TestLump("VERTEXES", shorts(0, 0, 128, 0)),
                new TestLump("SEGS", shorts(0, 1, 0, 0, 0, 0)),
                new TestLump("SSECTORS", shorts(1, 0)),
                new TestLump("NODES", new byte[0]),
                new TestLump("SECTORS", sector(0, 128, "FLOOR0_1", "CEIL1_1", 160, 0, 0)),
                new TestLump("REJECT", new byte[] {0}),
                new TestLump("BLOCKMAP", shorts(0, 0, 1, 1, 5, 0, 0, 0xffff))));
    }

    private WadArchive writeAndLoadMap(TestLump... mapLumps) throws IOException {
        TestLump[] lumps = new TestLump[mapLumps.length + 1];
        lumps[0] = new TestLump("MAP01", new byte[0]);
        System.arraycopy(mapLumps, 0, lumps, 1, mapLumps.length);

        int contentSize = 0;
        for (TestLump lump : lumps) {
            contentSize += lump.content().length;
        }
        int directoryOffset = 12 + contentSize;
        ByteBuffer bytes = ByteBuffer.allocate(directoryOffset + lumps.length * 16).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put("PWAD".getBytes(StandardCharsets.US_ASCII));
        bytes.putInt(lumps.length);
        bytes.putInt(directoryOffset);

        int offset = 12;
        for (TestLump lump : lumps) {
            bytes.put(lump.content());
        }
        for (TestLump lump : lumps) {
            bytes.putInt(offset);
            bytes.putInt(lump.content().length);
            putName(bytes, lump.name());
            offset += lump.content().length;
        }

        Path source = temporaryDirectory.resolve("map.wad");
        Files.write(source, bytes.array());
        return new WadLoader().load(source).archive().orElseThrow();
    }

    private static byte[] shorts(int... values) {
        ByteBuffer bytes = ByteBuffer.allocate(values.length * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int value : values) {
            bytes.putShort((short) value);
        }
        return bytes.array();
    }

    private static byte[] sidedef(
            int xOffset, int yOffset, String upper, String lower, String middle, int sector) {
        ByteBuffer bytes = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putShort((short) xOffset);
        bytes.putShort((short) yOffset);
        putName(bytes, upper);
        putName(bytes, lower);
        putName(bytes, middle);
        bytes.putShort((short) sector);
        return bytes.array();
    }

    private static byte[] sector(
            int floor,
            int ceiling,
            String floorTexture,
            String ceilingTexture,
            int light,
            int special,
            int tag) {
        ByteBuffer bytes = ByteBuffer.allocate(26).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putShort((short) floor);
        bytes.putShort((short) ceiling);
        putName(bytes, floorTexture);
        putName(bytes, ceilingTexture);
        bytes.putShort((short) light);
        bytes.putShort((short) special);
        bytes.putShort((short) tag);
        return bytes.array();
    }

    private static void putName(ByteBuffer target, String name) {
        byte[] encoded = name.getBytes(StandardCharsets.US_ASCII);
        target.put(encoded);
        target.put(new byte[8 - encoded.length]);
    }

    private record TestLump(String name, byte[] content) {}
}
