/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.jscene3d.game.application.ApplicationCommand;
import io.github.glynch.jscene3d.game.application.ApplicationControl;
import io.github.glynch.jscene3d.game.input.ActionSnapshot;
import io.github.glynch.jscene3d.game.input.InputAction;
import io.github.glynch.jscene3d.game.input.PointerSnapshot;
import io.github.glynch.jscene3d.game.input.ProjectInput;
import io.github.glynch.jscene3d.game.presentation.OverlayImageResource;
import io.github.glynch.jscene3d.project.runtime.FixedUpdateContext;
import io.github.glynch.jscene3d.render.OverlayImage;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Specifies startup, paused, and semantic selection behavior of the authored main menu. */
final class DoomMainMenuTest {
    private static final InputAction PREVIOUS = new InputAction("menu-previous");
    private static final InputAction NEXT = new InputAction("menu-next");
    private static final InputAction CONFIRM = new InputAction("menu-confirm");
    private static final InputAction BACK = new InputAction("menu");

    @Test
    void offersNewGameAndQuitAtStartup() {
        ProjectInput input = ProjectInput.empty();
        RecordingApplicationControl application = new RecordingApplicationControl(false);
        try (DoomMainMenu menu = menu(input, application)) {
            assertThat(menu.selectedCommand()).isEqualTo(ApplicationCommand.NEW_GAME);

            update(menu, input, NEXT, 0L);
            assertThat(menu.selectedCommand()).isEqualTo(ApplicationCommand.QUIT);

            update(menu, input, CONFIRM, 1L);
            assertThat(application.requested).isEqualTo(ApplicationCommand.QUIT);
        }
    }

    @Test
    void offersResumeFirstAndTreatsBackAsResumeWhenGameplayIsPaused() {
        ProjectInput input = ProjectInput.empty();
        RecordingApplicationControl application = new RecordingApplicationControl(true);
        try (DoomMainMenu menu = menu(input, application)) {
            assertThat(menu.selectedCommand()).isEqualTo(ApplicationCommand.RESUME);

            update(menu, input, BACK, 0L);

            assertThat(application.requested).isEqualTo(ApplicationCommand.RESUME);
        }
    }

    @Test
    void activatesThePausedOptionUnderThePointerInsteadOfTheInitiallySelectedResumeOption() {
        ProjectInput input = ProjectInput.empty();
        RecordingApplicationControl application = new RecordingApplicationControl(true);
        try (DoomMainMenu menu = menu(input, application)) {
            click(menu, input, 136.0, 0L);
            assertThat(application.requested).isEqualTo(ApplicationCommand.NEW_GAME);

            click(menu, input, 160.0, 1L);
            assertThat(application.requested).isEqualTo(ApplicationCommand.QUIT);
        }
    }

    @Test
    void ignoresBackAtStartupAndClicksOutsideMenuRows() {
        ProjectInput input = ProjectInput.empty();
        RecordingApplicationControl application = new RecordingApplicationControl(false);
        try (DoomMainMenu menu = menu(input, application)) {
            update(menu, input, BACK, 0L);
            click(menu, input, 10.0, 1L);

            assertThat(application.requested).isNull();
        }
    }

    /** Creates one menu with renderer-independent one-pixel images. */
    private static DoomMainMenu menu(ProjectInput input, ApplicationControl application) {
        TestPresentationWorldModule presentation = new TestPresentationWorldModule();
        OverlayImageResource image = OverlayImageResource.owning(
                OverlayImage.srgbRgba(1, 1, new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255}));
        return new DoomMainMenu(
                input,
                application,
                presentation,
                new DoomMainMenu.Images(image, image, image, image, image, image, image),
                new DoomMainMenu.Actions(PREVIOUS.name(), NEXT.name(), CONFIRM.name(), BACK.name()));
    }

    /** Publishes one semantic press and advances the menu once. */
    private static void update(DoomMainMenu menu, ProjectInput input, InputAction action, long tick) {
        input.publish(ActionSnapshot.builder().pressed(action).build());
        menu.onBeforePhysics(new FixedUpdateContext(tick, Duration.ofMillis(20), Duration.ofMillis(tick * 20L)));
    }

    /** Clicks the horizontal centre of one reference-resolution menu row. */
    private static void click(DoomMainMenu menu, ProjectInput input, double y, long tick) {
        input.publish(ActionSnapshot.builder()
                .pointer(new PointerSnapshot(160.0, y, 320, 200, true, true, false))
                .build());
        menu.onBeforePhysics(new FixedUpdateContext(tick, Duration.ofMillis(20), Duration.ofMillis(tick * 20L)));
    }

    /** Captures host requests without owning a desktop session. */
    private static final class RecordingApplicationControl implements ApplicationControl {
        private final boolean resumable;
        private ApplicationCommand requested;

        private RecordingApplicationControl(boolean resumable) {
            this.resumable = resumable;
        }

        @Override
        public boolean canResume() {
            return resumable;
        }

        @Override
        public void request(ApplicationCommand command) {
            requested = command;
        }

        @Override
        public void close() {
            // Test adapter owns no resources.
        }
    }
}
