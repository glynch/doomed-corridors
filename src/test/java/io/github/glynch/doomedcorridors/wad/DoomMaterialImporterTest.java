package io.github.glynch.doomedcorridors.wad;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Specifies map-scoped material import through the public WAD adapter seam. */
final class DoomMaterialImporterTest {
    @TempDir
    Path temporaryDirectory;

    /** Resolves a referenced flat and applies the first PLAYPAL palette. */
    @Test
    void importsReferencedFlat() throws IOException {
        byte[] palette = new byte[256 * 3];
        palette[3] = 10;
        palette[4] = 20;
        palette[5] = 30;
        byte[] flat = new byte[64 * 64];
        java.util.Arrays.fill(flat, (byte) 1);
        WadArchive archive = writeAndLoad(
                new TestLump("PLAYPAL", palette),
                new TestLump("F_START", new byte[0]),
                new TestLump("FLAT1", flat),
                new TestLump("F_END", new byte[0]));
        DoomMap map = mapWithMaterials(List.of(), List.of("FLAT1", "F_SKY1"));

        DoomMaterialImportResult result = new DoomMaterialImporter().importMap(archive, map);

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        DoomMapMaterials materials = result.materials().orElseThrow();
        assertThat(materials.wallTextures()).isEmpty();
        assertThat(materials.flats()).containsOnlyKeys("FLAT1");
        assertThat(materials.flats().get("FLAT1").image().width()).isEqualTo(64);
        assertThat(materials.flats().get("FLAT1").image().height()).isEqualTo(64);
        assertThat(materials.flats().get("FLAT1").image().rgba(0, 0)).isEqualTo(0x0a141eff);
        assertThat(materials.flats().get("FLAT1").sourceLumps())
                .extracting(WadLump::name)
                .containsExactly("PLAYPAL", "FLAT1");
    }

    /** Composes a referenced wall texture from transparent column-post patch data. */
    @Test
    void importsReferencedCompositeWallTexture() throws IOException {
        byte[] palette = new byte[256 * 3];
        setColor(palette, 1, 10, 20, 30);
        setColor(palette, 2, 40, 50, 60);
        setColor(palette, 3, 70, 80, 90);
        setColor(palette, 4, 100, 110, 120);
        WadArchive archive = writeAndLoad(
                new TestLump("PLAYPAL", palette),
                new TestLump("PATCH1", patch(2, 2, new int[][] {{1, 2}, {3, 4}})),
                new TestLump("PNAMES", patchNames("PATCH1")),
                new TestLump("TEXTURE1", textureDefinitions("WALL1", 3, 2, 1, 0, 0)));
        DoomMap map = mapWithMaterials(List.of("WALL1"), List.of("F_SKY1"));

        DoomMaterialImportResult result = new DoomMaterialImporter().importMap(archive, map);

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        DoomMapMaterials materials = result.materials().orElseThrow();
        assertThat(materials.flats()).isEmpty();
        assertThat(materials.wallTextures()).containsOnlyKeys("WALL1");
        assertThat(materials.wallTextures().get("WALL1").image().width()).isEqualTo(3);
        assertThat(materials.wallTextures().get("WALL1").image().height()).isEqualTo(2);
        assertThat(materials.wallTextures().get("WALL1").image().rgba(0, 0)).isZero();
        assertThat(materials.wallTextures().get("WALL1").image().rgba(1, 0)).isEqualTo(0x0a141eff);
        assertThat(materials.wallTextures().get("WALL1").image().rgba(2, 1)).isEqualTo(0x646e78ff);
        assertThat(materials.wallTextures().get("WALL1").sourceLumps())
                .extracting(WadLump::name)
                .containsExactly("PLAYPAL", "PNAMES", "TEXTURE1", "PATCH1");
    }

