/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.importing.internal;

import io.github.glynch.doomedcorridors.importing.DoomedCorridorsImportDiagnosticCode;
import io.github.glynch.doomedcorridors.internal.DoomedCorridorsTypes;
import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.wad.DoomMapDecodeResult;
import io.github.glynch.doomedcorridors.wad.DoomMapDecoder;
import io.github.glynch.doomedcorridors.wad.DoomMaterialImportResult;
import io.github.glynch.doomedcorridors.wad.DoomMaterialImporter;
import io.github.glynch.doomedcorridors.wad.WadArchive;
import io.github.glynch.doomedcorridors.wad.WadDiagnostic;
import io.github.glynch.doomedcorridors.wad.WadLoadResult;
import io.github.glynch.doomedcorridors.wad.WadLoader;
import io.github.glynch.jscene3d.doom.map.DoomMap;
import io.github.glynch.jscene3d.project.importing.ImportArtifactDescriptor;
import io.github.glynch.jscene3d.project.importing.SourceItem;
import io.github.glynch.jscene3d.project.importing.extension.ImportInspectionContext;
import io.github.glynch.jscene3d.project.importing.extension.ImportPreparationContext;
import io.github.glynch.jscene3d.project.importing.extension.ProjectImporter;
import io.github.glynch.jscene3d.project.value.ProjectValue;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Imports renderer-independent wall textures and flats required by selected Doom maps. */
public final class DoomMapMaterialsProjectImporter implements ProjectImporter {
    private static final String ITEM_KIND = "io.github.glynch.doomed-corridors/map-material-set";

    @Override
    public void inspect(ImportInspectionContext context) {
        loadAndDescribe(context);
    }

    @Override
    public void prepare(ImportPreparationContext context) throws IOException {
        Optional<WadArchive> loaded = loadAndDescribe(context);
        if (loaded.isEmpty()) {
            return;
        }
        WadArchive archive = loaded.orElseThrow();
        Set<String> selection = Set.copyOf(context.definition().selection());
        for (String mapName : archive.mapNames()) {
            String identity = identity(mapName);
            if (selection.contains(identity)) {
                prepare(context, archive, mapName, identity);
            }
        }
    }

    /** Loads the source and declares one selectable material set per classic map marker. */
    private static Optional<WadArchive> loadAndDescribe(ImportInspectionContext context) {
        context.checkCancelled();
        WadLoadResult result = new WadLoader().load(context.asset().path(), context.asset().sha256());
        if (!result.isValid()) {
            report(context, result.diagnostics(), DoomedCorridorsImportDiagnosticCode.WAD_SOURCE_INVALID);
            return Optional.empty();
        }
        WadArchive archive = result.archive().orElseThrow();
        for (String mapName : archive.mapNames()) {
            context.sourceItem(new SourceItem(
                    identity(mapName),
                    ITEM_KIND,
                    mapName + " materials",
                    true,
                    Map.of("map", new ProjectValue.TextValue(mapName)),
                    List.of()));
        }
        return Optional.of(archive);
    }

    /** Decodes one selected map and writes its complete referenced material set. */
    private static void prepare(
            ImportPreparationContext context,
            WadArchive archive,
            String mapName,
            String identity)
            throws IOException {
        context.checkCancelled();
        DoomMapDecodeResult mapResult = new DoomMapDecoder().decode(archive, mapName);
        if (!mapResult.isValid()) {
            report(context, mapResult.diagnostics(), DoomedCorridorsImportDiagnosticCode.MAP_INVALID);
            return;
        }
        DoomMap map = mapResult.map().orElseThrow();
        DoomMaterialImportResult materialResult = new DoomMaterialImporter().importMap(archive, map);
        if (!materialResult.isValid()) {
            report(
                    context,
                    materialResult.diagnostics(),
                    DoomedCorridorsImportDiagnosticCode.MAP_MATERIALS_INVALID);
            return;
        }
        DoomMapMaterials materials = materialResult.materials().orElseThrow();
        context.artifact(
                ImportArtifactDescriptor.resource(
                        identity, DoomedCorridorsTypes.MAP_MATERIALS, List.of()),
                output -> DoomMapMaterialsResourceWriter.write(output, materials));
    }

    /** Forwards legacy decoder details through one stable project-import diagnostic identity. */
    private static void report(
            ImportInspectionContext context,
            List<WadDiagnostic> diagnostics,
            DoomedCorridorsImportDiagnosticCode code) {
        for (WadDiagnostic diagnostic : diagnostics) {
            Map<String, String> details = Map.of(
                    "sourceCode", diagnostic.code(),
                    "message", diagnostic.message());
            if (diagnostic.severity() == WadDiagnostic.Severity.ERROR) {
                context.error(code, diagnostic.location(), details);
            } else {
                context.warning(code, diagnostic.location(), details);
            }
        }
    }

    /** Returns the stable source-item and output identity for one map's materials. */
    private static String identity(String mapName) {
        return "materials/" + mapName;
    }
}
