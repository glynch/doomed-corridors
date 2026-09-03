/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.map.DoomMap;
import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.wad.DoomMapDecodeResult;
import io.github.glynch.doomedcorridors.wad.DoomMapDecoder;
import io.github.glynch.doomedcorridors.wad.DoomMaterialImportResult;
import io.github.glynch.doomedcorridors.wad.DoomMaterialImporter;
import io.github.glynch.doomedcorridors.wad.WadArchive;
import io.github.glynch.doomedcorridors.wad.WadDiagnostic;
import io.github.glynch.doomedcorridors.wad.WadLoadResult;
import io.github.glynch.doomedcorridors.wad.WadLoader;
import io.github.glynch.doomedcorridors.world.DoomGeometryBuildResult;
import io.github.glynch.doomedcorridors.world.DoomGeometryDiagnostic;
import io.github.glynch.doomedcorridors.world.DoomStaticGeometry;
import io.github.glynch.doomedcorridors.world.DoomStaticGeometryBuilder;
import io.github.glynch.jscene3d.project.GameProject;
import io.github.glynch.jscene3d.project.ProjectDiagnostic;
import io.github.glynch.jscene3d.project.ProjectLoadResult;
import io.github.glynch.jscene3d.project.ProjectLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loaded startup data shared by graphical and headless hosts. */
record DoomStartup(
        GameProject project, DoomMap map, DoomMapMaterials materials, DoomStaticGeometry geometry) {
    private static final String ENGINE_VERSION = "0.1.0-SNAPSHOT";

    /** Loads the complete project-selected static-map pipeline. */
    static DoomStartup load(Path projectDirectory) {
        ProjectLoadResult projectResult = new ProjectLoader(ENGINE_VERSION).load(projectDirectory);
        projectResult.diagnostics().forEach(DoomStartup::printProjectDiagnostic);
        GameProject project = projectResult.project()
                .orElseThrow(() -> new IllegalStateException("Cannot load Doomed Corridors project"));
        System.out.printf(
                "Loaded %s %s; startup %s:%s%n",
                project.identity().name(),
                project.identity().version(),
                project.runtime().startup().asset(),
                project.runtime().startup().target());
        GameProject.AssetSource asset = startupAsset(project);
        WadArchive archive = loadArchive(project, asset);
        DoomMap map = decodeMap(project, archive);
        DoomMapMaterials materials = importMaterials(archive, map);
        DoomStaticGeometry geometry = buildGeometry(map, materials);
        return new DoomStartup(project, map, materials, geometry);
    }

    /** Resolves the manifest-selected source asset. */
    private static GameProject.AssetSource startupAsset(GameProject project) {
        GameProject.StartupTarget startup = project.runtime().startup();
        GameProject.AssetSource asset = project.assets().stream()
                .filter(candidate -> candidate.id().equals(startup.asset()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Startup asset is not defined"));
        if (!Files.isRegularFile(asset.path())) {
            throw new IllegalStateException("Startup asset is not installed: " + asset.path());
        }
        if (!asset.type().equals("doom-wad")) {
            throw new IllegalStateException("Startup asset is not a Doom WAD");
        }
        return asset;
    }

    /** Loads and checks the startup archive. */
    private static WadArchive loadArchive(GameProject project, GameProject.AssetSource asset) {
        WadLoadResult result = new WadLoader().load(asset.path(), asset.sha256());
        result.diagnostics().forEach(DoomStartup::printWadDiagnostic);
        WadArchive archive = result.archive()
                .orElseThrow(() -> new IllegalStateException("Cannot inspect startup WAD"));
        String target = project.runtime().startup().target();
        if (!archive.mapNames().contains(target)) {
            throw new IllegalStateException("Startup map is not present in WAD: " + target);
        }
        System.out.printf(
                "Inspected %s with %,d lumps and %,d maps; found startup map %s%n",
                archive.kind(), archive.lumps().size(), archive.mapNames().size(), target);
        return archive;
    }

    /** Decodes the selected classic map. */
    private static DoomMap decodeMap(GameProject project, WadArchive archive) {
        String target = project.runtime().startup().target();
        DoomMapDecodeResult result = new DoomMapDecoder().decode(archive, target);
        result.diagnostics().forEach(DoomStartup::printWadDiagnostic);
        DoomMap map = result.map()
                .orElseThrow(() -> new IllegalStateException("Cannot decode startup map: " + target));
        System.out.printf(
                "Decoded %s: %,d things, %,d linedefs, %,d sidedefs, %,d vertices, and %,d sectors%n",
                map.name(),
                map.things().size(),
                map.linedefs().size(),
                map.sidedefs().size(),
                map.vertices().size(),
                map.sectors().size());
        return map;
    }

    /** Imports the images referenced by the selected map. */
    private static DoomMapMaterials importMaterials(WadArchive archive, DoomMap map) {
        DoomMaterialImportResult result = new DoomMaterialImporter().importMap(archive, map);
        result.diagnostics().forEach(DoomStartup::printWadDiagnostic);
        DoomMapMaterials materials = result.materials()
                .orElseThrow(() -> new IllegalStateException("Cannot import startup map materials"));
        System.out.printf(
                "Imported %s materials: %,d wall textures and %,d flats%n",
                map.name(), materials.wallTextures().size(), materials.flats().size());
        return materials;
    }

    /** Builds renderer-independent static geometry. */
    private static DoomStaticGeometry buildGeometry(DoomMap map, DoomMapMaterials materials) {
        DoomGeometryBuildResult result = new DoomStaticGeometryBuilder().build(map, materials);
        result.diagnostics().forEach(DoomStartup::printGeometryDiagnostic);
        DoomStaticGeometry geometry = result.geometry()
                .orElseThrow(() -> new IllegalStateException("Cannot build startup map geometry"));
        int triangleCount = geometry.surfaces().stream()
                .mapToInt(surface -> surface.mesh().triangleCount())
                .sum();
        System.out.printf(
                "Built %s static geometry: %,d surfaces and %,d triangles%n",
                map.name(), geometry.surfaces().size(), triangleCount);
        return geometry;
    }

    /** Prints one project diagnostic consistently for both hosts. */
    private static void printProjectDiagnostic(ProjectDiagnostic diagnostic) {
        System.out.printf(
                "%s %s at %s%s: %s%n",
                diagnostic.severity(),
                diagnostic.code(),
                diagnostic.source(),
                diagnostic.location().isEmpty() ? "" : "#" + diagnostic.location(),
                diagnostic.message());
    }

    /** Prints one source/archive diagnostic consistently for both hosts. */
    private static void printWadDiagnostic(WadDiagnostic diagnostic) {
        System.out.printf(
                "%s %s at %s%s: %s%n",
                diagnostic.severity(),
                diagnostic.code(),
                diagnostic.source(),
                diagnostic.location().isEmpty() ? "" : "#" + diagnostic.location(),
                diagnostic.message());
    }

    /** Prints one geometry diagnostic consistently for both hosts. */
    private static void printGeometryDiagnostic(DoomGeometryDiagnostic diagnostic) {
        System.out.printf(
                "%s %s at %s: %s%n",
                diagnostic.severity(), diagnostic.code(), diagnostic.location(), diagnostic.message());
    }
}
