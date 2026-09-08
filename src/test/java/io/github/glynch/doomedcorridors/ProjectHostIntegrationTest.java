/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.glynch.jscene3d.project.asset.AssetId;
import io.github.glynch.jscene3d.project.component.ComponentId;
import io.github.glynch.jscene3d.project.desktop.StandardProjectEnvironment;
import io.github.glynch.jscene3d.project.entity.EntityId;
import io.github.glynch.jscene3d.project.physics3d.CollisionRaycastHit3d;
import io.github.glynch.jscene3d.project.physics3d.Physics3dWorldModule;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.EntityInstantiationKind;
import io.github.glynch.jscene3d.project.runtime.HostedProject;
import io.github.glynch.jscene3d.project.runtime.ProjectHost;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntimeHost;
import io.github.glynch.jscene3d.project.spatial3d.Material3dResource;
import io.github.glynch.jscene3d.project.spatial3d.Mesh3dResource;
import io.github.glynch.jscene3d.project.spatial3d.MeshRenderer3d;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dWorldModule;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises MAP01 publication and composition through the generic project-host boundary. */
final class ProjectHostIntegrationTest {
    private static final String ENGINE_VERSION = "0.1.0-SNAPSHOT";
    private static final String IMPORT_ID = "freedoom-map01";
    private static final AssetId MAP_DEFINITION = AssetId.from("15a64477-b57f-3ae3-bf65-33cd6baab7b6");
    private static final EntityId MAP_PLACEMENT = EntityId.from("9107e22b-adc5-4449-bd08-0e2066f50563");
    private static final ComponentId FIRST_RENDERER = componentId("maps/MAP01/root/mesh-renderers/00000");
    private static final ComponentId STATIC_COLLISION_SHAPE = componentId("maps/MAP01/root/collision/static-shape");
    private static final Path PROJECT_ROOT = Path.of(".").toAbsolutePath().normalize();

    @TempDir
    private Path temporaryDirectory;

    /** Publishes MAP01, resolves its generated definition, and composes all resources into a world. */
    @Test
    void composesPublishedMapDefinitionAndReleasesResources() {
        Path cache = temporaryDirectory.resolve("import-cache");
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);
        DoomedCorridorsContentPublisher.publish(PROJECT_ROOT, cache);
        MeshRenderer3d renderer;
        Mesh3dResource mesh;
        Material3dResource material;

        try (HostedProject loaded = load(cache)) {
            Entity placement = root(loaded, MAP_PLACEMENT);
            renderer = placement.component(FIRST_RENDERER, MeshRenderer3d.class).orElseThrow();
            mesh = renderer.mesh();
            material = renderer.material();

            assertThat(loaded.project().identity().id()).isEqualTo("io.github.glynch.doomed-corridors");
            assertThat(loaded.world().roots())
                    .extracting(entity -> entity.name().orElseThrow())
                    .containsExactly("Player Camera", "MAP01 Geometry");
            assertThat(placement.instantiationKind()).isEqualTo(EntityInstantiationKind.PLACEMENT);
            assertThat(placement.instantiatedDefinition()).contains(MAP_DEFINITION);
            assertThat(placement.componentIds()).hasSize(82);
            assertThat(renderer.isVisible()).isTrue();
            assertThat(mesh.isClosed()).isFalse();
            assertThat(material.isClosed()).isFalse();
            assertThat(loaded.world().requireModule(Physics3dWorldModule.class).collisionObjectCount())
                    .isOne();
            assertThat(loaded.world().requireModule(Physics3dWorldModule.class).collisionShapeCount())
                    .isOne();
            assertThat(loaded.world().requireModule(Spatial3dWorldModule.class).isReadyToRender())
                    .isFalse();

            loaded.world().activate();

            assertThat(loaded.world().requireModule(Spatial3dWorldModule.class).isReadyToRender())
                    .isTrue();
            Physics3dWorldModule physics = loaded.world().requireModule(Physics3dWorldModule.class);
            CollisionRaycastHit3d floor = physics.raycast(
                            new Vector3f(-6.0F, 1.28125F, 6.0F), new Vector3f(0.0F, -1.0F, 0.0F), 4.0F)
                    .orElseThrow();
            assertThat(floor.shape().componentId()).isEqualTo(STATIC_COLLISION_SHAPE);
            assertThat(floor.distance()).isCloseTo(1.28125F, within(1.0E-5F));
            assertThat(floor.point(new Vector3f()).y).isCloseTo(0.0F, within(1.0E-5F));
        }

        assertThat(renderer.isClosed()).isTrue();
        assertThat(mesh.isClosed()).isTrue();
        assertThat(material.isClosed()).isTrue();
    }

    /** Loads the authored project through the same generic host used by desktop exports. */
    private static HostedProject load(Path cache) {
        ProjectHost host = new ProjectRuntimeHost(
                ENGINE_VERSION,
                ProjectHostIntegrationTest.class.getClassLoader(),
                new StandardProjectEnvironment(cache));
        return host.load(PROJECT_ROOT);
    }

    /** Finds one authored world root by its stable placement identity. */
    private static Entity root(HostedProject loaded, EntityId authoredId) {
        return loaded.world().roots().stream()
                .filter(entity -> entity.authoredId().equals(authoredId))
                .findFirst()
                .orElseThrow();
    }

    /** Reproduces the importer's stable source-derived component identity contract. */
    private static ComponentId componentId(String locator) {
        UUID id = UUID.nameUUIDFromBytes((IMPORT_ID + ':' + locator).getBytes(StandardCharsets.UTF_8));
        return new ComponentId(id);
    }
}
