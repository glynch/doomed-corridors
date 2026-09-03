/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.wad;

import io.github.glynch.doomedcorridors.material.RgbaImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Shared decoder for Doom column-post patches used by textures and sprites. */
final class DoomPatchDecoder {
    private static final int PALETTE_SIZE = 256 * 3;

    private DoomPatchDecoder() {
        throw new AssertionError("DoomPatchDecoder cannot be instantiated");
    }

    /** Decodes one patch with its classic origin offsets. */
    static DoomPatchImage decode(byte[] data, byte[] palette, String name) {
        if (palette.length < PALETTE_SIZE) {
            throw data(name, "Palette is shorter than 256 RGB entries");
        }
        if (data.length < 8) {
            throw data(name, "Patch header is shorter than eight bytes");
        }
        ByteBuffer input = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int width = Short.toUnsignedInt(input.getShort());
        int height = Short.toUnsignedInt(input.getShort());
        int leftOffset = input.getShort();
        int topOffset = input.getShort();
        if (width == 0 || height == 0 || width > input.remaining() / Integer.BYTES) {
            throw data(name, "Patch dimensions or column directory are invalid");
        }
        int[] offsets = new int[width];
        for (int column = 0; column < width; column++) {
            offsets[column] = input.getInt();
        }
        byte[] pixels = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
        PatchTarget target = new PatchTarget(name, width, height, pixels, palette);
        for (int column = 0; column < width; column++) {
            decodeColumn(data, target, column, offsets[column]);
        }
        return new DoomPatchImage(new RgbaImage(width, height, pixels), leftOffset, topOffset);
    }

    /** Decodes every terminated post belonging to one patch column. */
    private static void decodeColumn(byte[] data, PatchTarget target, int column, int offset) {
        if (offset < 0 || offset >= data.length) {
            throw data(target.name, "Patch column offset is outside the lump");
        }
        ByteBuffer input = ByteBuffer.wrap(data);
        input.position(offset);
        while (input.hasRemaining()) {
            int top = Byte.toUnsignedInt(input.get());
            if (top == 0xff) {
                return;
            }
            decodePost(input, target, column, top);
        }
        throw data(target.name, "Patch column is not terminated");
    }

    /** Decodes one bounded column post into transparent RGBA storage. */
    private static void decodePost(ByteBuffer input, PatchTarget target, int column, int top) {
        if (input.remaining() < 2) {
            throw data(target.name, "Patch post header is truncated");
        }
        int length = Byte.toUnsignedInt(input.get());
        input.get();
        if (input.remaining() < length + 1) {
            throw data(target.name, "Patch post pixels are truncated");
        }
        for (int row = 0; row < length; row++) {
            int paletteIndex = Byte.toUnsignedInt(input.get());
            int y = top + row;
            if (y < target.height) {
                setPixel(target, column, y, paletteIndex);
            }
        }
        input.get();
    }

    /** Writes one opaque palette entry into row-major RGBA storage. */
    private static void setPixel(PatchTarget target, int x, int y, int paletteIndex) {
        int sourceOffset = paletteIndex * 3;
        int targetOffset = (y * target.width + x) * 4;
        target.pixels[targetOffset] = target.palette[sourceOffset];
        target.pixels[targetOffset + 1] = target.palette[sourceOffset + 1];
        target.pixels[targetOffset + 2] = target.palette[sourceOffset + 2];
        target.pixels[targetOffset + 3] = (byte) 0xff;
    }

    /** Creates a consistently identified patch-data failure. */
    private static DoomPatchDataException data(String name, String message) {
        return new DoomPatchDataException(name + ": " + message);
    }

    /** Mutable decoding target retained only during one patch conversion. */
    private static final class PatchTarget {
        private final String name;
        private final int width;
        private final int height;
        private final byte[] pixels;
        private final byte[] palette;

        private PatchTarget(String name, int width, int height, byte[] pixels, byte[] palette) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.pixels = pixels;
            this.palette = palette;
        }
    }
}
