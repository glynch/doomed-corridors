package io.github.glynch.doomedcorridors.wad;

import io.github.glynch.jscene3d.doom.map.DoomMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Result of decoding one classic Doom map from a validated WAD. */
public record DoomMapDecodeResult(Optional<DoomMap> map, List<WadDiagnostic> diagnostics) {
    /** Creates an immutable decode result. */
    public DoomMapDecodeResult {
        Objects.requireNonNull(map, "map");
        diagnostics = List.copyOf(diagnostics);
    }

    /** Returns whether decoding produced a map without errors. */
    public boolean isValid() {
        return map.isPresent()
                && diagnostics.stream().noneMatch(item -> item.severity() == WadDiagnostic.Severity.ERROR);
    }
}
