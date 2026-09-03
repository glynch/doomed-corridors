/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

import io.github.glynch.doomedcorridors.actor.DoomActor;
import io.github.glynch.doomedcorridors.map.DoomMap;
import io.github.glynch.doomedcorridors.world.DoomPlayerState;
import io.github.glynch.doomedcorridors.world.DoomStaticGeometryBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** Deterministic headless combat session for hitscan, health, ammunition, and death. */
public final class DoomCombatSession {
    private static final float INTERSECTION_TOLERANCE = 0.000_01F;

    private final DoomMap map;
    private final DoomCombatRules rules;
    private final Random random;
    private List<DoomCombatantState> combatants;
    private int playerHealth;
    private int bullets;

    /** Initializes mutable session state from immutable map, rules, and actor inputs. */
    private DoomCombatSession(
            DoomMap map, DoomCombatRules rules, List<DoomActor> actors, long randomSeed) {
        this.map = map;
        this.rules = rules;
        random = new Random(randomSeed);
        playerHealth = rules.startingHealth();
        bullets = rules.startingBullets();
        combatants = createCombatants(actors, rules);
    }

    /**
     * Creates a deterministic combat session without graphics, audio, or input dependencies.
     *
     * @param map decoded classic map used for hitscan occlusion
     * @param rules validated provider combat rules
     * @param actors resolved, difficulty-filtered map actors
     * @param randomSeed explicit seed controlling repeatable damage rolls
     * @return a new independent combat session
     */
    public static DoomCombatSession create(
            DoomMap map, DoomCombatRules rules, List<DoomActor> actors, long randomSeed) {
        return new DoomCombatSession(
                Objects.requireNonNull(map, "map"),
                Objects.requireNonNull(rules, "rules"),
                List.copyOf(Objects.requireNonNull(actors, "actors")),
                randomSeed);
    }

    /** Returns an immutable snapshot of current combat state. */
    public DoomCombatState state() {
        return new DoomCombatState(
                playerHealth,
                rules.startingHealth(),
                bullets,
                rules.primaryWeaponId(),
                combatants);
    }

    /**
     * Fires the selected hitscan weapon from the supplied current player pose.
     *
     * <p>A dead player cannot fire. An empty weapon emits only {@link
     * DoomCombatEvent.Type#WEAPON_EMPTY}. Otherwise ammunition is consumed before tracing the
     * nearest living collision cylinder, with intervening solid map geometry taking precedence.
     */
    public DoomCombatUpdate firePrimary(DoomPlayerState shooter) {
        DoomPlayerState validShooter = Objects.requireNonNull(shooter, "shooter");
        if (playerHealth == 0) {
            return update(List.of());
        }
        DoomCombatRules.WeaponDefinition weapon = rules.primaryWeapon();
        if (bullets < weapon.ammoPerShot()) {
            return update(List.of(event(DoomCombatEvent.Type.WEAPON_EMPTY, DoomCombatEvent.PLAYER, 0)));
        }
        bullets -= weapon.ammoPerShot();
        List<DoomCombatEvent> events = new ArrayList<>();
        events.add(event(DoomCombatEvent.Type.WEAPON_FIRED, DoomCombatEvent.PLAYER, 0));
        Target target = closestTarget(validShooter, weapon);
        if (target != null) {
            damageCombatant(target.index(), weaponDamage(weapon), events);
        }
        return update(events);
    }

    /** Applies positive incoming damage and emits the resulting player lifecycle events. */
    public DoomCombatUpdate damagePlayer(int damage) {
        if (damage <= 0) {
            throw new IllegalArgumentException("damage must be positive");
        }
        if (playerHealth == 0) {
            return update(List.of());
        }
        int previousHealth = playerHealth;
        long remainingHealth = (long) playerHealth - damage;
        playerHealth = (int) Math.clamp(remainingHealth, 0L, rules.startingHealth());
        int appliedDamage = previousHealth - playerHealth;
        List<DoomCombatEvent> events = new ArrayList<>();
        events.add(event(DoomCombatEvent.Type.PLAYER_DAMAGED, DoomCombatEvent.PLAYER, appliedDamage));
        if (playerHealth == 0) {
            events.add(event(DoomCombatEvent.Type.PLAYER_KILLED, DoomCombatEvent.PLAYER, 0));
        }
        return update(events);
    }

