/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.actor.DoomActorCatalog;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalogLoadResult;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalogLoader;
import io.github.glynch.doomedcorridors.combat.DoomCombatRules;
import io.github.glynch.doomedcorridors.combat.DoomCombatRulesLoadResult;
import io.github.glynch.doomedcorridors.combat.DoomCombatRulesLoader;
import io.github.glynch.doomedcorridors.internal.DoomedCorridorsDescriptors;
import io.github.glynch.doomedcorridors.internal.RuntimeProperties;
import io.github.glynch.jscene3d.game.application.ApplicationControl;
import io.github.glynch.jscene3d.game.input.InputWorldModule;
import io.github.glynch.jscene3d.game.presentation.OverlayImageResource;
import io.github.glynch.jscene3d.game.presentation.PcmAudioResource;
import io.github.glynch.jscene3d.game.presentation.PositionalSoundAttenuation;
import io.github.glynch.jscene3d.game.presentation.PresentationWorldModule;
import io.github.glynch.jscene3d.project.component.PropertyId;
import io.github.glynch.jscene3d.project.manifest.GameProject;
import io.github.glynch.jscene3d.project.physics3d.Physics3dWorldModule;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.HostedProject;
import io.github.glynch.jscene3d.project.runtime.extension.ApplicationRuntimeExtension;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentFactory;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentFactoryContext;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentFactoryRegistry;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentPreparationContext;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentProperties;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Manifest-selected Doomed Corridors application extension. */
public final class DoomedCorridorsRuntimeExtension implements ApplicationRuntimeExtension {
    static final String ID = DoomedCorridorsDescriptors.EXTENSION_ID;

    /** Creates the stateless provider used by standard Java service discovery. */
    public DoomedCorridorsRuntimeExtension() {
        // Public construction is required by ServiceLoader on the class path and module path.
    }

    /** Returns the identity shared with the project and safe extension descriptor. */
    @Override
    public String id() {
        return ID;
    }

