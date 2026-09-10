/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import io.github.glynch.jscene3d.doom.material.RgbaImage;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Composes immutable menu labels from project-imported Doom font glyphs. */
public final class DoomMenuImageComposer {
    private static final int GLYPH_SPACING = 1;
    private static final int SPACE_WIDTH = 4;

    private DoomMenuImageComposer() {
        throw new AssertionError("DoomMenuImageComposer cannot be instantiated");
    }

    /**
     * Composes one uppercase label while retaining each glyph's RGBA pixels.
     *
     * @param text non-blank menu label
     * @param glyphs decoded glyphs indexed by uppercase character
     * @return tightly packed transparent RGBA label
     */
    public static RgbaImage compose(String text, Map<Character, RgbaImage> glyphs) {
        String label = Objects.requireNonNull(text, "text").strip().toUpperCase(Locale.ROOT);
        if (label.isEmpty()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        Map<Character, RgbaImage> available = Map.copyOf(Objects.requireNonNull(glyphs, "glyphs"));
        int width = label.chars()
                        .map(character -> width((char) character, available))
                        .sum()
                + GLYPH_SPACING * (label.length() - 1);
        int height = label.chars()
                .filter(character -> character != ' ')
                .map(character -> glyph((char) character, available).height())
                .max()
                .orElseThrow();
        byte[] pixels = new byte[Math.multiplyExact(Math.multiplyExact(width, height), 4)];
        int offsetX = 0;
        for (int index = 0; index < label.length(); index++) {
            char character = label.charAt(index);
            if (character != ' ') {
                copy(glyph(character, available), pixels, width, offsetX, height);
            }
            offsetX += width(character, available) + GLYPH_SPACING;
        }
        return new RgbaImage(width, height, pixels);
    }

    /** Copies one glyph bottom-aligned into the destination label. */
    private static void copy(RgbaImage glyph, byte[] destination, int destinationWidth, int offsetX, int height) {
        byte[] source = glyph.pixels();
        int offsetY = height - glyph.height();
        for (int y = 0; y < glyph.height(); y++) {
            int sourceOffset = y * glyph.width() * 4;
            int destinationOffset = ((offsetY + y) * destinationWidth + offsetX) * 4;
            System.arraycopy(source, sourceOffset, destination, destinationOffset, glyph.width() * 4);
        }
    }

    /** Returns the exact glyph width, using a fixed transparent advance for spaces. */
    private static int width(char character, Map<Character, RgbaImage> glyphs) {
        return character == ' ' ? SPACE_WIDTH : glyph(character, glyphs).width();
    }

    /** Requires one decoded glyph for every non-space label character. */
    private static RgbaImage glyph(char character, Map<Character, RgbaImage> glyphs) {
        RgbaImage glyph = glyphs.get(character);
        if (glyph == null) {
            throw new IllegalArgumentException("missing Doom font glyph: " + character);
        }
        return glyph;
    }
}
