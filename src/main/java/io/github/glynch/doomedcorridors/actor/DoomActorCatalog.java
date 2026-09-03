/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.actor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable actor definitions indexed by classic Doom thing type. */
public final class DoomActorCatalog {
    private final List<DoomActorDefinition> definitions;
    private final Map<Integer, DoomActorDefinition> byThingType;

    /** Copies definitions while rejecting duplicate IDs and thing types. */
    public DoomActorCatalog(List<DoomActorDefinition> definitions) {
        this.definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions"));
        Map<Integer, DoomActorDefinition> indexed = new LinkedHashMap<>();
        Map<String, DoomActorDefinition> byId = new LinkedHashMap<>();
        for (DoomActorDefinition definition : this.definitions) {
            DoomActorDefinition previousType = indexed.putIfAbsent(definition.thingType(), definition);
            if (previousType != null) {
                throw new IllegalArgumentException("Duplicate actor thingType: " + definition.thingType());
            }
            DoomActorDefinition previousId = byId.putIfAbsent(definition.id(), definition);
            if (previousId != null) {
                throw new IllegalArgumentException("Duplicate actor id: " + definition.id());
            }
        }
        byThingType = Map.copyOf(indexed);
    }

    /** Returns definitions in source order. */
    public List<DoomActorDefinition> definitions() {
        return definitions;
    }

    /** Looks up the provider definition for one classic thing type. */
    public Optional<DoomActorDefinition> definition(int thingType) {
        return Optional.ofNullable(byThingType.get(thingType));
    }
}