    /** Creates initial combatant snapshots for actors named by the combat rules. */
    private static List<DoomCombatantState> createCombatants(
            List<DoomActor> actors, DoomCombatRules rules) {
        List<DoomCombatantState> result = new ArrayList<>();
        for (DoomActor actor : actors) {
            DoomCombatRules.CombatantDefinition definition =
                    rules.combatant(actor.definition().id());
            if (definition != null) {
                result.add(new DoomCombatantState(
                        actor,
                        world(definition.radius()),
                        world(definition.height()),
                        definition.health()));
            }
        }
        return List.copyOf(result);
    }

    /** Selects the nearest living actor intersection not hidden behind map geometry. */
    private Target closestTarget(
            DoomPlayerState shooter, DoomCombatRules.WeaponDefinition weapon) {
        float range = world(weapon.range());
        Ray ray = Ray.from(shooter);
        float wallDistance = nearestWallDistance(ray, range);
        Target nearest = null;
        for (int index = 0; index < combatants.size(); index++) {
            DoomCombatantState combatant = combatants.get(index);
            if (combatant.status() == DoomCombatantStatus.ALIVE) {
                float distance = combatantDistance(ray, combatant, range);
                if (distance < wallDistance
                        && (nearest == null || distance < nearest.distance())) {
                    nearest = new Target(index, distance);
                }
            }
        }
        return nearest;
    }

    /** Returns the first distance where a ray enters one finite vertical collision cylinder. */
    private static float combatantDistance(
            Ray ray, DoomCombatantState combatant, float maximumDistance) {
        float offsetX = combatant.x() - ray.x();
        float offsetZ = combatant.z() - ray.z();
        float projection = offsetX * ray.directionX() + offsetZ * ray.directionZ();
        float perpendicularSquared = offsetX * offsetX + offsetZ * offsetZ - projection * projection;
        float radiusSquared = combatant.radius() * combatant.radius();
        if (perpendicularSquared > radiusSquared) {
            return Float.POSITIVE_INFINITY;
        }
        float halfChord = (float) Math.sqrt(Math.max(0.0F, radiusSquared - perpendicularSquared));
        float near = projection - halfChord;
        float far = projection + halfChord;
        if (far < 0.0F) {
            return Float.POSITIVE_INFINITY;
        }
        if (near < 0.0F) {
            near = 0.0F;
        }
        return verticalIntersection(ray, combatant, near, far, maximumDistance);
    }

    /** Intersects a horizontal cylinder chord with the ray's vertical span. */
    private static float verticalIntersection(
            Ray ray,
            DoomCombatantState combatant,
            float near,
            float far,
            float maximumDistance) {
        float bottom = combatant.floorHeight();
        float top = bottom + combatant.height();
        if (Math.abs(ray.verticalSlope()) < INTERSECTION_TOLERANCE) {
            return ray.height() >= bottom && ray.height() <= top && near <= maximumDistance
                    ? near
                    : Float.POSITIVE_INFINITY;
        }
        float firstVertical = (bottom - ray.height()) / ray.verticalSlope();
        float secondVertical = (top - ray.height()) / ray.verticalSlope();
        float verticalStart = firstVertical < secondVertical ? firstVertical : secondVertical;
        float verticalEnd = firstVertical < secondVertical ? secondVertical : firstVertical;
        float candidate = near < verticalStart ? verticalStart : near;
        float end = far < verticalEnd ? far : verticalEnd;
        return candidate >= 0.0F && candidate <= end && candidate <= maximumDistance
                ? candidate
                : Float.POSITIVE_INFINITY;
    }

    /** Returns the nearest solid map intersection or positive infinity when unobstructed. */
    private float nearestWallDistance(Ray ray, float maximumDistance) {
        float nearest = Float.POSITIVE_INFINITY;
        for (DoomMap.Linedef linedef : map.linedefs()) {
            float distance = lineDistance(ray, linedef);
            if (distance >= 0.0F && distance <= maximumDistance && distance < nearest) {
                float shotHeight = ray.height() + distance * ray.verticalSlope();
                if (blocksShot(linedef, shotHeight)) {
                    nearest = distance;
                }
            }
        }
        return nearest;
    }

