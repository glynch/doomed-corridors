/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.jscene3d.doom.geometry.DoomUnits;
import io.github.glynch.jscene3d.doom.map.DoomMap;
import io.github.glynch.jscene3d.doom.map.DoomMapDecoder;
import io.github.glynch.jscene3d.physics.CharacterController;
import io.github.glynch.jscene3d.physics.KinematicBody;
import io.github.glynch.jscene3d.physics.PhysicsWorld;
import io.github.glynch.jscene3d.physics.movement.CharacterControllerSettings;
import io.github.glynch.jscene3d.physics.movement.KinematicMoveSettings;
import io.github.glynch.jscene3d.physics.shapes.CapsuleShape;
import io.github.glynch.jscene3d.physics.shapes.TriangleMeshShape;
import io.github.glynch.jscene3d.project.component.ComponentId;
import io.github.glynch.jscene3d.project.entity.EntityId;
import io.github.glynch.jscene3d.project.physics3d.CollisionShape3d;
import io.github.glynch.jscene3d.project.physics3d.TriangleMeshCollisionShape3dResource;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.HostedProject;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntimeHost;
import io.github.glynch.jscene3d.wad.WadArchive;
import io.github.glynch.jscene3d.wad.WadLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises authored Doom step limits against the collision mesh published from real MAP01 geometry. */
final class Map01StepTraversalTest {
    private static final String ENGINE_VERSION = "0.1.0-SNAPSHOT";
    private static final String IMPORT_ID = "freedoom-map01";
    private static final String FREEDOOM_SHA256 = "a8772e088847032510d97ba2312406a6998f21cbab44d4ff10696faa9c0ecd4b";
    private static final EntityId MAP_PLACEMENT = EntityId.from("9107e22b-adc5-4449-bd08-0e2066f50563");
    private static final ComponentId STATIC_COLLISION_SHAPE = componentId("maps/MAP01/root/collision/static-shape");
    private static final Path PROJECT_ROOT = Path.of(".").toAbsolutePath().normalize();
    private static final Quaternionf IDENTITY = new Quaternionf();
    private static final float CAPSULE_RADIUS = DoomUnits.toWorld(14.0F);
    private static final float CAPSULE_SEGMENT_LENGTH = DoomUnits.toWorld(24.0F);
    private static final float CAPSULE_HALF_HEIGHT = CAPSULE_RADIUS + CAPSULE_SEGMENT_LENGTH * 0.5F;
    private static final Duration FIXED_STEP = Duration.ofNanos(8_333_333L);
    private static final float FIXED_SECONDS = FIXED_STEP.toNanos() / 1_000_000_000.0F;

    @TempDir
    private Path temporaryDirectory;

