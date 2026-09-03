package io.github.glynch.doomedcorridors.material;

import java.util.Objects;

/** Immutable row-major RGBA8 image independent of rendering and UI toolkits. */
public final class RgbaImage {
    private final int width;
    private final int height;
    private final byte[] pixels;

    /** Creates an image by defensively copying its row-major RGBA8 pixels. */
    public RgbaImage(int width, int height, byte[] pixels) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("image dimensions must be positive");
        }
        Objects.requireNonNull(pixels, "pixels");
        int expectedLength = Math.multiplyExact(Math.multiplyExact(width, height), 4);
        if (pixels.length != expectedLength) {
            throw new IllegalArgumentException(
                    "pixel data contains " + pixels.length + " bytes; expected " + expectedLength);
        }
        this.width = width;
        this.height = height;
        this.pixels = pixels.clone();
    }

    /** Returns the width in pixels. */
    public int width() {
        return width;
    }

    /** Returns the height in pixels. */
    public int height() {
        return height;
    }

    /** Returns one pixel packed as {@code 0xRRGGBBAA}. */
    public int rgba(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            throw new IndexOutOfBoundsException("pixel is outside the image");
        }
        int offset = (y * width + x) * 4;
        return Byte.toUnsignedInt(pixels[offset]) << 24
                | Byte.toUnsignedInt(pixels[offset + 1]) << 16
                | Byte.toUnsignedInt(pixels[offset + 2]) << 8
                | Byte.toUnsignedInt(pixels[offset + 3]);
    }

    /** Returns a defensive copy of the row-major RGBA8 pixels. */
    public byte[] pixels() {
        return pixels.clone();
    }
}
