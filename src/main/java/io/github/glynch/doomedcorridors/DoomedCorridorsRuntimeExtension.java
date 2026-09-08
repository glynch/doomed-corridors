/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.jscene3d.project.runtime.HostedProject;
import io.github.glynch.jscene3d.project.runtime.extension.ApplicationRuntimeExtension;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentFactoryRegistry;
import java.util.Objects;

/** Manifest-selected Doomed Corridors application extension. */
public final class DoomedCorridorsRuntimeExtension implements ApplicationRuntimeExtension {
    /** Creates the stateless provider used by standard Java service discovery. */
    public DoomedCorridorsRuntimeExtension() {
        // Public construction is required by ServiceLoader on the class path and module path.
    }

    /** Returns the identity shared with the project and safe extension descriptor. */
    @Override
    public String id() {
        return "io.github.glynch.doomed-corridors";
    }

    /** Validates the registry; the static MAP01 slice declares no application behavior components. */
    @Override
    public void register(ComponentFactoryRegistry registry) {
        Objects.requireNonNull(registry, "registry");
    }

    /** Validates the composed project; this static slice requires no additional preparation. */
    @Override
    public void prepare(HostedProject project) {
        Objects.requireNonNull(project, "project");
    }
}
