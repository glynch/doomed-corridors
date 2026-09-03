/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.actor;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Provider-owned meaning assigned to one classic Doom thing type. */
public record DoomActorDefinition(
        int thingType,
        String id,
        String name,
        DoomActorCategory category,
        Optional<String> spriteFrame) {
    private static final Pattern SPRITE_FRAME = Pattern.compile("[A-Z0-9]{4}[A-Z]");

    /** Creates a validated actor definition. */
    public DoomActorDefinition {
        if (thingType <= 0 || thingType > 0xffff) {
            throw new IllegalArgumentException("thingType must be in the range [1, 65535]");
        }
        requireActorId(id);
        if (Objects.requireNonNull(name, "name").isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(spriteFrame, "spriteFrame")
                .ifPresent(value -> requireMatch(value, SPRITE_FRAME, "spriteFrame"));
        if (category == DoomActorCategory.MARKER && spriteFrame.isPresent()) {
            throw new IllegalArgumentException("marker definitions must not have a sprite frame");
        }
        if (category != DoomActorCategory.MARKER && spriteFrame.isEmpty()) {
            throw new IllegalArgumentException("visible actor definitions require a sprite frame");
        }
    }

    /** Requires a lower-case, hyphen-separated provider identifier. */
    private static void requireActorId(String value) {
        String id = Objects.requireNonNull(value, "id");
        boolean valid = !id.isEmpty() && isLowercaseLetter(id.charAt(0));
        boolean previousHyphen = false;
        for (int index = 1; valid && index < id.length(); index++) {
            char character = id.charAt(index);
            boolean hyphen = character == '-';
            valid = isLowercaseLetter(character)
                    || isDigit(character)
                    || (hyphen && !previousHyphen && index + 1 < id.length());
            previousHyphen = hyphen;
        }
        if (!valid) {
            throw new IllegalArgumentException("id has an invalid value: " + id);
        }
    }

    /** Reports whether one character is an ASCII lower-case letter. */
    private static boolean isLowercaseLetter(char character) {
        return character >= 'a' && character <= 'z';
    }

    /** Reports whether one character is an ASCII digit. */
    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    /** Requires a non-null string matching one complete pattern. */
    private static void requireMatch(String value, Pattern pattern, String name) {
        if (!pattern.matcher(Objects.requireNonNull(value, name)).matches()) {
            throw new IllegalArgumentException(name + " has an invalid value: " + value);
        }
    }
}
