/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.internal;

import io.github.glynch.jscene3d.project.component.CapabilityId;
import io.github.glynch.jscene3d.project.component.ComponentType;
import io.github.glynch.jscene3d.project.component.EndpointId;
import io.github.glynch.jscene3d.project.component.PropertyId;
import io.github.glynch.jscene3d.project.extension.RegisteredType;

/** Stable component and property identities shared by project publication and application runtime behavior. */
public final class DoomedCorridorsRuntimeTypes {
    /** Application extension identity declared by the project manifest. */
    public static final String EXTENSION_ID = "io.github.glynch.doomed-corridors";

    /** Source-asset type for the provider-owned actor catalog. */
    public static final String ACTOR_CATALOG_ASSET_TYPE = EXTENSION_ID + "/actor-catalog";

    /** Source-asset type for the provider-owned combat rules. */
    public static final String COMBAT_RULES_ASSET_TYPE = EXTENSION_ID + "/combat-rules";

    /** Runtime payload type carrying the camera-relative position of a successful weapon hit. */
    public static final RegisteredType WEAPON_HIT_PAYLOAD_TYPE = new RegisteredType(EXTENSION_ID + "/weapon-hit", 1);

    /** Runtime type for the player resource state. */
    public static final ComponentType PLAYER_STATE_TYPE = ComponentType.of(EXTENSION_ID + "/player-state", 1);

    /** Runtime type for one collectable actor. */
    public static final ComponentType PICKUP_TYPE = ComponentType.of(EXTENSION_ID + "/pickup", 1);

    /** Runtime type for mutable combatant health. */
    public static final ComponentType COMBATANT_STATE_TYPE = ComponentType.of(EXTENSION_ID + "/combatant-state", 1);

    /** Runtime type for one input-driven hitscan weapon. */
    public static final ComponentType HITSCAN_WEAPON_TYPE = ComponentType.of(EXTENSION_ID + "/hitscan-weapon", 1);

    /** Runtime type for first-person weapon overlay and local firing sound. */
    public static final ComponentType WEAPON_PRESENTATION_TYPE =
            ComponentType.of(EXTENSION_ID + "/weapon-presentation", 1);

    /** Semantic capability exposing mutable player resources on the exact player entity. */
    public static final CapabilityId PLAYER_RESOURCES_CAPABILITY = new CapabilityId(EXTENSION_ID + "/player-resources");

    /** Semantic capability exposing mutable damage state on the exact target entity. */
    public static final CapabilityId DAMAGEABLE_CAPABILITY = new CapabilityId(EXTENSION_ID + "/damageable");

    /** Semantic capability exposing target geometry and damage application for hitscan weapons. */
    public static final CapabilityId HITSCAN_TARGET_CAPABILITY = new CapabilityId(EXTENSION_ID + "/hitscan-target");

    /** Semantic capability identifying the exact player weapon component. */
    public static final CapabilityId WEAPON_CAPABILITY = new CapabilityId(EXTENSION_ID + "/weapon");

    /** Player-state reference to the authoritative actor catalog. */
    public static final PropertyId ACTOR_CATALOG_PROPERTY = new PropertyId("actor-catalog");

    /** Player-state reference to the authoritative combat rules. */
    public static final PropertyId COMBAT_RULES_PROPERTY = new PropertyId("combat-rules");

    /** Provider actor identity used to initialize one combatant. */
    public static final PropertyId ACTOR_ID_PROPERTY = new PropertyId("actor-id");

    /** Provider weapon identity used to configure one weapon component. */
    public static final PropertyId WEAPON_ID_PROPERTY = new PropertyId("weapon-id");

    /** Explicit component target supplying the weapon's world-space origin and direction. */
    public static final PropertyId VIEW_TRANSFORM_PROPERTY = new PropertyId("view-transform");

    /** Explicit component target supplying the weapon's perspective projection. */
    public static final PropertyId VIEW_CAMERA_PROPERTY = new PropertyId("view-camera");

    /** Semantic input action which fires the weapon. */
    public static final PropertyId FIRE_ACTION_PROPERTY = new PropertyId("fire-action");

    /** Ready overlay texture displayed while the weapon is idle. */
    public static final PropertyId READY_FRAME_PROPERTY = new PropertyId("ready-frame");

    /** Ordered overlay textures displayed after an accepted shot. */
    public static final PropertyId FIRE_FRAMES_PROPERTY = new PropertyId("fire-frames");

    /** Listener-relative PCM resource played after an accepted shot. */
    public static final PropertyId FIRE_SOUND_PROPERTY = new PropertyId("fire-sound");

    /** Duration of each firing overlay frame in milliseconds. */
    public static final PropertyId FRAME_MILLISECONDS_PROPERTY = new PropertyId("frame-milliseconds");

    /** Duration of the successful-hit indicator in milliseconds. */
    public static final PropertyId HIT_INDICATOR_MILLISECONDS_PROPERTY = new PropertyId("hit-indicator-milliseconds");

    /** Pickup resource-kind property. */
    public static final PropertyId PICKUP_RESOURCE_PROPERTY = new PropertyId("resource");

    /** Pickup resource-amount property. */
    public static final PropertyId PICKUP_AMOUNT_PROPERTY = new PropertyId("amount");

    /** Pickup resource-limit property. */
    public static final PropertyId PICKUP_LIMIT_PROPERTY = new PropertyId("limit");

    /** Pickup action receiving the generic physics overlap payload. */
    public static final EndpointId RECEIVE_OVERLAP_ACTION = new EndpointId("receive-overlap");

    /** Signal emitted exactly once after a weapon accepts and resolves one shot. */
    public static final EndpointId WEAPON_FIRED_SIGNAL = new EndpointId("fired");

    /** Signal emitted only when an accepted shot applies damage to a target. */
    public static final EndpointId WEAPON_HIT_SIGNAL = new EndpointId("hit");

    /** Presentation action receiving one accepted weapon shot. */
    public static final EndpointId RECEIVE_WEAPON_FIRED_ACTION = new EndpointId("receive-fired");

    /** Presentation action receiving one shot which applied damage. */
    public static final EndpointId RECEIVE_WEAPON_HIT_ACTION = new EndpointId("receive-hit");

    /** Prevents construction of this identity container. */
    private DoomedCorridorsRuntimeTypes() {
        throw new AssertionError("DoomedCorridorsRuntimeTypes cannot be instantiated");
    }
}
