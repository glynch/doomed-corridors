/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.jscene3d.game.application.ApplicationControl;
import io.github.glynch.jscene3d.game.presentation.PresentationWorldModule;
import io.github.glynch.jscene3d.project.asset.AssetCatalog;
import io.github.glynch.jscene3d.project.desktop.StandardProjectEnvironment;
import io.github.glynch.jscene3d.project.extension.ExtensionDescriptor;
import io.github.glynch.jscene3d.project.extension.RegisteredTypeCatalog;
import io.github.glynch.jscene3d.project.input.InputMapDefinition;
import io.github.glynch.jscene3d.project.manifest.GameProject;
import io.github.glynch.jscene3d.project.runtime.ProjectContent;
import io.github.glynch.jscene3d.project.runtime.ProjectRuntimeEnvironment;
import io.github.glynch.jscene3d.project.runtime.WorldModuleBinding;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentRuntimeExtension;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Standard desktop-equivalent environment with its native presentation module replaced for headless tests. */
final class TestProjectEnvironment implements ProjectRuntimeEnvironment {
    private final StandardProjectEnvironment delegate;
    private final PresentationWorldModule presentation;
    private final Optional<ApplicationControl> application;

    /** Retains one standard project-content environment and its test presentation module. */
    TestProjectEnvironment(Path cache, PresentationWorldModule presentation) {
        this(cache, presentation, Optional.empty());
    }

    /** Retains content services while replacing native presentation and application control for host tests. */
    TestProjectEnvironment(Path cache, PresentationWorldModule presentation, ApplicationControl application) {
        this(cache, presentation, Optional.of(application));
    }

    /** Retains the standard environment and optional test-owned application control replacement. */
    private TestProjectEnvironment(
            Path cache, PresentationWorldModule presentation, Optional<ApplicationControl> application) {
        delegate = new StandardProjectEnvironment(cache);
        this.presentation = presentation;
        this.application = application;
    }

    @Override
    public List<ExtensionDescriptor> descriptors() {
        return delegate.descriptors();
    }

    @Override
    public List<ComponentRuntimeExtension> runtimeExtensions() {
        return delegate.runtimeExtensions();
    }

    @Override
    public List<WorldModuleBinding<?>> createWorldModules(Optional<InputMapDefinition> inputMap) {
        List<WorldModuleBinding<?>> bindings = new ArrayList<>();
        for (WorldModuleBinding<?> binding : delegate.createWorldModules(inputMap)) {
            if (binding.type().equals(PresentationWorldModule.class)) {
                binding.module().close();
            } else if (application.isPresent() && binding.type().equals(ApplicationControl.class)) {
                binding.module().close();
            } else {
                bindings.add(binding);
            }
        }
        bindings.add(WorldModuleBinding.of(PresentationWorldModule.class, presentation));
        application.ifPresent(control -> bindings.add(WorldModuleBinding.of(ApplicationControl.class, control)));
        return List.copyOf(bindings);
    }

    @Override
    public ProjectContent loadContent(GameProject project, RegisteredTypeCatalog types, AssetCatalog authored) {
        return delegate.loadContent(project, types, authored);
    }
}
