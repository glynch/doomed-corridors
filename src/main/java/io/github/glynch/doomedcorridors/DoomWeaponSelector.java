/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsDescriptors;
import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.game.input.InputWorldModule;
import io.github.glynch.jscene3d.project.runtime.FixedUpdateContext;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceResolver;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentUpdateCallbacks;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Selects owned weapons from parallel descriptor-authored weapon and input-action lists. */
final class DoomWeaponSelector implements ComponentReferenceBinder, ComponentUpdateCallbacks {
    private final InputWorldModule input;
    private final List<String> weapons;
    private final List<InputAction> actions;
    private @Nullable DoomPlayerState playerState;

    /** Retains validated selection bindings while deferring the explicit player-state reference. */
    DoomWeaponSelector(InputWorldModule input, List<String> weapons, List<String> actions) {
        this.input = Objects.requireNonNull(input, "input");
        this.weapons = List.copyOf(Objects.requireNonNull(weapons, "weapons"));
        List<String> actionNames = List.copyOf(Objects.requireNonNull(actions, "actions"));
        if (this.weapons.isEmpty() || this.weapons.size() != actionNames.size()) {
            throw new IllegalArgumentException("weapons and actions must be non-empty parallel lists");
        }
        this.actions = actionNames.stream().map(InputAction::new).toList();
    }

    @Override
    public void bindReferences(ComponentReferenceResolver references) {
        playerState = Objects.requireNonNull(references, "references")
                .component(DoomedCorridorsDescriptors.WEAPON_SELECTOR_PLAYER_STATE_PROPERTY, DoomPlayerState.class);
    }

    @Override
    public void onBeforePhysics(FixedUpdateContext update) {
        Objects.requireNonNull(update, "update");
        for (int index = 0; index < actions.size(); index++) {
            if (input.snapshot().wasPressed(actions.get(index))) {
                requiredPlayerState().selectWeapon(weapons.get(index));
                return;
            }
        }
    }

    /** Requires explicit reference binding before the active world accepts input. */
    private DoomPlayerState requiredPlayerState() {
        return Objects.requireNonNull(playerState, "player state has not been bound");
    }
}