    /** Registers the executable player, combatant, pickup, and weapon factories. */
    @Override
    public void register(ComponentFactoryRegistry registry) {
        ComponentFactoryRegistry validRegistry = Objects.requireNonNull(registry, "registry");
        validRegistry.register(
                DoomedCorridorsDescriptors.PLAYER_STATE_TYPE,
                context -> new DoomPlayerState(
                        context.properties().resourceReference(DoomedCorridorsDescriptors.ACTOR_CATALOG_PROPERTY),
                        context.properties().resourceReference(DoomedCorridorsDescriptors.COMBAT_RULES_PROPERTY)));
        validRegistry.register(
                DoomedCorridorsDescriptors.PICKUP_TYPE,
                context -> new DoomPickup(
                        context.owner(),
                        context.world(),
                        context.properties().text(DoomedCorridorsDescriptors.PICKUP_RESOURCE_PROPERTY),
                        RuntimeProperties.positiveInteger(
                                context.properties(), DoomedCorridorsDescriptors.PICKUP_AMOUNT_PROPERTY),
                        RuntimeProperties.positiveInteger(
                                context.properties(), DoomedCorridorsDescriptors.PICKUP_LIMIT_PROPERTY),
                        RuntimeProperties.nonNegativeInteger(
                                context.properties(), DoomedCorridorsDescriptors.PICKUP_ARMOR_PROTECTION_PROPERTY),
                        context.properties().text(DoomedCorridorsDescriptors.PICKUP_GRANTED_WEAPON_PROPERTY)));
        validRegistry.register(
                DoomedCorridorsDescriptors.COMBATANT_STATE_TYPE,
                context -> new DoomCombatantState(
                        context.owner(),
                        context.properties().resourceReference(DoomedCorridorsDescriptors.ACTOR_CATALOG_PROPERTY),
                        context.properties().resourceReference(DoomedCorridorsDescriptors.COMBAT_RULES_PROPERTY),
                        context.properties().text(DoomedCorridorsDescriptors.ACTOR_ID_PROPERTY)));
        validRegistry.register(DoomedCorridorsDescriptors.ENEMY_TARGET_TYPE, context -> new DoomEnemyTargetProvider());
        validRegistry.register(
                DoomedCorridorsDescriptors.ENEMY_BEHAVIOR_TYPE,
                context -> new DoomEnemyBehavior(
                        context.owner(),
                        context.world().requireModule(Physics3dWorldModule.class),
                        context.properties().resourceReference(DoomedCorridorsDescriptors.ACTOR_CATALOG_PROPERTY),
                        context.properties().resourceReference(DoomedCorridorsDescriptors.COMBAT_RULES_PROPERTY),
                        context.properties().text(DoomedCorridorsDescriptors.ACTOR_ID_PROPERTY)));
        validRegistry.register(
                DoomedCorridorsDescriptors.COMBATANT_PRESENTATION_TYPE, new CombatantPresentationFactory());
        validRegistry.register(
                DoomedCorridorsDescriptors.HITSCAN_WEAPON_TYPE,
                context -> new DoomHitscanWeapon(
                        context.owner(),
                        context.world().requireModule(InputWorldModule.class),
                        context.world().requireModule(Physics3dWorldModule.class),
                        context.properties().resourceReference(DoomedCorridorsDescriptors.ACTOR_CATALOG_PROPERTY),
                        context.properties().resourceReference(DoomedCorridorsDescriptors.COMBAT_RULES_PROPERTY),
                        context.properties().text(DoomedCorridorsDescriptors.FIRE_ACTION_PROPERTY)));
        validRegistry.register(
                DoomedCorridorsDescriptors.WEAPON_SELECTOR_TYPE,
                context -> new DoomWeaponSelector(
                        context.world().requireModule(InputWorldModule.class),
                        RuntimeProperties.textValues(
                                context.properties(), DoomedCorridorsDescriptors.SELECTABLE_WEAPONS_PROPERTY),
                        RuntimeProperties.textValues(
                                context.properties(), DoomedCorridorsDescriptors.WEAPON_SELECTION_ACTIONS_PROPERTY)));
        validRegistry.register(DoomedCorridorsDescriptors.WEAPON_PRESENTATION_TYPE, new WeaponPresentationFactory());
        validRegistry.register(DoomedCorridorsDescriptors.PLAYER_PRESENTATION_TYPE, new PlayerPresentationFactory());
        validRegistry.register(
                DoomedCorridorsDescriptors.PLAYER_LIFECYCLE_TYPE,
                context -> new DoomPlayerDeathController(
                        context.world(),
                        context.world().requireModule(InputWorldModule.class),
                        context.world().requireModule(ApplicationControl.class),
                        Duration.ofMillis(RuntimeProperties.positiveInteger(
                                context.properties(),
                                DoomedCorridorsDescriptors.PLAYER_GAME_OVER_DELAY_MILLISECONDS_PROPERTY)),
                        context.properties().text(DoomedCorridorsDescriptors.PLAYER_RETURN_TO_MENU_ACTION_PROPERTY)));
        validRegistry.register(DoomedCorridorsDescriptors.PLAYER_HUD_TYPE, context -> new DoomPlayerHud());
        validRegistry.register(
                DoomedCorridorsDescriptors.DOOR_INTERACTOR_TYPE,
                context -> new DoomDoorInteractor(
                        context.world().requireModule(InputWorldModule.class),
                        context.world().requireModule(Physics3dWorldModule.class),
                        context.properties().text(DoomedCorridorsDescriptors.INTERACTION_ACTION_PROPERTY),
                        RuntimeProperties.positiveFloat(
                                context.properties(),
                                DoomedCorridorsDescriptors.INTERACTION_MAXIMUM_DISTANCE_PROPERTY)));
        validRegistry.register(DoomedCorridorsDescriptors.DOOR_PRESENTATION_TYPE, new DoorPresentationFactory());
        validRegistry.register(DoomedCorridorsDescriptors.MAIN_MENU_TYPE, new MainMenuFactory());
        validRegistry.register(DoomedCorridorsDescriptors.GAME_OVER_MENU_TYPE, context -> {
            ComponentProperties properties = context.properties();
            return new DoomGameOverMenu(
                    context.world(),
                    context.world().requireModule(InputWorldModule.class),
                    context.world().requireModule(ApplicationControl.class),
                    DoomGameOverMenu.Actions.of(
                            properties.text(DoomedCorridorsDescriptors.GAME_OVER_PREVIOUS_ACTION_PROPERTY),
                            properties.text(DoomedCorridorsDescriptors.GAME_OVER_NEXT_ACTION_PROPERTY),
                            properties.text(DoomedCorridorsDescriptors.GAME_OVER_CONFIRM_ACTION_PROPERTY)),
                    new DoomGameOverMenu.Layout(
                            RuntimeProperties.positiveFloat(
                                    properties, DoomedCorridorsDescriptors.GAME_OVER_REFERENCE_WIDTH_PROPERTY),
                            RuntimeProperties.positiveFloat(
                                    properties, DoomedCorridorsDescriptors.GAME_OVER_REFERENCE_HEIGHT_PROPERTY),
                            RuntimeProperties.nonNegativeFloat(
                                    properties, DoomedCorridorsDescriptors.GAME_OVER_ITEM_CENTER_X_PROPERTY),
                            RuntimeProperties.nonNegativeFloat(
                                    properties, DoomedCorridorsDescriptors.GAME_OVER_ITEM_START_Y_PROPERTY),
                            RuntimeProperties.positiveFloat(
                                    properties, DoomedCorridorsDescriptors.GAME_OVER_ITEM_SPACING_PROPERTY),
                            RuntimeProperties.positiveFloat(
                                    properties, DoomedCorridorsDescriptors.GAME_OVER_ITEM_HIT_WIDTH_PROPERTY),
                            RuntimeProperties.positiveFloat(
                                    properties, DoomedCorridorsDescriptors.GAME_OVER_ITEM_HIT_HEIGHT_PROPERTY)));
        });
    }

