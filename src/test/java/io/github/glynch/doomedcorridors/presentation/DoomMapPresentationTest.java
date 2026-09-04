/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.glynch.doomedcorridors.actor.DoomActor;
import io.github.glynch.doomedcorridors.actor.DoomActorCategory;
import io.github.glynch.doomedcorridors.actor.DoomActorDefinition;
import io.github.glynch.doomedcorridors.actor.DoomActorSprite;
import io.github.glynch.doomedcorridors.actor.DoomActorSprites;
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
import io.github.glynch.jscene3d.objects.Billboard;
import io.github.glynch.jscene3d.objects.BillboardAlignment;
import io.github.glynch.jscene3d.objects.Mesh;
import io.github.glynch.jscene3d.textures.Texture;
import io.github.glynch.jscene3d.textures.TextureCoordinateOrigin;
import io.github.glynch.jscene3d.textures.TextureWrap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

/** Specifies the adapter from headless Doom geometry to owned JScene3D resources. */
final class DoomMapPresentationTest {
    /** Creates a scene mesh and WAD-oriented camera without initializing native rendering. */
    @Test
    void createsStaticMapMeshAndPlayerCamera() {
        DoomStaticGeometry geometry = new DoomStaticGeometry(
                List.of(maskedWall()), new DoomPlayerStart(1.0F, 2.0F, 3.0F, 0.0F));

        try (DoomMapPresentation presentation = DoomMapPresentation.create(
                geometry, materials(), List.of(), new DoomActorSprites(Map.of()), 16.0F / 9.0F)) {
            assertThat(presentation.scene().children()).hasSize(1);
            assertThat(presentation.scene().children().getFirst()).isInstanceOf(Mesh.class);
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
        }
    }

    /** Creates an upright, grounded cylindrical billboard for an imported actor sprite. */
    @Test
    void createsGroundedActorBillboard() {
        DoomStaticGeometry geometry =
                new DoomStaticGeometry(List.of(), new DoomPlayerStart(0.0F, 1.0F, 0.0F, 0.0F));

        try (DoomMapPresentation presentation =
                DoomMapPresentation.create(geometry, materials(), List.of(actor()), sprites(), 1.0F)) {
            assertThat(presentation.scene().children()).hasSize(1);
            Billboard billboard = (Billboard) presentation.scene().children().getFirst();
            assertThat(billboard.alignment()).isEqualTo(BillboardAlignment.CYLINDRICAL);
            assertThat(billboard.position()).satisfies(position -> {
                assertThat(position.x()).isEqualTo(2.0F);
                assertThat(position.y()).isEqualTo(0.5F);
                assertThat(position.z()).isEqualTo(-1.0F);
            });
            assertThat(billboard.anchor()).satisfies(anchor -> {
                assertThat(anchor.x()).isEqualTo(0.25F);
                assertThat(anchor.y()).isZero();
            });
            assertThat(billboard.scale()).satisfies(scale -> {
                assertThat(scale.x()).isEqualTo(4.0F / 32.0F);
                assertThat(scale.y()).isEqualTo(6.0F / 32.0F);
            });
            assertThat(billboard.material().alphaMode()).isEqualTo(AlphaMode.MASK);
            assertThat(billboard.material().colorMap().orElseThrow().coordinateOrigin())
                    .isEqualTo(TextureCoordinateOrigin.BOTTOM_LEFT);
            assertThat(billboard.material().colorMap().orElseThrow().horizontalWrap())
                    .isEqualTo(TextureWrap.CLAMP_TO_EDGE);
            assertThat(billboard.material().colorMap().orElseThrow().verticalWrap())
                    .isEqualTo(TextureWrap.CLAMP_TO_EDGE);
        }
    }

    /** Applies viewport and player-state changes to the presentation camera. */
    @Test
    void updatesCameraFromViewportAndPlayerState() {
        DoomStaticGeometry geometry =
                new DoomStaticGeometry(List.of(), new DoomPlayerStart(0.0F, 1.0F, 0.0F, 0.0F));

        try (DoomMapPresentation presentation = DoomMapPresentation.create(
                geometry, materials(), List.of(), new DoomActorSprites(Map.of()), 1.0F)) {

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
        DoomMapPresentation presentation = DoomMapPresentation.create(
                geometry, materials(), List.of(), new DoomActorSprites(Map.of()), 1.0F);
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
        return new DoomMapMaterials(
                "MAP01", Map.of("GRATE", grate), Map.of("FLOOR", floor));
    }

    private static DoomSurface maskedWall() {
        return new DoomSurface(
                DoomSurface.Type.MASKED_MIDDLE_WALL,
                "GRATE",
                0,
                new DoomMeshData(
                        new float[] {0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F},
                        new float[] {0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F},
                        new float[] {0.0F, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F},
                        new int[] {0, 1, 2}));
    }

    private static DoomActor actor() {
        DoomActorDefinition definition = new DoomActorDefinition(
                3004,
                "zombieman",
                "Zombieman",
                DoomActorCategory.ENEMY,
                Optional.of("POSSA"));
        return new DoomActor(4, definition, 2.0F, 0.5F, -1.0F, 0.0F);
    }

    private static DoomActorSprites sprites() {
        byte[] pixels = new byte[4 * 6 * 4];
        pixels[0] = (byte) 0xff;
        pixels[3] = (byte) 0xff;
        DoomActorSprite sprite = new DoomActorSprite(
                "POSSA", "POSSA1", new RgbaImage(4, 6, pixels), 1, 6, List.of());
        return new DoomActorSprites(Map.of("POSSA", sprite));
    }

    private static org.assertj.core.data.Offset<Float> within() {
        return org.assertj.core.data.Offset.offset(0.000_01F);
    }
}
