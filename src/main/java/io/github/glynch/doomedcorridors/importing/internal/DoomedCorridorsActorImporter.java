/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.importing.internal;

import io.github.glynch.doomedcorridors.actor.DoomActor;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalog;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalogLoadResult;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalogLoader;
import io.github.glynch.doomedcorridors.actor.DoomActorDefinition;
import io.github.glynch.doomedcorridors.actor.DoomActorDiagnostic;
import io.github.glynch.doomedcorridors.actor.DoomActorResolution;
import io.github.glynch.doomedcorridors.actor.DoomSkillLevel;
import io.github.glynch.doomedcorridors.combat.DoomCombatRules;
import io.github.glynch.doomedcorridors.combat.DoomCombatRulesLoadResult;
import io.github.glynch.doomedcorridors.combat.DoomCombatRulesLoader;
import io.github.glynch.doomedcorridors.internal.DoomedCorridorsRuntimeTypes;
import io.github.glynch.doomedcorridors.presentation.DoomCombatPresentationLoadResult;
import io.github.glynch.doomedcorridors.presentation.DoomCombatPresentationLoader;
import io.github.glynch.doomedcorridors.presentation.DoomCombatPresentationRules;
import io.github.glynch.doomedcorridors.wad.DoomDmxSoundDecoder;
import io.github.glynch.doomedcorridors.world.DoomActorResolver;
import io.github.glynch.jscene3d.audio.PcmAudio;
import io.github.glynch.jscene3d.diagnostic.DiagnosticCode;
import io.github.glynch.jscene3d.doom.diagnostic.DoomDiagnostic;
import io.github.glynch.jscene3d.doom.geometry.DoomUnits;
import io.github.glynch.jscene3d.doom.map.DoomMapDecodeResult;
import io.github.glynch.jscene3d.doom.map.DoomMapDecoder;
import io.github.glynch.jscene3d.doom.material.DoomPatchDataException;
import io.github.glynch.jscene3d.doom.material.DoomPatchDecoder;
import io.github.glynch.jscene3d.doom.material.DoomPatchImage;
import io.github.glynch.jscene3d.doom.material.RgbaImage;
import io.github.glynch.jscene3d.game.presentation.GamePresentationDescriptors;
import io.github.glynch.jscene3d.game.presentation.GamePresentationResourceWriter;
import io.github.glynch.jscene3d.materials.AlphaMode;
import io.github.glynch.jscene3d.materials.BasicMaterial;
import io.github.glynch.jscene3d.project.asset.AssetId;
import io.github.glynch.jscene3d.project.asset.AssetRef;
import io.github.glynch.jscene3d.project.asset.DefinitionWriter;
import io.github.glynch.jscene3d.project.component.ComponentDefinition;
import io.github.glynch.jscene3d.project.component.ComponentId;
import io.github.glynch.jscene3d.project.component.ComponentType;
import io.github.glynch.jscene3d.project.component.PropertyId;
import io.github.glynch.jscene3d.project.contract.EntityContract;
import io.github.glynch.jscene3d.project.entity.ComponentTarget;
import io.github.glynch.jscene3d.project.entity.EndpointTarget;
import io.github.glynch.jscene3d.project.entity.EntityDefinition;
import io.github.glynch.jscene3d.project.entity.EntityEntry;
import io.github.glynch.jscene3d.project.entity.EntityId;
import io.github.glynch.jscene3d.project.entity.EntityPlacement;
import io.github.glynch.jscene3d.project.entity.LocalEntity;
import io.github.glynch.jscene3d.project.entity.PropertyTarget;
import io.github.glynch.jscene3d.project.entity.SignalConnection;
import io.github.glynch.jscene3d.project.extension.ProjectValueKind;
import io.github.glynch.jscene3d.project.importing.ImportArtifactDescriptor;
import io.github.glynch.jscene3d.project.importing.SourceItem;
import io.github.glynch.jscene3d.project.importing.extension.ImportInspectionContext;
import io.github.glynch.jscene3d.project.importing.extension.ImportPreparationContext;
import io.github.glynch.jscene3d.project.importing.extension.ProjectImporter;
import io.github.glynch.jscene3d.project.manifest.GameProject;
import io.github.glynch.jscene3d.project.physics3d.Physics3dDescriptors;
import io.github.glynch.jscene3d.project.physics3d.Physics3dResourceWriter;
import io.github.glynch.jscene3d.project.spatial3d.descriptor.Spatial3dDescriptors;
import io.github.glynch.jscene3d.project.spatial3d.resource.Spatial3dResourceWriter;
import io.github.glynch.jscene3d.project.value.ProjectValue;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import io.github.glynch.jscene3d.textures.Texture;
import io.github.glynch.jscene3d.textures.TextureCoordinateOrigin;
import io.github.glynch.jscene3d.textures.TextureFilter;
import io.github.glynch.jscene3d.textures.TextureWrap;
import io.github.glynch.jscene3d.wad.WadArchive;
import io.github.glynch.jscene3d.wad.WadDiagnostic;
import io.github.glynch.jscene3d.wad.WadLoadResult;
import io.github.glynch.jscene3d.wad.WadLoader;
import io.github.glynch.jscene3d.wad.WadLump;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/** Publishes game-owned actor definitions and MAP actor placements from generic decoded Doom content. */
final class DoomedCorridorsActorImporter implements ProjectImporter {
    private static final String ITEM_KIND = DoomedCorridorsImportExtension.EXTENSION_ID + "/actor-map";
    private static final String ACTOR_CATALOG_TYPE = DoomedCorridorsImportExtension.EXTENSION_ID + "/actor-catalog";
    private static final String COMBAT_RULES_TYPE = DoomedCorridorsImportExtension.EXTENSION_ID + "/combat-rules";
    private static final String COMBAT_PRESENTATION_TYPE =
            DoomedCorridorsImportExtension.EXTENSION_ID + "/combat-presentation";
    private static final String ACTOR_CATALOG_SETTING = "actor-catalog";
    private static final String COMBAT_RULES_SETTING = "combat-rules";
    private static final String COMBAT_PRESENTATION_SETTING = "combat-presentation";
    private static final String TEXTURE_MEDIA_TYPE = "application/vnd.jscene3d.rgba8-v1";
    private static final String PCM_MEDIA_TYPE = "application/vnd.jscene3d.pcm16le-v1";
    private static final int PALETTE_SIZE = 256 * 3;
    private static final float PICKUP_SENSOR_CENTER_HEIGHT = DoomUnits.toWorld(28.0F);
    private static final float DOOM_SOUND_FULL_VOLUME_DISTANCE = DoomUnits.toWorld(160.0F);
    private static final float DOOM_SOUND_MAXIMUM_DISTANCE = DoomUnits.toWorld(1200.0F);
    private static final float DOOM_SOUND_ROLLOFF_FACTOR = 1.0F;
    private static final Set<String> START_MARKERS = Set.of("S_START", "SS_START");
    private static final Set<String> END_MARKERS = Set.of("S_END", "SS_END");
    private static final PropertyId POSITION_ARGUMENT = new PropertyId("position");
    private static final PropertyId PLAYER_ARGUMENT = new PropertyId("player");
    private static final PropertyId TARGET_PROVIDER_ARGUMENT = new PropertyId("target-provider");

    @Override
    public void inspect(ImportInspectionContext context) {
        loadArchive(context).ifPresent(archive -> describeMaps(context, archive));
    }

