/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.wad;

import io.github.glynch.doomedcorridors.actor.DoomActorSprite;
import io.github.glynch.doomedcorridors.presentation.DoomCombatAssets;
import io.github.glynch.doomedcorridors.presentation.DoomCombatPresentationRules;
import io.github.glynch.jscene3d.audio.PcmAudio;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Imports exact WAD patches and DMX sounds declared by combat presentation rules. */
public final class DoomCombatAssetImporter {
    private static final int PALETTE_SIZE = 256 * 3;

    /** Imports every required presentation lump or returns one structured fatal diagnostic. */
    public DoomCombatAssetImportResult importAssets(
            WadArchive archive, DoomCombatPresentationRules rules) {
        WadArchive validArchive = Objects.requireNonNull(archive, "archive");
        DoomCombatPresentationRules validRules = Objects.requireNonNull(rules, "rules");
        List<WadDiagnostic> diagnostics = new ArrayList<>();
        try {
            WadLump paletteLump = requiredLump(validArchive, "PLAYPAL", "/combat/images/PLAYPAL");
            byte[] palette = validArchive.read(paletteLump);
            if (palette.length < PALETTE_SIZE) {
                throw new AssetFailure("doom.combat.palette-size", "/combat/images/PLAYPAL",
                        "PLAYPAL is shorter than one complete palette");
            }
            Map<String, DoomActorSprite> images = importImages(
                    validArchive, paletteLump, palette, validRules.imageLumps());
            Map<String, PcmAudio> sounds = importSounds(validArchive, validRules.soundLumps());
            DoomCombatAssets assets = new DoomCombatAssets(validRules, images, sounds);
            return new DoomCombatAssetImportResult(Optional.of(assets), diagnostics);
        } catch (AssetFailure failure) {
            diagnostics.add(new WadDiagnostic(WadDiagnostic.Severity.ERROR, failure.code,
                    validArchive.source(), failure.location, failure.getMessage()));
        } catch (IOException exception) {
            diagnostics.add(new WadDiagnostic(WadDiagnostic.Severity.ERROR, "doom.combat.asset-read",
                    validArchive.source(), "/combat", "Cannot read combat assets: " + exception.getMessage()));
        }
        return new DoomCombatAssetImportResult(Optional.empty(), diagnostics);
    }

    /** Decodes exact patch lumps in deterministic name order. */
    private static Map<String, DoomActorSprite> importImages(
            WadArchive archive,
            WadLump paletteLump,
            byte[] palette,
            Set<String> requiredNames) throws IOException {
        Map<String, DoomActorSprite> images = new LinkedHashMap<>();
        for (String name : new TreeSet<>(requiredNames)) {
            WadLump lump = requiredLump(archive, name, "/combat/images/" + name);
            try {
                DoomPatchImage patch = DoomPatchDecoder.decode(archive.read(lump), palette, name);
                images.put(name, new DoomActorSprite(name, lump.name(), patch.image(),
                        patch.leftOffset(), patch.topOffset(), List.of(paletteLump, lump)));
            } catch (DoomPatchDataException exception) {
                throw new AssetFailure("doom.combat.patch-data", "/combat/images/" + name,
                        exception.getMessage());
            }
        }
        return Map.copyOf(images);
    }

    /** Decodes exact DMX sound lumps in deterministic name order. */
    private static Map<String, PcmAudio> importSounds(
            WadArchive archive, Set<String> requiredNames) throws IOException {
        Map<String, PcmAudio> sounds = new LinkedHashMap<>();
        for (String name : new TreeSet<>(requiredNames)) {
            WadLump lump = requiredLump(archive, name, "/combat/sounds/" + name);
            try {
                sounds.put(name, DoomDmxSoundDecoder.decode(archive.read(lump), name));
            } catch (DoomPatchDataException exception) {
                throw new AssetFailure("doom.combat.sound-data", "/combat/sounds/" + name,
                        exception.getMessage());
            }
        }
        return Map.copyOf(sounds);
    }

    /** Resolves one exact required lump using normal WAD override semantics. */
    private static WadLump requiredLump(WadArchive archive, String name, String location) {
        return archive.lastLumpNamed(name)
                .orElseThrow(() -> new AssetFailure("doom.combat.lump-missing", location,
                        "Required combat lump is missing: " + name));
    }

    /** Internal import failure carrying stable diagnostic metadata. */
    private static final class AssetFailure extends RuntimeException {
        private final String code;
        private final String location;

        private AssetFailure(String code, String location, String message) {
            super(message);
            this.code = code;
            this.location = location;
        }
    }
}
