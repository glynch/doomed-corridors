/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import io.github.glynch.doomedcorridors.combat.DoomCombatEvent;
import io.github.glynch.doomedcorridors.combat.DoomCombatState;
import io.github.glynch.doomedcorridors.combat.DoomCombatUpdate;
import io.github.glynch.doomedcorridors.combat.DoomCombatantState;
import io.github.glynch.doomedcorridors.combat.DoomCombatantStatus;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Renderer-independent timed weapon and combatant visual state derived from combat events. */
public final class DoomCombatPresentationState {
    private final DoomCombatPresentationRules rules;
    private final Map<Integer, ActorTrack> actors;
    private FrameSequence weaponSequence;
    private int health;
    private int bullets;

    /** Builds state tracks for the combatants present in the initial snapshot. */
    public DoomCombatPresentationState(
            DoomCombatPresentationRules rules, DoomCombatState initialState) {
        this.rules = Objects.requireNonNull(rules, "rules");
        DoomCombatState state = Objects.requireNonNull(initialState, "initialState");
        health = state.playerHealth();
        bullets = state.bullets();
        actors = new LinkedHashMap<>();
        for (DoomCombatantState combatant : state.combatants()) {
            DoomCombatPresentationRules.Combatant actorRules = rules.combatant(combatant.actorId());
            if (actorRules != null) {
                actors.put(combatant.thingIndex(), new ActorTrack(actorRules));
            }
        }
    }

    /** Applies one atomic combat result to HUD resources and presentation tracks. */
    public void apply(DoomCombatUpdate update) {
        DoomCombatUpdate validUpdate = Objects.requireNonNull(update, "update");
        health = validUpdate.state().playerHealth();
        bullets = validUpdate.state().bullets();
        for (DoomCombatEvent event : validUpdate.events()) {
            applyEvent(event, validUpdate.state());
        }
    }

    /** Advances all active visual sequences by a non-negative elapsed duration. */
    public void advance(Duration elapsed) {
        Duration validElapsed = Objects.requireNonNull(elapsed, "elapsed");
        if (validElapsed.isNegative()) {
            throw new IllegalArgumentException("elapsed must not be negative");
        }
        if (weaponSequence != null && weaponSequence.advance(validElapsed)) {
            weaponSequence = null;
        }
        actors.values().forEach(actor -> actor.advance(validElapsed));
    }

    /** Returns current player health for the HUD. */
    public int health() {
        return health;
    }

    /** Returns current bullet ammunition for the HUD. */
    public int bullets() {
        return bullets;
    }

    /** Returns the exact WAD patch currently used for the first-person weapon. */
    public String weaponFrame() {
        return weaponSequence == null ? rules.weapon().readyFrame() : weaponSequence.frame();
    }

    /** Returns a temporary or terminal actor-frame override, absent for the idle spawn frame. */
    public Optional<String> actorFrame(int thingIndex) {
        ActorTrack track = actors.get(thingIndex);
        return track == null ? Optional.empty() : track.frame();
    }

    /** Starts the sequence implied by one presentation-neutral combat event. */
    private void applyEvent(DoomCombatEvent event, DoomCombatState state) {
        switch (event.type()) {
            case WEAPON_FIRED -> weaponSequence = new FrameSequence(
                    rules.weapon().fireFrames(), rules.weapon().frameDuration(), false);
            case COMBATANT_DAMAGED -> applyActorEvent(event.thingIndex(), state, false);
            case COMBATANT_KILLED -> applyActorEvent(event.thingIndex(), state, true);
            case WEAPON_EMPTY, PLAYER_DAMAGED, PLAYER_KILLED -> {
                // These events change no visual sequence in the first combat presentation.
            }
        }
    }

    /** Starts pain or terminal death frames for one known combatant. */
    private void applyActorEvent(int thingIndex, DoomCombatState state, boolean killed) {
        ActorTrack track = actors.get(thingIndex);
        DoomCombatantState combatant = state.combatant(thingIndex).orElse(null);
        if (track == null || combatant == null) {
            return;
        }
        if (killed || combatant.status() == DoomCombatantStatus.DEAD) {
            track.die();
        } else {
            track.hurt();
        }
    }

    /** Mutable timed presentation state belonging to one combatant identity. */
    private static final class ActorTrack {
        private final DoomCombatPresentationRules.Combatant rules;
        private FrameSequence sequence;
        private boolean dead;

        private ActorTrack(DoomCombatPresentationRules.Combatant rules) {
            this.rules = rules;
        }

        /** Starts a temporary pain sequence unless death is already terminal. */
        private void hurt() {
            if (!dead) {
                sequence = new FrameSequence(rules.painFrames(), rules.frameDuration(), false);
            }
        }

        /** Starts a non-looping death sequence that remains on its last frame. */
        private void die() {
            dead = true;
            sequence = new FrameSequence(rules.deathFrames(), rules.frameDuration(), true);
        }

        /** Advances a live sequence, returning temporary pain to the idle frame. */
        private void advance(Duration elapsed) {
            if (sequence != null && sequence.advance(elapsed) && !dead) {
                sequence = null;
            }
        }

        /** Returns the current override frame. */
        private Optional<String> frame() {
            return sequence == null ? Optional.empty() : Optional.of(sequence.frame());
        }
    }

    /** Mutable frame cursor with bounded arithmetic and optional terminal last-frame retention. */
    private static final class FrameSequence {
        private final List<String> frames;
        private final long frameNanos;
        private final boolean retainLastFrame;
        private int index;
        private long remainderNanos;

        private FrameSequence(List<String> frames, Duration frameDuration, boolean retainLastFrame) {
            this.frames = List.copyOf(frames);
            frameNanos = frameDuration.toNanos();
            this.retainLastFrame = retainLastFrame;
        }

        /** Returns the current exact patch lump. */
        private String frame() {
            return frames.get(index);
        }

        /** Advances by whole frame periods and reports completion of a temporary sequence. */
        private boolean advance(Duration elapsed) {
            long elapsedNanos = elapsed.toNanos();
            long available = Long.MAX_VALUE - remainderNanos;
            long totalNanos = elapsedNanos > available ? Long.MAX_VALUE : remainderNanos + elapsedNanos;
            long steps = totalNanos / frameNanos;
            remainderNanos = totalNanos % frameNanos;
            long lastIndex = frames.size() - 1L;
            if (index + steps <= lastIndex) {
                index += Math.toIntExact(steps);
                return false;
            }
            index = frames.size() - 1;
            return !retainLastFrame;
        }
    }
}