    @Override
    public void prepare(ImportPreparationContext context) throws IOException {
        Optional<WadArchive> loadedArchive = loadArchive(context);
        loadedArchive.ifPresent(archive -> describeMaps(context, archive));
        Optional<DoomActorCatalog> loadedCatalog = loadCatalog(context);
        Optional<DoomCombatRules> loadedRules = loadedCatalog.flatMap(catalog -> loadCombatRules(context, catalog));
        Optional<DoomCombatPresentationRules> loadedPresentation =
                loadedRules.flatMap(rules -> loadCombatPresentation(context, rules));
        if (loadedArchive.isEmpty()
                || loadedCatalog.isEmpty()
                || loadedRules.isEmpty()
                || loadedPresentation.isEmpty()) {
            return;
        }
        WadArchive archive = loadedArchive.orElseThrow();
        DoomActorCatalog catalog = loadedCatalog.orElseThrow();
        DoomCombatRules rules = loadedRules.orElseThrow();
        DoomCombatPresentationRules presentation = loadedPresentation.orElseThrow();
        Optional<CombatPresentationAssets> loadedAssets = importPresentationAssets(context, archive, presentation);
        if (loadedAssets.isEmpty()) {
            return;
        }
        CombatPresentationAssets assets = loadedAssets.orElseThrow();
        Set<String> selection = Set.copyOf(context.definition().selection());
        for (String mapName : new DoomMapDecoder().discover(archive)) {
            String identity = mapIdentity(mapName);
            if (selection.contains(identity)) {
                prepareMap(context, archive, catalog, rules, assets, mapName, identity);
            }
        }
    }

    /** Declares selectable actor publications for every classic map marker. */
    private static void describeMaps(ImportInspectionContext context, WadArchive archive) {
        for (String mapName : new DoomMapDecoder().discover(archive)) {
            context.sourceItem(new SourceItem(
                    mapIdentity(mapName),
                    ITEM_KIND,
                    mapName + " actors",
                    true,
                    Map.of("map", new ProjectValue.TextValue(mapName)),
                    List.of()));
        }
    }

    /** Loads and verifies the authoritative WAD source. */
    private static Optional<WadArchive> loadArchive(ImportInspectionContext context) {
        context.checkCancelled();
        WadLoadResult result = context.asset().sha256().isPresent()
                ? WadLoader.load(
                        context.asset().path(), context.asset().sha256().orElseThrow())
                : WadLoader.load(context.asset().path());
        result.diagnostics().forEach(diagnostic -> report(context, diagnostic));
        return result.isValid() ? result.archive() : Optional.empty();
    }

    /** Resolves and loads the provider-owned actor catalog selected by the import recipe. */
    private static Optional<DoomActorCatalog> loadCatalog(ImportPreparationContext context) {
        ProjectValue setting = context.definition().settings().get(ACTOR_CATALOG_SETTING);
        if (!(setting instanceof ProjectValue.TextValue(String assetId))) {
            context.error(
                    ActorImportDiagnosticCode.CATALOG_SETTING_INVALID,
                    "/settings/" + ACTOR_CATALOG_SETTING,
                    Map.of("expected", "declared actor-catalog asset id"));
            return Optional.empty();
        }
        Optional<GameProject.AssetSource> selected = context.project().assets().stream()
                .filter(asset -> asset.id().equals(assetId))
                .findFirst();
        if (selected.isEmpty() || !selected.orElseThrow().type().equals(ACTOR_CATALOG_TYPE)) {
            context.error(
                    ActorImportDiagnosticCode.CATALOG_SETTING_INVALID,
                    "/settings/" + ACTOR_CATALOG_SETTING,
                    Map.of("asset", assetId, "expectedType", ACTOR_CATALOG_TYPE));
            return Optional.empty();
        }
        GameProject.AssetSource source = selected.orElseThrow();
        context.dependency(source.path());
        DoomActorCatalogLoadResult result = new DoomActorCatalogLoader().load(source.path());
        result.diagnostics().forEach(diagnostic -> report(context, diagnostic));
        return result.isValid() ? result.catalog() : Optional.empty();
    }

    /** Loads the provider combat rules selected by the recipe and records them as an import dependency. */
    private static Optional<DoomCombatRules> loadCombatRules(
            ImportPreparationContext context, DoomActorCatalog catalog) {
        Optional<GameProject.AssetSource> selected = configuredAsset(
                context,
                COMBAT_RULES_SETTING,
                COMBAT_RULES_TYPE,
                ActorImportDiagnosticCode.COMBAT_RULES_SETTING_INVALID);
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        GameProject.AssetSource source = selected.orElseThrow();
        context.dependency(source.path());
        DoomCombatRulesLoadResult result = new DoomCombatRulesLoader().load(source.path(), catalog);
        result.diagnostics()
                .forEach(diagnostic -> context.error(
                        ActorImportDiagnosticCode.COMBAT_RULES_INVALID,
                        diagnostic.location(),
                        Map.of("sourceCode", diagnostic.code(), "message", diagnostic.message())));
        return result.isValid() ? result.rules() : Optional.empty();
    }

    /** Loads the provider presentation rules selected by the recipe and records them as a dependency. */
    private static Optional<DoomCombatPresentationRules> loadCombatPresentation(
            ImportPreparationContext context, DoomCombatRules combatRules) {
        Optional<GameProject.AssetSource> selected = configuredAsset(
                context,
                COMBAT_PRESENTATION_SETTING,
                COMBAT_PRESENTATION_TYPE,
                ActorImportDiagnosticCode.COMBAT_PRESENTATION_SETTING_INVALID);
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        GameProject.AssetSource source = selected.orElseThrow();
        context.dependency(source.path());
        DoomCombatPresentationLoadResult result = new DoomCombatPresentationLoader().load(source.path(), combatRules);
        result.diagnostics()
                .forEach(diagnostic -> context.error(
                        ActorImportDiagnosticCode.COMBAT_PRESENTATION_INVALID,
                        diagnostic.location(),
                        Map.of("sourceCode", diagnostic.code(), "message", diagnostic.message())));
        return result.isValid() ? result.rules() : Optional.empty();
    }

    /** Decodes selected weapon, HUD, and combatant audio so runtime publication never needs the source WAD. */
    private static Optional<CombatPresentationAssets> importPresentationAssets(
            ImportPreparationContext context, WadArchive archive, DoomCombatPresentationRules presentation) {
        DoomCombatPresentationRules.Weapon weapon = presentation.weapon();
        try {
            WadLump paletteLump = requiredLump(archive, "PLAYPAL");
            byte[] palette = archive.readAllBytes(paletteLump, paletteLump.size());
            if (palette.length < PALETTE_SIZE) {
                throw new DoomPatchDataException("PLAYPAL is shorter than one complete palette");
            }
            Map<String, RgbaImage> images = new LinkedHashMap<>();
            List<String> requiredImages = new ArrayList<>();
            requiredImages.add(weapon.readyFrame());
            requiredImages.addAll(weapon.fireFrames());
            requiredImages.addAll(presentation.hud().digits());
            requiredImages.add(presentation.hud().percent());
            for (String image : requiredImages) {
                WadLump lump = requiredLump(archive, image);
                DoomPatchImage patch =
                        DoomPatchDecoder.decode(archive.readAllBytes(lump, lump.size()), palette, lump.name());
                images.put(image, patch.image());
            }
            Map<String, PcmAudio> sounds = new LinkedHashMap<>();
            for (String sound : presentation.soundLumps()) {
                WadLump soundLump = requiredLump(archive, sound);
                sounds.put(
                        sound,
                        DoomDmxSoundDecoder.decode(
                                archive.readAllBytes(soundLump, soundLump.size()), soundLump.name()));
            }
            return Optional.of(new CombatPresentationAssets(presentation, images, sounds));
        } catch (IOException | DoomPatchDataException | IllegalArgumentException exception) {
            context.error(
                    ActorImportDiagnosticCode.COMBAT_PRESENTATION_INVALID,
                    "/presentation/weapon",
                    Map.of("message", String.valueOf(exception.getMessage())));
            return Optional.empty();
        }
    }

    /** Resolves one exact required lump using normal WAD override semantics. */
    private static WadLump requiredLump(WadArchive archive, String name) {
        return archive.lastLumpNamed(name)
                .orElseThrow(() -> new IllegalArgumentException("required WAD lump is missing: " + name));
    }

    /** Resolves one recipe setting to a declared source asset of the required type. */
    private static Optional<GameProject.AssetSource> configuredAsset(
            ImportPreparationContext context,
            String settingId,
            String assetType,
            ActorImportDiagnosticCode diagnosticCode) {
        ProjectValue setting = context.definition().settings().get(settingId);
        if (!(setting instanceof ProjectValue.TextValue(String assetId))) {
            context.error(diagnosticCode, "/settings/" + settingId, Map.of("expected", "declared " + assetType));
            return Optional.empty();
        }
        Optional<GameProject.AssetSource> selected = context.project().assets().stream()
                .filter(asset -> asset.id().equals(assetId) && asset.type().equals(assetType))
                .findFirst();
        if (selected.isEmpty()) {
            context.error(
                    diagnosticCode, "/settings/" + settingId, Map.of("asset", assetId, "expectedType", assetType));
        }
        return selected;
    }

