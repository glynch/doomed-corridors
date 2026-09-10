/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.jscene3d.project.physics3d.CharacterBody3d;
import io.github.glynch.jscene3d.project.physics3d.Physics3dDescriptors;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.HostedProject;
import io.github.glynch.jscene3d.project.spatial3d.Transform3d;
import io.github.glynch.jscene3d.project.spatial3d.descriptor.Spatial3dDescriptors;
import io.github.glynch.jscene3d.project.value.ProjectValue;
import java.util.Objects;
import java.util.Optional;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Applies Doomed Corridors' optional parameters from the generic development launch request. */
final class DoomPlaytestParameters {
    static final String INVULNERABLE = "doomed-corridors.invulnerable";
    static final String SPAWN = "doomed-corridors.spawn";
    static final String YAW_DEGREES = "doomed-corridors.yaw-degrees";

    private DoomPlaytestParameters() {}

    /** Applies all selected parameters before the hosted world becomes active. */
    static void apply(HostedProject project, Entity player, DoomPlayerState state) {
        Objects.requireNonNull(project, "project");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(state, "state");
        state.setInvulnerable(booleanParameter(project, INVULNERABLE).orElse(false));
        vectorParameter(project, SPAWN).ifPresent(position -> teleport(player, position));
        numberParameter(project, YAW_DEGREES).ifPresent(yaw -> setYaw(viewTransform(player), yaw));
    }

    /** Repositions both the physics-authoritative character body and its associated transform. */
    private static void teleport(Entity player, float[] position) {
        Transform3d transform = player.capability(Spatial3dDescriptors.spatialCapability(), Transform3d.class)
                .orElseThrow(() -> new IllegalStateException("playtest player has no spatial transform"));
        CharacterBody3d body = player.capability(Physics3dDescriptors.characterBodyCapability(), CharacterBody3d.class)
                .orElseThrow(() -> new IllegalStateException("playtest player has no character body"));
        body.teleport(new Vector3f(position[0], position[1], position[2]), transform.orientation());
    }

    /** Replaces the view orientation with the selected yaw. */
    private static void setYaw(Transform3d view, float yawDegrees) {
        Quaternionf orientation = new Quaternionf().rotationY((float) Math.toRadians(yawDegrees));
        view.setOrientation(orientation.x, orientation.y, orientation.z, orientation.w);
    }

    /** Finds the spatial child used as the first-person view. */
    private static Transform3d viewTransform(Entity player) {
        return player.children().stream()
                .map(child -> child.capability(Spatial3dDescriptors.spatialCapability(), Transform3d.class))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("playtest player has no spatial view"));
    }

    /** Reads an optional strict boolean parameter. */
    private static Optional<Boolean> booleanParameter(HostedProject project, String name) {
        return project.launchRequest().parameter(name).map(value -> {
            if (value instanceof ProjectValue.BooleanValue(boolean selected)) {
                return selected;
            }
            throw new IllegalArgumentException(name + " must be a boolean");
        });
    }

    /** Reads an optional finite number parameter. */
    private static Optional<Float> numberParameter(HostedProject project, String name) {
        return project.launchRequest().parameter(name).map(value -> number(value, name));
    }

    /** Reads an optional three-coordinate finite vector parameter. */
    private static Optional<float[]> vectorParameter(HostedProject project, String name) {
        return project.launchRequest().parameter(name).map(value -> {
            if (!(value instanceof ProjectValue.ArrayValue(var values)) || values.size() != 3) {
                throw new IllegalArgumentException(name + " must contain three numbers");
            }
            return new float[] {number(values.get(0), name), number(values.get(1), name), number(values.get(2), name)};
        });
    }

    /** Converts one portable number to the finite representation used by the spatial runtime. */
    private static float number(ProjectValue value, String name) {
        if (!(value instanceof ProjectValue.NumberValue(var number))) {
            throw new IllegalArgumentException(name + " must contain finite numbers");
        }
        float converted = number.floatValue();
        if (!Float.isFinite(converted)) {
            throw new IllegalArgumentException(name + " must contain finite numbers");
        }
        return converted;
    }
}