    /** Traverses MAP01's 16-unit staircase while rejecting a real 24-unit ledge with the same exact limit. */
    @Test
    void honorsAuthoredMap01StepBoundary() {
        Path cache = temporaryDirectory.resolve("import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);

        try (HostedProject loaded = load(cache)) {
            TriangleMeshCollisionShape3dResource collision = collisionResource(loaded);
            DoomMap map = map01();
            PhysicsWorld world = collisionWorld(collision);
            float maximumStepHeight = DoomUnits.toWorld(16.0F);
            StepPortal staircase = stepPortal(map, 88);
            float usableHalfWidth = staircase.halfWidth() - CAPSULE_RADIUS * 1.1F;
            List<Float> blockedOffsets = new ArrayList<>();

            for (float offsetFactor : List.of(-1.0F, -0.5F, 0.0F, 0.5F, 1.0F)) {
                float offset = usableHalfWidth * offsetFactor;
                if (!traversesStaircase(world, staircase.withWidthOffset(offset), maximumStepHeight)) {
                    blockedOffsets.add(offsetFactor);
                }
            }
            assertThat(blockedOffsets)
                    .as("width offsets blocked on four consecutive real MAP01 16-unit steps")
                    .isEmpty();
            StepPortal lowLedge = stepPortal(map, 36);
            assertThat(traversesPortal(world, lowLedge, maximumStepHeight))
                    .as("real MAP01 24-unit ledge with a 16-unit limit")
                    .isFalse();
            assertThat(traversesPortal(world, lowLedge, DoomUnits.toWorld(24.0F)))
                    .as("same 24-unit ledge with an exact 24-unit limit")
                    .isTrue();
        }
    }

    /** Traverses every width sample of the four 16-unit risers beyond moving floor sector 34. */
    @Test
    void traversesMovingFloorStaircaseAcrossItsUsableWidth() {
        Path cache = temporaryDirectory.resolve("moving-floor-staircase-import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);

        try (HostedProject loaded = load(cache)) {
            DoomMap map = map01();
            assertThat(List.of(214, 216, 189, 191).stream()
                            .map(index -> stepHeight(map, index))
                            .toList())
                    .containsExactly(16, 16, 16, 16);
            PhysicsWorld world = collisionWorld(collisionResource(loaded));
            StepPortal firstRiser = stepPortal(map, 214);
            float usableHalfWidth = firstRiser.halfWidth() - CAPSULE_RADIUS * 1.1F;
            List<Float> blockedOffsets = new ArrayList<>();

            for (float offsetFactor : List.of(-1.0F, -0.5F, 0.0F, 0.5F, 1.0F)) {
                float offset = usableHalfWidth * offsetFactor;
                if (!traversesMovingFloorStaircase(
                        world, firstRiser.withWidthOffset(offset), DoomUnits.toWorld(16.0F))) {
                    blockedOffsets.add(offsetFactor);
                }
            }

            assertThat(blockedOffsets)
                    .as("width offsets blocked on the four 16-unit risers beyond moving floor sector 34")
                    .isEmpty();
        }
    }

    /** Returns up the right side of the moving-floor staircase using the game's 25 ms fixed step. */
    @Test
    void returnsUpMovingFloorStaircaseAtGameplayFixedStep() {
        Path cache = temporaryDirectory.resolve("moving-floor-staircase-round-trip-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);

        try (HostedProject loaded = load(cache)) {
            PhysicsWorld world = collisionWorld(collisionResource(loaded));
            KinematicBody body = character(world, new Vector3f(36.3F, -1.125F, -4.5F));
            CharacterController controller = controller(world, body, DoomUnits.toWorld(16.0F));
            float gameplaySeconds = 0.025F;
            try {
                settle(controller, gameplaySeconds);
                for (int update = 0; update < 40; update++) {
                    controller.move(new Vector3f(0.0F, 0.0F, 8.0F), gameplaySeconds);
                }
                assertThat(body.position(new Vector3f()).z).isGreaterThan(2.0F);

                for (int update = 0; update < 50; update++) {
                    controller.move(new Vector3f(0.0F, 0.0F, -8.0F), gameplaySeconds);
                }

                Vector3f returned = body.position(new Vector3f());
                assertThat(returned.z).isLessThan(-4.0F);
                assertThat(returned.y).isGreaterThan(-1.2F);
            } finally {
                world.remove(body);
            }
        }
    }

    /** Moves continuously across the four east-west staircase sectors beginning at the supplied first riser. */
    private static boolean traversesStaircase(PhysicsWorld world, StepPortal firstStep, float maximumStepHeight) {
        KinematicBody body = character(world, firstStep.lowerPosition());
        CharacterController controller = controller(world, body, maximumStepHeight);
        try {
            settle(controller);
            for (int update = 0; update < 240; update++) {
                controller.move(new Vector3f(-8.0F, 0.0F, 0.0F), FIXED_SECONDS);
                if (body.position(new Vector3f()).x < -1.5F) {
                    return body.position(new Vector3f()).y > CAPSULE_HALF_HEIGHT;
                }
            }
            return false;
        } finally {
            world.remove(body);
        }
    }

    /** Moves from sector 36 through four consecutive risers into sector 29. */
    private static boolean traversesMovingFloorStaircase(
            PhysicsWorld world, StepPortal firstRiser, float maximumStepHeight) {
        KinematicBody body = character(world, firstRiser.lowerPosition());
        CharacterController controller = controller(world, body, maximumStepHeight);
        try {
            settle(controller);
            Vector3f velocity = new Vector3f(firstRiser.direction()).mul(8.0F);
            float finalLandingDistance = DoomUnits.toWorld(192.0F) + CAPSULE_RADIUS * 0.5F;
            float finalCenterHeight = firstRiser.lowerFloor() + DoomUnits.toWorld(64.0F) + CAPSULE_HALF_HEIGHT;
            for (int update = 0; update < 240; update++) {
                controller.move(velocity, FIXED_SECONDS);
                Vector3f position = body.position(new Vector3f());
                if (firstRiser.signedDistance(position) > finalLandingDistance) {
                    return position.y > finalCenterHeight - 0.05F;
                }
            }
            return false;
        } finally {
            world.remove(body);
        }
    }

    /** Attempts one perpendicular portal crossing using the supplied authored limit. */
    private static boolean traversesPortal(PhysicsWorld world, StepPortal portal, float maximumStepHeight) {
        KinematicBody body = character(world, portal.lowerPosition());
        CharacterController controller = controller(world, body, maximumStepHeight);
        try {
            settle(controller);
            Vector3f velocity = new Vector3f(portal.direction()).mul(8.0F);
            for (int update = 0; update < 20; update++) {
                controller.move(velocity, FIXED_SECONDS);
                if (portal.signedDistance(body.position(new Vector3f())) > CAPSULE_RADIUS * 0.5F) {
                    return body.position(new Vector3f()).y > portal.lowerFloor() + CAPSULE_HALF_HEIGHT + 0.25F;
                }
            }
            return false;
        } finally {
            world.remove(body);
        }
    }

    /** Adds one Doom-sized capsule at an independently selected source-semantic approach point. */
    private static KinematicBody character(PhysicsWorld world, Vector3f position) {
        KinematicBody body = world.addKinematicBody(position, IDENTITY);
        body.addCollider(new CapsuleShape(CAPSULE_RADIUS, CAPSULE_SEGMENT_LENGTH));
        return body;
    }

    /** Creates the production character controller using the selected exact gameplay limit. */
    private static CharacterController controller(PhysicsWorld world, KinematicBody body, float maximumStepHeight) {
        KinematicMoveSettings movement = KinematicMoveSettings.DEFAULT
                .withMaximumStepHeight(maximumStepHeight)
                .withGroundSnapDistance(DoomUnits.toWorld(8.0F));
        return new CharacterController(
                world,
                body,
                CharacterControllerSettings.DEFAULT
                        .withMovementSettings(movement)
                        .withGravity(18.0F)
                        .withJumpSpeed(0.0F));
    }

    /** Grounds a newly placed character before applying horizontal movement. */
    private static void settle(CharacterController controller) {
        settle(controller, FIXED_SECONDS);
    }

    /** Grounds a newly placed character using one selected fixed-step duration. */
    private static void settle(CharacterController controller, float fixedSeconds) {
        for (int update = 0; update < 15; update++) {
            controller.move(new Vector3f(), fixedSeconds);
        }
    }

    /** Creates the immutable MAP01 static collision shared by independent character attempts. */
    private static PhysicsWorld collisionWorld(TriangleMeshCollisionShape3dResource collision) {
        PhysicsWorld world = new PhysicsWorld();
        world.addStaticBody(new Vector3f(), IDENTITY)
                .addCollider(new TriangleMeshShape(positions(collision), indices(collision)));
        return world;
    }

    /** Resolves one known open two-sided MAP01 boundary into an upward engine-space crossing. */
    private static StepPortal stepPortal(DoomMap map, int linedefIndex) {
        DoomMap.Linedef linedef = map.linedefs().get(linedefIndex);
        DoomMap.Sector right =
                map.sectors().get(map.sidedefs().get(linedef.rightSidedef()).sector());
        DoomMap.Sector left =
                map.sectors().get(map.sidedefs().get(linedef.leftSidedef()).sector());
        int difference = left.floorHeight() - right.floorHeight();
        DoomMap.Vertex first = map.vertices().get(linedef.startVertex());
        DoomMap.Vertex second = map.vertices().get(linedef.endVertex());
        float deltaX = DoomUnits.deltaToWorld(second.x(), first.x());
        float deltaZ = -DoomUnits.deltaToWorld(second.y(), first.y());
        float length = (float) Math.hypot(deltaX, deltaZ);
        Vector3f leftNormal = new Vector3f(deltaZ, 0.0F, -deltaX).normalize();
        Vector3f direction = difference > 0 ? leftNormal : new Vector3f(leftNormal).negate();
        float lowerFloor = DoomUnits.toWorld(Math.min(left.floorHeight(), right.floorHeight()));
        Vector3f midpoint = new Vector3f(
                DoomUnits.toWorld((first.x() + second.x()) * 0.5F),
                lowerFloor + CAPSULE_HALF_HEIGHT + 0.001F,
                DoomUnits.yToWorldZ((first.y() + second.y()) * 0.5));
        return new StepPortal(
                new Vector3f(midpoint).fma(-(CAPSULE_RADIUS + 0.1F), direction),
                direction,
                midpoint,
                lowerFloor,
                new Vector3f(deltaX, 0.0F, deltaZ).div(length),
                length * 0.5F);
    }

    /** Returns one two-sided linedef's absolute source-authored floor-height difference. */
    private static int stepHeight(DoomMap map, int linedefIndex) {
        DoomMap.Linedef linedef = map.linedefs().get(linedefIndex);
        DoomMap.Sector right =
                map.sectors().get(map.sidedefs().get(linedef.rightSidedef()).sector());
        DoomMap.Sector left =
                map.sectors().get(map.sidedefs().get(linedef.leftSidedef()).sector());
        return Math.abs(left.floorHeight() - right.floorHeight());
    }

    /** Copies every published collision vertex into the backend's flattened representation. */
    private static float[] positions(TriangleMeshCollisionShape3dResource collision) {
        float[] positions = new float[collision.vertexCount() * 3];
        Vector3f vertex = new Vector3f();
        for (int index = 0; index < collision.vertexCount(); index++) {
            collision.vertex(index, vertex);
            int offset = index * 3;
            positions[offset] = vertex.x;
            positions[offset + 1] = vertex.y;
            positions[offset + 2] = vertex.z;
        }
        return positions;
    }

    /** Copies the published flattened triangle-index sequence. */
    private static int[] indices(TriangleMeshCollisionShape3dResource collision) {
        int[] indices = new int[collision.triangleCount() * 3];
        for (int index = 0; index < indices.length; index++) {
            indices[index] = collision.index(index);
        }
        return indices;
    }

    /** Resolves the imported map collision component from the composed placement. */
    private static TriangleMeshCollisionShape3dResource collisionResource(HostedProject loaded) {
        Entity placement = loaded.world().roots().stream()
                .filter(entity -> entity.authoredId().equals(MAP_PLACEMENT))
                .findFirst()
                .orElseThrow();
        CollisionShape3d shape = placement
                .component(STATIC_COLLISION_SHAPE, CollisionShape3d.class)
                .orElseThrow();
        return (TriangleMeshCollisionShape3dResource) shape.resource();
    }

    /** Decodes the pinned real MAP01 source used by the publication under test. */
    private static DoomMap map01() {
        WadArchive archive = WadLoader.load(Path.of("assets/freedoom2.wad"), FREEDOOM_SHA256)
                .archive()
                .orElseThrow();
        return new DoomMapDecoder().decode(archive, "MAP01").map().orElseThrow();
    }

    /** Loads the authored project through the generic project host. */
    private HostedProject load(Path cache) {
        ProjectRuntimeHost host = new ProjectRuntimeHost(
                ENGINE_VERSION,
                Map01StepTraversalTest.class.getClassLoader(),
                new TestProjectEnvironment(cache, new TestPresentationWorldModule()));
        return host.loadEntry(PROJECT_ROOT);
    }

    /** Reproduces the importer's stable source-derived component identity contract. */
    private static ComponentId componentId(String locator) {
        UUID id = UUID.nameUUIDFromBytes((IMPORT_ID + ':' + locator).getBytes(StandardCharsets.UTF_8));
        return new ComponentId(id);
    }

    /** One source-semantic upward crossing and its signed upper-side half-plane. */
    private record StepPortal(
            Vector3f lowerPosition,
            Vector3f direction,
            Vector3f midpoint,
            float lowerFloor,
            Vector3f widthDirection,
            float halfWidth) {
        private float signedDistance(Vector3f position) {
            return new Vector3f(position).sub(midpoint).dot(direction);
        }

        private StepPortal withWidthOffset(float offset) {
            return new StepPortal(
                    new Vector3f(lowerPosition).fma(offset, widthDirection),
                    direction,
                    new Vector3f(midpoint).fma(offset, widthDirection),
                    lowerFloor,
                    widthDirection,
                    halfWidth);
        }
    }
}
