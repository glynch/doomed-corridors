/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.combat;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.glynch.doomedcorridors.actor.DoomActor;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalog;
import io.github.glynch.doomedcorridors.actor.DoomActorCatalogLoader;
import io.github.glynch.doomedcorridors.actor.DoomActorCategory;
import io.github.glynch.doomedcorridors.actor.DoomActorDefinition;
import io.github.glynch.doomedcorridors.actor.DoomActorSprite;
import io.github.glynch.doomedcorridors.actor.DoomActorSprites;
import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.material.RgbaImage;
import io.github.glynch.doomedcorridors.presentation.DoomCombatAssets;
import io.github.glynch.doomedcorridors.presentation.DoomCombatPresentationLoader;
import io.github.glynch.doomedcorridors.presentation.DoomCombatPresentationRules;
import io.github.glynch.doomedcorridors.presentation.DoomCombatPresentationState;
import io.github.glynch.doomedcorridors.presentation.DoomMapPresentation;
import io.github.glynch.doomedcorridors.world.DoomPlayerStart;
import io.github.glynch.doomedcorridors.world.DoomStaticGeometry;
import io.github.glynch.jscene3d.materials.BasicMaterial;
import io.github.glynch.jscene3d.objects.Billboard;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Specifies synchronization from headless combat state to actor billboards. */
final class DoomCombatMapPresentationTest {
    private static final int THING_INDEX = 9;

    /** Replaces idle material and patch geometry when an actor enters its death sequence. */
    @Test
    void appliesDeathFrameToActorBillboard() {
        DoomActor actor = actor();
        DoomCombatantState alive = new DoomCombatantState(actor, 0.5F, 1.5F, 20);
        DoomCombatPresentationRules rules = presentationRules();
        DoomCombatPresentationState state = new DoomCombatPresentationState(
                rules, combatState(alive));
        DoomCombatAssets assets = new DoomCombatAssets(
                rules,
                Map.of("POSSH0", sprite("POSSH0", 8, 3, 2, 3)),
                Map.of());
        DoomStaticGeometry geometry = new DoomStaticGeometry(
                List.of(), new DoomPlayerStart(0.0F, 1.0F, 0.0F, 0.0F));
        DoomActorSprites idleSprites = new DoomActorSprites(
                Map.of("POSSA", sprite("POSSA1", 4, 6, 1, 6)));

        try (DoomMapPresentation presentation = DoomMapPresentation.create(
                geometry,
                new DoomMapMaterials(Map.of(), Map.of()),
                List.of(actor),
                idleSprites,
                assets,
                1.0F)) {
            Billboard billboard = (Billboard) presentation.scene().children().getFirst();
            BasicMaterial idleMaterial = billboard.material();
            DoomCombatantState dead = alive.withPose(
                            3.0F, 0.25F, -2.0F, DoomCombatantActivity.PURSUING)
                    .withHealth(0);
            state.apply(new DoomCombatUpdate(
                    combatState(dead),
                    List.of(new DoomCombatEvent(
                            DoomCombatEvent.Type.COMBATANT_KILLED, THING_INDEX, 0))));

            presentation.applyCombatState(state);

            assertThat(billboard.material()).isNotSameAs(idleMaterial);
            assertThat(billboard.anchor().x()).isEqualTo(0.25F);
            assertThat(billboard.anchor().y()).isZero();
            assertThat(billboard.scale().x()).isEqualTo(8.0F / 32.0F);
            assertThat(billboard.scale().y()).isEqualTo(3.0F / 32.0F);
            assertThat(billboard.position().x()).isEqualTo(3.0F);
            assertThat(billboard.position().y()).isEqualTo(0.25F);
            assertThat(billboard.position().z()).isEqualTo(-2.0F);
        }
    }

    /** Hides the billboard whose stable WAD thing index was collected by the headless session. */
    @Test
    void hidesCollectedPickupBillboard() {
        DoomActor pickup = pickup();
        DoomCombatPresentationRules rules = presentationRules();
        DoomCombatState initial = new DoomCombatState(
                100, 200, 50, 200, "pistol", List.of(), List.of());
        DoomCombatPresentationState state = new DoomCombatPresentationState(rules, initial);
        DoomCombatAssets assets = new DoomCombatAssets(rules, Map.of(), Map.of());
        DoomStaticGeometry geometry = new DoomStaticGeometry(
                List.of(), new DoomPlayerStart(0.0F, 1.0F, 0.0F, 0.0F));
        DoomActorSprites sprites = new DoomActorSprites(
                Map.of("CLIPA", sprite("CLIPA0", 4, 3, 2, 3)));

        try (DoomMapPresentation presentation = DoomMapPresentation.create(
                geometry,
                new DoomMapMaterials(Map.of(), Map.of()),
                List.of(pickup),
                sprites,
                assets,
                1.0F)) {
            Billboard billboard = (Billboard) presentation.scene().children().getFirst();
            DoomCombatState collected = new DoomCombatState(
                    100, 200, 60, 200, "pistol", List.of(), List.of(pickup.thingIndex()));
            state.apply(new DoomCombatUpdate(
                    collected,
                    List.of(new DoomCombatEvent(
                            DoomCombatEvent.Type.AMMUNITION_PICKED_UP,
                            pickup.thingIndex(),
                            10))));

            presentation.applyCombatState(state);

            assertThat(state.isPickupCollected(pickup.thingIndex())).isTrue();
            assertThat(billboard.isVisible()).isFalse();
        }
    }

    /** Creates a one-combatant state visible to the presentation. */
    private static DoomCombatState combatState(DoomCombatantState combatant) {
        return new DoomCombatState(
                100, 200, 50, 200, "pistol", List.of(combatant), List.of());
    }

    /** Creates the resolved actor shared by simulation and scene adaptation. */
    private static DoomActor actor() {
        DoomActorDefinition definition = new DoomActorDefinition(
                3004,
                "zombieman",
                "Zombieman",
                DoomActorCategory.ENEMY,
                Optional.of("POSSA"));
        return new DoomActor(THING_INDEX, definition, 2.0F, 0.0F, -1.0F, 0.0F);
    }

    /** Creates one collectable ammunition actor with a distinct source-map identity. */
    private static DoomActor pickup() {
        DoomActorDefinition definition = new DoomActorDefinition(
                2007,
                "ammunition-clip",
                "Ammunition Clip",
                DoomActorCategory.AMMUNITION,
                Optional.of("CLIPA"));
        return new DoomActor(15, definition, 1.0F, 0.0F, -1.0F, 0.0F);
    }

    /** Creates a transparent test patch with classic origin metadata. */
    private static DoomActorSprite sprite(
            String lumpName, int width, int height, int leftOffset, int topOffset) {
        return new DoomActorSprite(
                lumpName,
                lumpName,
                new RgbaImage(width, height, new byte[width * height * 4]),
                leftOffset,
                topOffset,
                List.of());
    }

    /** Loads the checked-in combat presentation through its validated companion rules. */
    private static DoomCombatPresentationRules presentationRules() {
        DoomActorCatalog actors = new DoomActorCatalogLoader()
                .load(Path.of("game/actors.json"))
                .catalog()
                .orElseThrow();
        DoomCombatRules combat = new DoomCombatRulesLoader()
                .load(Path.of("game/combat.json"), actors)
                .rules()
                .orElseThrow();
        return new DoomCombatPresentationLoader()
                .load(Path.of("game/combat-presentation.json"), combat)
                .rules()
                .orElseThrow();
    }
}
