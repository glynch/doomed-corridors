/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.jscene3d.doom.material.RgbaImage;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Specifies deterministic transparent label composition independently from WAD decoding. */
final class DoomMenuImageComposerTest {
    @Test
    void composesGlyphsWithSpacingAndBottomAlignment() {
        RgbaImage tall = image(2, 3, 0xff0000ff);
        RgbaImage shortGlyph = image(1, 2, 0x00ff00ff);

        RgbaImage result = DoomMenuImageComposer.compose("A B", Map.of('A', tall, 'B', shortGlyph));

        assertThat(result.width()).isEqualTo(9);
        assertThat(result.height()).isEqualTo(3);
        assertThat(result.rgba(0, 0)).isEqualTo(0xff0000ff);
        assertThat(result.rgba(8, 0)).isZero();
        assertThat(result.rgba(8, 1)).isEqualTo(0x00ff00ff);
    }

    /** Creates one uniformly colored immutable test image. */
    private static RgbaImage image(int width, int height, int rgba) {
        byte[] pixels = new byte[width * height * 4];
        for (int offset = 0; offset < pixels.length; offset += 4) {
            pixels[offset] = (byte) (rgba >>> 24);
            pixels[offset + 1] = (byte) (rgba >>> 16);
            pixels[offset + 2] = (byte) (rgba >>> 8);
            pixels[offset + 3] = (byte) rgba;
        }
        return new RgbaImage(width, height, pixels);
    }
}
