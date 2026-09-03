/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.material;

import io.github.glynch.doomedcorridors.actor.DoomActorSprite;
import io.github.glynch.doomedcorridors.actor.DoomActorSprites;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;

/** Writes imported actor spawn frames as a deterministic PNG for manual inspection. */
public final class DoomSpriteContactSheet {
    /** Writes unique sprite frames in provider-frame order. */
    public void write(DoomActorSprites sprites, Path output) throws IOException {
        Objects.requireNonNull(sprites, "sprites");
        Objects.requireNonNull(output, "output");
        var images = sprites.byFrame().values().stream()
                .sorted(Comparator.comparing(DoomActorSprite::frame))
                .map(DoomActorSprite::image)
                .toList();
        new RgbaContactSheetWriter().write(images, output);
    }
}
