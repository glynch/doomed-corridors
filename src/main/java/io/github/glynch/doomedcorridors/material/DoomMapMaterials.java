package io.github.glynch.doomedcorridors.material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable wall textures and flats referenced by one decoded map. */
public record DoomMapMaterials(
        Map<String, DoomMaterial> wallTextures, Map<String, DoomMaterial> flats) {
    /** Creates an immutable material set while preserving deterministic iteration order. */
    public DoomMapMaterials {
        wallTextures = Collections.unmodifiableMap(new LinkedHashMap<>(wallTextures));
        flats = Collections.unmodifiableMap(new LinkedHashMap<>(flats));
    }
}
