/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.world;

import io.github.glynch.doomedcorridors.actor.DoomActor;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalog;
import io.github.glynch.doomedcorridors.actor.DoomActorDefinition;
import io.github.glynch.doomedcorridors.actor.DoomActorDiagnostic;
import io.github.glynch.doomedcorridors.actor.DoomActorResolution;
import io.github.glynch.doomedcorridors.actor.DoomSkillLevel;
import io.github.glynch.doomedcorridors.map.DoomMap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Resolves classic map things through provider definitions into visible world actors. */
public final class DoomActorResolver {
    private static final int MULTIPLAYER_ONLY = 0x0010;

    /** Resolves single-player actors for one skill group while preserving thing order. */
    public DoomActorResolution resolve(
            Path source, DoomMap map, DoomActorCatalog catalog, DoomSkillLevel skillLevel) {
        Path normalizedSource = source.toAbsolutePath().normalize();
        DoomMap validMap = Objects.requireNonNull(map, "map");
        DoomActorCatalog validCatalog = Objects.requireNonNull(catalog, "catalog");
        DoomSkillLevel validSkillLevel = Objects.requireNonNull(skillLevel, "skillLevel");
        DoomCollisionWorld world = new DoomCollisionWorld(validMap);
        List<DoomActor> actors = new ArrayList<>();
        List<DoomActorDiagnostic> diagnostics = new ArrayList<>();
        for (int index = 0; index < validMap.things().size(); index++) {
            DoomMap.Thing thing = validMap.things().get(index);
            if (!validSkillLevel.includes(thing.flags()) || (thing.flags() & MULTIPLAYER_ONLY) != 0) {
                continue;
            }
            DoomActorDefinition definition = validCatalog.definition(thing.type()).orElse(null);
            if (definition == null) {
                diagnostics.add(unsupported(normalizedSource, index, thing.type()));
            } else if (definition.spriteFrame().isPresent()) {
                actors.add(resolve(index, thing, definition, world));
            }
        }
        return new DoomActorResolution(actors, diagnostics);
    }

    /** Converts one selected visible thing into engine world coordinates. */
    private static DoomActor resolve(
            int index, DoomMap.Thing thing, DoomActorDefinition definition, DoomCollisionWorld world) {
        float x = world(thing.x());
        float z = world(-thing.y());
        return new DoomActor(
                index,
                definition,
                x,
                world.floorHeight(x, z),
                z,
                (float) Math.toRadians(thing.angle()));
    }

    /** Creates a stable warning for one selected but undefined classic thing type. */
    private static DoomActorDiagnostic unsupported(Path source, int index, int thingType) {
        return new DoomActorDiagnostic(
                DoomActorDiagnostic.Severity.WARNING,
                "doom.actor.thing-unsupported",
                source,
                "/things/" + index,
                "No actor definition exists for classic thing type " + thingType);
    }

    /** Converts classic Doom map units to JScene3D world units. */
    private static float world(float value) {
        return value / DoomStaticGeometryBuilder.DOOM_UNITS_PER_WORLD_UNIT;
    }
}