    /** Reports a missing texture patch at the wall material that references it. */
    @Test
    void reportsMissingReferencedPatch() throws IOException {
        WadArchive archive = writeAndLoad(
                new TestLump("PLAYPAL", new byte[256 * 3]),
                new TestLump("PNAMES", patchNames("PATCH1")),
                new TestLump("TEXTURE1", textureDefinitions("WALL1", 2, 2, 0, 0, 0)));
        DoomMap map = mapWithMaterials(List.of("WALL1"), List.of("F_SKY1"));

        DoomMaterialImportResult result = new DoomMaterialImporter().importMap(archive, map);

        assertThat(result.materials()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.material.patch-missing");
            assertThat(diagnostic.location()).isEqualTo("/materials/wall-textures/WALL1/PATCH1");
        });
    }

    /** Reports malformed patch bytes at the wall material that consumes them. */
    @Test
    void reportsMalformedReferencedPatch() throws IOException {
        WadArchive archive = writeAndLoad(
                new TestLump("PLAYPAL", new byte[256 * 3]),
                new TestLump("PATCH1", new byte[] {1}),
                new TestLump("PNAMES", patchNames("PATCH1")),
                new TestLump("TEXTURE1", textureDefinitions("WALL1", 2, 2, 0, 0, 0)));
        DoomMap map = mapWithMaterials(List.of("WALL1"), List.of("F_SKY1"));

        DoomMaterialImportResult result = new DoomMaterialImporter().importMap(archive, map);

        assertThat(result.materials()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.material.patch-data");
            assertThat(diagnostic.location()).isEqualTo("/materials/wall-textures/WALL1/PATCH1");
        });
    }

    /** Keeps flat lookup inside F_START/F_END even when a later global lump has the same name. */
    @Test
    void resolvesFlatInsideItsNamespace() throws IOException {
        byte[] palette = new byte[256 * 3];
        setColor(palette, 1, 10, 20, 30);
        setColor(palette, 2, 200, 210, 220);
        byte[] namespaced = new byte[64 * 64];
        java.util.Arrays.fill(namespaced, (byte) 1);
        byte[] unrelated = new byte[64 * 64];
        java.util.Arrays.fill(unrelated, (byte) 2);
        WadArchive archive = writeAndLoad(
                new TestLump("PLAYPAL", palette),
                new TestLump("F_START", new byte[0]),
                new TestLump("FLAT1", namespaced),
                new TestLump("F_END", new byte[0]),
                new TestLump("FLAT1", unrelated));
        DoomMap map = mapWithMaterials(List.of(), List.of("FLAT1"));

        DoomMaterialImportResult result = new DoomMaterialImporter().importMap(archive, map);

        assertThat(result.materials().orElseThrow().flats().get("FLAT1").image().rgba(0, 0))
                .isEqualTo(0x0a141eff);
    }

    /** Reports a referenced flat absent from the flat namespace. */
    @Test
    void reportsMissingReferencedFlat() throws IOException {
        WadArchive archive = writeAndLoad(
                new TestLump("PLAYPAL", new byte[256 * 3]),
                new TestLump("FLAT1", new byte[64 * 64]));
        DoomMap map = mapWithMaterials(List.of(), List.of("FLAT1"));

        DoomMaterialImportResult result = new DoomMaterialImporter().importMap(archive, map);

        assertThat(result.materials()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.code()).isEqualTo("doom.material.flat-missing");
            assertThat(diagnostic.location()).isEqualTo("/materials/flats/FLAT1");
        });
    }

    private static DoomMap mapWithMaterials(List<String> wallNames, List<String> flatNames) {
        List<DoomMap.Sidedef> sidedefs = wallNames.stream()
                .map(name -> new DoomMap.Sidedef(0, 0, "-", "-", name, 0))
                .toList();
        List<DoomMap.Sector> sectors = flatNames.stream()
                .map(name -> new DoomMap.Sector(0, 128, name, name, 160, 0, 0))
                .toList();
        return new DoomMap(
                "MAP01",
                List.of(),
                new DoomMap.Geometry(List.of(), List.of(), sidedefs, sectors),
                new DoomMap.Bsp(List.of(), List.of(), List.of()),
                List.of(),
                new DoomMap.Blockmap(0, 0, 0, 0, List.of()));
    }

    private WadArchive writeAndLoad(TestLump... lumps) throws IOException {
        int contentSize = java.util.Arrays.stream(lumps)
                .mapToInt(lump -> lump.content().length)
                .sum();
        int directoryOffset = 12 + contentSize;
        ByteBuffer bytes = ByteBuffer.allocate(directoryOffset + lumps.length * 16).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put("PWAD".getBytes(StandardCharsets.US_ASCII));
        bytes.putInt(lumps.length);
        bytes.putInt(directoryOffset);
        for (TestLump lump : lumps) {
            bytes.put(lump.content());
        }
        int offset = 12;
        for (TestLump lump : lumps) {
            bytes.putInt(offset);
            bytes.putInt(lump.content().length);
            putName(bytes, lump.name());
            offset += lump.content().length;
        }
        Path source = temporaryDirectory.resolve("materials.wad");
        Files.write(source, bytes.array());
        return new WadLoader().load(source).archive().orElseThrow();
    }

    private static void putName(ByteBuffer target, String name) {
        byte[] encoded = name.getBytes(StandardCharsets.US_ASCII);
        target.put(encoded);
        target.put(new byte[8 - encoded.length]);
    }

    private static void setColor(byte[] palette, int index, int red, int green, int blue) {
        palette[index * 3] = (byte) red;
        palette[index * 3 + 1] = (byte) green;
        palette[index * 3 + 2] = (byte) blue;
    }

    private static byte[] patchNames(String... names) {
        ByteBuffer bytes = ByteBuffer.allocate(Integer.BYTES + names.length * 8).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(names.length);
        for (String name : names) {
            putName(bytes, name);
        }
        return bytes.array();
    }

    private static byte[] textureDefinitions(
            String name, int width, int height, int originX, int originY, int patchIndex) {
        ByteBuffer bytes = ByteBuffer.allocate(8 + 22 + 10).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(1);
        bytes.putInt(8);
        putName(bytes, name);
        bytes.putInt(0);
        bytes.putShort((short) width);
        bytes.putShort((short) height);
        bytes.putInt(0);
        bytes.putShort((short) 1);
        bytes.putShort((short) originX);
        bytes.putShort((short) originY);
        bytes.putShort((short) patchIndex);
        bytes.putShort((short) 0);
        bytes.putShort((short) 0);
        return bytes.array();
    }

    private static byte[] patch(int width, int height, int[][] columns) {
        int headerSize = 8 + width * Integer.BYTES;
        int columnSize = height + 5;
        ByteBuffer bytes = ByteBuffer.allocate(headerSize + width * columnSize).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putShort((short) width);
        bytes.putShort((short) height);
        bytes.putShort((short) 0);
        bytes.putShort((short) 0);
        for (int column = 0; column < width; column++) {
            bytes.putInt(headerSize + column * columnSize);
        }
        for (int[] column : columns) {
            bytes.put((byte) 0);
            bytes.put((byte) column.length);
            bytes.put((byte) 0);
            for (int index : column) {
                bytes.put((byte) index);
            }
            bytes.put((byte) 0);
            bytes.put((byte) 0xff);
        }
        return bytes.array();
    }

    private record TestLump(String name, byte[] content) {}
}
