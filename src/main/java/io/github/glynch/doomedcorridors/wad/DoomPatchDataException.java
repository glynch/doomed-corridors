/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.wad;

/** Internal malformed Doom patch signal translated by the consuming importer. */
final class DoomPatchDataException extends RuntimeException {
    /** Creates a patch-data failure with a user-facing explanation. */
    DoomPatchDataException(String message) {
        super(message);
    }
}
