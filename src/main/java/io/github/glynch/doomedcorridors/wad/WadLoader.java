package io.github.glynch.doomedcorridors.wad;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Validates and indexes Doom WAD containers without interpreting lumps or starting native systems. */
public final class WadLoader {
    private static final int HEADER_SIZE = 12;
    private static final int DIRECTORY_ENTRY_SIZE = 16;
    private static final int NAME_SIZE = 8;
    private static final int MAX_LUMP_COUNT = 1_000_000;

    /** Loads a WAD without checking a pinned digest. */
    public WadLoadResult load(Path source) {
        return load(source, Optional.empty());
    }

    /** Loads a WAD and optionally verifies its expected SHA-256 digest. */
    public WadLoadResult load(Path source, Optional<String> expectedSha256) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(expectedSha256, "expectedSha256");
        Path normalizedSource = source.toAbsolutePath().normalize();
        List<WadDiagnostic> diagnostics = new ArrayList<>();

        if (!Files.isRegularFile(normalizedSource)) {
            return error(
                    diagnostics,
                    normalizedSource,
                    "wad.source.missing",
                    "",
                    "WAD source is not a regular file");
        }

        try {
            if (expectedSha256.isPresent()) {
                String actual = sha256(normalizedSource);
                if (!actual.equalsIgnoreCase(expectedSha256.orElseThrow())) {
                    return error(
                            diagnostics,
                            normalizedSource,
                            "wad.source.sha256",
                            "",
                            "WAD SHA-256 is " + actual + ", expected " + expectedSha256.orElseThrow());
                }
            }
            return readDirectory(normalizedSource, diagnostics);
        } catch (IOException exception) {
            return error(
                    diagnostics,
                    normalizedSource,
                    "wad.source.read",
                    "",
                    "Cannot read WAD: " + exception.getMessage());
        }
    }

    private static WadLoadResult readDirectory(Path source, List<WadDiagnostic> diagnostics) throws IOException {
        try (FileChannel channel = FileChannel.open(source, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            if (fileSize < HEADER_SIZE) {
                return error(
                        diagnostics, source, "wad.header.truncated", "/header", "WAD header is shorter than 12 bytes");
            }

            ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            readFully(channel, header, 0);
            header.flip();
            WadArchive.Kind kind = parseKind(header, source, diagnostics);
            if (kind == null) {
                return new WadLoadResult(Optional.empty(), diagnostics);
            }

            int lumpCount = header.getInt();
            int directoryOffset = header.getInt();
            if (lumpCount < 0 || lumpCount > MAX_LUMP_COUNT) {
                return error(
                        diagnostics,
                        source,
                        "wad.directory.count",
                        "/header/lumpCount",
                        "WAD lump count is outside the supported range: " + lumpCount);
            }
            if (directoryOffset < 0) {
                return error(
                        diagnostics,
                        source,
                        "wad.directory.offset",
                        "/header/directoryOffset",
                        "WAD directory offset must not be negative");
            }

            long directorySize = (long) lumpCount * DIRECTORY_ENTRY_SIZE;
            if (directoryOffset > fileSize || directorySize > fileSize - directoryOffset) {
                return error(
                        diagnostics,
                        source,
                        "wad.directory.bounds",
                        "/directory",
                        "WAD directory extends beyond the source file");
            }

            ByteBuffer directory = ByteBuffer.allocate(Math.toIntExact(directorySize)).order(ByteOrder.LITTLE_ENDIAN);
            readFully(channel, directory, directoryOffset);
            directory.flip();
            List<WadLump> lumps = new ArrayList<>(lumpCount);
            for (int index = 0; index < lumpCount; index++) {
                int offset = directory.getInt();
                int size = directory.getInt();
                byte[] rawName = new byte[NAME_SIZE];
                directory.get(rawName);
                String location = "/directory/" + index;
                if (offset < 0 || size < 0 || offset > fileSize || size > fileSize - offset) {
                    return error(
                            diagnostics,
                            source,
                            "wad.lump.bounds",
                            location,
                            "Lump " + index + " extends beyond the source file");
                }
                String name = parseName(rawName);
                if (name == null) {
                    return error(
                            diagnostics,
                            source,
                            "wad.lump.name",
                            location + "/name",
                            "Lump name must contain printable ASCII followed only by NUL padding");
                }
                lumps.add(new WadLump(index, name, offset, size));
            }
            return new WadLoadResult(Optional.of(new WadArchive(source, kind, fileSize, lumps)), diagnostics);
        }
    }

    private static WadArchive.Kind parseKind(
            ByteBuffer header, Path source, List<WadDiagnostic> diagnostics) {
        byte[] signatureBytes = new byte[4];
        header.get(signatureBytes);
        String signature = new String(signatureBytes, StandardCharsets.US_ASCII);
        return switch (signature) {
            case "IWAD" -> WadArchive.Kind.IWAD;
            case "PWAD" -> WadArchive.Kind.PWAD;
            default -> {
                diagnostics.add(new WadDiagnostic(
                        WadDiagnostic.Severity.ERROR,
                        "wad.header.signature",
                        source,
                        "/header/signature",
                        "WAD signature must be IWAD or PWAD, found " + printable(signatureBytes)));
                yield null;
            }
        };
    }

    private static String parseName(byte[] rawName) {
        int length = 0;
        boolean padded = false;
        for (byte value : rawName) {
            int character = Byte.toUnsignedInt(value);
            if (character == 0) {
                padded = true;
            } else if (padded || character < 0x20 || character > 0x7e) {
                return null;
            } else {
                length++;
            }
        }
        return new String(rawName, 0, length, StandardCharsets.US_ASCII).toUpperCase(java.util.Locale.ROOT);
    }

    private static String printable(byte[] bytes) {
        StringBuilder text = new StringBuilder(4);
        for (byte value : bytes) {
            int character = Byte.toUnsignedInt(value);
            text.append(character >= 0x20 && character <= 0x7e ? (char) character : '?');
        }
        return text.toString();
    }

    private static String sha256(Path source) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("Java runtime does not provide SHA-256", exception);
        }
        try (var input = new DigestInputStream(Files.newInputStream(source), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void readFully(FileChannel channel, ByteBuffer target, long offset) throws IOException {
        long position = offset;
        while (target.hasRemaining()) {
            int count = channel.read(target, position);
            if (count < 0) {
                throw new EOFException("unexpected end of WAD");
            }
            position += count;
        }
    }

    private static WadLoadResult error(
            List<WadDiagnostic> diagnostics, Path source, String code, String location, String message) {
        diagnostics.add(new WadDiagnostic(WadDiagnostic.Severity.ERROR, code, source, location, message));
        return new WadLoadResult(Optional.empty(), diagnostics);
    }
}
