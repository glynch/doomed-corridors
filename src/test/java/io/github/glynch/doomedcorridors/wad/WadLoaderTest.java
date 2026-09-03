package io.github.glynch.doomedcorridors.wad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests bounded WAD container validation independently of rendering and game rules. */
final class WadLoaderTest {
    private static final String FREEDOOM_SHA256 =
            "a8772e088847032510d97ba2312406a6998f21cbab44d4ff10696faa9c0ecd4b";

    @TempDir
    Path temporaryDirectory;

    /** Preserves directory ordering, duplicate names, map markers, and lump content. */
    @Test
    void indexesValidWadWithoutInterpretingItsContent() throws IOException {
        Path source = writeWad(
                "valid.wad",
                "IWAD",
                List.of(
                        new TestLump("MAP01", new byte[0]),
                        new TestLump("THINGS", new byte[] {1, 2, 3, 4}),
                        new TestLump("DUP", new byte[] {5}),
                        new TestLump("DUP", new byte[] {6, 7})));

        WadLoadResult result = new WadLoader().load(source);

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        WadArchive archive = result.archive().orElseThrow();
        assertThat(archive.kind()).isEqualTo(WadArchive.Kind.IWAD);
        assertThat(archive.mapNames()).containsExactly("MAP01");
        assertThat(archive.lumps()).extracting(WadLump::name).containsExactly("MAP01", "THINGS", "DUP", "DUP");
        WadLump overridden = archive.lastLumpNamed("dup").orElseThrow();
        assertThat(overridden.index()).isEqualTo(3);
        assertThat(archive.read(overridden)).containsExactly(6, 7);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> archive.read(new WadLump(0, "FOREIGN", 0, 0)))
                .withMessage("lump does not belong to this archive");
    }

    /** Rejects a file before reading directory fields when its header is truncated. */
    @Test
    void rejectsTruncatedHeader() throws IOException {
        Path source = temporaryDirectory.resolve("truncated.wad");
        Files.write(source, "IWAD".getBytes(StandardCharsets.US_ASCII));

        assertSingleError(new WadLoader().load(source), "wad.header.truncated", "/header");
    }

    /** Rejects container signatures outside the Doom IWAD/PWAD contract. */
    @Test
    void rejectsUnknownSignature() throws IOException {
        Path source = writeWad("unknown.wad", "NOPE", List.of());

        assertSingleError(new WadLoader().load(source), "wad.header.signature", "/header/signature");
    }

    /** Rejects directory metadata that attempts to address bytes beyond the file. */
    @Test
    void rejectsOutOfBoundsLump() throws IOException {
        ByteBuffer bytes = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put("PWAD".getBytes(StandardCharsets.US_ASCII));
        bytes.putInt(1);
        bytes.putInt(12);
        bytes.putInt(1_000);
        bytes.putInt(8);
        putName(bytes, "BROKEN");
        Path source = temporaryDirectory.resolve("bounds.wad");
        Files.write(source, bytes.array());

        assertSingleError(new WadLoader().load(source), "wad.lump.bounds", "/directory/0");
    }

    /** Checks the immutable source digest before accepting its directory. */
    @Test
    void rejectsUnexpectedSourceDigest() throws IOException {
        Path source = writeWad("digest.wad", "IWAD", List.of());

        WadLoadResult result = new WadLoader().load(source, Optional.of("0".repeat(64)));

        assertSingleError(result, "wad.source.sha256", "");
    }

    /** Exercises the pinned release when the ignored local source asset is installed. */
    @Test
    void inspectsPinnedFreedoomPhaseTwo() {
        Path source = Path.of("assets/freedoom2.wad");
        Assumptions.assumeTrue(Files.isRegularFile(source), "pinned Freedoom WAD is not installed");

        WadLoadResult result = new WadLoader().load(source, Optional.of(FREEDOOM_SHA256));

        assertThat(result.diagnostics()).isEmpty();
        WadArchive archive = result.archive().orElseThrow();
        assertThat(archive.kind()).isEqualTo(WadArchive.Kind.IWAD);
        assertThat(archive.mapNames()).hasSize(32).contains("MAP01", "MAP32");
        assertThat(archive.lastLumpNamed("PLAYPAL")).isPresent();
    }

    private Path writeWad(String filename, String signature, List<TestLump> lumps) throws IOException {
        int contentSize = lumps.stream().mapToInt(item -> item.content().length).sum();
        int directoryOffset = 12 + contentSize;
        ByteBuffer bytes = ByteBuffer.allocate(directoryOffset + lumps.size() * 16).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put(signature.getBytes(StandardCharsets.US_ASCII));
        bytes.putInt(lumps.size());
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
        Path source = temporaryDirectory.resolve(filename);
        Files.write(source, bytes.array());
        return source;
    }

    private static void putName(ByteBuffer target, String name) {
        byte[] encoded = name.getBytes(StandardCharsets.US_ASCII);
        if (encoded.length > 8) {
            throw new IllegalArgumentException("test lump name is longer than eight bytes");
        }
        target.put(encoded);
        target.put(new byte[8 - encoded.length]);
    }

    private static void assertSingleError(WadLoadResult result, String code, String location) {
        assertThat(result.archive()).isEmpty();
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.severity()).isEqualTo(WadDiagnostic.Severity.ERROR);
            assertThat(diagnostic.code()).isEqualTo(code);
            assertThat(diagnostic.location()).isEqualTo(location);
        });
    }

    private record TestLump(String name, byte[] content) {}
}