    /** Decodes, resolves, and publishes one selected map's visible normal-skill actors. */
    private static void prepareMap(
            ImportPreparationContext context,
            WadArchive archive,
            DoomActorCatalog catalog,
            DoomCombatRules rules,
            CombatPresentationAssets presentation,
            String mapName,
            String prefix)
            throws IOException {
        DoomMapDecodeResult decoded = new DoomMapDecoder().decode(archive, mapName);
        decoded.diagnostics().forEach(diagnostic -> report(context, diagnostic));
        if (!decoded.isValid()) {
            return;
        }
        DoomActorResolution resolution = new DoomActorResolver()
                .resolve(context.asset().path(), decoded.map().orElseThrow(), catalog, DoomSkillLevel.NORMAL);
        resolution.diagnostics().forEach(diagnostic -> report(context, diagnostic));
        Optional<Map<String, ImportedSprite>> imported =
                importSprites(context, archive, resolution.actors(), presentation.presentation());
        if (imported.isEmpty()) {
            return;
        }
        publishMap(context, prefix, resolution.actors(), imported.orElseThrow(), rules, presentation);
        publishWeaponPresentation(context, prefix, presentation);
    }

    /** Imports every unique selected spawn frame while preserving classic patch origin metadata. */
    private static Optional<Map<String, ImportedSprite>> importSprites(
            ImportPreparationContext context,
            WadArchive archive,
            List<DoomActor> actors,
            DoomCombatPresentationRules presentation) {
        WadLump paletteLump = archive.lastLumpNamed("PLAYPAL").orElse(null);
        if (paletteLump == null) {
            context.error(ActorImportDiagnosticCode.PALETTE_MISSING, "/sprites/palette", Map.of());
            return Optional.empty();
        }
        try {
            byte[] palette = archive.readAllBytes(paletteLump, paletteLump.size());
            if (palette.length < PALETTE_SIZE) {
                context.error(
                        ActorImportDiagnosticCode.PALETTE_INVALID,
                        "/sprites/palette",
                        Map.of("actualBytes", Integer.toString(palette.length)));
                return Optional.empty();
            }
            Map<String, WadLump> namespace = spriteLumps(archive);
            Map<String, ImportedSprite> result = new LinkedHashMap<>();
            for (String frame : requiredFrames(actors, presentation)) {
                WadLump lump = frameLump(namespace, frame);
                if (lump == null) {
                    context.warning(
                            ActorImportDiagnosticCode.SPRITE_MISSING, "/sprites/" + frame, Map.of("frame", frame));
                    continue;
                }
                DoomPatchImage patch =
                        DoomPatchDecoder.decode(archive.readAllBytes(lump, lump.size()), palette, lump.name());
                result.put(frame, new ImportedSprite(frame, patch.image(), patch.leftOffset(), patch.topOffset()));
            }
            return Optional.of(Collections.unmodifiableMap(result));
        } catch (IOException | DoomPatchDataException exception) {
            context.error(
                    ActorImportDiagnosticCode.SPRITE_INVALID,
                    "/sprites",
                    Map.of("message", String.valueOf(exception.getMessage())));
            return Optional.empty();
        }
    }

    /** Publishes shared sprite resources, reusable actor definitions, and one aggregate placement definition. */
    private static void publishMap(
            ImportPreparationContext context,
            String prefix,
            List<DoomActor> actors,
            Map<String, ImportedSprite> sprites,
            DoomCombatRules rules,
            CombatPresentationAssets presentation)
            throws IOException {
        RuleReferences ruleReferences = ruleReferences(context);
        for (ImportedSprite sprite : sprites.values()) {
            publishSprite(context, prefix, sprite);
        }
        Map<String, DoomActorDefinition> definitions = selectedDefinitions(actors, sprites);
        for (DoomActorDefinition definition : definitions.values()) {
            Optional<DoomCombatRules.PickupDefinition> pickup = rules.findPickup(definition.id());
            Optional<DoomCombatRules.CombatantBounds> combatant = rules.findCombatantBounds(definition.id());
            ActorPresentation actorPresentation = actorPresentation(definition, sprites, presentation.presentation());
            if (pickup.isPresent()) {
                publishPickupShape(context, prefix, definition, pickup.orElseThrow());
            }
            if (combatant.isPresent()) {
                publishCombatantShape(context, prefix, definition, combatant.orElseThrow());
                if (actorPresentation.combatant().isPresent()) {
                    publishCombatantSounds(
                            context,
                            prefix,
                            definition.id(),
                            actorPresentation.combatant().orElseThrow().rules(),
                            presentation.sounds());
                }
            }
            publishActorDefinition(context, prefix, definition, actorPresentation, pickup, combatant, ruleReferences);
        }
        publishActorPlacements(context, prefix, actors, definitions, rules);
    }

    /** Publishes one nearest-filtered alpha-masked sprite texture and material. */
    private static void publishSprite(ImportPreparationContext context, String prefix, ImportedSprite sprite)
            throws IOException {
        String textureIdentity = textureIdentity(prefix, sprite.frame());
        String payloadIdentity = textureIdentity + ".rgba8";
        String materialIdentity = materialIdentity(prefix, sprite.frame());
        try (Texture texture = createTexture(sprite.image())) {
            context.artifact(
                    ImportArtifactDescriptor.payload(payloadIdentity, TEXTURE_MEDIA_TYPE),
                    output -> Spatial3dResourceWriter.writeTexturePayload(output, texture));
            ResourceReference payload = imported(context, payloadIdentity);
            context.artifact(
                    ImportArtifactDescriptor.resource(
                            textureIdentity, Spatial3dDescriptors.textureResourceType(), List.of(payloadIdentity)),
                    output -> Spatial3dResourceWriter.writeTextureDefinition(output, texture, payload));
            publishMaterial(context, texture, textureIdentity, materialIdentity);
        }
    }

    /** Publishes one shared unlit alpha-masked material for an imported sprite texture. */
    private static void publishMaterial(
            ImportPreparationContext context, Texture texture, String textureIdentity, String materialIdentity)
            throws IOException {
        try (BasicMaterial material = new BasicMaterial()) {
            material.setColorMap(texture);
            material.setAlphaMode(AlphaMode.MASK);
            material.setAlphaCutoff(0.5F);
            ResourceReference colorMap = imported(context, textureIdentity);
            context.artifact(
                    ImportArtifactDescriptor.resource(
                            materialIdentity,
                            Spatial3dDescriptors.basicMaterialResourceType(),
                            List.of(textureIdentity)),
                    output -> Spatial3dResourceWriter.writeBasicMaterial(output, material, colorMap));
        }
    }

    /** Publishes the selected weapon, HUD images, and local firing sound for this slice. */
    private static void publishWeaponPresentation(
            ImportPreparationContext context, String prefix, CombatPresentationAssets assets) throws IOException {
        DoomCombatPresentationRules.Weapon weapon = assets.presentation().weapon();
        List<String> frames = new ArrayList<>();
        frames.add(weapon.readyFrame());
        frames.addAll(weapon.fireFrames());
        for (String frame : frames) {
            publishOverlayImage(
                    context, weaponImageIdentity(prefix, frame), assets.images().get(frame));
        }
        for (String digit : assets.presentation().hud().digits()) {
            publishOverlayImage(
                    context, hudImageIdentity(prefix, digit), assets.images().get(digit));
        }
        String percent = assets.presentation().hud().percent();
        publishOverlayImage(
                context, hudImageIdentity(prefix, percent), assets.images().get(percent));
        publishSound(
                context,
                weaponSoundIdentity(prefix, weapon.fireSound()),
                assets.sounds().get(weapon.fireSound()));
    }

