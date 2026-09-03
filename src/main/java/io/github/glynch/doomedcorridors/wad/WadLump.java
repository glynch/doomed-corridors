package io.github.glynch.doomedcorridors.wad;

import java.util.Objects;

/** One entry in a WAD directory. Duplicate names are valid and retain their directory order. */
public record WadLump(int index, String name, long offset, int size) {
    /** Creates validated lump metadata. */
    public WadLump {
        if (index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        Objects.requireNonNull(name, "name");
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
    }
}
