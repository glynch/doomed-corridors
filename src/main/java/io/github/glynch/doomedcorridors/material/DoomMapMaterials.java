package io.github.glynch.doomedcorridors.material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable wall textures and flats referenced by one decoded map. */
public record DoomMapMaterials(
        String mapName,
        Map<String, DoomMaterial> wallTextures,
        Map<String, DoomMaterial> flats) {
    /** Creates an immutable material set while preserving deterministic iteration order. */
    public DoomMapMaterials {
        mapName = Objects.requireNonNull(mapName, "mapName");
        if (mapName.isBlank()) {
            throw new IllegalArgumentException("mapName must not be blank");
        }
        wallTextures = Collections.unmodifiableMap(new LinkedHashMap<>(wallTextures));
        flats = Collections.unmodifiableMap(new LinkedHashMap<>(flats));
    }
}
