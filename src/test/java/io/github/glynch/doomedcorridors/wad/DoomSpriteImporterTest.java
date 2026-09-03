/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.wad;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.doomedcorridors.actor.DoomActor;
import io.github.glynch.doomedcorridors.actor.DoomActorCategory;
import io.github.glynch.doomedcorridors.actor.DoomActorDefinition;
import io.github.glynch.doomedcorridors.actor.DoomActorSprite;
import io.github.glynch.doomedcorridors.actor.DoomActorSprites;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Specifies map-scoped sprite import through the public WAD adapter seam. */
final class DoomSpriteImporterTest {
    @TempDir
    Path temporaryDirectory;

    /** Imports non-directional and rotation-one spawn frames with patch metadata. */
    @Test
    void importsRequiredSpawnFrames() throws IOException {
        byte[] palette = new byte[256 * 3];
        setColor(palette, 1, 10, 20, 30);
        WadArchive archive = writeAndLoad(
                new TestLump("PLAYPAL", palette),
                new TestLump("S_START", new byte[0]),
                new TestLump("ITEMA0", patch(2, 2, -3, 2)),
                new TestLump("FOOZA1", patch(2, 2, 1, 2)),
                new TestLump("S_END", new byte[0]));

        DoomSpriteImportResult result = new DoomSpriteImporter()
                .importActors(archive, List.of(actor(1, "item", "ITEMA"), actor(2, "fooz", "FOOZA")));

        assertThat(result.isValid()).isTrue();
        assertThat(result.diagnostics()).isEmpty();
        DoomActorSprites sprites = result.sprites().orElseThrow();
        assertThat(sprites.byFrame()).containsOnlyKeys("ITEMA", "FOOZA");
        assertThat(sprites.sprite("ITEMA")).hasValueSatisfying(sprite -> {
            assertThat(sprite.lumpName()).isEqualTo("ITEMA0");
            assertThat(sprite.leftOffset()).isEqualTo(-3);
            assertThat(sprite.topOffset()).isEqualTo(2);
            assertThat(sprite.image().rgba(0, 0)).isEqualTo(0x0a141eff);
            assertThat(sprite.sourceLumps()).extracting(WadLump::name).containsExactly("PLAYPAL", "ITEMA0");
        });
        assertThat(sprites.sprite("FOOZA")).map(DoomActorSprite::lumpName).contains("FOOZA1");
    }

    /** Keeps a partial sprite set and reports a missing referenced frame explicitly. */
    @Test
    void reportsMissingFrame() throws IOException {
        WadArchive archive = writeAndLoad(
                new TestLump("PLAYPAL", new byte[256 * 3]),
                new TestLump("S_START", new byte[0]),
                new TestLump("S_END", new byte[0]));

        DoomSpriteImportResult result =
                new DoomSpriteImporter().importActors(archive, List.of(actor(1, "missing", "MISSA")));

        assertThat(result.sprites()).hasValueSatisfying(sprites -> assertThat(sprites.byFrame()).isEmpty());
        assertThat(result.diagnostics()).singleElement().satisfies(diagnostic -> {
            assertThat(diagnostic.severity()).isEqualTo(WadDiagnostic.Severity.WARNING);
            assertThat(diagnostic.code()).isEqualTo("doom.sprite.frame-missing");
            assertThat(diagnostic.location()).isEqualTo("/sprites/MISSA");
        });
    }

    private static DoomActor actor(int thingType, String id, String frame) {
        DoomActorDefinition definition = new DoomActorDefinition(
                thingType, id, id, DoomActorCategory.DECORATION, Optional.of(frame));
        return new DoomActor(thingType, definition, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    private WadArchive writeAndLoad(TestLump... lumps) throws IOException {
        int contentSize = java.util.Arrays.stream(lumps)
                .mapToInt(lump -> lump.content().length)
                .sum();
        int directoryOffset = 12 + contentSize;
        ByteBuffer bytes = ByteBuffer.allocate(directoryOffset + lumps.length * 16).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put("PWAD".getBytes(StandardCharsets.US_ASCII));
        bytes.putInt(lumps.length);
        bytes.putInt(directoryOffset);
        for (TestLump lump : lumps) {
            bytes.put(lump.content());
        }
        int offset = 12;
        for (TestLump lump : lumps) {
            bytes.putInt(offset);
            bytes.putInt(lump.content().length);
            putName(bytes, lump.name());
            offset += lump.content().length;
        }
        Path source = temporaryDirectory.resolve("sprites.wad");
        Files.write(source, bytes.array());
        return new WadLoader().load(source).archive().orElseThrow();
    }

    private static void putName(ByteBuffer target, String name) {
        byte[] encoded = name.getBytes(StandardCharsets.US_ASCII);
        target.put(encoded);
        target.put(new byte[8 - encoded.length]);
    }

    private static void setColor(byte[] palette, int index, int red, int green, int blue) {
        palette[index * 3] = (byte) red;
        palette[index * 3 + 1] = (byte) green;
        palette[index * 3 + 2] = (byte) blue;
    }

    private static byte[] patch(int width, int height, int leftOffset, int topOffset) {
        int headerSize = 8 + width * Integer.BYTES;
        int columnSize = height + 5;
        ByteBuffer bytes = ByteBuffer.allocate(headerSize + width * columnSize).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putShort((short) width);
        bytes.putShort((short) height);
        bytes.putShort((short) leftOffset);
        bytes.putShort((short) topOffset);
        for (int column = 0; column < width; column++) {
            bytes.putInt(headerSize + column * columnSize);
        }
        for (int column = 0; column < width; column++) {
            bytes.put((byte) 0);
            bytes.put((byte) height);
            bytes.put((byte) 0);
            for (int row = 0; row < height; row++) {
                bytes.put((byte) 1);
            }
            bytes.put((byte) 0);
            bytes.put((byte) 0xff);
        }
        return bytes.array();
    }

    private record TestLump(String name, byte[] content) {}
}