    /** Loads authoritative provider rules and initializes every descriptor-declared consumer before activation. */
    @Override
    public void prepare(HostedProject project) {
        HostedProject validProject = Objects.requireNonNull(project, "project");
        List<DoomRuleConsumer> consumers = new ArrayList<>();
        validProject.world().roots().forEach(root -> collectRuleConsumers(root, consumers));
        Optional<Entity> player = validProject.world().roots().stream()
                .map(DoomedCorridorsRuntimeExtension::findPlayer)
                .flatMap(Optional::stream)
                .findFirst();
        if (consumers.isEmpty()) {
            return;
        }
        if (player.isEmpty()) {
            throw new IllegalStateException("the startup world has no Doom player-state component");
        }
        Entity playerEntity = player.orElseThrow();
        DoomPlayerState playerState = playerEntity
                .capability(DoomedCorridorsDescriptors.PLAYER_RESOURCES_CAPABILITY, DoomPlayerState.class)
                .orElseThrow();
        DoomPlaytestParameters.apply(validProject, playerEntity, playerState);
        Map<RuleSources, DoomCombatRules> loadedRules = new LinkedHashMap<>();
        for (DoomRuleConsumer consumer : consumers) {
            RuleSources sources = new RuleSources(consumer.actorCatalog(), consumer.combatRules());
            DoomCombatRules rules = loadedRules.computeIfAbsent(
                    sources, key -> loadRules(validProject.project(), key.actorCatalog(), key.combatRules()));
            consumer.configure(rules);
        }
    }

    /** Finds the entity providing player resources in one owned subtree. */
    private static Optional<Entity> findPlayer(Entity entity) {
        if (entity.capability(DoomedCorridorsDescriptors.PLAYER_RESOURCES_CAPABILITY, DoomPlayerState.class)
                .isPresent()) {
            return Optional.of(entity);
        }
        return entity.children().stream()
                .map(DoomedCorridorsRuntimeExtension::findPlayer)
                .flatMap(Optional::stream)
                .findFirst();
    }

    /** Resolves authored menu images before constructing interactive overlay behavior. */
    private static final class MainMenuFactory implements ComponentFactory<DoomMainMenu> {
        private static final List<PropertyId> IMAGE_PROPERTIES = List.of(
                DoomedCorridorsDescriptors.MENU_BACKGROUND_PROPERTY,
                DoomedCorridorsDescriptors.MENU_TITLE_PROPERTY,
                DoomedCorridorsDescriptors.MENU_RESUME_PROPERTY,
                DoomedCorridorsDescriptors.MENU_NEW_GAME_PROPERTY,
                DoomedCorridorsDescriptors.MENU_QUIT_PROPERTY,
                DoomedCorridorsDescriptors.MENU_CURSOR_FIRST_PROPERTY,
                DoomedCorridorsDescriptors.MENU_CURSOR_SECOND_PROPERTY);

