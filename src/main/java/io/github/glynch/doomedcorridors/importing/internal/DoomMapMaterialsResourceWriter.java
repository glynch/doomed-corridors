/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.importing.internal;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import io.github.glynch.doomedcorridors.internal.DoomedCorridorsTypes;
import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.material.DoomMaterial;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/** Writes decoded Doom map materials as one deterministic native project resource. */
final class DoomMapMaterialsResourceWriter {
    private static final JsonFactory JSON_FACTORY =
            JsonFactory.builder().disable(StreamWriteFeature.AUTO_CLOSE_TARGET).build();

    /** Prevents construction of this stateless serializer. */
    private DoomMapMaterialsResourceWriter() {
        throw new AssertionError("DoomMapMaterialsResourceWriter cannot be instantiated");
    }

    /** Writes one pretty-printed material-set resource with base64-encoded RGBA8 pixels. */
    static void write(OutputStream output, DoomMapMaterials materials) throws IOException {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(materials, "materials");
        try (JsonGenerator generator = JSON_FACTORY.createGenerator(output, JsonEncoding.UTF8)) {
            generator.setPrettyPrinter(prettyPrinter());
            generator.writeStartObject();
            generator.writeNumberField("schemaVersion", 1);
            generator.writeStringField("type", DoomedCorridorsTypes.MAP_MATERIALS.id());
            generator.writeNumberField("typeVersion", DoomedCorridorsTypes.MAP_MATERIALS.version());
            generator.writeObjectFieldStart("properties");
            generator.writeStringField("map", materials.mapName());
            writeMaterials(generator, "wall-textures", materials.wallTextures());
            writeMaterials(generator, "flats", materials.flats());
            generator.writeEndObject();
            generator.writeEndObject();
            generator.writeRaw('\n');
        }
    }

    /** Writes one deterministic material namespace in map iteration order. */
    private static void writeMaterials(
            JsonGenerator generator, String field, Map<String, DoomMaterial> materials)
            throws IOException {
        generator.writeArrayFieldStart(field);
        for (DoomMaterial material : materials.values()) {
            generator.writeStartObject();
            generator.writeStringField("name", material.name());
            generator.writeNumberField("width", material.image().width());
            generator.writeNumberField("height", material.image().height());
            generator.writeStringField(
                    "pixels-base64",
                    Base64.getEncoder().encodeToString(material.image().pixels()));
            generator.writeEndObject();
        }
        generator.writeEndArray();
    }

    /** Creates the common deterministic indentation policy for generated JSON. */
    private static DefaultPrettyPrinter prettyPrinter() {
        DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter();
        prettyPrinter.indentObjectsWith(indenter);
        prettyPrinter.indentArraysWith(indenter);
        return prettyPrinter;
    }
}