    /** Publishes one exact WAD patch as a generic immutable screen image. */
    private static void publishOverlayImage(ImportPreparationContext context, String identity, RgbaImage image)
            throws IOException {
        String payloadIdentity = identity + ".rgba8";
        context.artifact(
                ImportArtifactDescriptor.payload(payloadIdentity, TEXTURE_MEDIA_TYPE),
                output -> GamePresentationResourceWriter.writeOverlayImagePayload(
                        output, image.width(), image.height(), image.pixels()));
        context.artifact(
                ImportArtifactDescriptor.resource(
                        identity, GamePresentationDescriptors.overlayImageResourceType(), List.of(payloadIdentity)),
                output -> GamePresentationResourceWriter.writeOverlayImageDefinition(
                        output, image.width(), image.height(), imported(context, payloadIdentity)));
    }

    /** Publishes one decoded local sound as a generic signed PCM resource. */
    private static void publishSound(ImportPreparationContext context, String resourceIdentity, PcmAudio audio)
            throws IOException {
        String payloadIdentity = resourceIdentity + ".pcm16le";
        context.artifact(
                ImportArtifactDescriptor.payload(payloadIdentity, PCM_MEDIA_TYPE),
                output -> GamePresentationResourceWriter.writePcmAudioPayload(output, audio));
        context.artifact(
                ImportArtifactDescriptor.resource(
                        resourceIdentity, GamePresentationDescriptors.pcmAudioResourceType(), List.of(payloadIdentity)),
                output -> GamePresentationResourceWriter.writePcmAudioDefinition(
                        output, audio, imported(context, payloadIdentity)));
    }

    /** Publishes every positional sound referenced by one combatant definition. */
    private static void publishCombatantSounds(
            ImportPreparationContext context,
            String prefix,
            String actorId,
            DoomCombatPresentationRules.Combatant combatant,
            Map<String, PcmAudio> sounds)
            throws IOException {
        Set<String> required = new TreeSet<>();
        required.addAll(combatant.sounds().sightSounds());
        required.add(combatant.sounds().attackSound());
        required.add(combatant.sounds().painSound());
        required.addAll(combatant.sounds().deathSounds());
        for (String sound : required) {
            publishSound(context, combatantSoundIdentity(prefix, actorId, sound), sounds.get(sound));
        }
    }

    /** Publishes the provider-sized non-blocking contact volume shared by one pickup definition's instances. */
    private static void publishPickupShape(
            ImportPreparationContext context,
            String prefix,
            DoomActorDefinition actor,
            DoomCombatRules.PickupDefinition pickup)
            throws IOException {
        String identity = pickupShapeIdentity(prefix, actor.id());
        context.artifact(
                ImportArtifactDescriptor.resource(identity, Physics3dDescriptors.sphereResourceType(), List.of()),
                output -> Physics3dResourceWriter.writeSphere(output, DoomUnits.toWorld(pickup.radius())));
    }

    /** Publishes one provider-sized capsule used by every placement of a solid combatant definition. */
    private static void publishCombatantShape(
            ImportPreparationContext context,
            String prefix,
            DoomActorDefinition actor,
            DoomCombatRules.CombatantBounds bounds)
            throws IOException {
        float radius = DoomUnits.toWorld(bounds.radius());
        float segmentLength = DoomUnits.toWorld(bounds.height() - 2.0F * bounds.radius());
        String identity = collisionShapeIdentity(prefix, actor.id());
        context.artifact(
                ImportArtifactDescriptor.resource(identity, Physics3dDescriptors.capsuleResourceType(), List.of()),
                output -> Physics3dResourceWriter.writeCapsule(output, radius, segmentLength));
    }

    /** Publishes one reusable provider actor definition backed by its shared idle-frame material. */
    private static void publishActorDefinition(
            ImportPreparationContext context,
            String prefix,
            DoomActorDefinition actor,
            ActorPresentation presentation,
            Optional<DoomCombatRules.PickupDefinition> pickup,
            Optional<DoomCombatRules.CombatantBounds> combatant,
            RuleReferences ruleReferences)
            throws IOException {
        String definitionIdentity = actorDefinitionIdentity(prefix, actor.id());
        AssetId definitionId = assetId(context.definition().id(), definitionIdentity);
        String rootLocator = definitionIdentity + "/root";
        EntityId rootId = entityId(context.definition().id(), rootLocator);
        ComponentId transformId = componentId(context.definition().id(), rootLocator + "/transform");
        ComponentDefinition transform = new ComponentDefinition(
                transformId,
                Spatial3dDescriptors.transformType().id(),
                Spatial3dDescriptors.transformType().version(),
                Map.of());
        ComponentDefinition billboard = billboard(
                context.definition().id(), prefix, rootLocator + "/billboard", presentation.idleFrame(), true);
        List<ComponentDefinition> components = new ArrayList<>();
        components.add(transform);
        components.add(billboard);
        List<SignalConnection> connections = new ArrayList<>();
        List<String> references = new ArrayList<>();
        String materialIdentity =
                materialIdentity(prefix, presentation.idleFrame().frame());
        references.add(materialIdentity);
        pickup.ifPresent(rule -> addPickupComponents(
                new PickupPublication(context.definition().id(), prefix, actor, rootLocator, rootId),
                rule,
                components,
                connections,
                references));
        combatant.ifPresent(bounds -> addCombatantComponents(
                new CombatantPublication(context.definition().id(), prefix, actor, rootLocator, rootId),
                bounds,
                ruleReferences,
                presentation.combatant(),
                components,
                connections,
                references));
        List<EntityContract.Parameter> parameters = new ArrayList<>();
        parameters.add(new EntityContract.Parameter(
                POSITION_ARGUMENT,
                ProjectValueKind.ARRAY,
                EntityContract.Requirement.REQUIRED,
                PropertyTarget.component(rootId, transformId, Spatial3dDescriptors.positionProperty())));
        if (combatant.isPresent()) {
            ComponentId behaviorId = componentId(context.definition().id(), rootLocator + "/enemy-behavior");
            parameters.add(new EntityContract.Parameter(
                    TARGET_PROVIDER_ARGUMENT,
                    ProjectValueKind.ENTITY_TARGET,
                    EntityContract.Requirement.REQUIRED,
                    PropertyTarget.component(
                            rootId, behaviorId, DoomedCorridorsRuntimeTypes.ENEMY_TARGET_PROVIDER_PROPERTY)));
        }
        EntityContract contract = new EntityContract(parameters, List.of(), List.of(), List.of(), List.of(), List.of());
        LocalEntity root = new LocalEntity(rootId, actor.name(), true, components, List.of());
        EntityDefinition definition = new EntityDefinition(definitionId, actor.name(), contract, connections, root);
        context.artifact(
                ImportArtifactDescriptor.entityDefinition(definitionIdentity, definitionId, references),
                output -> DefinitionWriter.write(output, definition));
    }

    /** Adds generic sensor composition and its game-owned response to one collectable actor definition. */
    private static void addPickupComponents(
            PickupPublication publication,
            DoomCombatRules.PickupDefinition pickup,
            List<ComponentDefinition> components,
            List<SignalConnection> connections,
            List<String> references) {
        String importId = publication.importId();
        String rootLocator = publication.rootLocator();
        ComponentId shapeId = componentId(importId, rootLocator + "/pickup-shape");
        ComponentId sensorId = componentId(importId, rootLocator + "/pickup-sensor");
        ComponentId behaviorId = componentId(importId, rootLocator + "/pickup");
        String shapeIdentity =
                pickupShapeIdentity(publication.prefix(), publication.actor().id());
        components.add(component(
                importId,
                rootLocator + "/pickup-shape",
                Physics3dDescriptors.collisionShapeType(),
                Map.of(
                        Physics3dDescriptors.shapeProperty(), reference(importId, shapeIdentity),
                        Physics3dDescriptors.localPositionProperty(),
                                numbers(0.0F, PICKUP_SENSOR_CENTER_HEIGHT, 0.0F))));
        components.add(component(
                importId,
                rootLocator + "/pickup-sensor",
                Physics3dDescriptors.collisionSensorType(),
                Map.of(Physics3dDescriptors.shapesProperty(), componentTargets(publication.rootId(), shapeId))));
        components.add(new ComponentDefinition(
                behaviorId,
                DoomedCorridorsRuntimeTypes.PICKUP_TYPE.id(),
                DoomedCorridorsRuntimeTypes.PICKUP_TYPE.version(),
                Map.of(
                        DoomedCorridorsRuntimeTypes.PICKUP_RESOURCE_PROPERTY,
                                new ProjectValue.TextValue(
                                        pickup.resource().name().toLowerCase(Locale.ROOT)),
                        DoomedCorridorsRuntimeTypes.PICKUP_AMOUNT_PROPERTY, number(pickup.amount()),
                        DoomedCorridorsRuntimeTypes.PICKUP_LIMIT_PROPERTY, number(pickup.limit()))));
        connections.add(new SignalConnection(
                EndpointTarget.component(publication.rootId(), sensorId, Physics3dDescriptors.overlapEnteredSignal()),
                EndpointTarget.component(
                        publication.rootId(), behaviorId, DoomedCorridorsRuntimeTypes.RECEIVE_OVERLAP_ACTION)));
        references.add(shapeIdentity);
    }

