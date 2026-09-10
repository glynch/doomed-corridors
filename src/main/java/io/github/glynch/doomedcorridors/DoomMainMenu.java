/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import io.github.glynch.jscene3d.game.application.ApplicationCommand;
import io.github.glynch.jscene3d.game.application.ApplicationControl;
import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.game.input.InputWorldModule;
import io.github.glynch.jscene3d.game.input.PointerSnapshot;
import io.github.glynch.jscene3d.game.presentation.OverlayImageResource;
import io.github.glynch.jscene3d.game.presentation.OverlayRegistration;
import io.github.glynch.jscene3d.game.presentation.PresentationWorldModule;
import io.github.glynch.jscene3d.math.Color;
import io.github.glynch.jscene3d.project.runtime.FixedUpdateContext;
import io.github.glynch.jscene3d.project.runtime.extension.ComponentUpdateCallbacks;
import io.github.glynch.jscene3d.render.Overlay;
import io.github.glynch.jscene3d.render.OverlayCanvas;
import io.github.glynch.jscene3d.render.OverlayImage;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/** Project-authored Doom menu presentation and semantic selection behavior. */
final class DoomMainMenu implements ComponentUpdateCallbacks, Overlay, AutoCloseable {
    private static final float REFERENCE_WIDTH = 320.0F;
    private static final float REFERENCE_HEIGHT = 200.0F;
    private static final float ITEM_CENTER_X = 160.0F;
    private static final float ITEM_START_Y = 112.0F;
    private static final float ITEM_SPACING = 24.0F;
    private static final float ITEM_HIT_WIDTH = 180.0F;
    private static final float ITEM_HIT_HEIGHT = 22.0F;
    private static final Color DIMMED = Color.srgb(0x777777);

    private final InputWorldModule input;
    private final ApplicationControl application;
    private final OverlayImage background;
    private final OverlayImage title;
    private final OverlayImage resume;
    private final OverlayImage newGame;
    private final OverlayImage quit;
    private final OverlayImage firstCursor;
    private final OverlayImage secondCursor;
    private final InputAction previousAction;
    private final InputAction nextAction;
    private final InputAction confirmAction;
    private final InputAction backAction;
    private final OverlayRegistration registration;

    private int selectedIndex;
    private long cursorTick;

    /** Shared authored image resources required to paint one menu. */
    record Images(
            OverlayImageResource background,
            OverlayImageResource title,
            OverlayImageResource resume,
            OverlayImageResource newGame,
            OverlayImageResource quit,
            OverlayImageResource firstCursor,
            OverlayImageResource secondCursor) {
        Images {
            Objects.requireNonNull(background, "background");
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(resume, "resume");
            Objects.requireNonNull(newGame, "newGame");
            Objects.requireNonNull(quit, "quit");
            Objects.requireNonNull(firstCursor, "firstCursor");
            Objects.requireNonNull(secondCursor, "secondCursor");
        }
    }

    /** Semantic input actions used to navigate and activate the menu. */
    record Actions(String previous, String next, String confirm, String back) {
        Actions {
            Objects.requireNonNull(previous, "previous");
            Objects.requireNonNull(next, "next");
            Objects.requireNonNull(confirm, "confirm");
            Objects.requireNonNull(back, "back");
        }
    }

    /** Retains authored images and semantic actions without owning their shared resources. */
    DoomMainMenu(
            InputWorldModule input,
            ApplicationControl application,
            PresentationWorldModule presentation,
            Images images,
            Actions actions) {
        this.input = Objects.requireNonNull(input, "input");
        this.application = Objects.requireNonNull(application, "application");
        Images validImages = Objects.requireNonNull(images, "images");
        Actions validActions = Objects.requireNonNull(actions, "actions");
        this.background = validImages.background().image();
        this.title = validImages.title().image();
        this.resume = validImages.resume().image();
        this.newGame = validImages.newGame().image();
        this.quit = validImages.quit().image();
        this.firstCursor = validImages.firstCursor().image();
        this.secondCursor = validImages.secondCursor().image();
        this.previousAction = new InputAction(validActions.previous());
        this.nextAction = new InputAction(validActions.next());
        this.confirmAction = new InputAction(validActions.confirm());
        this.backAction = new InputAction(validActions.back());
        registration = Objects.requireNonNull(presentation, "presentation").registerOverlay(this);
    }

    @Override
    public void onBeforePhysics(FixedUpdateContext update) {
        Objects.requireNonNull(update, "update");
        List<ApplicationCommand> items = items();
        var snapshot = input.snapshot();
        selectedIndex = Math.min(selectedIndex, items.size() - 1);
        if (snapshot.wasPressed(previousAction)) {
            selectedIndex = Math.floorMod(selectedIndex - 1, items.size());
        }
        if (snapshot.wasPressed(nextAction)) {
            selectedIndex = (selectedIndex + 1) % items.size();
        }
        OptionalInt pointedIndex = snapshot.pointer()
                .map(pointer -> pointedIndex(pointer, items.size()))
                .orElseGet(OptionalInt::empty);
        pointedIndex.ifPresent(index -> selectedIndex = index);
        if (snapshot.wasPressed(backAction) && application.canResume()) {
            application.request(ApplicationCommand.RESUME);
        } else if (snapshot.pointer().filter(PointerSnapshot::primaryPressed).isPresent() && pointedIndex.isPresent()) {
            application.request(items.get(pointedIndex.getAsInt()));
        } else if (snapshot.wasPressed(confirmAction)) {
            application.request(items.get(selectedIndex));
        }
        cursorTick = update.tick();
    }

