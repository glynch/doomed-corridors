/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.actor;

import io.github.glynch.doomedcorridors.material.RgbaImage;
import io.github.glynch.doomedcorridors.wad.WadLump;
import java.util.List;
import java.util.Objects;

/** One imported spawn-frame image with classic patch-origin metadata and provenance. */
public record DoomActorSprite(
        String frame,
        String lumpName,
        RgbaImage image,
        int leftOffset,
        int topOffset,
        List<WadLump> sourceLumps) {
    /** Creates an immutable imported actor sprite. */
    public DoomActorSprite {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(lumpName, "lumpName");
        Objects.requireNonNull(image, "image");
        sourceLumps = List.copyOf(sourceLumps);
    }
}
