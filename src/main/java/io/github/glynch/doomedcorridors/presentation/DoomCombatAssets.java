/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import io.github.glynch.doomedcorridors.actor.DoomActorSprite;
import io.github.glynch.jscene3d.audio.PcmAudio;
import java.util.Map;
import java.util.Objects;

/** Immutable decoded images and sounds required by one combat presentation definition. */
public record DoomCombatAssets(
        DoomCombatPresentationRules rules,
        Map<String, DoomActorSprite> images,
        Map<String, PcmAudio> sounds) {
    /** Copies lookup maps while retaining immutable decoded values. */
    public DoomCombatAssets {
        Objects.requireNonNull(rules, "rules");
        images = Map.copyOf(Objects.requireNonNull(images, "images"));
        sounds = Map.copyOf(Objects.requireNonNull(sounds, "sounds"));
    }

    /** Returns one required decoded patch by its exact lump name. */
    public DoomActorSprite image(String lumpName) {
        DoomActorSprite image = images.get(Objects.requireNonNull(lumpName, "lumpName"));
        if (image == null) {
            throw new IllegalArgumentException("Combat image was not imported: " + lumpName);
        }
        return image;
    }

    /** Returns one required decoded sound by its exact lump name. */
    public PcmAudio sound(String lumpName) {
        PcmAudio sound = sounds.get(Objects.requireNonNull(lumpName, "lumpName"));
        if (sound == null) {
            throw new IllegalArgumentException("Combat sound was not imported: " + lumpName);
        }
        return sound;
    }
}
