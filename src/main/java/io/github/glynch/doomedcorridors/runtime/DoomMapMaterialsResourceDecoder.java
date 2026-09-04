/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.runtime;

import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.material.DoomMaterial;
import io.github.glynch.doomedcorridors.material.RgbaImage;
import io.github.glynch.jscene3d.project.runtime.extension.ResourceFactoryContext;
import io.github.glynch.jscene3d.project.value.ProjectValue;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reconstructs application material values from imported native project resources. */
final class DoomMapMaterialsResourceDecoder {
    /** Prevents construction of this stateless decoder. */
    private DoomMapMaterialsResourceDecoder() {
        throw new AssertionError("DoomMapMaterialsResourceDecoder cannot be instantiated");
    }

    /** Creates one immutable material set from validated effective resource properties. */
    static DoomMapMaterials decode(ResourceFactoryContext context) {
        Map<String, ProjectValue> properties = context.properties();
        Map<String, DoomMaterial> walls =
                materials(properties, "wall-textures", DoomMaterial.Kind.WALL_TEXTURE);
        Map<String, DoomMaterial> flats = materials(properties, "flats", DoomMaterial.Kind.FLAT);
        return new DoomMapMaterials(ProjectValues.text(properties, "map"), walls, flats);
    }

    /** Decodes one ordered material namespace. */
    private static Map<String, DoomMaterial> materials(
            Map<String, ProjectValue> properties, String field, DoomMaterial.Kind kind) {
        List<ProjectValue> values = ProjectValues.array(properties, field);
        Map<String, DoomMaterial> result = new LinkedHashMap<>();
        for (int index = 0; index < values.size(); index++) {
            Map<String, ProjectValue> material =
                    ProjectValues.object(values.get(index), field + '[' + index + ']');
            String name = ProjectValues.text(material, "name");
            int width = ProjectValues.integer(material, "width");
            int height = ProjectValues.integer(material, "height");
            byte[] pixels = decodePixels(ProjectValues.text(material, "pixels-base64"), field, index);
            DoomMaterial previous = result.put(
                    name,
                    new DoomMaterial(name, kind, new RgbaImage(width, height, pixels), List.of()));
            if (previous != null) {
                throw new IllegalArgumentException(field + " contains duplicate material " + name);
            }
        }
        return result;
    }

    /** Decodes one bounded base64 pixel payload. */
    private static byte[] decodePixels(String encoded, String field, int index) {
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    field + '[' + index + "].pixels-base64 is not valid base64", exception);
        }
    }
}
