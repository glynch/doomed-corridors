/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsRuntimeTypes;
import io.github.glynch.jscene3d.game.presentation.ScreenNumber;
import io.github.glynch.jscene3d.project.runtime.FrameUpdateContext;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceResolver;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentUpdateCallbacks;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Copies Doom player resources into explicitly targeted generic screen-number components. */
final class DoomPlayerHud implements ComponentReferenceBinder, ComponentUpdateCallbacks {
    private @Nullable DoomPlayerState playerState;
    private @Nullable ScreenNumber healthNumber;
    private @Nullable ScreenNumber ammoNumber;

    @Override
    public void bindReferences(ComponentReferenceResolver references) {
        ComponentReferenceResolver validReferences = Objects.requireNonNull(references, "references");
        playerState =
                validReferences.component(DoomedCorridorsRuntimeTypes.HUD_PLAYER_STATE_PROPERTY, DoomPlayerState.class);
        healthNumber =
                validReferences.component(DoomedCorridorsRuntimeTypes.HUD_HEALTH_NUMBER_PROPERTY, ScreenNumber.class);
        ammoNumber =
                validReferences.component(DoomedCorridorsRuntimeTypes.HUD_AMMO_NUMBER_PROPERTY, ScreenNumber.class);
    }

    @Override
    public void onFrameUpdate(FrameUpdateContext update) {
        Objects.requireNonNull(update, "update");
        requiredHealthNumber().setValue(requiredPlayerState().health());
        requiredAmmoNumber().setValue(requiredPlayerState().bullets());
    }

    private DoomPlayerState requiredPlayerState() {
        return Objects.requireNonNull(playerState, "player state has not been bound");
    }

    private ScreenNumber requiredHealthNumber() {
        return Objects.requireNonNull(healthNumber, "health number has not been bound");
    }

    private ScreenNumber requiredAmmoNumber() {
        return Objects.requireNonNull(ammoNumber, "ammo number has not been bound");
    }
}