    /** Adds an explicitly shaped movable solid body to one configured combatant definition. */
    private static void addCombatantComponents(
            CombatantPublication publication,
            DoomCombatRules.CombatantBounds bounds,
            RuleReferences ruleReferences,
            Optional<CombatantPresentation> presentation,
            List<ComponentDefinition> components,
            List<SignalConnection> connections,
            List<String> references) {
        String importId = publication.importId();
        String rootLocator = publication.rootLocator();
        ComponentId shapeId = componentId(importId, rootLocator + "/combatant-shape");
        ComponentId bodyId = componentId(importId, rootLocator + "/combatant-body");
        ComponentId stateId = componentId(importId, rootLocator + "/combatant-state");
        String shapeIdentity =
                collisionShapeIdentity(publication.prefix(), publication.actor().id());
        components.add(component(
                importId,
                rootLocator + "/combatant-state",
                DoomedCorridorsRuntimeTypes.COMBATANT_STATE_TYPE,
                Map.of(
                        DoomedCorridorsRuntimeTypes.ACTOR_CATALOG_PROPERTY, ruleReferences.actorCatalog(),
                        DoomedCorridorsRuntimeTypes.COMBAT_RULES_PROPERTY, ruleReferences.combatRules(),
                        DoomedCorridorsRuntimeTypes.ACTOR_ID_PROPERTY,
                                new ProjectValue.TextValue(publication.actor().id()),
                        DoomedCorridorsRuntimeTypes.COMBATANT_BODY_PROPERTY,
                                componentTarget(publication.rootId(), bodyId))));
        components.add(component(
                importId,
                rootLocator + "/combatant-shape",
                Physics3dDescriptors.collisionShapeType(),
                Map.of(
                        Physics3dDescriptors.shapeProperty(), reference(importId, shapeIdentity),
                        Physics3dDescriptors.localPositionProperty(),
                                numbers(0.0F, DoomUnits.toWorld(bounds.height()) / 2.0F, 0.0F))));
        components.add(component(
                importId,
                rootLocator + "/combatant-body",
                Physics3dDescriptors.characterBodyType(),
                Map.of(Physics3dDescriptors.shapesProperty(), componentTargets(publication.rootId(), shapeId))));
        components.add(component(
                importId,
                rootLocator + "/enemy-behavior",
                DoomedCorridorsRuntimeTypes.ENEMY_BEHAVIOR_TYPE,
                Map.of(
                        DoomedCorridorsRuntimeTypes.ACTOR_CATALOG_PROPERTY, ruleReferences.actorCatalog(),
                        DoomedCorridorsRuntimeTypes.COMBAT_RULES_PROPERTY, ruleReferences.combatRules(),
                        DoomedCorridorsRuntimeTypes.ACTOR_ID_PROPERTY,
                                new ProjectValue.TextValue(publication.actor().id()),
                        DoomedCorridorsRuntimeTypes.ENEMY_TARGET_PROVIDER_PROPERTY,
                                new ProjectValue.EntityTargetValue(publication.rootId()),
                        DoomedCorridorsRuntimeTypes.ENEMY_STATE_PROPERTY,
                                componentTarget(publication.rootId(), stateId),
                        DoomedCorridorsRuntimeTypes.COMBATANT_BODY_PROPERTY,
                                componentTarget(publication.rootId(), bodyId))));
        presentation.ifPresent(
                value -> addCombatantPresentation(publication, stateId, value, components, connections, references));
        references.add(shapeIdentity);
    }

