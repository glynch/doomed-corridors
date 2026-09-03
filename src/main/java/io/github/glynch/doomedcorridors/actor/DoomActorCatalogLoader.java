/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.actor;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Loads the versioned actor catalog owned by the Doomed Corridors Game Provider. */
public final class DoomActorCatalogLoader {
    private static final int SCHEMA_VERSION = 1;

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    /** Loads and semantically validates one actor catalog JSON document. */
    public DoomActorCatalogLoadResult load(Path source) {
        Path normalizedSource = source.toAbsolutePath().normalize();
        List<DoomActorDiagnostic> diagnostics = new ArrayList<>();
        try {
            RawCatalog raw = mapper.readValue(normalizedSource.toFile(), RawCatalog.class);
            if (raw.schemaVersion() != SCHEMA_VERSION) {
                return error(
                        normalizedSource,
                        "doom.actor.catalog-version",
                        "/schemaVersion",
                        "Unsupported actor catalog schemaVersion: " + raw.schemaVersion());
            }
            if (raw.actors() == null) {
                return error(
                        normalizedSource,
                        "doom.actor.catalog-actors",
                        "/actors",
                        "Actor catalog must contain an actors array");
            }
            List<DoomActorDefinition> definitions = new ArrayList<>(raw.actors().size());
            for (int index = 0; index < raw.actors().size(); index++) {
                definitions.add(toDefinition(raw.actors().get(index), index));
            }
            return new DoomActorCatalogLoadResult(
                    Optional.of(new DoomActorCatalog(definitions)), diagnostics);
        } catch (IOException | IllegalArgumentException exception) {
            diagnostics.add(new DoomActorDiagnostic(
                    DoomActorDiagnostic.Severity.ERROR,
                    "doom.actor.catalog-invalid",
                    normalizedSource,
                    "/",
                    "Cannot load actor catalog: " + exception.getMessage()));
            return new DoomActorCatalogLoadResult(Optional.empty(), diagnostics);
        }
    }

    /** Converts one nullable JSON binding into the validated domain value. */
    private static DoomActorDefinition toDefinition(RawActor raw, int index) {
        if (raw == null) {
            throw new IllegalArgumentException("actors[" + index + "] must be an object");
        }
        DoomActorCategory category;
        try {
            category = DoomActorCategory.valueOf(required(raw.category(), "category").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("actors[" + index + "] has an invalid category", exception);
        }
        return new DoomActorDefinition(
                raw.thingType(),
                required(raw.id(), "id"),
                required(raw.name(), "name"),
                category,
                Optional.ofNullable(raw.spriteFrame()));
    }

    /** Requires a JSON string field before domain construction. */
    private static String required(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    /** Returns a failed result for one independently located catalog problem. */
    private static DoomActorCatalogLoadResult error(Path source, String code, String location, String message) {
        return new DoomActorCatalogLoadResult(
                Optional.empty(),
                List.of(new DoomActorDiagnostic(
                        DoomActorDiagnostic.Severity.ERROR, code, source, location, message)));
    }

    /** Direct JSON binding retained only long enough for semantic validation. */
    private record RawCatalog(
            @JsonProperty("$schema") String schema, int schemaVersion, List<RawActor> actors) {}

    /** Nullable JSON actor fields retained only long enough for semantic validation. */
    private record RawActor(int thingType, String id, String name, String category, String spriteFrame) {}
}
