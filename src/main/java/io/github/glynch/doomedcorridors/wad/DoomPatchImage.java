/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.wad;

import io.github.glynch.doomedcorridors.material.RgbaImage;
import java.util.Objects;

/** Decoded Doom patch image and its source-space origin offsets. */
record DoomPatchImage(RgbaImage image, int leftOffset, int topOffset) {
    /** Creates an immutable decoded patch. */
    DoomPatchImage {
        Objects.requireNonNull(image, "image");
    }
}