    /** Adds hidden animation billboards and explicit behavior/state connections for one combatant. */
    private static void addCombatantPresentation(
            CombatantPublication publication,
            ComponentId stateId,
            CombatantPresentation presentation,
            List<ComponentDefinition> components,
            List<SignalConnection> connections,
            List<String> references) {
        String importId = publication.importId();
        String rootLocator = publication.rootLocator();
        ComponentId enemyBehaviorId = componentId(importId, rootLocator + "/enemy-behavior");
        ComponentId presentationId = componentId(importId, rootLocator + "/combatant-presentation");
        List<ComponentId> walkFrames =
                addAnimationFrames(publication, "walk", presentation.walkFrames(), components, references);
        List<ComponentId> attackFrames =
                addAnimationFrames(publication, "attack", presentation.attackFrames(), components, references);
        List<ComponentId> painFrames =
                addAnimationFrames(publication, "pain", presentation.painFrames(), components, references);
        List<ComponentId> deathFrames =
                addAnimationFrames(publication, "death", presentation.deathFrames(), components, references);
        DoomCombatPresentationRules.Combatant rules = presentation.rules();
        ProjectValue frameMilliseconds =
                number(Math.toIntExact(rules.animations().frameDuration().toMillis()));
        components.add(component(
                importId,
                rootLocator + "/combatant-presentation",
                DoomedCorridorsRuntimeTypes.COMBATANT_PRESENTATION_TYPE,
                Map.ofEntries(
                        Map.entry(
                                DoomedCorridorsRuntimeTypes.COMBATANT_TRANSFORM_PROPERTY,
                                componentTarget(
                                        publication.rootId(), componentId(importId, rootLocator + "/transform"))),
                        Map.entry(
                                DoomedCorridorsRuntimeTypes.COMBATANT_IDLE_FRAME_PROPERTY,
                                componentTarget(
                                        publication.rootId(), componentId(importId, rootLocator + "/billboard"))),
                        Map.entry(
                                DoomedCorridorsRuntimeTypes.COMBATANT_WALK_FRAMES_PROPERTY,
                                componentTargets(publication.rootId(), walkFrames)),
                        Map.entry(
                                DoomedCorridorsRuntimeTypes.COMBATANT_ATTACK_FRAMES_PROPERTY,
                                componentTargets(publication.rootId(), attackFrames)),
                        Map.entry(
                                DoomedCorridorsRuntimeTypes.COMBATANT_PAIN_FRAMES_PROPERTY,
                                componentTargets(publication.rootId(), painFrames)),
                        Map.entry(
                                DoomedCorridorsRuntimeTypes.COMBATANT_DEATH_FRAMES_PROPERTY,
                                componentTargets(publication.rootId(), deathFrames)),
                        Map.entry(
                                DoomedCorridorsRuntimeTypes.COMBATANT_SIGHT_SOUNDS_PROPERTY,
                                resourceReferences(
                                        importId,
                                        rules.sounds().sightSounds().stream()
                                                .map(sound -> combatantSoundIdentity(
                                                        publication.prefix(),
                                                        publication.actor().id(),
                                                        sound))
                                                .toList())),
                        Map.entry(
                                DoomedCorridorsRuntimeTypes.COMBATANT_ATTACK_SOUND_PROPERTY,
                                reference(
                                        importId,
                                        combatantSoundIdentity(
                                                publication.prefix(),
                                                publication.actor().id(),
                                                rules.sounds().attackSound()))),
                        Map.entry(
                                DoomedCorridorsRuntimeTypes.COMBATANT_PAIN_SOUND_PROPERTY,
                                reference(
                                        importId,
                                        combatantSoundIdentity(
                                                publication.prefix(),
                                                publication.actor().id(),
                                                rules.sounds().painSound()))),
                        Map.entry(
                                DoomedCorridorsRuntimeTypes.COMBATANT_DEATH_SOUNDS_PROPERTY,
                                resourceReferences(
                                        importId,
                                        rules.sounds().deathSounds().stream()
                                                .map(sound -> combatantSoundIdentity(
                                                        publication.prefix(),
                                                        publication.actor().id(),
                                                        sound))
                                                .toList())),
                        Map.entry(
                                DoomedCorridorsRuntimeTypes.COMBATANT_SOUND_REFERENCE_DISTANCE_PROPERTY,
                                number(DOOM_SOUND_FULL_VOLUME_DISTANCE)),
                        Map.entry(
                                DoomedCorridorsRuntimeTypes.COMBATANT_SOUND_MAXIMUM_DISTANCE_PROPERTY,
                                number(DOOM_SOUND_MAXIMUM_DISTANCE)),
                        Map.entry(
                                DoomedCorridorsRuntimeTypes.COMBATANT_SOUND_ROLLOFF_FACTOR_PROPERTY,
                                number(DOOM_SOUND_ROLLOFF_FACTOR)),
                        Map.entry(
                                DoomedCorridorsRuntimeTypes.COMBATANT_FRAME_MILLISECONDS_PROPERTY,
                                frameMilliseconds))));
        connections.add(new SignalConnection(
                EndpointTarget.component(
                        publication.rootId(), enemyBehaviorId, DoomedCorridorsRuntimeTypes.ENEMY_ALERTED_SIGNAL),
                EndpointTarget.component(
                        publication.rootId(),
                        presentationId,
                        DoomedCorridorsRuntimeTypes.RECEIVE_COMBATANT_ALERTED_ACTION)));
        connections.add(new SignalConnection(
                EndpointTarget.component(
                        publication.rootId(),
                        enemyBehaviorId,
                        DoomedCorridorsRuntimeTypes.ENEMY_MOVEMENT_STARTED_SIGNAL),
                EndpointTarget.component(
                        publication.rootId(),
                        presentationId,
                        DoomedCorridorsRuntimeTypes.RECEIVE_COMBATANT_MOVEMENT_STARTED_ACTION)));
        connections.add(new SignalConnection(
                EndpointTarget.component(
                        publication.rootId(),
                        enemyBehaviorId,
                        DoomedCorridorsRuntimeTypes.ENEMY_MOVEMENT_STOPPED_SIGNAL),
                EndpointTarget.component(
                        publication.rootId(),
                        presentationId,
                        DoomedCorridorsRuntimeTypes.RECEIVE_COMBATANT_MOVEMENT_STOPPED_ACTION)));
        connections.add(new SignalConnection(
                EndpointTarget.component(
                        publication.rootId(), enemyBehaviorId, DoomedCorridorsRuntimeTypes.ENEMY_ATTACKED_SIGNAL),
                EndpointTarget.component(
                        publication.rootId(),
                        presentationId,
                        DoomedCorridorsRuntimeTypes.RECEIVE_COMBATANT_ATTACKED_ACTION)));
        connections.add(new SignalConnection(
                EndpointTarget.component(publication.rootId(), stateId, DoomedCorridorsRuntimeTypes.HURT_SIGNAL),
                EndpointTarget.component(
                        publication.rootId(),
                        presentationId,
                        DoomedCorridorsRuntimeTypes.RECEIVE_COMBATANT_HURT_ACTION)));
        connections.add(new SignalConnection(
                EndpointTarget.component(publication.rootId(), stateId, DoomedCorridorsRuntimeTypes.DIED_SIGNAL),
                EndpointTarget.component(
                        publication.rootId(),
                        presentationId,
                        DoomedCorridorsRuntimeTypes.RECEIVE_COMBATANT_DIED_ACTION)));
        rules.sounds()
                .sightSounds()
                .forEach(sound -> references.add(combatantSoundIdentity(
                        publication.prefix(), publication.actor().id(), sound)));
        references.add(combatantSoundIdentity(
                publication.prefix(), publication.actor().id(), rules.sounds().attackSound()));
        references.add(combatantSoundIdentity(
                publication.prefix(), publication.actor().id(), rules.sounds().painSound()));
        rules.sounds()
                .deathSounds()
                .forEach(sound -> references.add(combatantSoundIdentity(
                        publication.prefix(), publication.actor().id(), sound)));
    }

    /** Adds one ordered hidden billboard sequence and returns its explicit component targets. */
    private static List<ComponentId> addAnimationFrames(
            CombatantPublication publication,
            String animation,
            List<ImportedSprite> frames,
            List<ComponentDefinition> components,
            List<String> references) {
        List<ComponentId> result = new ArrayList<>(frames.size());
        for (int index = 0; index < frames.size(); index++) {
            ImportedSprite frame = frames.get(index);
            String locator = publication.rootLocator() + "/combatant-presentation/" + animation + '/' + index;
            ComponentDefinition component =
                    billboard(publication.importId(), publication.prefix(), locator, frame, false);
            components.add(component);
            result.add(component.id());
            String material = materialIdentity(publication.prefix(), frame.frame());
            if (!references.contains(material)) {
                references.add(material);
            }
        }
        return List.copyOf(result);
    }

    /** Creates one generic actor billboard using exact imported patch origin and dimensions. */
    private static ComponentDefinition billboard(
            String importId, String prefix, String locator, ImportedSprite sprite, boolean visible) {
        return component(
                importId,
                locator,
                Spatial3dDescriptors.billboardRendererType(),
                Map.of(
                        Spatial3dDescriptors.materialProperty(),
                                reference(importId, materialIdentity(prefix, sprite.frame())),
                        Spatial3dDescriptors.sizeProperty(),
                                numbers(
                                        DoomUnits.toWorld(sprite.image().width()),
                                        DoomUnits.toWorld(sprite.image().height())),
                        Spatial3dDescriptors.anchorProperty(),
                                numbers(
                                        sprite.leftOffset()
                                                / (float) sprite.image().width(),
                                        (sprite.image().height() - sprite.topOffset())
                                                / (float) sprite.image().height()),
                        Spatial3dDescriptors.alignmentProperty(), new ProjectValue.TextValue("cylindrical"),
                        Spatial3dDescriptors.visibleProperty(), new ProjectValue.BooleanValue(visible)));
    }

    /** Converts validated recipe source-asset identities into portable authored references. */
    private static RuleReferences ruleReferences(ImportPreparationContext context) {
        return new RuleReferences(
                sourceAssetReference(context, ACTOR_CATALOG_SETTING),
                sourceAssetReference(context, COMBAT_RULES_SETTING));
    }

    /** Returns one source-asset reference from a setting already validated during preparation. */
    private static ProjectValue.ReferenceValue sourceAssetReference(
            ImportPreparationContext context, String settingId) {
        ProjectValue value = context.definition().settings().get(settingId);
        if (!(value instanceof ProjectValue.TextValue(String assetId))) {
            throw new IllegalStateException("validated import setting is not text: " + settingId);
        }
        return new ProjectValue.ReferenceValue(ResourceReference.asset(assetId));
    }

