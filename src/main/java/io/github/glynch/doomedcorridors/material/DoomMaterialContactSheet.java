package io.github.glynch.doomedcorridors.material;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Writes imported map materials as a deterministic PNG for manual inspection. */
public final class DoomMaterialContactSheet {
    /** Writes all wall textures followed by all flats in alphabetical order. */
    public void write(DoomMapMaterials materials, Path output) throws IOException {
        Objects.requireNonNull(materials, "materials");
        Objects.requireNonNull(output, "output");
        List<RgbaImage> images = entries(materials).stream().map(DoomMaterial::image).toList();
        new RgbaContactSheetWriter().write(images, output);
    }

    private static List<DoomMaterial> entries(DoomMapMaterials materials) {
        List<DoomMaterial> entries = new ArrayList<>(
                materials.wallTextures().size() + materials.flats().size());
        entries.addAll(materials.wallTextures().values());
        entries.addAll(materials.flats().values());
        entries.sort(Comparator.comparing(DoomMaterial::kind).thenComparing(DoomMaterial::name));
        return entries;
    }

}
