/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import io.github.glynch.doomedcorridors.combat.DoomCombatEvent;
import io.github.glynch.doomedcorridors.combat.DoomCombatState;
import io.github.glynch.doomedcorridors.combat.DoomCombatUpdate;
import io.github.glynch.doomedcorridors.combat.DoomCombatantActivity;
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
    private static final long DAMAGE_FLASH_NANOS = 180_000_000L;

    private final DoomCombatPresentationRules rules;
    private final Map<Integer, ActorTrack> actors;
    private final Map<Integer, DoomCombatantState> combatants;
    private FrameSequence weaponSequence;
    private int health;
    private int bullets;
    private long damageFlashNanos;
    private boolean playerDead;

    /** Builds state tracks for the combatants present in the initial snapshot. */
    public DoomCombatPresentationState(
            DoomCombatPresentationRules rules, DoomCombatState initialState) {
        this.rules = Objects.requireNonNull(rules, "rules");
        DoomCombatState state = Objects.requireNonNull(initialState, "initialState");
        health = state.playerHealth();
        bullets = state.bullets();
        playerDead = state.isPlayerDead();
        actors = new LinkedHashMap<>();
        combatants = new LinkedHashMap<>();
        for (DoomCombatantState combatant : state.combatants()) {
            combatants.put(combatant.thingIndex(), combatant);
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
        playerDead = validUpdate.state().isPlayerDead();
        synchronizeCombatants(validUpdate.state());
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
        damageFlashNanos = Math.clamp(
                damageFlashNanos - validElapsed.toNanos(), 0L, damageFlashNanos);
    }

    /** Returns current player health for the HUD. */
    public int health() {
        return health;
    }

    /** Returns current bullet ammunition for the HUD. */
    public int bullets() {
        return bullets;
    }

    /** Returns whether terminal player death should suppress weapon presentation and input. */
    public boolean isPlayerDead() {
        return playerDead;
    }

    /** Returns the current red damage-flash opacity in the inclusive range [0, 0.45]. */
    public float damageFlashAlpha() {
        return 0.45F * damageFlashNanos / DAMAGE_FLASH_NANOS;
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

    /** Returns the latest headless state for one presented combatant. */
    Optional<DoomCombatantState> combatant(int thingIndex) {
        return Optional.ofNullable(combatants.get(thingIndex));
    }

    /** Synchronizes observable positions and base activities before transient events apply. */
    private void synchronizeCombatants(DoomCombatState state) {
        for (DoomCombatantState combatant : state.combatants()) {
            combatants.put(combatant.thingIndex(), combatant);
            ActorTrack track = actors.get(combatant.thingIndex());
            if (track != null) {
                track.synchronize(combatant.activity());
            }
        }
    }

    /** Starts the sequence implied by one presentation-neutral combat event. */
    private void applyEvent(DoomCombatEvent event, DoomCombatState state) {
        switch (event.type()) {
            case WEAPON_FIRED -> weaponSequence = new FrameSequence(
                    rules.weapon().fireFrames(),
                    rules.weapon().frameDuration(),
                    Completion.CLEAR);
            case COMBATANT_DAMAGED -> applyActorEvent(event.thingIndex(), state, false);
            case COMBATANT_KILLED -> applyActorEvent(event.thingIndex(), state, true);
            case COMBATANT_ATTACKED -> applyActorAttack(event.thingIndex());
            case PLAYER_DAMAGED -> damageFlashNanos = DAMAGE_FLASH_NANOS;
            case PLAYER_KILLED -> playerDead = true;
            case WEAPON_EMPTY, COMBATANT_ALERTED -> {
                // These events have no transient visual beyond synchronized base state.
            }
        }
    }

    /** Starts the configured attack sequence for one known living combatant. */
    private void applyActorAttack(int thingIndex) {
        ActorTrack track = actors.get(thingIndex);
        if (track != null) {
            track.attack();
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
        private FrameSequence movementSequence;
        private FrameSequence overrideSequence;
        private boolean dead;

        private ActorTrack(DoomCombatPresentationRules.Combatant rules) {
            this.rules = rules;
        }

        /** Starts a temporary pain sequence unless death is already terminal. */
        private void hurt() {
            if (!dead) {
                overrideSequence = new FrameSequence(
                        rules.animations().painFrames(),
                        rules.animations().frameDuration(),
                        Completion.CLEAR);
            }
        }

        /** Starts a temporary ranged-attack sequence unless death is terminal. */
        private void attack() {
            if (!dead) {
                overrideSequence = new FrameSequence(
                        rules.animations().attackFrames(),
                        rules.animations().frameDuration(),
                        Completion.CLEAR);
            }
        }

        /** Starts a non-looping death sequence that remains on its last frame. */
        private void die() {
            dead = true;
            movementSequence = null;
            overrideSequence = new FrameSequence(
                    rules.animations().deathFrames(),
                    rules.animations().frameDuration(),
                    Completion.HOLD);
        }

        /** Synchronizes the looping walk track with the latest simulation activity. */
        private void synchronize(DoomCombatantActivity activity) {
            if (activity == DoomCombatantActivity.DEAD) {
                dead = true;
                movementSequence = null;
            } else if (activity == DoomCombatantActivity.PURSUING) {
                if (movementSequence == null) {
                    movementSequence = new FrameSequence(
                            rules.animations().walkFrames(),
                            rules.animations().frameDuration(),
                            Completion.LOOP);
                }
            } else {
                movementSequence = null;
            }
        }

        /** Advances a live sequence, returning temporary pain to the idle frame. */
        private void advance(Duration elapsed) {
            if (movementSequence != null) {
                movementSequence.advance(elapsed);
            }
            if (overrideSequence != null && overrideSequence.advance(elapsed) && !dead) {
                overrideSequence = null;
            }
        }

        /** Returns the current override frame. */
        private Optional<String> frame() {
            if (overrideSequence != null) {
                return Optional.of(overrideSequence.frame());
            }
            return movementSequence == null
                    ? Optional.empty()
                    : Optional.of(movementSequence.frame());
        }
    }

    /** Mutable frame cursor with bounded arithmetic and optional terminal last-frame retention. */
    private static final class FrameSequence {
        private final List<String> frames;
        private final long frameNanos;
        private final Completion completion;
        private int index;
        private long remainderNanos;

        private FrameSequence(
                List<String> frames, Duration frameDuration, Completion completion) {
            this.frames = List.copyOf(frames);
            frameNanos = frameDuration.toNanos();
            this.completion = Objects.requireNonNull(completion, "completion");
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
            if (completion == Completion.LOOP) {
                index = (int) ((index + steps) % frames.size());
                return false;
            }
            long lastIndex = frames.size() - 1L;
            if (index + steps <= lastIndex) {
                index += Math.toIntExact(steps);
                return false;
            }
            index = frames.size() - 1;
            return completion == Completion.CLEAR;
        }
    }

    /** Behavior when one frame sequence advances past its final frame. */
    private enum Completion {
        CLEAR,
        LOOP,
        HOLD
    }
}
