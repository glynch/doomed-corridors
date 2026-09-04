/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

import io.github.glynch.doomedcorridors.actor.DoomActor;
import io.github.glynch.jscene3d.doom.map.DoomMap;
import io.github.glynch.doomedcorridors.world.DoomCollisionWorld;
import io.github.glynch.doomedcorridors.world.DoomPlayerState;
import io.github.glynch.doomedcorridors.world.DoomUnits;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/** Deterministic headless combat session for hitscan, health, ammunition, and death. */
public final class DoomCombatSession {
    private static final long FIXED_STEP_NANOS = 1_000_000_000L / 35L;
    private static final float FIXED_STEP_SECONDS = 1.0F / 35.0F;
    private static final float ENEMY_MAXIMUM_STEP = DoomUnits.toWorld(24.0F);
    private static final float PICKUP_MINIMUM_HEIGHT_DELTA = DoomUnits.toWorld(-8.0F);
    private static final float AUTO_AIM_ANGLE = (float) Math.toRadians(5.625);
    private static final float AUTO_AIM_MAXIMUM_SLOPE = 100.0F / 160.0F;
    private static final float INTERSECTION_TOLERANCE = 0.000_01F;

    private final DoomMap map;
    private final DoomCombatRules rules;
    private final DoomCollisionWorld collision;
    private final Random random;
    private final List<EnemyRuntime> enemyRuntimes;
    private final List<Pickup> pickups;
    private final Set<Integer> collectedPickups = new LinkedHashSet<>();
    private List<DoomCombatantState> combatants;
    private int playerHealth;
    private int bullets;
    private long accumulatedNanos;