    /** Finds a forward ray intersection with one finite linedef segment. */
    private float lineDistance(Ray ray, DoomMap.Linedef linedef) {
        DoomMap.Vertex start = map.vertices().get(linedef.startVertex());
        DoomMap.Vertex end = map.vertices().get(linedef.endVertex());
        float startX = world(start.x());
        float startZ = world(-start.y());
        float segmentX = world(end.x() - start.x());
        float segmentZ = world(start.y() - end.y());
        float denominator = cross(ray.directionX(), ray.directionZ(), segmentX, segmentZ);
        if (Math.abs(denominator) < INTERSECTION_TOLERANCE) {
            return Float.POSITIVE_INFINITY;
        }
        float offsetX = startX - ray.x();
        float offsetZ = startZ - ray.z();
        float distance = cross(offsetX, offsetZ, segmentX, segmentZ) / denominator;
        float segmentAmount = cross(offsetX, offsetZ, ray.directionX(), ray.directionZ()) / denominator;
        return distance >= 0.0F && segmentAmount >= 0.0F && segmentAmount <= 1.0F
                ? distance
                : Float.POSITIVE_INFINITY;
    }

    /** Determines whether a linedef is solid at the shot's intersection height. */
    private boolean blocksShot(DoomMap.Linedef linedef, float shotHeight) {
        if (linedef.rightSidedef() < 0 || linedef.leftSidedef() < 0) {
            return true;
        }
        DoomMap.Sector right = sectorForSide(linedef.rightSidedef());
        DoomMap.Sector left = sectorForSide(linedef.leftSidedef());
        float openingBottom = world(Math.max(right.floorHeight(), left.floorHeight()));
        float openingTop = world(Math.min(right.ceilingHeight(), left.ceilingHeight()));
        return shotHeight <= openingBottom || shotHeight >= openingTop;
    }

    /** Applies weapon damage to one selected mutable combatant slot. */
    private void damageCombatant(int index, int damage, List<DoomCombatEvent> events) {
        DoomCombatantState previous = combatants.get(index);
        long remainingHealth = (long) previous.health() - damage;
        int health = (int) Math.clamp(remainingHealth, 0L, previous.maximumHealth());
        int appliedDamage = previous.health() - health;
        DoomCombatantState damaged = previous.withHealth(health);
        List<DoomCombatantState> updated = new ArrayList<>(combatants);
        updated.set(index, damaged);
        combatants = List.copyOf(updated);
        events.add(event(
                DoomCombatEvent.Type.COMBATANT_DAMAGED, damaged.thingIndex(), appliedDamage));
        if (damaged.status() == DoomCombatantStatus.DEAD) {
            events.add(event(DoomCombatEvent.Type.COMBATANT_KILLED, damaged.thingIndex(), 0));
        }
    }

    /** Rolls one deterministic configured damage value. */
    private int weaponDamage(DoomCombatRules.WeaponDefinition weapon) {
        return weapon.damageMinimum()
                + random.nextInt(weapon.damageValueCount()) * weapon.damageStep();
    }

    /** Returns the sector referenced by one map sidedef. */
    private DoomMap.Sector sectorForSide(int sidedefIndex) {
        return map.sectors().get(map.sidedefs().get(sidedefIndex).sector());
    }

    /** Creates an update from current state and ordered operation events. */
    private DoomCombatUpdate update(List<DoomCombatEvent> events) {
        return new DoomCombatUpdate(state(), events);
    }

    /** Creates one compact event value. */
    private static DoomCombatEvent event(
            DoomCombatEvent.Type type, int thingIndex, int amount) {
        return new DoomCombatEvent(type, thingIndex, amount);
    }

    /** Returns the signed two-dimensional cross product. */
    private static float cross(float firstX, float firstZ, float secondX, float secondZ) {
        return firstX * secondZ - firstZ * secondX;
    }

    /** Converts classic Doom map units to JScene3D world units. */
    private static float world(float doomUnits) {
        return doomUnits / DoomStaticGeometryBuilder.DOOM_UNITS_PER_WORLD_UNIT;
    }

    /** Horizontal ray origin and direction plus vertical slope. */
    private record Ray(
            float x,
            float height,
            float z,
            float directionX,
            float directionZ,
            float verticalSlope) {
        /** Creates a normalized horizontal ray from one player view pose. */
        private static Ray from(DoomPlayerState player) {
            return new Ray(
                    player.x(),
                    player.eyeHeight(),
                    player.z(),
                    (float) Math.cos(player.yawRadians()),
                    -(float) Math.sin(player.yawRadians()),
                    (float) Math.tan(player.pitchRadians()));
        }
    }

    /** Selected combatant list slot and first ray-intersection distance. */
    private record Target(int index, float distance) {}
}
