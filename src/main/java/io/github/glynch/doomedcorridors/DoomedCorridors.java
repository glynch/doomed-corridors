package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.map.DoomMap;
import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.material.DoomMaterialContactSheet;
import io.github.glynch.doomedcorridors.wad.DoomMapDecodeResult;
import io.github.glynch.doomedcorridors.wad.DoomMapDecoder;
import io.github.glynch.doomedcorridors.wad.DoomMaterialImportResult;
import io.github.glynch.doomedcorridors.wad.DoomMaterialImporter;
import io.github.glynch.doomedcorridors.wad.WadArchive;
import io.github.glynch.doomedcorridors.wad.WadDiagnostic;
import io.github.glynch.doomedcorridors.wad.WadLoadResult;
import io.github.glynch.doomedcorridors.wad.WadLoader;
import io.github.glynch.jscene3d.project.GameProject;
import io.github.glynch.jscene3d.project.ProjectDiagnostic;
import io.github.glynch.jscene3d.project.ProjectLoadResult;
import io.github.glynch.jscene3d.project.ProjectLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Standalone entry point for Doomed Corridors. */
public final class DoomedCorridors {
    private static final String ENGINE_VERSION = "0.1.0-SNAPSHOT";

    private DoomedCorridors() {
        throw new AssertionError("DoomedCorridors cannot be instantiated");
    }

    /**
     * Loads the game project without importing assets or starting native subsystems.
     *
     * @param arguments optional project directory; defaults to the working directory
     */
    public static void main(String[] arguments) {
        Path projectDirectory = Path.of(arguments.length == 0 ? "." : arguments[0]);
        ProjectLoadResult result = new ProjectLoader(ENGINE_VERSION).load(projectDirectory);
        for (ProjectDiagnostic diagnostic : result.diagnostics()) {
            System.out.printf(
                    "%s %s at %s%s: %s%n",
                    diagnostic.severity(),
                    diagnostic.code(),
                    diagnostic.source(),
                    diagnostic.location().isEmpty() ? "" : "#" + diagnostic.location(),
                    diagnostic.message());
        }
        GameProject project = result.project()
                .orElseThrow(() -> new IllegalStateException("Cannot load Doomed Corridors project"));
        System.out.printf(
                "Loaded %s %s; startup %s:%s%n",
                project.identity().name(),
                project.identity().version(),
                project.runtime().startup().asset(),
                project.runtime().startup().target());
        inspectStartupWad(project);
    }

    private static void inspectStartupWad(GameProject project) {
        GameProject.StartupTarget startup = project.runtime().startup();
        GameProject.AssetSource asset = project.assets().stream()
                .filter(candidate -> candidate.id().equals(startup.asset()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Startup asset is not defined"));
        if (!Files.isRegularFile(asset.path())) {
            return;
        }
        if (!asset.type().equals("doom-wad")) {
            throw new IllegalStateException("Startup asset is not a Doom WAD");
        }

        WadLoadResult result = new WadLoader().load(asset.path(), asset.sha256());
        for (WadDiagnostic diagnostic : result.diagnostics()) {
            System.out.printf(
                    "%s %s at %s%s: %s%n",
                    diagnostic.severity(),
                    diagnostic.code(),
                    diagnostic.source(),
                    diagnostic.location().isEmpty() ? "" : "#" + diagnostic.location(),
                    diagnostic.message());
        }
        WadArchive archive = result.archive()
                .orElseThrow(() -> new IllegalStateException("Cannot inspect startup WAD"));
        if (!archive.mapNames().contains(startup.target())) {
            throw new IllegalStateException("Startup map is not present in WAD: " + startup.target());
        }
        System.out.printf(
                "Inspected %s with %,d lumps and %,d maps; found startup map %s%n",
                archive.kind(), archive.lumps().size(), archive.mapNames().size(), startup.target());
        DoomMapDecodeResult mapResult = new DoomMapDecoder().decode(archive, startup.target());
        for (WadDiagnostic diagnostic : mapResult.diagnostics()) {
            System.out.printf(
                    "%s %s at %s%s: %s%n",
                    diagnostic.severity(),
                    diagnostic.code(),
                    diagnostic.source(),
                    diagnostic.location().isEmpty() ? "" : "#" + diagnostic.location(),
                    diagnostic.message());
        }
        DoomMap map = mapResult.map()
                .orElseThrow(() -> new IllegalStateException("Cannot decode startup map: " + startup.target()));
        System.out.printf(
                "Decoded %s: %,d things, %,d linedefs, %,d sidedefs, %,d vertices, and %,d sectors%n",
                map.name(),
                map.things().size(),
                map.linedefs().size(),
                map.sidedefs().size(),
                map.vertices().size(),
                map.sectors().size());
        DoomMaterialImportResult materialResult = new DoomMaterialImporter().importMap(archive, map);
        for (WadDiagnostic diagnostic : materialResult.diagnostics()) {
            System.out.printf(
                    "%s %s at %s%s: %s%n",
                    diagnostic.severity(),
                    diagnostic.code(),
                    diagnostic.source(),
                    diagnostic.location().isEmpty() ? "" : "#" + diagnostic.location(),
                    diagnostic.message());
        }
        DoomMapMaterials materials = materialResult.materials()
                .orElseThrow(() -> new IllegalStateException("Cannot import startup map materials"));
        System.out.printf(
                "Imported %s materials: %,d wall textures and %,d flats%n",
                map.name(), materials.wallTextures().size(), materials.flats().size());
        Path contactSheet = project.root()
                .resolve("target/smoke/" + map.name().toLowerCase(Locale.ROOT) + "-materials.png");
        try {
            new DoomMaterialContactSheet().write(materials, contactSheet);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write material contact sheet", exception);
        }
        System.out.println("Wrote material contact sheet to " + contactSheet);
    }
}
