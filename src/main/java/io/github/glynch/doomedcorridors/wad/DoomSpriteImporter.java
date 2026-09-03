/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.wad;

import io.github.glynch.doomedcorridors.actor.DoomActor;
import io.github.glynch.doomedcorridors.actor.DoomActorSprite;
import io.github.glynch.doomedcorridors.actor.DoomActorSprites;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Imports the initial WAD sprite frame required by each resolved visible actor. */
public final class DoomSpriteImporter {
    private static final int PALETTE_SIZE = 256 * 3;
    private static final Set<String> START_MARKERS = Set.of("S_START", "SS_START");
    private static final Set<String> END_MARKERS = Set.of("S_END", "SS_END");

    /** Imports unique actor spawn frames through classic sprite namespace semantics. */
    public DoomSpriteImportResult importActors(WadArchive archive, List<DoomActor> actors) {
        WadArchive validArchive = Objects.requireNonNull(archive, "archive");
        List<DoomActor> validActors = List.copyOf(Objects.requireNonNull(actors, "actors"));
        List<WadDiagnostic> diagnostics = new ArrayList<>();
        try {
            WadLump paletteLump = validArchive.lastLumpNamed("PLAYPAL")
                    .orElseThrow(() -> new SpriteFailure(
                            "doom.sprite.palette-missing", "/sprites/palette", "Required PLAYPAL lump is missing"));
            byte[] palette = validArchive.read(paletteLump);
            if (palette.length < PALETTE_SIZE) {
                throw new SpriteFailure(
                        "doom.sprite.palette-size",
                        "/sprites/palette",
                        "PLAYPAL contains " + palette.length + " bytes; at least " + PALETTE_SIZE + " required");
            }
            Map<String, WadLump> namespace = spriteLumps(validArchive);
            Map<String, DoomActorSprite> imported = new LinkedHashMap<>();
            for (String frame : requiredFrames(validActors)) {
                WadLump lump = frameLump(namespace, frame);
                if (lump == null) {
                    diagnostics.add(new WadDiagnostic(
                            WadDiagnostic.Severity.WARNING,
                            "doom.sprite.frame-missing",
                            validArchive.source(),
                            "/sprites/" + frame,
                            "No sprite lump exists for actor frame " + frame));
                    continue;
                }
                imported.put(frame, importSprite(validArchive, paletteLump, palette, frame, lump));
            }
            return new DoomSpriteImportResult(
                    Optional.of(new DoomActorSprites(imported)), diagnostics);
        } catch (SpriteFailure failure) {
            diagnostics.add(new WadDiagnostic(
                    WadDiagnostic.Severity.ERROR,
                    failure.code,
                    validArchive.source(),
                    failure.location,
                    failure.getMessage()));
        } catch (IOException exception) {
            diagnostics.add(new WadDiagnostic(
                    WadDiagnostic.Severity.ERROR,
                    "doom.sprite.read",
                    validArchive.source(),
                    "/sprites",
                    "Cannot read sprite source data: " + exception.getMessage()));
        }
        return new DoomSpriteImportResult(Optional.empty(), diagnostics);
    }

    /** Decodes one sprite lump while translating patch failures into importer diagnostics. */
    private static DoomActorSprite importSprite(
            WadArchive archive,
            WadLump paletteLump,
            byte[] palette,
            String frame,
            WadLump lump)
            throws IOException {
        try {
            DoomPatchImage patch = DoomPatchDecoder.decode(archive.read(lump), palette, lump.name());
            return new DoomActorSprite(
                    frame,
                    lump.name(),
                    patch.image(),
                    patch.leftOffset(),
                    patch.topOffset(),
                    List.of(paletteLump, lump));
        } catch (DoomPatchDataException exception) {
            throw new SpriteFailure(
                    "doom.sprite.patch-data", "/sprites/" + frame, exception.getMessage());
        }
    }

    /** Collects required provider frame identifiers in deterministic order. */
    private static Set<String> requiredFrames(List<DoomActor> actors) {
        Set<String> frames = new TreeSet<>();
        for (DoomActor actor : actors) {
            frames.add(actor.definition().spriteFrame().orElseThrow());
        }
        return frames;
    }

    /** Indexes sprite lumps only while inside standard Doom sprite namespaces. */
    private static Map<String, WadLump> spriteLumps(WadArchive archive) {
        Map<String, WadLump> result = new LinkedHashMap<>();
        boolean inNamespace = false;
        for (WadLump lump : archive.lumps()) {
            if (START_MARKERS.contains(lump.name())) {
                inNamespace = true;
            } else if (END_MARKERS.contains(lump.name())) {
                inNamespace = false;
            } else if (inNamespace) {
                result.put(lump.name(), lump);
            }
        }
        return result;
    }

    /** Selects a non-directional frame or the forward-facing rotation-one frame. */
    private static WadLump frameLump(Map<String, WadLump> namespace, String frame) {
        WadLump nonDirectional = namespace.get(frame + "0");
        return nonDirectional == null ? namespace.get(frame + "1") : nonDirectional;
    }

    /** Internal import failure carrying a stable diagnostic identity and location. */
    private static final class SpriteFailure extends RuntimeException {
        private final String code;
        private final String location;

        private SpriteFailure(String code, String location, String message) {
            super(message);
            this.code = code;
            this.location = location;
        }
    }
}
