package io.github.glynch.doomedcorridors.wad;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable directory metadata for a validated WAD source. */
public record WadArchive(Path source, Kind kind, long fileSize, List<WadLump> lumps) {
    /** WAD container kind. */
    public enum Kind {
        /** A complete game-data WAD. */
        IWAD,
        /** A patch WAD applied over an IWAD. */
        PWAD
    }

    /** Creates an immutable archive description. */
    public WadArchive {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(kind, "kind");
        if (fileSize < 0) {
            throw new IllegalArgumentException("fileSize must not be negative");
        }
        lumps = List.copyOf(lumps);
    }

    /** Returns map-marker names in directory order. */
    public List<String> mapNames() {
        return lumps.stream().map(WadLump::name).filter(WadArchive::isMapMarker).distinct().toList();
    }

    /** Returns the last lump with the requested name, matching Doom override semantics. */
    public Optional<WadLump> lastLumpNamed(String name) {
        Objects.requireNonNull(name, "name");
        for (int index = lumps.size() - 1; index >= 0; index--) {
            WadLump lump = lumps.get(index);
            if (lump.name().equalsIgnoreCase(name)) {
                return Optional.of(lump);
            }
        }
        return Optional.empty();
    }

    /** Reads the bytes of a lump belonging to this archive. */
    public byte[] read(WadLump lump) throws IOException {
        Objects.requireNonNull(lump, "lump");
        if (lump.index() >= lumps.size() || !lumps.get(lump.index()).equals(lump)) {
            throw new IllegalArgumentException("lump does not belong to this archive");
        }
        ByteBuffer content = ByteBuffer.allocate(lump.size());
        try (FileChannel channel = FileChannel.open(source, StandardOpenOption.READ)) {
            readFully(channel, content, lump.offset());
        }
        return content.array();
    }

    private static boolean isMapMarker(String name) {
        return name.length() == 5
                        && name.startsWith("MAP")
                        && Character.isDigit(name.charAt(3))
                        && Character.isDigit(name.charAt(4))
                || name.length() == 4
                        && name.charAt(0) == 'E'
                        && Character.isDigit(name.charAt(1))
                        && name.charAt(2) == 'M'
                        && Character.isDigit(name.charAt(3));
    }

    private static void readFully(FileChannel channel, ByteBuffer target, long offset) throws IOException {
        long position = offset;
        while (target.hasRemaining()) {
            int count = channel.read(target, position);
            if (count < 0) {
                throw new EOFException("WAD changed after its directory was validated");
            }
            position += count;
        }
    }
}