    /** Publishes one hierarchy whose children place shared actor definitions at resolved WAD thing positions. */
    private static void publishActorPlacements(
            ImportPreparationContext context,
            String prefix,
            List<DoomActor> actors,
            Map<String, DoomActorDefinition> definitions,
            DoomCombatRules rules)
            throws IOException {
        String rootLocator = prefix + "/actors/root";
        EntityId rootId = entityId(context.definition().id(), rootLocator);
        ComponentId targetId = componentId(context.definition().id(), rootLocator + "/enemy-target");
        List<EntityEntry> placements = new ArrayList<>();
        for (DoomActor actor : actors) {
            DoomActorDefinition definition = definitions.get(actor.definition().id());
            if (definition == null) {
                continue;
            }
            String definitionIdentity = actorDefinitionIdentity(prefix, definition.id());
            Map<PropertyId, ProjectValue> arguments = new LinkedHashMap<>();
            arguments.put(POSITION_ARGUMENT, numbers(actor.x(), actor.floorHeight(), actor.z()));
            if (rules.hasCombatant(definition.id())) {
                arguments.put(TARGET_PROVIDER_ARGUMENT, new ProjectValue.EntityTargetValue(rootId));
            }
            placements.add(new EntityPlacement(
                    entityId(context.definition().id(), prefix + "/actors/placements/" + formatted(actor.thingIndex())),
                    definition.name() + " " + actor.thingIndex(),
                    true,
                    AssetRef.to(assetId(context.definition().id(), definitionIdentity)),
                    arguments));
        }
        String definitionIdentity = actorMapDefinitionIdentity(prefix);
        List<ComponentDefinition> components = List.of(
                component(
                        context.definition().id(),
                        rootLocator + "/transform",
                        Spatial3dDescriptors.transformType(),
                        Map.of()),
                component(
                        context.definition().id(),
                        rootLocator + "/enemy-target",
                        DoomedCorridorsRuntimeTypes.ENEMY_TARGET_TYPE,
                        Map.of(
                                DoomedCorridorsRuntimeTypes.PLAYER_TARGET_PROPERTY,
                                new ProjectValue.EntityTargetValue(rootId))));
        LocalEntity root = new LocalEntity(rootId, "Actors", true, components, placements);
        EntityContract contract = new EntityContract(
                List.of(new EntityContract.Parameter(
                        PLAYER_ARGUMENT,
                        ProjectValueKind.ENTITY_TARGET,
                        EntityContract.Requirement.REQUIRED,
                        PropertyTarget.component(
                                rootId, targetId, DoomedCorridorsRuntimeTypes.PLAYER_TARGET_PROPERTY))),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        EntityDefinition definition = new EntityDefinition(
                assetId(context.definition().id(), definitionIdentity), prefix + " actors", contract, List.of(), root);
        List<String> references = definitions.values().stream()
                .map(actor -> actorDefinitionIdentity(prefix, actor.id()))
                .toList();
        context.artifact(
                ImportArtifactDescriptor.entityDefinition(definitionIdentity, definition.id(), references),
                output -> DefinitionWriter.write(output, definition));
    }

    /** Returns unique visible definitions with an imported frame in catalog encounter order. */
    private static Map<String, DoomActorDefinition> selectedDefinitions(
            List<DoomActor> actors, Map<String, ImportedSprite> sprites) {
        Map<String, DoomActorDefinition> result = new LinkedHashMap<>();
        for (DoomActor actor : actors) {
            DoomActorDefinition definition = actor.definition();
            if (sprites.containsKey(definition.spriteFrame().orElseThrow())) {
                result.putIfAbsent(definition.id(), definition);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /** Collects spawn and configured combatant frame identifiers in deterministic order. */
    private static Set<String> requiredFrames(List<DoomActor> actors, DoomCombatPresentationRules presentation) {
        Set<String> frames = new TreeSet<>();
        for (DoomActor actor : actors) {
            frames.add(actor.definition().spriteFrame().orElseThrow());
            DoomCombatPresentationRules.Combatant combatant =
                    presentation.combatant(actor.definition().id());
            if (combatant != null) {
                frames.addAll(combatant.animations().walkFrames());
                frames.addAll(combatant.animations().attackFrames());
                frames.addAll(combatant.animations().painFrames());
                frames.addAll(combatant.animations().deathFrames());
            }
        }
        return frames;
    }

    /** Resolves one actor's idle and optional combatant reaction frames from imported sprite data. */
    private static ActorPresentation actorPresentation(
            DoomActorDefinition actor, Map<String, ImportedSprite> sprites, DoomCombatPresentationRules presentation) {
        ImportedSprite idle = sprites.get(actor.spriteFrame().orElseThrow());
        DoomCombatPresentationRules.Combatant combatant = presentation.combatant(actor.id());
        if (combatant == null) {
            return new ActorPresentation(idle, Optional.empty());
        }
        return new ActorPresentation(
                idle,
                Optional.of(new CombatantPresentation(
                        combatant,
                        sprites(combatant.animations().walkFrames(), sprites),
                        sprites(combatant.animations().attackFrames(), sprites),
                        sprites(combatant.animations().painFrames(), sprites),
                        sprites(combatant.animations().deathFrames(), sprites))));
    }

    /** Resolves one complete ordered frame sequence after import diagnostics validated its lumps. */
    private static List<ImportedSprite> sprites(List<String> frames, Map<String, ImportedSprite> imported) {
        return frames.stream()
                .map(frame -> Objects.requireNonNull(imported.get(frame), "missing imported sprite: " + frame))
                .toList();
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
        WadLump exact = namespace.get(frame);
        if (exact != null) {
            return exact;
        }
        WadLump nonDirectional = namespace.get(frame + '0');
        return nonDirectional == null ? namespace.get(frame + '1') : nonDirectional;
    }

    /** Creates one sprite texture with classic nearest filtering and edge clamping. */
    private static Texture createTexture(RgbaImage image) {
        Texture texture = Texture.baseColor(image.width(), image.height(), image.pixels());
        texture.setCoordinateOrigin(TextureCoordinateOrigin.BOTTOM_LEFT);
        texture.setHorizontalWrap(TextureWrap.CLAMP_TO_EDGE);
        texture.setVerticalWrap(TextureWrap.CLAMP_TO_EDGE);
        texture.setMinificationFilter(TextureFilter.NEAREST_MIPMAP_NEAREST);
        texture.setMagnificationFilter(TextureFilter.NEAREST);
        return texture;
    }

    /** Creates one typed component with deterministic source-derived identity. */
    private static ComponentDefinition component(
            String importId, String locator, ComponentType type, Map<PropertyId, ProjectValue> properties) {
        return new ComponentDefinition(componentId(importId, locator), type.id(), type.version(), properties);
    }

    /** Creates one imported resource property. */
    private static ProjectValue.ReferenceValue reference(String importId, String identity) {
        return new ProjectValue.ReferenceValue(ResourceReference.imported(importId + '/' + identity));
    }

    /** Creates one portable component-target array for explicit collision-shape membership. */
    private static ProjectValue.ArrayValue componentTargets(EntityId entity, ComponentId... components) {
        List<ProjectValue> targets = new ArrayList<>(components.length);
        for (ComponentId component : components) {
            targets.add(new ProjectValue.ComponentTargetValue(new ComponentTarget(entity, component)));
        }
        return new ProjectValue.ArrayValue(targets);
    }

    /** Creates one portable component-target array from an ordered component list. */
    private static ProjectValue.ArrayValue componentTargets(EntityId entity, List<ComponentId> components) {
        return new ProjectValue.ArrayValue(components.stream()
                .<ProjectValue>map(
                        component -> new ProjectValue.ComponentTargetValue(new ComponentTarget(entity, component)))
                .toList());
    }

    /** Creates one portable component target. */
    private static ProjectValue.ComponentTargetValue componentTarget(EntityId entity, ComponentId component) {
        return new ProjectValue.ComponentTargetValue(new ComponentTarget(entity, component));
    }

    /** Creates one portable imported-resource reference array. */
    private static ProjectValue.ArrayValue resourceReferences(String importId, List<String> identities) {
        return new ProjectValue.ArrayValue(identities.stream()
                .<ProjectValue>map(identity -> reference(importId, identity))
                .toList());
    }

    /** Creates one imported resource reference for the active recipe. */
    private static ResourceReference imported(ImportPreparationContext context, String identity) {
        return ResourceReference.imported(context.definition().id() + '/' + identity);
    }

    /** Creates one portable numeric array. */
    private static ProjectValue.ArrayValue numbers(float... values) {
        List<ProjectValue> result = new ArrayList<>(values.length);
        for (float value : values) {
            result.add(number(value));
        }
        return new ProjectValue.ArrayValue(result);
    }

    /** Creates one portable exact integer number. */
    private static ProjectValue.NumberValue number(int value) {
        return new ProjectValue.NumberValue(BigDecimal.valueOf(value));
    }

    /** Creates one portable finite float number without a binary floating-point serialization artifact. */
    private static ProjectValue.NumberValue number(float value) {
        return new ProjectValue.NumberValue(new BigDecimal(Float.toString(value)));
    }

    /** Returns the stable source-item prefix for one map. */
    private static String mapIdentity(String mapName) {
        return "maps/" + mapName;
    }

    /** Returns one reusable actor-definition output identity. */
    private static String actorDefinitionIdentity(String prefix, String actorId) {
        return prefix + "/actors/definitions/" + actorId;
    }

    /** Returns the aggregate actor-placement definition identity. */
    private static String actorMapDefinitionIdentity(String prefix) {
        return prefix + "/actors/definition";
    }

    /** Returns one shared sprite-texture output identity. */
    private static String textureIdentity(String prefix, String frame) {
        return prefix + "/actors/resources/textures/" + frame.toLowerCase(Locale.ROOT);
    }

    /** Returns one shared sprite-material output identity. */
    private static String materialIdentity(String prefix, String frame) {
        return prefix + "/actors/resources/materials/" + frame.toLowerCase(Locale.ROOT);
    }

    /** Returns one selected weapon overlay-image identity. */
    private static String weaponImageIdentity(String prefix, String frame) {
        return prefix + "/presentation/weapons/images/" + frame.toLowerCase(Locale.ROOT);
    }

    /** Returns one selected HUD overlay-image identity. */
    private static String hudImageIdentity(String prefix, String lump) {
        return prefix + "/presentation/hud/images/" + lump.toLowerCase(Locale.ROOT);
    }

    /** Returns one selected weapon local-sound identity. */
    private static String weaponSoundIdentity(String prefix, String sound) {
        return prefix + "/presentation/weapons/audio/" + sound.toLowerCase(Locale.ROOT);
    }

    /** Returns one actor-specific positional sound identity. */
    private static String combatantSoundIdentity(String prefix, String actorId, String sound) {
        return prefix + "/presentation/combatants/" + actorId + "/audio/" + sound.toLowerCase(Locale.ROOT);
    }

    /** Returns the provider-sized collision-resource identity for one actor definition. */
    private static String collisionShapeIdentity(String prefix, String actorId) {
        return prefix + "/actors/resources/collision/" + actorId;
    }

    /** Returns the provider-sized collision-resource identity for one pickup definition. */
    private static String pickupShapeIdentity(String prefix, String actorId) {
        return collisionShapeIdentity(prefix, actorId);
    }

    /** Formats source thing indices so lexical and source order agree. */
    private static String formatted(int index) {
        return String.format(Locale.ROOT, "%05d", index);
    }

    /** Creates one deterministic source-derived component identity. */
    private static ComponentId componentId(String importId, String locator) {
        return new ComponentId(stableId(importId, locator));
    }

    /** Creates one deterministic source-derived entity identity. */
    private static EntityId entityId(String importId, String locator) {
        return new EntityId(stableId(importId, locator));
    }

    /** Creates one deterministic source-derived definition identity. */
    private static AssetId assetId(String importId, String locator) {
        return new AssetId(stableId(importId, locator));
    }

    /** Uses standard name UUIDs so reimport preserves identity for unchanged source locators. */
    private static UUID stableId(String importId, String locator) {
        return UUID.nameUUIDFromBytes((importId + ':' + locator).getBytes(StandardCharsets.UTF_8));
    }

    /** Forwards one WAD diagnostic without replacing its feature-owned identity. */
    private static void report(ImportInspectionContext context, WadDiagnostic diagnostic) {
        if (diagnostic.severity() == WadDiagnostic.Severity.ERROR) {
            context.error(diagnostic.code(), diagnostic.location(), diagnostic.details());
        } else {
            context.warning(diagnostic.code(), diagnostic.location(), diagnostic.details());
        }
    }

    /** Forwards one decoded-map diagnostic without replacing its feature-owned identity. */
    private static void report(ImportInspectionContext context, DoomDiagnostic diagnostic) {
        if (diagnostic.severity() == DoomDiagnostic.Severity.ERROR) {
            context.error(diagnostic.code(), diagnostic.location(), diagnostic.details());
        } else {
            context.warning(diagnostic.code(), diagnostic.location(), diagnostic.details());
        }
    }

    /** Forwards one provider diagnostic through a stable actor-import code. */
    private static void report(ImportInspectionContext context, DoomActorDiagnostic diagnostic) {
        Map<String, String> details = Map.of(
                "sourceCode", diagnostic.code(),
                "message", diagnostic.message());
        if (diagnostic.severity() == DoomActorDiagnostic.Severity.ERROR) {
            context.error(ActorImportDiagnosticCode.ACTOR_INVALID, diagnostic.location(), details);
        } else {
            context.warning(ActorImportDiagnosticCode.ACTOR_INVALID, diagnostic.location(), details);
        }
    }

    /** Stable inputs identifying where pickup components are published in one actor definition. */
    private record PickupPublication(
            String importId, String prefix, DoomActorDefinition actor, String rootLocator, EntityId rootId) {}

    /** Stable inputs identifying where solid combatant components are published in one actor definition. */
    private record CombatantPublication(
            String importId, String prefix, DoomActorDefinition actor, String rootLocator, EntityId rootId) {}

    /** Portable source references embedded in every provider-defined combatant state component. */
    private record RuleReferences(ProjectValue.ReferenceValue actorCatalog, ProjectValue.ReferenceValue combatRules) {}

    /** One decoded actor frame retained only while artifacts are being published. */
    private record ImportedSprite(String frame, RgbaImage image, int leftOffset, int topOffset) {}

    /** Imported visual inputs used to publish one actor definition. */
    private record ActorPresentation(ImportedSprite idleFrame, Optional<CombatantPresentation> combatant) {
        private ActorPresentation {
            Objects.requireNonNull(idleFrame, "idleFrame");
            Objects.requireNonNull(combatant, "combatant");
        }
    }

    /** Provider rules and resolved billboard frames for one combatant's reactions. */
    private record CombatantPresentation(
            DoomCombatPresentationRules.Combatant rules,
            List<ImportedSprite> walkFrames,
            List<ImportedSprite> attackFrames,
            List<ImportedSprite> painFrames,
            List<ImportedSprite> deathFrames) {
        private CombatantPresentation {
            Objects.requireNonNull(rules, "rules");
            walkFrames = List.copyOf(walkFrames);
            attackFrames = List.copyOf(attackFrames);
            painFrames = List.copyOf(painFrames);
            deathFrames = List.copyOf(deathFrames);
        }
    }

    /** Build-time assets required by descriptor-selected combat presentation components. */
    private record CombatPresentationAssets(
            DoomCombatPresentationRules presentation, Map<String, RgbaImage> images, Map<String, PcmAudio> sounds) {
        private CombatPresentationAssets {
            images = Map.copyOf(images);
            sounds = Map.copyOf(sounds);
        }
    }

    /** Stable game-owned actor-import diagnostics. */
    private enum ActorImportDiagnosticCode implements DiagnosticCode {
        CATALOG_SETTING_INVALID(
                "doomed-corridors.actor-import.catalog-setting", "The actor import requires an actor catalog asset"),
        COMBAT_RULES_SETTING_INVALID(
                "doomed-corridors.actor-import.combat-rules-setting", "The actor import requires a combat-rules asset"),
        COMBAT_RULES_INVALID("doomed-corridors.actor-import.combat-rules", "The actor import combat rules are invalid"),
        COMBAT_PRESENTATION_SETTING_INVALID(
                "doomed-corridors.actor-import.combat-presentation-setting",
                "The actor import requires a combat-presentation asset"),
        COMBAT_PRESENTATION_INVALID(
                "doomed-corridors.actor-import.combat-presentation", "The actor import combat presentation is invalid"),
        ACTOR_INVALID("doomed-corridors.actor-import.actor", "An actor catalog or placement is invalid"),
        PALETTE_MISSING("doomed-corridors.actor-import.palette-missing", "The WAD has no PLAYPAL palette"),
        PALETTE_INVALID("doomed-corridors.actor-import.palette-invalid", "The WAD PLAYPAL palette is incomplete"),
        SPRITE_MISSING("doomed-corridors.actor-import.sprite-missing", "A selected actor sprite frame is missing"),
        SPRITE_INVALID("doomed-corridors.actor-import.sprite-invalid", "An actor sprite could not be decoded");

        private final String code;
        private final String message;

        ActorImportDiagnosticCode(String code, String message) {
            this.code = code;
            this.message = message;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public String defaultMessage() {
            return message;
        }
    }
}
