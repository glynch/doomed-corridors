/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.importing;

import io.github.glynch.jscene3d.diagnostic.DiagnosticCode;

/** Stable diagnostic identities produced by Doomed Corridors project importers. */
public enum DoomedCorridorsImportDiagnosticCode implements DiagnosticCode {
    /** The configured WAD source could not be loaded. */
    WAD_SOURCE_INVALID("doomed-corridors.import.wad-source", "The WAD source could not be loaded"),

    /** A selected classic Doom map could not be decoded. */
    MAP_INVALID("doomed-corridors.import.map", "The selected Doom map could not be decoded"),

    /** Referenced wall textures or flats could not be imported. */
    MAP_MATERIALS_INVALID(
            "doomed-corridors.import.map-materials",
            "The selected Doom map materials could not be imported");

    private final String code;
    private final String message;

    /** Stores one stable code and its English fallback message. */
    DoomedCorridorsImportDiagnosticCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String defaultMessage() {
        return message;
    }
}
