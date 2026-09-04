/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.actor.DoomActorCatalog;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalogLoadResult;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalogLoader;
import io.github.glynch.doomedcorridors.actor.DoomActorDiagnostic;
import io.github.glynch.doomedcorridors.actor.DoomActorResolution;
import io.github.glynch.doomedcorridors.actor.DoomActorSprites;
import io.github.glynch.doomedcorridors.actor.DoomSkillLevel;
import io.github.glynch.doomedcorridors.combat.DoomCombatDiagnostic;
import io.github.glynch.doomedcorridors.combat.DoomCombatRules;
import io.github.glynch.doomedcorridors.combat.DoomCombatRulesLoadResult;
import io.github.glynch.doomedcorridors.combat.DoomCombatRulesLoader;
import io.github.glynch.jscene3d.doom.map.DoomMap;
import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.presentation.DoomCombatAssets;
import io.github.glynch.doomedcorridors.presentation.DoomCombatPresentationLoadResult;
import io.github.glynch.doomedcorridors.presentation.DoomCombatPresentationLoader;
import io.github.glynch.doomedcorridors.presentation.DoomCombatPresentationRules;
import io.github.glynch.doomedcorridors.wad.DoomCombatAssetImportResult;
import io.github.glynch.doomedcorridors.wad.DoomCombatAssetImporter;
import io.github.glynch.doomedcorridors.wad.DoomMapDecodeResult;
import io.github.glynch.doomedcorridors.wad.DoomMapDecoder;
import io.github.glynch.doomedcorridors.wad.DoomMaterialImportResult;
import io.github.glynch.doomedcorridors.wad.DoomMaterialImporter;
import io.github.glynch.doomedcorridors.wad.DoomSpriteImportResult;
import io.github.glynch.doomedcorridors.wad.DoomSpriteImporter;
import io.github.glynch.doomedcorridors.wad.WadArchive;
import io.github.glynch.doomedcorridors.wad.WadDiagnostic;
import io.github.glynch.doomedcorridors.wad.WadLoadResult;
import io.github.glynch.doomedcorridors.wad.WadLoader;
import io.github.glynch.doomedcorridors.world.DoomActorResolver;
import io.github.glynch.doomedcorridors.world.DoomGeometryBuildResult;
import io.github.glynch.doomedcorridors.world.DoomGeometryDiagnostic;
import io.github.glynch.doomedcorridors.world.DoomStaticGeometry;
import io.github.glynch.doomedcorridors.world.DoomStaticGeometryBuilder;
import io.github.glynch.jscene3d.project.diagnostic.ProjectDiagnostic;
import io.github.glynch.jscene3d.project.imports.ImportDefinition;
import io.github.glynch.jscene3d.project.imports.ImportLoadResult;
import io.github.glynch.jscene3d.project.imports.ImportLoader;
import io.github.glynch.jscene3d.project.manifest.GameProject;
import io.github.glynch.jscene3d.project.manifest.ProjectLoadResult;
import io.github.glynch.jscene3d.project.manifest.ProjectLoader;
import io.github.glynch.jscene3d.project.scene.SceneDefinition;
import io.github.glynch.jscene3d.project.scene.SceneLoadResult;
import io.github.glynch.jscene3d.project.scene.SceneLoader;
import io.github.glynch.jscene3d.project.scene.SceneNodeDefinition;
import io.github.glynch.jscene3d.project.value.ProjectValue;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/** Loaded startup data shared by graphical and headless hosts. */
record DoomStartup(
        GameProject project,
        SceneDefinition entryScene,
        DoomMap map,
        DoomMapMaterials materials,
        DoomStaticGeometry geometry,
        DoomActorResolution actors,
        DoomActorSprites sprites,
        DoomCombatStartup combat) {
    private static final String ENGINE_VERSION = "0.1.0-SNAPSHOT";
    private static final String APPLICATION_EXTENSION = "io.github.glynch.doomed-corridors";
    private static final String DOOM_LEVEL_TYPE = APPLICATION_EXTENSION + "/doom-level-3d";
    private static final String DOOM_MAP_IMPORTER = "io.github.glynch.jscene3d.doom/maps";
    private static final String WAD_SOURCE_TYPE = "io.github.glynch.jscene3d.wad/source";
    private static final String ACTOR_CATALOG_TYPE = APPLICATION_EXTENSION + "/actor-catalog";
    private static final String COMBAT_RULES_TYPE = APPLICATION_EXTENSION + "/combat-rules";
    private static final String COMBAT_PRESENTATION_TYPE = APPLICATION_EXTENSION + "/combat-presentation";

    /** Loads the complete project-selected static-map pipeline. */
    static DoomStartup load(Path projectDirectory) {
        ProjectLoadResult projectResult = new ProjectLoader(ENGINE_VERSION).load(projectDirectory);
        projectResult.diagnostics().forEach(DoomStartup::printProjectDiagnostic);
        GameProject project = projectResult.project()
                .orElseThrow(() -> new IllegalStateException("Cannot load Doomed Corridors project"));
        SceneDefinition entryScene = loadEntryScene(project);
        DoomLevelSource level = levelSource(project, entryScene);
        System.out.printf(
                "Loaded %s %s; startup %s:%s%n",
                project.identity().name(),
                project.identity().version(),
                level.assetId(),
                level.mapName());
        DoomActorCatalog actorCatalog = loadActorCatalog(project);
        DoomCombatRules combatRules = loadCombatRules(project, actorCatalog);
        DoomCombatPresentationRules combatPresentation = loadCombatPresentation(project, combatRules);
        GameProject.AssetSource asset = startupAsset(project, level.assetId());
        WadArchive archive = loadArchive(asset, level.mapName());
        DoomMap map = decodeMap(archive, level.mapName());
        DoomMapMaterials materials = importMaterials(archive, map);
        DoomStaticGeometry geometry = buildGeometry(map, materials);
        DoomActorResolution actors = resolveActors(archive, map, actorCatalog);
        DoomActorSprites sprites = importSprites(archive, actors);
        DoomCombatAssets combatAssets = importCombatAssets(archive, combatPresentation);
        return new DoomStartup(
                project, entryScene, map, materials, geometry, actors, sprites,
                new DoomCombatStartup(combatRules, combatAssets));
    }

    /** Loads the manifest-selected entry scene. */
    private static SceneDefinition loadEntryScene(GameProject project) {
        SceneLoadResult result = new SceneLoader().loadEntryScene(project);
        result.diagnostics().forEach(DoomStartup::printProjectDiagnostic);
        return result.scene().orElseThrow(() -> new IllegalStateException("Cannot load entry scene"));
    }

    /** Reads the Doom source selection from the typed entry-scene root. */
    private static DoomLevelSource levelSource(GameProject project, SceneDefinition scene) {
        SceneNodeDefinition.TypedNode typed = findDoomLevel(scene.root())
                .orElseThrow(() -> new IllegalStateException(
                        "Entry scene must contain a Doomed Corridors Doom level"));
        ProjectValue map = typed.properties().get("map");
        if (!(map instanceof ProjectValue.ReferenceValue reference)
                || reference.reference().kind() != ResourceReference.Kind.IMPORT) {
            throw new IllegalStateException("Doom level map must reference an imported resource");
        }
        String locator = reference.reference().locator();
        int separator = locator.indexOf('/');
        String importId = locator.substring(0, separator);
        String output = locator.substring(separator + 1);
        ImportDefinition definition = findImport(project, importId);
        if (!definition.importer().equals(DOOM_MAP_IMPORTER) || !definition.selection().contains(output)) {
            throw new IllegalStateException("Doom level map is not selected by its declared import");
        }
        if (!output.startsWith("maps/") || output.length() == "maps/".length()) {
            throw new IllegalStateException("Doom level map output has an invalid identity: " + output);
        }
        return new DoomLevelSource(definition.asset().id(), output.substring("maps/".length()));
    }

    /** Finds the first typed Doom level in scene-tree order. */
    private static Optional<SceneNodeDefinition.TypedNode> findDoomLevel(
            SceneNodeDefinition node) {
        if (node.source() instanceof SceneNodeDefinition.TypedNode typed
                && typed.type().id().equals(DOOM_LEVEL_TYPE)) {
            return Optional.of(typed);
        }
        for (SceneNodeDefinition child : node.children()) {
            Optional<SceneNodeDefinition.TypedNode> result = findDoomLevel(child);
            if (result.isPresent()) {
                return result;
            }
        }
        return Optional.empty();
    }

    /** Finds the import definition selected by an imported resource reference. */
    private static ImportDefinition findImport(GameProject project, String importId) {
        ImportLoader loader = new ImportLoader();
        for (Path path : project.imports()) {
            ImportLoadResult result = loader.load(project, path);
            result.diagnostics().forEach(DoomStartup::printProjectDiagnostic);
            ImportDefinition definition = result.definition()
                    .orElseThrow(() -> new IllegalStateException("Cannot load import definition: " + path));
            if (definition.id().equals(importId)) {
                return definition;
            }
        }
        throw new IllegalStateException("Doom level import is not defined: " + importId);
    }

    /** Loads the independently versioned combat-to-WAD presentation bindings. */
    private static DoomCombatPresentationRules loadCombatPresentation(
            GameProject project, DoomCombatRules combatRules) {
        GameProject.AssetSource source = project.assets().stream()
                .filter(asset -> asset.id().equals("combat-presentation"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Combat presentation asset is not defined"));
        if (!source.type().equals(COMBAT_PRESENTATION_TYPE)) {
            throw new IllegalStateException("Combat presentation asset has the wrong type");
        }
        DoomCombatPresentationLoadResult result =
                new DoomCombatPresentationLoader().load(source.path(), combatRules);
        result.diagnostics().forEach(DoomStartup::printCombatDiagnostic);
        DoomCombatPresentationRules rules = result.rules()
                .orElseThrow(() -> new IllegalStateException("Cannot load combat presentation"));
        System.out.printf(
                "Loaded combat presentation with %,d images and %,d sounds%n",
                rules.imageLumps().size(), rules.soundLumps().size());
        return rules;
    }

    /** Loads the actor catalog declared by this Game Provider's project. */
    private static DoomActorCatalog loadActorCatalog(GameProject project) {
        GameProject.AssetSource source = project.assets().stream()
                .filter(asset -> asset.id().equals("actors"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Actor catalog asset is not defined"));
        if (!source.type().equals(ACTOR_CATALOG_TYPE)) {
            throw new IllegalStateException("Actor catalog asset has the wrong type");
        }
        DoomActorCatalogLoadResult result = new DoomActorCatalogLoader().load(source.path());
        result.diagnostics().forEach(DoomStartup::printActorDiagnostic);
        DoomActorCatalog catalog = result.catalog()
                .orElseThrow(() -> new IllegalStateException("Cannot load actor catalog"));
        System.out.printf("Loaded %,d provider actor definitions%n", catalog.definitions().size());
        return catalog;
    }

    /** Loads the combat rules declared by this Game Provider's project. */
    private static DoomCombatRules loadCombatRules(
            GameProject project, DoomActorCatalog actorCatalog) {
        GameProject.AssetSource source = project.assets().stream()
                .filter(asset -> asset.id().equals("combat"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Combat rules asset is not defined"));
        if (!source.type().equals(COMBAT_RULES_TYPE)) {
            throw new IllegalStateException("Combat rules asset has the wrong type");
        }
        DoomCombatRulesLoadResult result =
                new DoomCombatRulesLoader().load(source.path(), actorCatalog);
        result.diagnostics().forEach(DoomStartup::printCombatDiagnostic);
        DoomCombatRules rules = result.rules()
                .orElseThrow(() -> new IllegalStateException("Cannot load combat rules"));
        System.out.printf(
                "Loaded combat rules for %s, %,d combatant definition, and %,d pickup definitions%n",
                rules.primaryWeaponId(),
                rules.combatantDefinitionCount(),
                rules.pickupDefinitionCount());
        return rules;
    }

    /** Resolves the manifest-selected source asset. */
    private static GameProject.AssetSource startupAsset(GameProject project, String assetId) {
        GameProject.AssetSource asset = project.assets().stream()
                .filter(candidate -> candidate.id().equals(assetId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Startup asset is not defined"));
        if (!Files.isRegularFile(asset.path())) {
            throw new IllegalStateException("Startup asset is not installed: " + asset.path());
        }
        if (!asset.type().equals(WAD_SOURCE_TYPE)) {
            throw new IllegalStateException("Startup asset is not a Doom WAD");
        }
        return asset;
    }

    /** Loads and checks the startup archive. */
    private static WadArchive loadArchive(GameProject.AssetSource asset, String target) {
        WadLoadResult result = new WadLoader().load(asset.path(), asset.sha256());
        result.diagnostics().forEach(DoomStartup::printWadDiagnostic);
        WadArchive archive = result.archive()
                .orElseThrow(() -> new IllegalStateException("Cannot inspect startup WAD"));
        if (!archive.mapNames().contains(target)) {
            throw new IllegalStateException("Startup map is not present in WAD: " + target);
        }
        System.out.printf(
                "Inspected %s with %,d lumps and %,d maps; found startup map %s%n",
                archive.kind(), archive.lumps().size(), archive.mapNames().size(), target);
        return archive;
    }

    /** Decodes the selected classic map. */
    private static DoomMap decodeMap(WadArchive archive, String target) {
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

    /** Resolves normal-skill single-player map things through provider definitions. */
    private static DoomActorResolution resolveActors(
            WadArchive archive, DoomMap map, DoomActorCatalog catalog) {
        DoomActorResolution result =
                new DoomActorResolver().resolve(archive.source(), map, catalog, DoomSkillLevel.NORMAL);
        result.diagnostics().forEach(DoomStartup::printActorDiagnostic);
        System.out.printf("Resolved %s actors: %,d visible placements%n", map.name(), result.actors().size());
        return result;
    }

    /** Imports the unique initial sprite frames used by resolved actors. */
    private static DoomActorSprites importSprites(WadArchive archive, DoomActorResolution actors) {
        DoomSpriteImportResult result = new DoomSpriteImporter().importActors(archive, actors.actors());
        result.diagnostics().forEach(DoomStartup::printWadDiagnostic);
        DoomActorSprites sprites = result.sprites()
                .orElseThrow(() -> new IllegalStateException("Cannot import startup actor sprites"));
        System.out.printf("Imported %,d unique actor sprite frames%n", sprites.byFrame().size());
        return sprites;
    }

    /** Imports every exact image and DMX sound required by combat presentation. */
    private static DoomCombatAssets importCombatAssets(
            WadArchive archive, DoomCombatPresentationRules rules) {
        DoomCombatAssetImportResult result = new DoomCombatAssetImporter().importAssets(archive, rules);
        result.diagnostics().forEach(DoomStartup::printWadDiagnostic);
        DoomCombatAssets assets = result.assets()
                .orElseThrow(() -> new IllegalStateException("Cannot import combat presentation assets"));
        System.out.printf(
                "Imported combat presentation: %,d images and %,d sounds%n",
                assets.images().size(), assets.sounds().size());
        return assets;
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

    /** Prints one actor diagnostic consistently for both hosts. */
    private static void printActorDiagnostic(DoomActorDiagnostic diagnostic) {
        System.out.printf(
                "%s %s at %s%s: %s%n",
                diagnostic.severity(),
                diagnostic.code(),
                diagnostic.source(),
                diagnostic.location().isEmpty() ? "" : "#" + diagnostic.location(),
                diagnostic.message());
    }

    /** Prints one provider combat-rules diagnostic consistently for both hosts. */
    private static void printCombatDiagnostic(DoomCombatDiagnostic diagnostic) {
        System.out.printf(
                "%s %s at %s%s: %s%n",
                diagnostic.severity(),
                diagnostic.code(),
                diagnostic.source(),
                diagnostic.location().isEmpty() ? "" : "#" + diagnostic.location(),
                diagnostic.message());
    }

    /** Declarative WAD asset and map selected by the entry scene. */
    private record DoomLevelSource(String assetId, String mapName) {}
}

/** Combat simulation rules paired with presentation assets for both application hosts. */
record DoomCombatStartup(DoomCombatRules rules, DoomCombatAssets assets) {}
