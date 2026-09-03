/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.material.DoomMaterial;
import io.github.glynch.doomedcorridors.material.RgbaImage;
import io.github.glynch.doomedcorridors.world.DoomMeshData;
import io.github.glynch.doomedcorridors.world.DoomPlayerStart;
import io.github.glynch.doomedcorridors.world.DoomPlayerState;
import io.github.glynch.doomedcorridors.world.DoomStaticGeometry;
import io.github.glynch.doomedcorridors.world.DoomSurface;
import io.github.glynch.jscene3d.geometries.BufferGeometry;
import io.github.glynch.jscene3d.materials.AlphaMode;
import io.github.glynch.jscene3d.materials.BasicMaterial;
import io.github.glynch.jscene3d.objects.Mesh;
import io.github.glynch.jscene3d.textures.Texture;
import io.github.glynch.jscene3d.textures.TextureCoordinateOrigin;
import io.github.glynch.jscene3d.textures.TextureWrap;
import java.util.List;
import java.util.Map;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Specifies the adapter from headless Doom geometry to owned JScene3D resources. */
final class DoomMapPresentationTest {
    /** Creates scene meshes and a WAD-oriented camera without initializing native rendering. */
    @Test
    void createsSceneAndPlayerCamera() {
        DoomSurface surface = new DoomSurface(
                DoomSurface.Type.MASKED_MIDDLE_WALL,
                "GRATE",
                0,
                new DoomMeshData(
                        new float[] {0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F},
                        new float[] {0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F},
                        new float[] {0.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F},
                        new int[] {0, 1, 2}));
        DoomStaticGeometry geometry =
                new DoomStaticGeometry(List.of(surface), new DoomPlayerStart(1.0F, 2.0F, 3.0F, 0.0F));

        try (DoomMapPresentation presentation =
                DoomMapPresentation.create(geometry, materials(), 16.0F / 9.0F)) {
            assertThat(presentation.scene().children()).singleElement().isInstanceOf(Mesh.class);
            assertThat(presentation.camera().position()).satisfies(position -> {
                assertThat(position.x()).isEqualTo(1.0F);
                assertThat(position.y()).isEqualTo(2.0F);
                assertThat(position.z()).isEqualTo(3.0F);
            });
            Vector3f forward = new Vector3f(0.0F, 0.0F, -1.0F)
                    .rotate(presentation.camera().quaternion());
            assertThat(forward.x()).isCloseTo(1.0F, within());
            assertThat(forward.y()).isCloseTo(0.0F, within());
            assertThat(forward.z()).isCloseTo(0.0F, within());
            assertThat(presentation.camera().aspectRatio()).isEqualTo(16.0F / 9.0F);

            Mesh mesh = (Mesh) presentation.scene().children().getFirst();
            BufferGeometry bufferGeometry = mesh.geometry();
            assertThat(bufferGeometry.attribute(BufferGeometry.POSITION)).isNotNull().satisfies(attribute -> {
                assertThat(attribute.count()).isEqualTo(3);
                assertThat(attribute.value(1, 0)).isEqualTo(1.0F);
            });
            BasicMaterial material = (BasicMaterial) mesh.material();
            assertThat(material.alphaMode()).isEqualTo(AlphaMode.MASK);
            Texture texture = material.colorMap().orElseThrow();
            assertThat(texture.coordinateOrigin()).isEqualTo(TextureCoordinateOrigin.TOP_LEFT);
            assertThat(texture.horizontalWrap()).isEqualTo(TextureWrap.REPEAT);
            assertThat(texture.verticalWrap()).isEqualTo(TextureWrap.REPEAT);

            presentation.resize(4.0F / 3.0F);
            assertThat(presentation.camera().aspectRatio()).isEqualTo(4.0F / 3.0F);

            presentation.applyPlayerState(new DoomPlayerState(4.0F, 5.0F, 6.0F, 0.0F, 0.25F));
            assertThat(presentation.camera().position()).satisfies(position -> {
                assertThat(position.x()).isEqualTo(4.0F);
                assertThat(position.y()).isEqualTo(5.0F);
                assertThat(position.z()).isEqualTo(6.0F);
            });
            Vector3f raisedForward = new Vector3f(0.0F, 0.0F, -1.0F)
                    .rotate(presentation.camera().quaternion());
            assertThat(raisedForward.x()).isCloseTo((float) Math.cos(0.25F), within());
            assertThat(raisedForward.y()).isCloseTo((float) Math.sin(0.25F), within());
            assertThat(raisedForward.z()).isCloseTo(0.0F, within());
        }
    }

    /** Closes every adapter-owned resource exactly through the presentation lifecycle. */
    @Test
    void closesOwnedResources() {
        DoomSurface surface = new DoomSurface(
                DoomSurface.Type.FLOOR,
                "FLOOR",
                0,
                new DoomMeshData(
                        new float[] {0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.0F},
                        new float[] {0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F},
                        new float[] {0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F},
                        new int[] {0, 1, 2}));
        DoomStaticGeometry geometry =
                new DoomStaticGeometry(List.of(surface), new DoomPlayerStart(0.0F, 1.0F, 0.0F, 0.0F));
        DoomMapPresentation presentation = DoomMapPresentation.create(geometry, materials(), 1.0F);
        Mesh mesh = (Mesh) presentation.scene().children().getFirst();
        BufferGeometry bufferGeometry = mesh.geometry();
        BasicMaterial material = (BasicMaterial) mesh.material();
        Texture texture = material.colorMap().orElseThrow();

        presentation.close();
        presentation.close();

        assertThat(bufferGeometry.isClosed()).isTrue();
        assertThat(material.isClosed()).isTrue();
        assertThat(texture.isClosed()).isTrue();
        assertThatThrownBy(presentation::scene).isInstanceOf(IllegalStateException.class);
    }

    private static DoomMapMaterials materials() {
        byte[] opaquePixels = {
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x00
        };
        DoomMaterial grate = new DoomMaterial(
                "GRATE", DoomMaterial.Kind.WALL_TEXTURE, new RgbaImage(2, 1, opaquePixels), List.of());
        DoomMaterial floor = new DoomMaterial(
                "FLOOR",
                DoomMaterial.Kind.FLAT,
                new RgbaImage(1, 1, new byte[] {(byte) 0xff, 0, 0, (byte) 0xff}),
                List.of());
        return new DoomMapMaterials(Map.of("GRATE", grate), Map.of("FLOOR", floor));
    }

    private static org.assertj.core.data.Offset<Float> within() {
        return org.assertj.core.data.Offset.offset(0.000_01F);
    }
}