        @Override
        public void prepare(ComponentPreparationContext context) {
            IMAGE_PROPERTIES.forEach(property -> context.resolveResource(
                    context.properties().resourceReference(property), OverlayImageResource.class));
        }

        @Override
        public DoomMainMenu create(ComponentFactoryContext context) {
            ComponentProperties properties = context.properties();
            return new DoomMainMenu(
                    context.world().requireModule(InputWorldModule.class),
                    context.world().requireModule(ApplicationControl.class),
                    context.world().requireModule(PresentationWorldModule.class),
                    new DoomMainMenu.Images(
                            image(context, properties, DoomedCorridorsDescriptors.MENU_BACKGROUND_PROPERTY),
                            image(context, properties, DoomedCorridorsDescriptors.MENU_TITLE_PROPERTY),
                            image(context, properties, DoomedCorridorsDescriptors.MENU_RESUME_PROPERTY),
                            image(context, properties, DoomedCorridorsDescriptors.MENU_NEW_GAME_PROPERTY),
                            image(context, properties, DoomedCorridorsDescriptors.MENU_QUIT_PROPERTY),
                            image(context, properties, DoomedCorridorsDescriptors.MENU_CURSOR_FIRST_PROPERTY),
                            image(context, properties, DoomedCorridorsDescriptors.MENU_CURSOR_SECOND_PROPERTY)),
                    new DoomMainMenu.Actions(
                            properties.text(DoomedCorridorsDescriptors.MENU_PREVIOUS_ACTION_PROPERTY),
                            properties.text(DoomedCorridorsDescriptors.MENU_NEXT_ACTION_PROPERTY),
                            properties.text(DoomedCorridorsDescriptors.MENU_CONFIRM_ACTION_PROPERTY),
                            properties.text(DoomedCorridorsDescriptors.MENU_BACK_ACTION_PROPERTY)));
        }

        /** Resolves one prepared authored menu image. */
        private static OverlayImageResource image(
                ComponentFactoryContext context, ComponentProperties properties, PropertyId property) {
            return context.resolveResource(properties.resourceReference(property), OverlayImageResource.class);
        }
    }

    /** Collects only descriptor-declared rule consumers throughout one owned entity subtree. */
    private static void collectRuleConsumers(Entity entity, List<DoomRuleConsumer> destination) {
        entity.capability(DoomedCorridorsDescriptors.PLAYER_RESOURCES_CAPABILITY, DoomPlayerState.class)
                .ifPresent(destination::add);
        entity.capability(DoomedCorridorsDescriptors.HITSCAN_TARGET_CAPABILITY, DoomCombatantState.class)
                .ifPresent(destination::add);
        entity.capability(DoomedCorridorsDescriptors.ENEMY_BEHAVIOR_CAPABILITY, DoomEnemyBehavior.class)
                .ifPresent(destination::add);
        entity.capability(DoomedCorridorsDescriptors.WEAPON_CAPABILITY, DoomHitscanWeapon.class)
                .ifPresent(destination::add);
        entity.children().forEach(child -> collectRuleConsumers(child, destination));
    }

    /** Loads combat rules against the explicitly referenced companion actor catalog. */
    private static DoomCombatRules loadRules(
            GameProject project, ResourceReference actorCatalogReference, ResourceReference combatRulesReference) {
        Path actorCatalog = source(
                project, actorCatalogReference, DoomedCorridorsDescriptors.ACTOR_CATALOG_ASSET_TYPE, "actor catalog");
        DoomActorCatalogLoadResult loadedActors = new DoomActorCatalogLoader().load(actorCatalog);
        DoomActorCatalog actors = loadedActors
                .catalog()
                .orElseThrow(
                        () -> new IllegalStateException("actor catalog loading failed: " + loadedActors.diagnostics()));
        Path combatRules = source(
                project, combatRulesReference, DoomedCorridorsDescriptors.COMBAT_RULES_ASSET_TYPE, "combat rules");
        DoomCombatRulesLoadResult loadedRules = new DoomCombatRulesLoader().load(combatRules, actors);
        return loadedRules
                .rules()
                .orElseThrow(
                        () -> new IllegalStateException("combat rules loading failed: " + loadedRules.diagnostics()));
    }

