/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.jscene3d.audio.AudioCategory;
import io.github.glynch.jscene3d.doom.runtime.DoomDoor;
import io.github.glynch.jscene3d.doom.runtime.DoomDoorDescriptors;
import io.github.glynch.jscene3d.game.presentation.PcmAudioResource;
import io.github.glynch.jscene3d.game.presentation.PositionalSound;
import io.github.glynch.jscene3d.game.presentation.PositionalSoundAttenuation;
import io.github.glynch.jscene3d.game.presentation.PresentationWorldModule;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.FixedUpdateContext;
import io.github.glynch.jscene3d.project.runtime.World;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentLifecycleCallbacks;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentUpdateCallbacks;
import io.github.glynch.jscene3d.project.spatial3d.Transform3d;
import io.github.glynch.jscene3d.project.spatial3d.descriptor.Spatial3dDescriptors;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.joml.Vector3f;

/** Plays project-authored positional sounds for descriptor-selected imported Doom doors. */
final class DoomDoorPresentation implements ComponentLifecycleCallbacks, ComponentUpdateCallbacks, AutoCloseable {
    private final World world;
    private final DoorSounds sounds;
    private final List<DoorState> doors = new ArrayList<>();
    private boolean closed;

    /** Acquires independent sound handles while retaining the world only for post-composition discovery. */
    DoomDoorPresentation(
            World world,
            PresentationWorldModule presentation,
            AudioResources audio,
            PositionalSoundAttenuation attenuation) {
        this.world = Objects.requireNonNull(world, "world");
        sounds = DoorSounds.create(
                Objects.requireNonNull(presentation, "presentation"),
                Objects.requireNonNull(audio, "audio"),
                Objects.requireNonNull(attenuation, "attenuation"));
    }

    /** Discovers only entities whose descriptors provide the stable Doom door capability. */
    @Override
    public void onCreated() {
        world.roots().forEach(this::collectDoors);
    }

    /** Presents only transitions into moving phases after door obstruction decisions are current. */
    @Override
    public void onAfterPhysics(FixedUpdateContext update) {
        Objects.requireNonNull(update, "update");
        requireOpen();
        doors.forEach(door -> door.presentTransition(sounds));
    }

    /** Releases exactly the four sound handles owned by this component. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        sounds.close();
        doors.clear();
    }

    /** Traverses the completed hierarchy and retains each capability-declared door with its sibling transform. */
    private void collectDoors(Entity entity) {
        entity.capability(DoomDoorDescriptors.DOOR_CAPABILITY, DoomDoor.class).ifPresent(door -> {
            Transform3d transform = entity.capability(Spatial3dDescriptors.spatialCapability(), Transform3d.class)
                    .orElseThrow(() -> new IllegalStateException("Doom door has no spatial transform: " + entity));
            doors.add(new DoorState(door, transform));
        });
        entity.children().forEach(this::collectDoors);
    }

    /** Rejects callbacks after component ownership has ended. */
    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("door presentation is closed");
        }
    }

    /** Immutable descriptor-resolved PCM resources used to create owned playback handles. */
    record AudioResources(
            PcmAudioResource normalOpening,
            PcmAudioResource normalClosing,
            PcmAudioResource blazeOpening,
            PcmAudioResource blazeClosing) {
        /** Validates every required resource. */
        AudioResources {
            Objects.requireNonNull(normalOpening, "normalOpening");
            Objects.requireNonNull(normalClosing, "normalClosing");
            Objects.requireNonNull(blazeOpening, "blazeOpening");
            Objects.requireNonNull(blazeClosing, "blazeClosing");
        }
    }

    /** Mutable observation of one door's last presented phase and reusable sound position. */
    private static final class DoorState {
        private final DoomDoor door;
        private final Transform3d transform;
        private final Vector3f position = new Vector3f();
        private DoomDoor.Phase previous;

        private DoorState(DoomDoor door, Transform3d transform) {
            this.door = Objects.requireNonNull(door, "door");
            this.transform = Objects.requireNonNull(transform, "transform");
            previous = door.phase();
        }

        /** Plays the selected transition sound once, including a closing-door obstruction reversal. */
        private void presentTransition(DoorSounds sounds) {
            DoomDoor.Phase current = door.phase();
            if (current != previous) {
                transform.worldMatrix().getTranslation(position);
                if (current == DoomDoor.Phase.OPENING) {
                    sounds.opening(door.profile()).restart(position);
                } else if (current == DoomDoor.Phase.CLOSING) {
                    sounds.closing(door.profile()).restart(position);
                }
                previous = current;
            }
        }
    }

    /** Owned positional handles selected by the imported door profile. */
    private record DoorSounds(
            PositionalSound normalOpening,
            PositionalSound normalClosing,
            PositionalSound blazeOpening,
            PositionalSound blazeClosing) {
        /** Creates all four independent effects from descriptor-resolved resources. */
        private static DoorSounds create(
                PresentationWorldModule presentation, AudioResources audio, PositionalSoundAttenuation attenuation) {
            return new DoorSounds(
                    presentation.createPositionalSound(audio.normalOpening(), AudioCategory.EFFECTS, attenuation),
                    presentation.createPositionalSound(audio.normalClosing(), AudioCategory.EFFECTS, attenuation),
                    presentation.createPositionalSound(audio.blazeOpening(), AudioCategory.EFFECTS, attenuation),
                    presentation.createPositionalSound(audio.blazeClosing(), AudioCategory.EFFECTS, attenuation));
        }

        /** Selects the opening movement sound for one imported profile. */
        private PositionalSound opening(DoomDoor.Profile profile) {
            return profile == DoomDoor.Profile.BLAZE ? blazeOpening : normalOpening;
        }

        /** Selects the closing movement sound for one imported profile. */
        private PositionalSound closing(DoomDoor.Profile profile) {
            return profile == DoomDoor.Profile.BLAZE ? blazeClosing : normalClosing;
        }

        /** Releases every independently owned playback handle. */
        private void close() {
            normalOpening.close();
            normalClosing.close();
            blazeOpening.close();
            blazeClosing.close();
        }
    }
}
