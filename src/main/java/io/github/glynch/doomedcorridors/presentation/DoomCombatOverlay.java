/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.presentation;

import io.github.glynch.doomedcorridors.material.RgbaImage;
import io.github.glynch.jscene3d.math.Color;
import io.github.glynch.jscene3d.render.Overlay;
import io.github.glynch.jscene3d.render.OverlayCanvas;
import io.github.glynch.jscene3d.render.OverlayImage;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** WAD-image overlay adapter for the first-person weapon and minimal health/ammo HUD. */
public final class DoomCombatOverlay implements Overlay {
    private static final float REFERENCE_WIDTH = 320.0F;
    private static final float REFERENCE_HEIGHT = 200.0F;
    private static final float HUD_MARGIN = 6.0F;
    private static final float HUD_PADDING = 3.0F;

    private final DoomCombatPresentationState state;
    private final DoomCombatPresentationRules rules;
    private final Map<String, OverlayImage> images;

    /** Converts every required combat patch into immutable overlay image storage. */
    public DoomCombatOverlay(DoomCombatAssets assets, DoomCombatPresentationState state) {
        DoomCombatAssets validAssets = Objects.requireNonNull(assets, "assets");
        this.state = Objects.requireNonNull(state, "state");
        rules = validAssets.rules();
        Map<String, OverlayImage> converted = new LinkedHashMap<>();
        validAssets.images().forEach((name, patch) -> converted.put(name, overlayImage(patch.image())));
        images = Map.copyOf(converted);
    }

    /** Draws the current weapon above compact WAD-glyph resource panels. */
    @Override
    public void paint(OverlayCanvas canvas, int width, int height) {
        Objects.requireNonNull(canvas, "canvas");
        float scale = scale(width, height);
        drawWeapon(canvas, width, height, scale);
        drawHealth(canvas, height, scale);
        drawAmmo(canvas, width, height, scale);
    }

    /** Draws the centered first-person weapon at the bottom edge. */
    private void drawWeapon(OverlayCanvas canvas, int width, int height, float scale) {
        OverlayImage weapon = image(state.weaponFrame());
        float imageWidth = weapon.width() * scale;
        float imageHeight = weapon.height() * scale;
        float x = (width - imageWidth) * 0.5F;
        float y = height - imageHeight;
        canvas.image(weapon.fullRegion(), x, y, imageWidth, imageHeight, Color.WHITE, 1.0F);
    }

    /** Draws health plus a percent sign in the lower-left corner. */
    private void drawHealth(OverlayCanvas canvas, int height, float scale) {
        List<OverlayImage> number = numberImages(state.health());
        OverlayImage percent = image(rules.hud().percent());
        float contentWidth = imageWidth(number, scale) + percent.width() * scale;
        float contentHeight = Math.max(imageHeight(number), percent.height()) * scale;
        float panelWidth = contentWidth + HUD_PADDING * 2.0F * scale;
        float panelHeight = contentHeight + HUD_PADDING * 2.0F * scale;
        float x = HUD_MARGIN * scale;
        float y = height - (HUD_MARGIN * scale + panelHeight);
        canvas.rectangle(x, y, panelWidth, panelHeight, Color.BLACK, 0.72F);
        float contentX = x + HUD_PADDING * scale;
        float contentY = y + HUD_PADDING * scale;
        drawImages(canvas, number, contentX, contentY, contentHeight, scale);
        float percentX = contentX + imageWidth(number, scale);
        drawImage(canvas, percent, percentX, contentY, contentHeight, scale);
    }

    /** Draws bullet ammunition right-aligned in the lower-right corner. */
    private void drawAmmo(OverlayCanvas canvas, int width, int height, float scale) {
        List<OverlayImage> number = numberImages(state.bullets());
        float contentWidth = imageWidth(number, scale);
        float contentHeight = imageHeight(number) * scale;
        float panelWidth = contentWidth + HUD_PADDING * 2.0F * scale;
        float panelHeight = contentHeight + HUD_PADDING * 2.0F * scale;
        float x = width - (HUD_MARGIN * scale + panelWidth);
        float y = height - (HUD_MARGIN * scale + panelHeight);
        canvas.rectangle(x, y, panelWidth, panelHeight, Color.BLACK, 0.72F);
        drawImages(canvas, number, x + HUD_PADDING * scale, y + HUD_PADDING * scale, contentHeight, scale);
    }

    /** Draws a left-to-right image sequence aligned to the bottom of its content box. */
    private static void drawImages(
            OverlayCanvas canvas,
            List<OverlayImage> values,
            float x,
            float y,
            float contentHeight,
            float scale) {
        float cursor = x;
        for (OverlayImage value : values) {
            drawImage(canvas, value, cursor, y, contentHeight, scale);
            cursor += value.width() * scale;
        }
    }

    /** Draws one bottom-aligned nearest-sampled source image. */
    private static void drawImage(
            OverlayCanvas canvas,
            OverlayImage value,
            float x,
            float y,
            float contentHeight,
            float scale) {
        float height = value.height() * scale;
        canvas.image(value.fullRegion(), x, y + contentHeight - height,
                value.width() * scale, height, Color.WHITE, 1.0F);
    }

    /** Resolves non-negative decimal digits into imported WAD glyphs. */
    private List<OverlayImage> numberImages(int value) {
        String text = Integer.toString(value);
        return text.chars()
                .map(character -> character - '0')
                .mapToObj(digit -> image(rules.hud().digits().get(digit)))
                .toList();
    }

    /** Returns summed scaled width. */
    private static float imageWidth(List<OverlayImage> values, float scale) {
        return values.stream().mapToInt(OverlayImage::width).sum() * scale;
    }

    /** Returns maximum unscaled height. */
    private static int imageHeight(List<OverlayImage> values) {
        return values.stream().mapToInt(OverlayImage::height).max().orElse(0);
    }

    /** Returns one required converted image. */
    private OverlayImage image(String name) {
        OverlayImage image = images.get(name);
        if (image == null) {
            throw new IllegalStateException("Overlay image was not imported: " + name);
        }
        return image;
    }

    /** Converts renderer-independent RGBA storage to an immutable renderer image. */
    private static OverlayImage overlayImage(RgbaImage source) {
        return OverlayImage.srgbRgba(source.width(), source.height(), source.pixels());
    }

    /** Preserves classic pixel proportions while fitting the current viewport. */
    private static float scale(int width, int height) {
        float fitted = Math.min(width / REFERENCE_WIDTH, height / REFERENCE_HEIGHT);
        return fitted < 1.0F ? 1.0F : fitted;
    }
}