    /** Resolves one explicitly referenced manifest source asset with its required provider type. */
    private static Path source(GameProject project, ResourceReference reference, String type, String name) {
        if (reference.kind() != ResourceReference.Kind.ASSET) {
            throw new IllegalArgumentException(name + " must reference a source asset");
        }
        return project.assets().stream()
                .filter(asset ->
                        asset.id().equals(reference.locator()) && asset.type().equals(type))
                .map(GameProject.AssetSource::path)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        name + " does not name a declared " + type + " asset: " + reference.locator()));
    }

    /** Explicit rule-source pair used to share one immutable load across matching runtime components. */
    private record RuleSources(ResourceReference actorCatalog, ResourceReference combatRules) {
        private RuleSources {
            Objects.requireNonNull(actorCatalog, "actorCatalog");
            Objects.requireNonNull(combatRules, "combatRules");
        }
    }

    /** Resolves immutable combatant sounds before constructing the descriptor-connected reaction component. */
    private static final class CombatantPresentationFactory implements ComponentFactory<DoomCombatantPresentation> {
        @Override
        public void prepare(ComponentPreparationContext context) {
            ComponentProperties properties = context.properties();
            for (ResourceReference reference : RuntimeProperties.resourceReferences(
                    properties, DoomedCorridorsDescriptors.COMBATANT_SIGHT_SOUNDS_PROPERTY)) {
                context.resolveResource(reference, PcmAudioResource.class);
            }
            context.resolveResource(
                    properties.resourceReference(DoomedCorridorsDescriptors.COMBATANT_ATTACK_SOUND_PROPERTY),
                    PcmAudioResource.class);
            context.resolveResource(
                    properties.resourceReference(DoomedCorridorsDescriptors.COMBATANT_PAIN_SOUND_PROPERTY),
                    PcmAudioResource.class);
            for (ResourceReference reference : RuntimeProperties.resourceReferences(
                    properties, DoomedCorridorsDescriptors.COMBATANT_DEATH_SOUNDS_PROPERTY)) {
                context.resolveResource(reference, PcmAudioResource.class);
            }
        }

        @Override
        public DoomCombatantPresentation create(ComponentFactoryContext context) {
            ComponentProperties properties = context.properties();
            List<PcmAudioResource> sightSounds =
                    RuntimeProperties.resourceReferences(
                                    properties, DoomedCorridorsDescriptors.COMBATANT_SIGHT_SOUNDS_PROPERTY)
                            .stream()
                            .map(reference -> context.resolveResource(reference, PcmAudioResource.class))
                            .toList();
            PcmAudioResource attackSound = context.resolveResource(
                    properties.resourceReference(DoomedCorridorsDescriptors.COMBATANT_ATTACK_SOUND_PROPERTY),
                    PcmAudioResource.class);
            PcmAudioResource painSound = context.resolveResource(
                    properties.resourceReference(DoomedCorridorsDescriptors.COMBATANT_PAIN_SOUND_PROPERTY),
                    PcmAudioResource.class);
            List<PcmAudioResource> deathSounds =
                    RuntimeProperties.resourceReferences(
                                    properties, DoomedCorridorsDescriptors.COMBATANT_DEATH_SOUNDS_PROPERTY)
                            .stream()
                            .map(reference -> context.resolveResource(reference, PcmAudioResource.class))
                            .toList();
            Duration frameDuration = Duration.ofMillis(RuntimeProperties.positiveInteger(
                    properties, DoomedCorridorsDescriptors.COMBATANT_FRAME_MILLISECONDS_PROPERTY));
            PositionalSoundAttenuation attenuation = new PositionalSoundAttenuation(
                    RuntimeProperties.positiveFloat(
                            properties, DoomedCorridorsDescriptors.COMBATANT_SOUND_REFERENCE_DISTANCE_PROPERTY),
                    RuntimeProperties.positiveFloat(
                            properties, DoomedCorridorsDescriptors.COMBATANT_SOUND_MAXIMUM_DISTANCE_PROPERTY),
                    RuntimeProperties.nonNegativeFloat(
                            properties, DoomedCorridorsDescriptors.COMBATANT_SOUND_ROLLOFF_FACTOR_PROPERTY));
            return new DoomCombatantPresentation(
                    context.owner().authoredId().value().getMostSignificantBits()
                            ^ context.owner().authoredId().value().getLeastSignificantBits(),
                    context.world().requireModule(PresentationWorldModule.class),
                    new DoomCombatantPresentation.AudioResources(sightSounds, attackSound, painSound, deathSounds),
                    frameDuration,
                    attenuation);
        }
    }

    /** Resolves all immutable presentation resources before constructing a weapon overlay. */
    private static final class WeaponPresentationFactory implements ComponentFactory<DoomWeaponPresentation> {
        @Override
        public void prepare(ComponentPreparationContext context) {
            ComponentProperties properties = context.properties();
            context.resolveResource(
                    properties.resourceReference(DoomedCorridorsDescriptors.READY_FRAME_PROPERTY),
                    OverlayImageResource.class);
            for (ResourceReference reference :
                    RuntimeProperties.resourceReferences(properties, DoomedCorridorsDescriptors.FIRE_FRAMES_PROPERTY)) {
                context.resolveResource(reference, OverlayImageResource.class);
            }
            context.resolveResource(
                    properties.resourceReference(DoomedCorridorsDescriptors.FIRE_SOUND_PROPERTY),
                    PcmAudioResource.class);
        }

        @Override
        public DoomWeaponPresentation create(ComponentFactoryContext context) {
            ComponentProperties properties = context.properties();
            OverlayImageResource readyFrame = context.resolveResource(
                    properties.resourceReference(DoomedCorridorsDescriptors.READY_FRAME_PROPERTY),
                    OverlayImageResource.class);
            List<OverlayImageResource> fireFrames =
                    RuntimeProperties.resourceReferences(properties, DoomedCorridorsDescriptors.FIRE_FRAMES_PROPERTY)
                            .stream()
                            .map(reference -> context.resolveResource(reference, OverlayImageResource.class))
                            .toList();
            PcmAudioResource fireSound = context.resolveResource(
                    properties.resourceReference(DoomedCorridorsDescriptors.FIRE_SOUND_PROPERTY),
                    PcmAudioResource.class);
            Duration frameDuration = Duration.ofMillis(RuntimeProperties.positiveInteger(
                    properties, DoomedCorridorsDescriptors.FRAME_MILLISECONDS_PROPERTY));
            Duration hitIndicatorDuration = Duration.ofMillis(RuntimeProperties.positiveInteger(
                    properties, DoomedCorridorsDescriptors.HIT_INDICATOR_MILLISECONDS_PROPERTY));
            Duration deathLowerDuration = Duration.ofMillis(RuntimeProperties.positiveInteger(
                    properties, DoomedCorridorsDescriptors.WEAPON_DEATH_LOWER_MILLISECONDS_PROPERTY));
            return new DoomWeaponPresentation(
                    context.world().requireModule(PresentationWorldModule.class),
                    properties.text(DoomedCorridorsDescriptors.WEAPON_ID_PROPERTY),
                    readyFrame,
                    fireFrames,
                    fireSound,
                    new DoomWeaponPresentation.Timing(frameDuration, hitIndicatorDuration, deathLowerDuration));
        }
    }

    /** Resolves immutable player sounds before constructing listener-relative damage presentation. */
    private static final class PlayerPresentationFactory implements ComponentFactory<DoomPlayerPresentation> {
        @Override
        public void prepare(ComponentPreparationContext context) {
            ComponentProperties properties = context.properties();
            context.resolveResource(
                    properties.resourceReference(DoomedCorridorsDescriptors.PLAYER_PAIN_SOUND_PROPERTY),
                    PcmAudioResource.class);
            context.resolveResource(
                    properties.resourceReference(DoomedCorridorsDescriptors.PLAYER_DEATH_SOUND_PROPERTY),
                    PcmAudioResource.class);
        }

        @Override
        public DoomPlayerPresentation create(ComponentFactoryContext context) {
            ComponentProperties properties = context.properties();
            PcmAudioResource painSound = context.resolveResource(
                    properties.resourceReference(DoomedCorridorsDescriptors.PLAYER_PAIN_SOUND_PROPERTY),
                    PcmAudioResource.class);
            PcmAudioResource deathSound = context.resolveResource(
                    properties.resourceReference(DoomedCorridorsDescriptors.PLAYER_DEATH_SOUND_PROPERTY),
                    PcmAudioResource.class);
            Duration painFlashDuration = Duration.ofMillis(RuntimeProperties.positiveInteger(
                    properties, DoomedCorridorsDescriptors.PLAYER_PAIN_FLASH_MILLISECONDS_PROPERTY));
            Duration deathFlashDuration = Duration.ofMillis(RuntimeProperties.positiveInteger(
                    properties, DoomedCorridorsDescriptors.PLAYER_DEATH_FLASH_MILLISECONDS_PROPERTY));
            return new DoomPlayerPresentation(
                    context.world().requireModule(PresentationWorldModule.class),
                    painSound,
                    deathSound,
                    new DoomPlayerPresentation.Flash(
                            painFlashDuration,
                            RuntimeProperties.positiveFloat(
                                    properties, DoomedCorridorsDescriptors.PLAYER_PAIN_FLASH_OPACITY_PROPERTY)),
                    new DoomPlayerPresentation.Flash(
                            deathFlashDuration,
                            RuntimeProperties.positiveFloat(
                                    properties, DoomedCorridorsDescriptors.PLAYER_DEATH_FLASH_OPACITY_PROPERTY)),
                    new DoomPlayerPresentation.ViewDrop(
                            Duration.ofMillis(RuntimeProperties.positiveInteger(
                                    properties,
                                    DoomedCorridorsDescriptors.PLAYER_DEATH_VIEW_DROP_MILLISECONDS_PROPERTY)),
                            RuntimeProperties.positiveFloat(
                                    properties, DoomedCorridorsDescriptors.PLAYER_DEATH_VIEW_DROP_DISTANCE_PROPERTY)),
                    RuntimeProperties.unitIntervalFloat(
                            properties, DoomedCorridorsDescriptors.PLAYER_TERMINAL_SHADE_OPACITY_PROPERTY));
        }
    }

    /** Resolves project-authored door sounds before constructing phase-driven positional presentation. */
    private static final class DoorPresentationFactory implements ComponentFactory<DoomDoorPresentation> {
        private static final List<PropertyId> SOUND_PROPERTIES = List.of(
                DoomedCorridorsDescriptors.NORMAL_DOOR_OPENING_SOUND_PROPERTY,
                DoomedCorridorsDescriptors.NORMAL_DOOR_CLOSING_SOUND_PROPERTY,
                DoomedCorridorsDescriptors.BLAZE_DOOR_OPENING_SOUND_PROPERTY,
                DoomedCorridorsDescriptors.BLAZE_DOOR_CLOSING_SOUND_PROPERTY);

        @Override
        public void prepare(ComponentPreparationContext context) {
            ComponentProperties properties = context.properties();
            SOUND_PROPERTIES.forEach(property ->
                    context.resolveResource(properties.resourceReference(property), PcmAudioResource.class));
        }

        @Override
        public DoomDoorPresentation create(ComponentFactoryContext context) {
            ComponentProperties properties = context.properties();
            DoomDoorPresentation.AudioResources audio = new DoomDoorPresentation.AudioResources(
                    resolve(context, DoomedCorridorsDescriptors.NORMAL_DOOR_OPENING_SOUND_PROPERTY),
                    resolve(context, DoomedCorridorsDescriptors.NORMAL_DOOR_CLOSING_SOUND_PROPERTY),
                    resolve(context, DoomedCorridorsDescriptors.BLAZE_DOOR_OPENING_SOUND_PROPERTY),
                    resolve(context, DoomedCorridorsDescriptors.BLAZE_DOOR_CLOSING_SOUND_PROPERTY));
            PositionalSoundAttenuation attenuation = new PositionalSoundAttenuation(
                    RuntimeProperties.positiveFloat(
                            properties, DoomedCorridorsDescriptors.DOOR_SOUND_REFERENCE_DISTANCE_PROPERTY),
                    RuntimeProperties.positiveFloat(
                            properties, DoomedCorridorsDescriptors.DOOR_SOUND_MAXIMUM_DISTANCE_PROPERTY),
                    RuntimeProperties.nonNegativeFloat(
                            properties, DoomedCorridorsDescriptors.DOOR_SOUND_ROLLOFF_FACTOR_PROPERTY));
            return new DoomDoorPresentation(
                    context.world(), context.world().requireModule(PresentationWorldModule.class), audio, attenuation);
        }

        /** Resolves one retained PCM resource selected by a descriptor property. */
        private static PcmAudioResource resolve(ComponentFactoryContext context, PropertyId property) {
            return context.resolveResource(context.properties().resourceReference(property), PcmAudioResource.class);
        }
    }
}