    @Override
    public void paint(OverlayCanvas canvas, int width, int height) {
        Objects.requireNonNull(canvas, "canvas");
        MenuLayout layout = MenuLayout.forViewport(width, height);
        canvas.image(background.fullRegion(), 0.0F, 0.0F, width, height, Color.WHITE, 1.0F);
        canvas.rectangle(0.0F, 0.0F, width, height, Color.BLACK, 0.42F);
        drawCentered(
                canvas,
                title,
                layout.originX(),
                layout.originY() + 28.0F * layout.scale(),
                layout.scale() * 1.5F,
                Color.WHITE);
        List<ApplicationCommand> items = items();
        for (int index = 0; index < items.size(); index++) {
            float centerY = layout.itemCenterY(index);
            OverlayImage item = image(items.get(index));
            Color tint = index == selectedIndex ? Color.WHITE : DIMMED;
            float left = drawCentered(canvas, item, layout.originX(), centerY, layout.scale(), tint);
            if (index == selectedIndex) {
                OverlayImage cursor = (cursorTick / 8L & 1L) == 0L ? firstCursor : secondCursor;
                float cursorHeight = cursor.height() * layout.scale();
                canvas.image(
                        cursor.fullRegion(),
                        left - (cursor.width() + 5.0F) * layout.scale(),
                        centerY - cursorHeight * 0.5F,
                        cursor.width() * layout.scale(),
                        cursorHeight,
                        Color.WHITE,
                        1.0F);
            }
        }
    }

    /** Returns the menu row under one absolute pointer, when it is inside an option's hit region. */
    private static OptionalInt pointedIndex(PointerSnapshot pointer, int itemCount) {
        MenuLayout layout = MenuLayout.forViewport(pointer.viewportWidth(), pointer.viewportHeight());
        float halfWidth = ITEM_HIT_WIDTH * layout.scale() * 0.5F;
        float halfHeight = ITEM_HIT_HEIGHT * layout.scale() * 0.5F;
        float centerX = layout.originX() + ITEM_CENTER_X * layout.scale();
        for (int index = 0; index < itemCount; index++) {
            float centerY = layout.itemCenterY(index);
            if (pointer.x() >= centerX - halfWidth
                    && pointer.x() <= centerX + halfWidth
                    && pointer.y() >= centerY - halfHeight
                    && pointer.y() <= centerY + halfHeight) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    @Override
    public void close() {
        registration.close();
    }

    /** Returns the command currently selected by the visible menu. */
    ApplicationCommand selectedCommand() {
        return items().get(selectedIndex);
    }

    /** Builds the visible command order from the host's resumable-session state. */
    private List<ApplicationCommand> items() {
        return application.canResume()
                ? List.of(ApplicationCommand.RESUME, ApplicationCommand.NEW_GAME, ApplicationCommand.QUIT)
                : List.of(ApplicationCommand.NEW_GAME, ApplicationCommand.QUIT);
    }

    /** Selects the authored image for one visible command. */
    private OverlayImage image(ApplicationCommand command) {
        return switch (command) {
            case RESUME -> resume;
            case NEW_GAME -> newGame;
            case QUIT -> quit;
            case SHOW_MENU -> throw new IllegalArgumentException("show-menu is not a menu item");
        };
    }

    /** Draws one image around the horizontal center and returns its left edge. */
    private static float drawCentered(
            OverlayCanvas canvas, OverlayImage image, float originX, float centerY, float scale, Color tint) {
        float width = image.width() * scale;
        float height = image.height() * scale;
        float left = originX + ITEM_CENTER_X * scale - width * 0.5F;
        canvas.image(image.fullRegion(), left, centerY - height * 0.5F, width, height, tint, 1.0F);
        return left;
    }

    /** Aspect-fitted reference-space menu layout shared by rendering and pointer hit-testing. */
    private record MenuLayout(float scale, float originX, float originY) {
        /** Fits the fixed reference canvas inside one positive viewport. */
        private static MenuLayout forViewport(int width, int height) {
            float scale = Math.min(width / REFERENCE_WIDTH, height / REFERENCE_HEIGHT);
            return new MenuLayout(
                    scale, (width - REFERENCE_WIDTH * scale) * 0.5F, (height - REFERENCE_HEIGHT * scale) * 0.5F);
        }

        /** Returns one option row's vertical centre. */
        private float itemCenterY(int index) {
            return originY + (ITEM_START_Y + index * ITEM_SPACING) * scale;
        }
    }
}
