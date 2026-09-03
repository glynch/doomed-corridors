/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.actor;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable imported spawn sprites indexed by provider frame identifier. */
public record DoomActorSprites(Map<String, DoomActorSprite> byFrame) {
    /** Copies imported sprites. */
    public DoomActorSprites {
        byFrame = Map.copyOf(Objects.requireNonNull(byFrame, "byFrame"));
    }

    /** Looks up the sprite imported for one provider frame identifier. */
    public Optional<DoomActorSprite> sprite(String frame) {
        return Optional.ofNullable(byFrame.get(Objects.requireNonNull(frame, "frame")));
    }
}
