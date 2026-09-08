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
import io.github.glynch.jscene3d.project.manifest.GameProject;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.HostedProject;
import io.github.glynch.jscene3d.project.runtime.extension.ApplicationRuntimeExtension;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentFactoryRegistry;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    /** Registers the executable player-state and pickup behavior factories. */
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
    }

    /** Loads authoritative provider rules and initializes every composed player before activation. */
    @Override
    public void prepare(HostedProject project) {
        HostedProject validProject = Objects.requireNonNull(project, "project");
        List<DoomPlayerState> players = new ArrayList<>();
        validProject.world().roots().forEach(root -> collectPlayers(root, players));
        if (players.isEmpty()) {
            throw new IllegalStateException("the startup world has no Doom player-state component");
        }
        for (DoomPlayerState player : players) {
            player.configure(loadRules(validProject.project(), player));
        }
    }

    /** Collects player-state components throughout one owned entity subtree. */
    private static void collectPlayers(Entity entity, List<DoomPlayerState> destination) {
        entity.capability(DoomedCorridorsRuntimeTypes.PLAYER_RESOURCES_CAPABILITY, DoomPlayerState.class)
                .ifPresent(destination::add);
        entity.children().forEach(child -> collectPlayers(child, destination));
    }

    /** Loads combat rules against the explicitly referenced companion actor catalog. */
    private static DoomCombatRules loadRules(GameProject project, DoomPlayerState player) {
        Path actorCatalog = source(
                project, player.actorCatalog(), DoomedCorridorsRuntimeTypes.ACTOR_CATALOG_ASSET_TYPE, "actor catalog");
        DoomActorCatalogLoadResult loadedActors = new DoomActorCatalogLoader().load(actorCatalog);
        DoomActorCatalog actors = loadedActors
                .catalog()
                .orElseThrow(
                        () -> new IllegalStateException("actor catalog loading failed: " + loadedActors.diagnostics()));
        Path combatRules = source(
                project, player.combatRules(), DoomedCorridorsRuntimeTypes.COMBAT_RULES_ASSET_TYPE, "combat rules");
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
}