    /** Initializes mutable session state from immutable map, rules, and actor inputs. */
    private DoomCombatSession(
            DoomMap map, DoomCombatRules rules, List<DoomActor> actors, long randomSeed) {
        this.map = map;
        this.rules = rules;
        collision = new DoomCollisionWorld(map);
        random = new Random(randomSeed);
        playerHealth = rules.startingHealth();
        bullets = rules.startingBullets();
        combatants = createCombatants(actors, rules);
        enemyRuntimes = createEnemyRuntimes(combatants.size());
        pickups = createPickups(actors, rules);
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
                rules.maximumHealth(),
                bullets,
                rules.maximumBullets(),
                rules.primaryWeaponId(),
                combatants,
                List.copyOf(collectedPickups));
    }

    /**
     * Advances enemy awareness, pursuit, and attacks using deterministic 35 Hz updates.
     *
     * @param player current player pose used for perception and targeting
     * @param elapsed non-negative render-frame time retained across partial simulation steps
     * @return resulting combat state and ordered behavior events
     */
    public DoomCombatUpdate advance(DoomPlayerState player, Duration elapsed) {
        DoomPlayerState validPlayer = Objects.requireNonNull(player, "player");
        Duration validElapsed = Objects.requireNonNull(elapsed, "elapsed");
        if (validElapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must not be negative");
        }
        accumulatedNanos = Math.addExact(accumulatedNanos, validElapsed.toNanos());
        List<DoomCombatEvent> events = new ArrayList<>();
        while (accumulatedNanos >= FIXED_STEP_NANOS) {
            collectPickups(validPlayer, events);
            advanceCombatants(validPlayer, events);
            accumulatedNanos -= FIXED_STEP_NANOS;
        }
        return update(events);
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
        List<DoomCombatEvent> events = new ArrayList<>();
        damagePlayer(damage, events);
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
                        DoomUnits.toWorld(definition.radius()),
                        DoomUnits.toWorld(definition.height()),
                        definition.health()));
            }
        }
        return List.copyOf(result);
    }

    /** Creates one mutable timing record for each indexed combatant. */
    private static List<EnemyRuntime> createEnemyRuntimes(int count) {
        List<EnemyRuntime> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            result.add(new EnemyRuntime());
        }
        return List.copyOf(result);
    }

    /** Creates immutable pickup placements for actors named by the provider rules. */
    private static List<Pickup> createPickups(
            List<DoomActor> actors, DoomCombatRules rules) {
        List<Pickup> result = new ArrayList<>();
        for (DoomActor actor : actors) {
            DoomCombatRules.PickupDefinition definition =
                    rules.pickup(actor.definition().id());
            if (definition != null) {
                result.add(new Pickup(
                        actor.thingIndex(),
                        actor.x(),
                        actor.floorHeight(),
                        actor.z(),
                        DoomUnits.toWorld(definition.radius()),
                        definition));
            }
        }
        return List.copyOf(result);
    }

    /** Collects every overlapping useful pickup once in source-map order. */
    private void collectPickups(
            DoomPlayerState player, List<DoomCombatEvent> events) {
        if (playerHealth == 0) {
            return;
        }
        for (Pickup pickup : pickups) {
            if (!collectedPickups.contains(pickup.thingIndex())
                    && touches(player, pickup)
                    && applyPickup(pickup, events)) {
                collectedPickups.add(pickup.thingIndex());
            }
        }
    }

    /** Applies one useful resource effect and emits its exact applied amount. */
    private boolean applyPickup(Pickup pickup, List<DoomCombatEvent> events) {
        DoomCombatRules.PickupDefinition definition = pickup.definition();
        int previous = switch (definition.resource()) {
            case HEALTH -> playerHealth;
            case BULLETS -> bullets;
        };
        int current = addResource(previous, definition.amount(), definition.limit());
        if (current == previous) {
            return false;
        }
        if (definition.resource() == DoomCombatRules.PickupResource.HEALTH) {
            playerHealth = current;
        } else {
            bullets = current;
        }
        DoomCombatEvent.Type eventType = switch (definition.resource()) {
            case HEALTH -> DoomCombatEvent.Type.HEALTH_PICKED_UP;
            case BULLETS -> DoomCombatEvent.Type.AMMUNITION_PICKED_UP;
        };
        int applied = current - previous;
        events.add(event(eventType, pickup.thingIndex(), applied));
        return true;
    }

    /** Adds a positive amount without exceeding a provider-defined resource limit. */
    private static int addResource(int current, int amount, int limit) {
        return (int) Math.clamp((long) current + amount, 0L, limit);
    }

    /** Tests classic horizontal pickup contact and asymmetric vertical player reach. */
    private static boolean touches(DoomPlayerState player, Pickup pickup) {
        float deltaX = player.x() - pickup.x();
        float deltaZ = player.z() - pickup.z();
        float contactRadius = DoomCollisionWorld.PLAYER_RADIUS + pickup.radius();
        float playerFloor = player.eyeHeight() - DoomCollisionWorld.PLAYER_EYE_HEIGHT;
        float heightDelta = pickup.floorHeight() - playerFloor;
        return deltaX * deltaX + deltaZ * deltaZ <= contactRadius * contactRadius
                && heightDelta <= DoomCollisionWorld.PLAYER_HEIGHT
                && heightDelta >= PICKUP_MINIMUM_HEIGHT_DELTA;
    }

    /** Advances each living combatant once and publishes one immutable state list. */
    private void advanceCombatants(
            DoomPlayerState player, List<DoomCombatEvent> events) {
        if (playerHealth == 0) {
            return;
        }
        List<DoomCombatantState> updated = new ArrayList<>(combatants);
        for (int index = 0; index < updated.size() && playerHealth > 0; index++) {
            DoomCombatantState combatant = updated.get(index);
            if (combatant.status() == DoomCombatantStatus.ALIVE) {
                updated.set(index, advanceCombatant(index, combatant, player, events));
            }
        }
        combatants = List.copyOf(updated);
    }

    /** Advances perception and selects pursuit or attack behavior for one living combatant. */
    private DoomCombatantState advanceCombatant(
            int index,
            DoomCombatantState combatant,
            DoomPlayerState player,
            List<DoomCombatEvent> events) {
        DoomCombatRules.CombatantDefinition definition = rules.combatant(combatant.actorId());
        DoomCombatRules.EnemyBehavior behavior = definition.behavior();
        EnemyRuntime runtime = enemyRuntimes.get(index);
        Perception perception = perception(combatant, player, behavior);
        if (!runtime.alerted && !perception.visible()) {
            return combatant;
        }
        if (!runtime.alerted) {
            alert(combatant, behavior, runtime, events);
        }
        if (perception.visible()) {
            runtime.remember(player.x(), player.z());
        }
        runtime.elapse(FIXED_STEP_NANOS);
        if (perception.visible()
                && perception.distance() <= DoomUnits.toWorld(behavior.attackRange())
                && runtime.cooldownNanos == 0L) {
            return attack(combatant, behavior, runtime, events);
        }
        if (perception.visible()
                && perception.distance() <= DoomUnits.toWorld(behavior.preferredRange())) {
            return combatant.withActivity(DoomCombatantActivity.ATTACKING);
        }
        return pursue(combatant, behavior, runtime);
    }

    /** Activates one enemy and starts its configured reaction delay. */
    private static void alert(
            DoomCombatantState combatant,
            DoomCombatRules.EnemyBehavior behavior,
            EnemyRuntime runtime,
            List<DoomCombatEvent> events) {
        runtime.alerted = true;
        runtime.cooldownNanos = millisecondsToNanos(behavior.reactionMilliseconds());
        events.add(event(DoomCombatEvent.Type.COMBATANT_ALERTED, combatant.thingIndex(), 0));
    }

    /** Performs a ready ranged attack or waits in the attacking activity. */
    private DoomCombatantState attack(
            DoomCombatantState combatant,
            DoomCombatRules.EnemyBehavior behavior,
            EnemyRuntime runtime,
            List<DoomCombatEvent> events) {
        events.add(event(DoomCombatEvent.Type.COMBATANT_ATTACKED, combatant.thingIndex(), 0));
        damagePlayer(enemyDamage(behavior), events);
        runtime.cooldownNanos = millisecondsToNanos(behavior.attackIntervalMilliseconds());
        return combatant.withActivity(DoomCombatantActivity.ATTACKING);
    }

    /** Moves one alerted enemy toward its most recently visible player position. */
    private DoomCombatantState pursue(
            DoomCombatantState combatant,
            DoomCombatRules.EnemyBehavior behavior,
            EnemyRuntime runtime) {
        float deltaX = runtime.targetX - combatant.x();
        float deltaZ = runtime.targetZ - combatant.z();
        float remaining = (float) Math.hypot(deltaX, deltaZ);
        if (remaining <= INTERSECTION_TOLERANCE) {
            return combatant.withActivity(DoomCombatantActivity.PURSUING);
        }
        float distance = Math.min(
                DoomUnits.toWorld(behavior.moveSpeed()) * FIXED_STEP_SECONDS, remaining);
        float scale = distance / remaining;
        DoomCollisionWorld.Position position = collision.moveActor(
                combatant.x(),
                combatant.z(),
                deltaX * scale,
                deltaZ * scale,
                combatant.radius(),
                combatant.height(),
                ENEMY_MAXIMUM_STEP);
        return combatant.withPose(
                position.x(),
                position.floorHeight(),
                position.z(),
                DoomCombatantActivity.PURSUING);
    }

    /** Computes visible range and map occlusion between one enemy and the player. */
    private Perception perception(
            DoomCombatantState combatant,
            DoomPlayerState player,
            DoomCombatRules.EnemyBehavior behavior) {
        float deltaX = player.x() - combatant.x();
        float deltaZ = player.z() - combatant.z();
        float distance = (float) Math.hypot(deltaX, deltaZ);
        if (distance > DoomUnits.toWorld(behavior.sightRange())) {
            return new Perception(false, distance);
        }
        if (distance <= INTERSECTION_TOLERANCE) {
            return new Perception(true, distance);
        }
        float eyeHeight = combatant.floorHeight() + combatant.height() * 0.75F;
        Ray sight = Ray.between(
                combatant.x(), eyeHeight, combatant.z(), player.x(), player.eyeHeight(), player.z());
        float wallDistance = nearestWallDistance(sight, distance);
        return new Perception(wallDistance + INTERSECTION_TOLERANCE >= distance, distance);
    }

    /** Selects the nearest living actor intersection not hidden behind map geometry. */
    private Target closestTarget(
            DoomPlayerState shooter, DoomCombatRules.WeaponDefinition weapon) {
        float range = DoomUnits.toWorld(weapon.range());
        Ray ray = Ray.from(shooter);
        Target target = closestTarget(ray, range);
        if (target != null) {
            return target;
        }
        target = closestAutoAimTarget(shooter, range, shooter.yawRadians());
        if (target != null) {
            return target;
        }
        target = closestAutoAimTarget(
                shooter, range, shooter.yawRadians() + AUTO_AIM_ANGLE);
        return target != null
                ? target
                : closestAutoAimTarget(
                        shooter, range, shooter.yawRadians() - AUTO_AIM_ANGLE);
    }

    /** Selects the nearest living actor intersected by one fully specified ray. */
    private Target closestTarget(Ray ray, float range) {
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

    /** Selects the nearest visible actor reached by one classic horizontal auto-aim probe. */
    private Target closestAutoAimTarget(
            DoomPlayerState shooter, float range, float yaw) {
        Target nearest = null;
        for (int index = 0; index < combatants.size(); index++) {
            DoomCombatantState combatant = combatants.get(index);
            if (combatant.status() == DoomCombatantStatus.ALIVE) {
                Ray ray = autoAimRay(shooter, combatant, yaw);
                float distance = combatantDistance(ray, combatant, range);
                float wallDistance = nearestWallDistance(ray, range);
                if (distance < wallDistance
                        && (nearest == null || distance < nearest.distance())) {
                    nearest = new Target(index, distance);
                }
            }
        }
        return nearest;
    }

    /** Aims toward a target's vertical center within Doom's classic aiming slope window. */
    private static Ray autoAimRay(
            DoomPlayerState shooter, DoomCombatantState combatant, float yaw) {
        float distance = (float) Math.hypot(
                combatant.x() - shooter.x(), combatant.z() - shooter.z());
        float targetHeight = combatant.floorHeight() + combatant.height() * 0.5F;
        float slope = distance < INTERSECTION_TOLERANCE
                ? 0.0F
                : Math.clamp(
                        (targetHeight - shooter.eyeHeight()) / distance,
                        -AUTO_AIM_MAXIMUM_SLOPE,
                        AUTO_AIM_MAXIMUM_SLOPE);
        return Ray.from(shooter, yaw, slope);
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
        float startX = DoomUnits.toWorld(start.x());
        float startZ = DoomUnits.yToWorldZ(start.y());
        float segmentX = DoomUnits.deltaToWorld(end.x(), start.x());
        float segmentZ = DoomUnits.deltaToWorld(start.y(), end.y());
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
        float openingBottom =
                DoomUnits.toWorld(Math.max(right.floorHeight(), left.floorHeight()));
        float openingTop =
                DoomUnits.toWorld(Math.min(right.ceilingHeight(), left.ceilingHeight()));
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

    /** Applies incoming player damage unless death is already terminal. */
    private void damagePlayer(int damage, List<DoomCombatEvent> events) {
        if (playerHealth == 0) {
            return;
        }
        int previousHealth = playerHealth;
        long remainingHealth = (long) playerHealth - damage;
        playerHealth = (int) Math.clamp(remainingHealth, 0L, rules.maximumHealth());
        int appliedDamage = previousHealth - playerHealth;
        events.add(event(
                DoomCombatEvent.Type.PLAYER_DAMAGED,
                DoomCombatEvent.PLAYER,
                appliedDamage));
        if (playerHealth == 0) {
            events.add(event(DoomCombatEvent.Type.PLAYER_KILLED, DoomCombatEvent.PLAYER, 0));
        }
    }

    /** Rolls one deterministic configured damage value. */
    private int weaponDamage(DoomCombatRules.WeaponDefinition weapon) {
        return weapon.damageMinimum()
                + random.nextInt(weapon.damageValueCount()) * weapon.damageStep();
    }

    /** Rolls one deterministic configured enemy hitscan damage value. */
    private int enemyDamage(DoomCombatRules.EnemyBehavior behavior) {
        DoomCombatRules.DamageDefinition damage = behavior.damage();
        return damage.minimum()
                + random.nextInt(behavior.damageValueCount()) * damage.step();
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

    /** Converts positive provider-authored milliseconds to exact nanoseconds. */
    private static long millisecondsToNanos(int milliseconds) {
        return Math.multiplyExact(milliseconds, 1_000_000L);
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
            return from(
                    player,
                    player.yawRadians(),
                    (float) Math.tan(player.pitchRadians()));
        }

        /** Creates a ray from one player position with an explicit yaw and vertical slope. */
        private static Ray from(DoomPlayerState player, float yaw, float verticalSlope) {
            return new Ray(
                    player.x(),
                    player.eyeHeight(),
                    player.z(),
                    (float) Math.cos(yaw),
                    -(float) Math.sin(yaw),
                    verticalSlope);
        }

        /** Creates a ray from one world point toward another. */
        private static Ray between(
                float startX,
                float startHeight,
                float startZ,
                float endX,
                float endHeight,
                float endZ) {
            float deltaX = endX - startX;
            float deltaZ = endZ - startZ;
            float distance = (float) Math.hypot(deltaX, deltaZ);
            return new Ray(
                    startX,
                    startHeight,
                    startZ,
                    deltaX / distance,
                    deltaZ / distance,
                    (endHeight - startHeight) / distance);
        }
    }

    /** Selected combatant list slot and first ray-intersection distance. */
    private record Target(int index, float distance) {}

    /** Visible-range result used to choose one behavior branch. */
    private record Perception(boolean visible, float distance) {}

    /** One provider-configured collectable at its resolved map placement. */
    private record Pickup(
            int thingIndex,
            float x,
            float floorHeight,
            float z,
            float radius,
            DoomCombatRules.PickupDefinition definition) {}

    /** Mutable internal enemy timing and last-known-target state. */
    private static final class EnemyRuntime {
        private boolean alerted;
        private float targetX;
        private float targetZ;
        private long cooldownNanos;

        /** Retains the latest position observed with clear line of sight. */
        private void remember(float x, float z) {
            targetX = x;
            targetZ = z;
        }

        /** Reduces a finite countdown without passing zero. */
        private void elapse(long elapsedNanos) {
            cooldownNanos = Math.clamp(cooldownNanos - elapsedNanos, 0L, cooldownNanos);
        }
    }
}
