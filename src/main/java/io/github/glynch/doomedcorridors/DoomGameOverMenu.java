/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.doomedcorridors.internal.DoomedCorridorsRuntimeTypes;
import io.github.glynch.jscene3d.game.application.ApplicationCommand;
import io.github.glynch.jscene3d.game.application.ApplicationControl;
import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.game.input.InputWorldModule;
import io.github.glynch.jscene3d.game.input.PointerSnapshot;
import io.github.glynch.jscene3d.project.runtime.Entity;
import io.github.glynch.jscene3d.project.runtime.FixedUpdateContext;
import io.github.glynch.jscene3d.project.runtime.World;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceBinder;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentReferenceResolver;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentUpdateCallbacks;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/** Controls terminal menu selection while generic screen components own its authored presentation. */
final class DoomGameOverMenu implements ComponentReferenceBinder, ComponentUpdateCallbacks {
    private static final List<ApplicationCommand> COMMANDS =
            List.of(ApplicationCommand.NEW_GAME, ApplicationCommand.RETURN_TO_MENU);

    private final World world;
    private final InputWorldModule input;
    private final ApplicationControl application;
    private final Actions actions;
    private final Layout layout;
    private @Nullable Entity restartCursor;
    private @Nullable Entity mainMenuCursor;
    private int selectedIndex;

    /** Semantic input actions used to navigate and activate the terminal menu. */
    record Actions(InputAction previous, InputAction next, InputAction confirm) {
        Actions {
            Objects.requireNonNull(previous, "previous");
            Objects.requireNonNull(next, "next");
            Objects.requireNonNull(confirm, "confirm");
        }

        /** Creates semantic input identities from descriptor-authored action names. */
        static Actions of(String previous, String next, String confirm) {
            return new Actions(new InputAction(previous), new InputAction(next), new InputAction(confirm));
        }
    }

    /** Authored reference-space hit-test layout corresponding to the generic screen regions. */
    record Layout(
            float referenceWidth,
            float referenceHeight,
            float itemCenterX,
            float itemStartY,
            float itemSpacing,
            float itemHitWidth,
            float itemHitHeight) {
        Layout {
            requirePositive(referenceWidth, "referenceWidth");
            requirePositive(referenceHeight, "referenceHeight");
            requireFinite(itemCenterX, "itemCenterX");
            requireFinite(itemStartY, "itemStartY");
            requirePositive(itemSpacing, "itemSpacing");
            requirePositive(itemHitWidth, "itemHitWidth");
            requirePositive(itemHitHeight, "itemHitHeight");
        }

        /** Returns the authored option under one absolute pointer, if any. */
        OptionalInt pointedIndex(PointerSnapshot pointer) {
            float scale =
                    Math.min(pointer.viewportWidth() / referenceWidth, pointer.viewportHeight() / referenceHeight);
            float originX = (pointer.viewportWidth() - referenceWidth * scale) * 0.5F;
            float originY = (pointer.viewportHeight() - referenceHeight * scale) * 0.5F;
            float centerX = originX + itemCenterX * scale;
            float halfWidth = itemHitWidth * scale * 0.5F;
            float halfHeight = itemHitHeight * scale * 0.5F;
            for (int index = 0; index < COMMANDS.size(); index++) {
                float centerY = originY + (itemStartY + index * itemSpacing) * scale;
                if (pointer.x() >= centerX - halfWidth
                        && pointer.x() <= centerX + halfWidth
                        && pointer.y() >= centerY - halfHeight
                        && pointer.y() <= centerY + halfHeight) {
                    return OptionalInt.of(index);
                }
            }
            return OptionalInt.empty();
        }

        /** Requires a finite positive layout value. */
        private static void requirePositive(float value, String name) {
            requireFinite(value, name);
            if (value <= 0.0F) {
                throw new IllegalArgumentException(name + " must be positive");
            }
        }

        /** Requires a finite layout value. */
        private static void requireFinite(float value, String name) {
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException(name + " must be finite");
            }
        }
    }

    /** Retains only terminal behavior; presentation remains in generic authored screen entities. */
    DoomGameOverMenu(
            World world, InputWorldModule input, ApplicationControl application, Actions actions, Layout layout) {
        this.world = Objects.requireNonNull(world, "world");
        this.input = Objects.requireNonNull(input, "input");
        this.application = Objects.requireNonNull(application, "application");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    @Override
    public void bindReferences(ComponentReferenceResolver references) {
        ComponentReferenceResolver validReferences = Objects.requireNonNull(references, "references");
        restartCursor = validReferences.entity(DoomedCorridorsRuntimeTypes.GAME_OVER_RESTART_CURSOR_PROPERTY);
        mainMenuCursor = validReferences.entity(DoomedCorridorsRuntimeTypes.GAME_OVER_MAIN_MENU_CURSOR_PROPERTY);
    }

    @Override
    public void onBeforePhysics(FixedUpdateContext update) {
        Objects.requireNonNull(update, "update");
        var snapshot = input.snapshot();
        if (snapshot.wasPressed(actions.previous())) {
            select(Math.floorMod(selectedIndex - 1, COMMANDS.size()));
        }
        if (snapshot.wasPressed(actions.next())) {
            select((selectedIndex + 1) % COMMANDS.size());
        }
        OptionalInt pointedIndex = snapshot.pointer().map(layout::pointedIndex).orElseGet(OptionalInt::empty);
        pointedIndex.ifPresent(this::select);
        if (snapshot.pointer().filter(PointerSnapshot::primaryPressed).isPresent() && pointedIndex.isPresent()) {
            application.request(COMMANDS.get(pointedIndex.getAsInt()));
        } else if (snapshot.wasPressed(actions.confirm())) {
            application.request(selectedCommand());
        }
    }

    /** Returns the host transition currently selected by the terminal menu. */
    ApplicationCommand selectedCommand() {
        return COMMANDS.get(selectedIndex);
    }

    /** Updates semantic selection and the two explicitly authored generic cursor entities together. */
    private void select(int index) {
        if (selectedIndex == index) {
            return;
        }
        selectedIndex = index;
        setEnabled(Objects.requireNonNull(restartCursor, "restart cursor has not been bound"), index == 0);
        setEnabled(Objects.requireNonNull(mainMenuCursor, "main-menu cursor has not been bound"), index == 1);
    }

    /** Applies one cursor's desired local state only when it changes. */
    private void setEnabled(Entity entity, boolean enabled) {
        if (entity.isLocallyEnabled() == enabled) {
            return;
        }
        if (enabled) {
            world.enable(entity);
        } else {
            world.disable(entity);
        }
    }
}
