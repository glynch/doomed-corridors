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
import io.github.glynch.doomedcorridors.internal.DoomedCorridorsRuntimeTypes;
import io.github.glynch.doomedcorridors.internal.RuntimeProperties;
import io.github.glynch.jscene3d.game.input.InputWorldModule;
import io.github.glynch.jscene3d.game.presentation.OverlayImageResource;
import io.github.glynch.jscene3d.game.presentation.PcmAudioResource;
import io.github.glynch.jscene3d.game.presentation.PositionalSoundAttenuation;
import io.github.glynch.jscene3d.game.presentation.PresentationWorldModule;
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

/** Manifest-selected Doomed Corridors application extension. */
public final class DoomedCorridorsRuntimeExtension implements ApplicationRuntimeExtension {
    static final String ID = DoomedCorridorsRuntimeTypes.EXTENSION_ID;

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
                DoomedCorridorsRuntimeTypes.PLAYER_STATE_TYPE,
                context -> new DoomPlayerState(
                        context.properties().resourceReference(DoomedCorridorsRuntimeTypes.ACTOR_CATALOG_PROPERTY),
                        context.properties().resourceReference(DoomedCorridorsRuntimeTypes.COMBAT_RULES_PROPERTY)));
        validRegistry.register(
                DoomedCorridorsRuntimeTypes.PICKUP_TYPE,
                context -> new DoomPickup(
                        context.owner(),
                        context.world(),
                        context.properties().text(DoomedCorridorsRuntimeTypes.PICKUP_RESOURCE_PROPERTY),
                        RuntimeProperties.positiveInteger(
                                context.properties(), DoomedCorridorsRuntimeTypes.PICKUP_AMOUNT_PROPERTY),
                        RuntimeProperties.positiveInteger(
                                context.properties(), DoomedCorridorsRuntimeTypes.PICKUP_LIMIT_PROPERTY)));
        validRegistry.register(
                DoomedCorridorsRuntimeTypes.COMBATANT_STATE_TYPE,
                context -> new DoomCombatantState(
                        context.owner(),
                        context.properties().resourceReference(DoomedCorridorsRuntimeTypes.ACTOR_CATALOG_PROPERTY),
                        context.properties().resourceReference(DoomedCorridorsRuntimeTypes.COMBAT_RULES_PROPERTY),
                        context.properties().text(DoomedCorridorsRuntimeTypes.ACTOR_ID_PROPERTY)));
        validRegistry.register(
                DoomedCorridorsRuntimeTypes.COMBATANT_PRESENTATION_TYPE, new CombatantPresentationFactory());
        validRegistry.register(
                DoomedCorridorsRuntimeTypes.HITSCAN_WEAPON_TYPE,
                context -> new DoomHitscanWeapon(
                        context.owner(),
                        context.world().requireModule(InputWorldModule.class),
                        context.world().requireModule(Physics3dWorldModule.class),
                        context.properties().resourceReference(DoomedCorridorsRuntimeTypes.ACTOR_CATALOG_PROPERTY),
                        context.properties().resourceReference(DoomedCorridorsRuntimeTypes.COMBAT_RULES_PROPERTY),
                        context.properties().text(DoomedCorridorsRuntimeTypes.WEAPON_ID_PROPERTY),
                        context.properties().text(DoomedCorridorsRuntimeTypes.FIRE_ACTION_PROPERTY)));
        validRegistry.register(DoomedCorridorsRuntimeTypes.WEAPON_PRESENTATION_TYPE, new WeaponPresentationFactory());
        validRegistry.register(DoomedCorridorsRuntimeTypes.PLAYER_HUD_TYPE, context -> new DoomPlayerHud());
    }

    /** Loads authoritative provider rules and initializes every descriptor-declared consumer before activation. */
    @Override
    public void prepare(HostedProject project) {
        HostedProject validProject = Objects.requireNonNull(project, "project");
        List<DoomRuleConsumer> consumers = new ArrayList<>();
        validProject.world().roots().forEach(root -> collectRuleConsumers(root, consumers));
        boolean hasPlayer = consumers.stream().anyMatch(DoomPlayerState.class::isInstance);
        if (!hasPlayer) {
            throw new IllegalStateException("the startup world has no Doom player-state component");
        }
        Map<RuleSources, DoomCombatRules> loadedRules = new LinkedHashMap<>();
        for (DoomRuleConsumer consumer : consumers) {
            RuleSources sources = new RuleSources(consumer.actorCatalog(), consumer.combatRules());
            DoomCombatRules rules = loadedRules.computeIfAbsent(
                    sources, key -> loadRules(validProject.project(), key.actorCatalog(), key.combatRules()));
            consumer.configure(rules);
        }
    }

    /** Collects only descriptor-declared rule consumers throughout one owned entity subtree. */
    private static void collectRuleConsumers(Entity entity, List<DoomRuleConsumer> destination) {
        entity.capability(DoomedCorridorsRuntimeTypes.PLAYER_RESOURCES_CAPABILITY, DoomPlayerState.class)
                .ifPresent(destination::add);
        entity.capability(DoomedCorridorsRuntimeTypes.DAMAGEABLE_CAPABILITY, DoomCombatantState.class)
                .ifPresent(destination::add);
        entity.capability(DoomedCorridorsRuntimeTypes.WEAPON_CAPABILITY, DoomHitscanWeapon.class)
                .ifPresent(destination::add);
        entity.children().forEach(child -> collectRuleConsumers(child, destination));
    }

    /** Loads combat rules against the explicitly referenced companion actor catalog. */
    private static DoomCombatRules loadRules(
            GameProject project, ResourceReference actorCatalogReference, ResourceReference combatRulesReference) {
        Path actorCatalog = source(
                project, actorCatalogReference, DoomedCorridorsRuntimeTypes.ACTOR_CATALOG_ASSET_TYPE, "actor catalog");
        DoomActorCatalogLoadResult loadedActors = new DoomActorCatalogLoader().load(actorCatalog);
        DoomActorCatalog actors = loadedActors
                .catalog()
                .orElseThrow(
                        () -> new IllegalStateException("actor catalog loading failed: " + loadedActors.diagnostics()));
        Path combatRules = source(
                project, combatRulesReference, DoomedCorridorsRuntimeTypes.COMBAT_RULES_ASSET_TYPE, "combat rules");
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
            context.resolveResource(
                    properties.resourceReference(DoomedCorridorsRuntimeTypes.COMBATANT_PAIN_SOUND_PROPERTY),
                    PcmAudioResource.class);
            for (ResourceReference reference : RuntimeProperties.resourceReferences(
                    properties, DoomedCorridorsRuntimeTypes.COMBATANT_DEATH_SOUNDS_PROPERTY)) {
                context.resolveResource(reference, PcmAudioResource.class);
            }
        }

        @Override
        public DoomCombatantPresentation create(ComponentFactoryContext context) {
            ComponentProperties properties = context.properties();
            PcmAudioResource painSound = context.resolveResource(
                    properties.resourceReference(DoomedCorridorsRuntimeTypes.COMBATANT_PAIN_SOUND_PROPERTY),
                    PcmAudioResource.class);
            List<PcmAudioResource> deathSounds = RuntimeProperties.resourceReferences(
                            properties, DoomedCorridorsRuntimeTypes.COMBATANT_DEATH_SOUNDS_PROPERTY)
                    .stream()
                    .map(reference -> context.resolveResource(reference, PcmAudioResource.class))
                    .toList();
            Duration frameDuration = Duration.ofMillis(RuntimeProperties.positiveInteger(
                    properties, DoomedCorridorsRuntimeTypes.COMBATANT_FRAME_MILLISECONDS_PROPERTY));
            PositionalSoundAttenuation attenuation = new PositionalSoundAttenuation(
                    RuntimeProperties.positiveFloat(
                            properties, DoomedCorridorsRuntimeTypes.COMBATANT_SOUND_REFERENCE_DISTANCE_PROPERTY),
                    RuntimeProperties.positiveFloat(
                            properties, DoomedCorridorsRuntimeTypes.COMBATANT_SOUND_MAXIMUM_DISTANCE_PROPERTY),
                    RuntimeProperties.nonNegativeFloat(
                            properties, DoomedCorridorsRuntimeTypes.COMBATANT_SOUND_ROLLOFF_FACTOR_PROPERTY));
            return new DoomCombatantPresentation(
                    context.owner(),
                    context.world().requireModule(PresentationWorldModule.class),
                    painSound,
                    deathSounds,
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
                    properties.resourceReference(DoomedCorridorsRuntimeTypes.READY_FRAME_PROPERTY),
                    OverlayImageResource.class);
            for (ResourceReference reference : RuntimeProperties.resourceReferences(
                    properties, DoomedCorridorsRuntimeTypes.FIRE_FRAMES_PROPERTY)) {
                context.resolveResource(reference, OverlayImageResource.class);
            }
            context.resolveResource(
                    properties.resourceReference(DoomedCorridorsRuntimeTypes.FIRE_SOUND_PROPERTY),
                    PcmAudioResource.class);
        }

        @Override
        public DoomWeaponPresentation create(ComponentFactoryContext context) {
            ComponentProperties properties = context.properties();
            OverlayImageResource readyFrame = context.resolveResource(
                    properties.resourceReference(DoomedCorridorsRuntimeTypes.READY_FRAME_PROPERTY),
                    OverlayImageResource.class);
            List<OverlayImageResource> fireFrames =
                    RuntimeProperties.resourceReferences(properties, DoomedCorridorsRuntimeTypes.FIRE_FRAMES_PROPERTY)
                            .stream()
                            .map(reference -> context.resolveResource(reference, OverlayImageResource.class))
                            .toList();
            PcmAudioResource fireSound = context.resolveResource(
                    properties.resourceReference(DoomedCorridorsRuntimeTypes.FIRE_SOUND_PROPERTY),
                    PcmAudioResource.class);
            Duration frameDuration = Duration.ofMillis(RuntimeProperties.positiveInteger(
                    properties, DoomedCorridorsRuntimeTypes.FRAME_MILLISECONDS_PROPERTY));
            Duration hitIndicatorDuration = Duration.ofMillis(RuntimeProperties.positiveInteger(
                    properties, DoomedCorridorsRuntimeTypes.HIT_INDICATOR_MILLISECONDS_PROPERTY));
            return new DoomWeaponPresentation(
                    context.world().requireModule(PresentationWorldModule.class),
                    readyFrame,
                    fireFrames,
                    fireSound,
                    frameDuration,
                    hitIndicatorDuration);
        }
    }
}
