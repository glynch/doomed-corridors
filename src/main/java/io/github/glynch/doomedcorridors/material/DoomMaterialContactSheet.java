package io.github.glynch.doomedcorridors.material;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.imageio.ImageIO;

/** Writes imported map materials as a deterministic PNG for manual inspection. */
public final class DoomMaterialContactSheet {
    private static final int COLUMN_COUNT = 4;
    private static final int CELL_WIDTH = 280;
    private static final int CELL_HEIGHT = 144;
    private static final int IMAGE_WIDTH = 256;
    private static final int IMAGE_HEIGHT = 128;
    private static final int PADDING = 8;
    private static final int BACKGROUND = 0xff202124;
    private static final int CHECKER_DARK = 0xff777777;
    private static final int CHECKER_LIGHT = 0xff999999;

    /** Writes all wall textures followed by all flats in alphabetical order. */
    public void write(DoomMapMaterials materials, Path output) throws IOException {
        Objects.requireNonNull(materials, "materials");
        Objects.requireNonNull(output, "output");
        List<DoomMaterial> entries = entries(materials);
        int rowCount = Math.max(1, (entries.size() + COLUMN_COUNT - 1) / COLUMN_COUNT);
        int width = COLUMN_COUNT * CELL_WIDTH;
        int height = rowCount * CELL_HEIGHT;
        int[] sheet = new int[Math.multiplyExact(width, height)];
        java.util.Arrays.fill(sheet, BACKGROUND);
        for (int index = 0; index < entries.size(); index++) {
            drawEntry(sheet, width, entries.get(index).image(), index);
        }

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, width, height, sheet, 0, width);
        Path normalizedOutput = output.toAbsolutePath().normalize();
        Files.createDirectories(normalizedOutput.getParent());
        if (!ImageIO.write(image, "png", normalizedOutput.toFile())) {
            throw new IOException("Java runtime does not provide a PNG writer");
        }
    }

    private static List<DoomMaterial> entries(DoomMapMaterials materials) {
        List<DoomMaterial> entries = new ArrayList<>(
                materials.wallTextures().size() + materials.flats().size());
        entries.addAll(materials.wallTextures().values());
        entries.addAll(materials.flats().values());
        entries.sort(Comparator.comparing(DoomMaterial::kind).thenComparing(DoomMaterial::name));
        return entries;
    }

    private static void drawEntry(int[] sheet, int sheetWidth, RgbaImage source, int index) {
        double scale = Math.min(
                2.0,
                Math.min(IMAGE_WIDTH / (double) source.width(), IMAGE_HEIGHT / (double) source.height()));
        int width = Math.max(1, (int) Math.floor(source.width() * scale));
        int height = Math.max(1, (int) Math.floor(source.height() * scale));
        int cellX = index % COLUMN_COUNT * CELL_WIDTH;
        int cellY = index / COLUMN_COUNT * CELL_HEIGHT;
        int x = cellX + PADDING + (IMAGE_WIDTH - width) / 2;
        int y = cellY + PADDING + (IMAGE_HEIGHT - height) / 2;
        drawCheckerboard(sheet, sheetWidth, x, y, width, height);
        drawImage(sheet, sheetWidth, x, y, width, height, source);
    }

    private static void drawCheckerboard(
            int[] sheet, int sheetWidth, int x, int y, int width, int height) {
        for (int targetY = 0; targetY < height; targetY++) {
            for (int targetX = 0; targetX < width; targetX++) {
                boolean dark = ((targetX / 8 + targetY / 8) & 1) == 0;
                sheet[(y + targetY) * sheetWidth + x + targetX] = dark ? CHECKER_DARK : CHECKER_LIGHT;
            }
        }
    }

    private static void drawImage(
            int[] sheet, int sheetWidth, int x, int y, int width, int height, RgbaImage source) {
        for (int targetY = 0; targetY < height; targetY++) {
            int sourceY = targetY * source.height() / height;
            for (int targetX = 0; targetX < width; targetX++) {
                int sourceX = targetX * source.width() / width;
                int rgba = source.rgba(sourceX, sourceY);
                blend(sheet, (y + targetY) * sheetWidth + x + targetX, rgba);
            }
        }
    }

    private static void blend(int[] sheet, int offset, int rgba) {
        int alpha = rgba & 0xff;
        if (alpha == 0) {
            return;
        }
        if (alpha == 0xff) {
            sheet[offset] = rgba >>> 8 | 0xff000000;
            return;
        }
        int background = sheet[offset];
        int inverse = 0xff - alpha;
        int red = ((rgba >>> 24) * alpha + (background >>> 16 & 0xff) * inverse) / 0xff;
        int green = ((rgba >>> 16 & 0xff) * alpha + (background >>> 8 & 0xff) * inverse) / 0xff;
        int blue = ((rgba >>> 8 & 0xff) * alpha + (background & 0xff) * inverse) / 0xff;
        sheet[offset] = 0xff000000 | red << 16 | green << 8 | blue;
    }
}
