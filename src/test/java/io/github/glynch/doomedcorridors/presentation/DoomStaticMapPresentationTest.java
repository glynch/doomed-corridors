/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.material.DoomMaterial;
import io.github.glynch.doomedcorridors.material.RgbaImage;
import io.github.glynch.doomedcorridors.world.DoomMeshData;
import io.github.glynch.doomedcorridors.world.DoomPlayerStart;
import io.github.glynch.doomedcorridors.world.DoomStaticGeometry;
import io.github.glynch.doomedcorridors.world.DoomSurface;
import io.github.glynch.jscene3d.geometries.BufferGeometry;
import io.github.glynch.jscene3d.objects.Mesh;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Specifies live sector-ceiling updates through the map-presentation seam. */
final class DoomStaticMapPresentationTest {
    /** Moves a door ceiling plane and the lower edge of its surrounding upper wall. */
    @Test
    void appliesMovingSectorCeilingToBoundSurfaces() {
        DoomStaticGeometry geometry = new DoomStaticGeometry(
                List.of(ceiling(), upperWall()),
                new DoomPlayerStart(0.0F, 1.0F, 0.0F, 0.0F));

        try (DoomStaticMapPresentation presentation =
                DoomStaticMapPresentation.create(geometry, materials())) {
            presentation.setSectorCeilingHeight(1, 3.0F);

            BufferGeometry ceilingGeometry = ((Mesh) presentation.root().children().get(0)).geometry();
            BufferGeometry wallGeometry = ((Mesh) presentation.root().children().get(1)).geometry();
            assertThat(ceilingGeometry.attribute(BufferGeometry.POSITION))
                    .isNotNull()
                    .satisfies(positions -> assertThat(positions.toArray())
                            .containsExactly(0.0F, 3.0F, 0.0F, 1.0F, 3.0F, 0.0F, 0.0F, 3.0F, 1.0F));
            assertThat(wallGeometry.attribute(BufferGeometry.POSITION))
                    .isNotNull()
                    .satisfies(positions -> {
                        assertThat(positions.value(0, 1)).isEqualTo(3.0F);
                        assertThat(positions.value(1, 1)).isEqualTo(3.0F);
                        assertThat(positions.value(2, 1)).isEqualTo(4.0F);
                        assertThat(positions.value(3, 1)).isEqualTo(4.0F);
                    });
        }
    }

    private static DoomSurface ceiling() {
        return new DoomSurface(
                DoomSurface.Type.CEILING,
                "CEILING",
                1,
                new DoomMeshData(
                        new float[] {0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F},
                        new float[] {0.0F, -1.0F, 0.0F, 0.0F, -1.0F, 0.0F, 0.0F, -1.0F, 0.0F},
                        new float[] {0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F},
                        new int[] {0, 1, 2}),
                OptionalInt.of(1));
    }

    private static DoomSurface upperWall() {
        return new DoomSurface(
                DoomSurface.Type.UPPER_WALL,
                "DOOR",
                0,
                new DoomMeshData(
                        new float[] {
                            0.0F, 0.0F, 0.0F,
                            1.0F, 0.0F, 0.0F,
                            1.0F, 4.0F, 0.0F,
                            0.0F, 4.0F, 0.0F
                        },
                        new float[] {
                            0.0F, 0.0F, 1.0F,
                            0.0F, 0.0F, 1.0F,
                            0.0F, 0.0F, 1.0F,
                            0.0F, 0.0F, 1.0F
                        },
                        new float[] {0.0F, 1.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F},
                        new int[] {0, 1, 2, 0, 2, 3}),
                OptionalInt.of(1));
    }

    private static DoomMapMaterials materials() {
        return new DoomMapMaterials(
                "MAP01",
                Map.of("DOOR", material("DOOR", DoomMaterial.Kind.WALL_TEXTURE, 32, 32)),
                Map.of("CEILING", material("CEILING", DoomMaterial.Kind.FLAT, 64, 64)));
    }

    private static DoomMaterial material(
            String name, DoomMaterial.Kind kind, int width, int height) {
        byte[] pixels = new byte[width * height * 4];
        java.util.Arrays.fill(pixels, (byte) 0xff);
        return new DoomMaterial(name, kind, new RgbaImage(width, height, pixels), List.of());
    }
}
