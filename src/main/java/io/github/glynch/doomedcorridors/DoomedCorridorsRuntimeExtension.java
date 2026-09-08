/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.game.input.InputWorldModule;
import io.github.glynch.jscene3d.project.runtime.HostedProject;
import io.github.glynch.jscene3d.project.runtime.extension.ApplicationRuntimeExtension;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentFactoryRegistry;
import io.github.glynch.jscene3d.project.value.ProjectValue;
import java.util.Objects;

/** Manifest-selected Doomed Corridors application extension. */
public final class DoomedCorridorsRuntimeExtension implements ApplicationRuntimeExtension {
    static final String ID = "io.github.glynch.doomed-corridors";

    /** Creates the stateless provider used by standard Java service discovery. */
    public DoomedCorridorsRuntimeExtension() {
        // Public construction is required by ServiceLoader on the class path and module path.
    }

    /** Returns the identity shared with the project and safe extension descriptor. */
    @Override
    public String id() {
        return ID;
    }

    /** Registers the application-owned player controller under its exact descriptor-backed component type. */
    @Override
    public void register(ComponentFactoryRegistry registry) {
        ComponentFactoryRegistry validRegistry = Objects.requireNonNull(registry, "registry");
        validRegistry.register(
                DoomPlayerController.TYPE,
                context -> new DoomPlayerController(
                        context.world().requireModule(InputWorldModule.class),
                        new DoomPlayerController.Actions(
                                inputAction(
                                        context.properties().get(DoomPlayerController.MOVE_ACTION_PROPERTY),
                                        "move-action"),
                                inputAction(
                                        context.properties().get(DoomPlayerController.LOOK_ACTION_PROPERTY),
                                        "look-action"),
                                inputAction(
                                        context.properties().get(DoomPlayerController.TURN_LEFT_ACTION_PROPERTY),
                                        "turn-left-action"),
                                inputAction(
                                        context.properties().get(DoomPlayerController.TURN_RIGHT_ACTION_PROPERTY),
                                        "turn-right-action")),
                        new DoomPlayerController.Tuning(
                                number(
                                        context.properties().get(DoomPlayerController.MOVE_SPEED_PROPERTY),
                                        "move-speed"),
                                number(
                                        context.properties().get(DoomPlayerController.TURN_SPEED_DEGREES_PROPERTY),
                                        "turn-speed-degrees"),
                                number(
                                        context.properties().get(DoomPlayerController.POINTER_SENSITIVITY_PROPERTY),
                                        "pointer-sensitivity"),
                                number(
                                        context.properties().get(DoomPlayerController.MAXIMUM_PITCH_DEGREES_PROPERTY),
                                        "maximum-pitch-degrees"))));
    }

    /** Validates the composed project; the player controller requires no asynchronous preparation. */
    @Override
    public void prepare(HostedProject project) {
        Objects.requireNonNull(project, "project");
    }

    /** Converts one descriptor-validated text value into a semantic action identity. */
    private static InputAction inputAction(ProjectValue value, String property) {
        if (!(value instanceof ProjectValue.TextValue(String name))) {
            throw new IllegalArgumentException(property + " must be text");
        }
        return new InputAction(name);
    }

    /** Converts one descriptor-validated number into the controller's finite float representation. */
    private static float number(ProjectValue value, String property) {
        if (!(value instanceof ProjectValue.NumberValue(var number))) {
            throw new IllegalArgumentException(property + " must be a number");
        }
        float converted = number.floatValue();
        if (!Float.isFinite(converted)) {
            throw new IllegalArgumentException(property + " must be representable as a finite float");
        }
        return converted;
    }
}
