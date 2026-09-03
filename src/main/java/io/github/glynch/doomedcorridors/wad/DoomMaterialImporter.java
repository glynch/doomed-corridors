package io.github.glynch.doomedcorridors.wad;

import io.github.glynch.doomedcorridors.map.DoomMap;
import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.material.DoomMaterial;
import io.github.glynch.doomedcorridors.material.RgbaImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Imports only the renderer-independent materials referenced by one decoded classic map. */
public final class DoomMaterialImporter {
    private static final int PALETTE_SIZE = 256 * 3;
    private static final int FLAT_SIZE = 64 * 64;

    /** Imports referenced flats and wall textures from the map's source archive. */
    public DoomMaterialImportResult importMap(WadArchive archive, DoomMap map) {
        Objects.requireNonNull(archive, "archive");
        Objects.requireNonNull(map, "map");
        List<WadDiagnostic> diagnostics = new ArrayList<>();
        try {
            WadLump paletteLump = requiredLump(archive, "PLAYPAL", "/materials/palette");
            byte[] palette = archive.read(paletteLump);
            if (palette.length < PALETTE_SIZE) {
                throw new ImportFailure(
                        "doom.material.palette-size",
                        "/materials/palette",
                        "PLAYPAL contains " + palette.length + " bytes; at least " + PALETTE_SIZE + " required");
            }
            Map<String, WadLump> flatLumps = flatLumps(archive);
            Map<String, DoomMaterial> flats = importFlats(archive, map, paletteLump, palette, flatLumps);
            Map<String, DoomMaterial> walls = importWallTextures(archive, map, paletteLump, palette);
            DoomMapMaterials materials = new DoomMapMaterials(walls, flats);
            return new DoomMaterialImportResult(Optional.of(materials), diagnostics);
        } catch (ImportFailure failure) {
            diagnostics.add(new WadDiagnostic(
                    WadDiagnostic.Severity.ERROR,
                    failure.code(),
                    archive.source(),
                    failure.location(),
                    failure.getMessage()));
        } catch (IOException exception) {
            diagnostics.add(new WadDiagnostic(
                    WadDiagnostic.Severity.ERROR,
                    "doom.material.read",
                    archive.source(),
                    "/materials",
                    "Cannot read material source data: " + exception.getMessage()));
        }
        return new DoomMaterialImportResult(Optional.empty(), diagnostics);
    }

    private static Map<String, DoomMaterial> importWallTextures(
            WadArchive archive, DoomMap map, WadLump paletteLump, byte[] palette) throws IOException {
        Set<String> names = wallTextureNames(map);
        if (names.isEmpty()) {
            return Map.of();
        }
        WadLump namesLump = requiredLump(archive, "PNAMES", "/materials/wall-textures/PNAMES");
        List<String> patchNames = parsePatchNames(archive.read(namesLump));
        Map<String, TextureDefinition> definitions = textureDefinitions(archive);
        Map<String, DoomMaterial> materials = new LinkedHashMap<>();
        for (String name : names) {
            TextureDefinition definition = definitions.get(name);
            if (definition == null) {
                throw new ImportFailure(
                        "doom.material.texture-missing",
                        "/materials/wall-textures/" + name,
                        "Referenced wall texture is not defined: " + name);
            }
            materials.put(
                    name,
                    composeTexture(archive, paletteLump, palette, namesLump, patchNames, definition));
        }
        return materials;
    }

    private static Set<String> wallTextureNames(DoomMap map) {
        Set<String> names = new TreeSet<>();
        for (DoomMap.Sidedef sidedef : map.sidedefs()) {
            addWallName(names, sidedef.upperTexture());
            addWallName(names, sidedef.lowerTexture());
            addWallName(names, sidedef.middleTexture());
        }
        return names;
    }

    private static void addWallName(Set<String> names, String name) {
        if (!name.equals("-")) {
            names.add(name);
        }
    }

    private static List<String> parsePatchNames(byte[] data) {
        ByteBuffer input = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (input.remaining() < Integer.BYTES) {
            throw materialData("PNAMES", "PNAMES does not contain a patch count");
        }
        int count = input.getInt();
        if (count < 0 || count > input.remaining() / 8) {
            throw materialData("PNAMES", "PNAMES patch-name table extends beyond the lump");
        }
        List<String> names = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            names.add(readName(input));
        }
        return List.copyOf(names);
    }

    private static Map<String, TextureDefinition> textureDefinitions(WadArchive archive) throws IOException {
        Map<String, TextureDefinition> definitions = new LinkedHashMap<>();
        WadLump texture1 = requiredLump(archive, "TEXTURE1", "/materials/wall-textures/TEXTURE1");
        addTextureDefinitions(definitions, texture1, archive.read(texture1));
        Optional<WadLump> texture2 = archive.lastLumpNamed("TEXTURE2");
        if (texture2.isPresent()) {
            addTextureDefinitions(definitions, texture2.orElseThrow(), archive.read(texture2.orElseThrow()));
        }
        return definitions;
    }

    private static void addTextureDefinitions(
            Map<String, TextureDefinition> definitions, WadLump source, byte[] data) {
        ByteBuffer input = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (input.remaining() < Integer.BYTES) {
            throw materialData(source.name(), source.name() + " does not contain a texture count");
        }
        int count = input.getInt();
        if (count < 0 || count > input.remaining() / Integer.BYTES) {
            throw materialData(source.name(), source.name() + " texture directory extends beyond the lump");
        }
        int[] offsets = new int[count];
        for (int index = 0; index < count; index++) {
            offsets[index] = input.getInt();
        }
        for (int index = 0; index < count; index++) {
            TextureDefinition definition = parseTextureDefinition(source, data, offsets[index], index);
            definitions.putIfAbsent(definition.name(), definition);
        }
    }

    private static TextureDefinition parseTextureDefinition(
            WadLump source, byte[] data, int offset, int index) {
        String location = source.name() + "/" + index;
        if (offset < 0 || offset > data.length - 22) {
            throw materialData(location, "Texture definition offset is outside " + source.name());
        }
        ByteBuffer input = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        input.position(offset);
        String name = readName(input);
        input.getInt();
        int width = Short.toUnsignedInt(input.getShort());
        int height = Short.toUnsignedInt(input.getShort());
        input.getInt();
        int patchCount = Short.toUnsignedInt(input.getShort());
        if (width == 0 || height == 0) {
            throw materialData(location, "Texture dimensions must be positive");
        }
        if (patchCount > input.remaining() / 10) {
            throw materialData(location, "Texture patch table extends beyond " + source.name());
        }
        List<PatchPlacement> patches = new ArrayList<>(patchCount);
        for (int patchIndex = 0; patchIndex < patchCount; patchIndex++) {
            patches.add(new PatchPlacement(
                    input.getShort(), input.getShort(), Short.toUnsignedInt(input.getShort())));
            input.getShort();
            input.getShort();
        }
        return new TextureDefinition(name, width, height, List.copyOf(patches), source);
    }

    private static DoomMaterial composeTexture(
            WadArchive archive,
            WadLump paletteLump,
            byte[] palette,
            WadLump namesLump,
            List<String> patchNames,
            TextureDefinition definition)
            throws IOException {
        byte[] target = new byte[Math.multiplyExact(Math.multiplyExact(definition.width(), definition.height()), 4)];
        LinkedHashSet<WadLump> sources = new LinkedHashSet<>();
        sources.add(paletteLump);
        sources.add(namesLump);
        sources.add(definition.source());
        for (PatchPlacement placement : definition.patches()) {
            if (placement.patchIndex() >= patchNames.size()) {
                throw materialData(
                        definition.name(),
                        "Texture references a patch index outside PNAMES: " + placement.patchIndex());
            }
            String patchName = patchNames.get(placement.patchIndex());
            String patchLocation = "/materials/wall-textures/" + definition.name() + "/" + patchName;
            WadLump patchLump = archive.lastLumpNamed(patchName)
                    .orElseThrow(() -> new ImportFailure(
                            "doom.material.patch-missing",
                            patchLocation,
                            "Referenced texture patch is missing: " + patchName));
            RgbaImage patch;
            try {
                patch = decodePatch(archive.read(patchLump), palette, patchName);
            } catch (ImportFailure failure) {
                throw new ImportFailure(
                        "doom.material.patch-data", patchLocation, failure.getMessage());
            }
            drawPatch(target, definition.width(), definition.height(), patch, placement);
            sources.add(patchLump);
        }
        return new DoomMaterial(
                definition.name(),
                DoomMaterial.Kind.WALL_TEXTURE,
                new RgbaImage(definition.width(), definition.height(), target),
                List.copyOf(sources));
    }

    private static RgbaImage decodePatch(byte[] data, byte[] palette, String name) {
        try {
            return DoomPatchDecoder.decode(data, palette, name).image();
        } catch (DoomPatchDataException exception) {
            throw materialData(name, exception.getMessage());
        }
    }

    private static void drawPatch(
            byte[] target, int width, int height, RgbaImage patch, PatchPlacement placement) {
        TextureCanvas canvas = new TextureCanvas(width, height, target);
        byte[] source = patch.pixels();
        for (int y = 0; y < patch.height(); y++) {
            for (int x = 0; x < patch.width(); x++) {
                int targetX = placement.originX() + x;
                int targetY = placement.originY() + y;
                if (targetX >= 0
                        && targetX < canvas.width
                        && targetY >= 0
                        && targetY < canvas.height) {
                    copyOpaquePixel(source, patch.width(), x, y, canvas, targetX, targetY);
                }
            }
        }
    }

    private static void copyOpaquePixel(
            byte[] source,
            int sourceWidth,
            int sourceX,
            int sourceY,
            TextureCanvas target,
            int targetX,
            int targetY) {
        int sourceOffset = (sourceY * sourceWidth + sourceX) * 4;
        if (source[sourceOffset + 3] == 0) {
            return;
        }
        int targetOffset = (targetY * target.width + targetX) * 4;
        System.arraycopy(source, sourceOffset, target.pixels, targetOffset, 4);
    }

    private static String readName(ByteBuffer input) {
        byte[] encoded = new byte[8];
        input.get(encoded);
        int length = 0;
        while (length < encoded.length && encoded[length] != 0) {
            length++;
        }
        return new String(encoded, 0, length, StandardCharsets.US_ASCII).toUpperCase(java.util.Locale.ROOT);
    }

    private static ImportFailure materialData(String location, String message) {
        return new ImportFailure("doom.material.data", "/materials/source/" + location, message);
    }

    private static Map<String, DoomMaterial> importFlats(
            WadArchive archive,
            DoomMap map,
            WadLump paletteLump,
            byte[] palette,
            Map<String, WadLump> flatLumps)
            throws IOException {
        Set<String> names = new TreeSet<>();
        for (DoomMap.Sector sector : map.sectors()) {
            addFlatName(names, sector.floorTexture());
            addFlatName(names, sector.ceilingTexture());
        }
        Map<String, DoomMaterial> materials = new LinkedHashMap<>();
        for (String name : names) {
            WadLump lump = flatLumps.get(name);
            if (lump == null) {
                throw new ImportFailure(
                        "doom.material.flat-missing",
                        "/materials/flats/" + name,
                        "Referenced flat is not present in the flat namespace: " + name);
            }
            byte[] indexes = archive.read(lump);
            if (indexes.length != FLAT_SIZE) {
                throw new ImportFailure(
                        "doom.material.flat-size",
                        "/materials/flats/" + name,
                        "Flat " + name + " contains " + indexes.length + " bytes; expected " + FLAT_SIZE);
            }
            materials.put(
                    name,
                    new DoomMaterial(
                            name,
                            DoomMaterial.Kind.FLAT,
                            indexedImage(64, 64, indexes, palette),
                            List.of(paletteLump, lump)));
        }
        return materials;
    }

    private static void addFlatName(Set<String> names, String name) {
        if (!name.equals("F_SKY1")) {
            names.add(name);
        }
    }

    private static Map<String, WadLump> flatLumps(WadArchive archive) {
        Map<String, WadLump> result = new LinkedHashMap<>();
        boolean inNamespace = false;
        for (WadLump lump : archive.lumps()) {
            if (lump.name().equals("F_START")) {
                inNamespace = true;
            } else if (lump.name().equals("F_END")) {
                inNamespace = false;
            } else if (inNamespace) {
                result.put(lump.name(), lump);
            }
        }
        return result;
    }

    private static WadLump requiredLump(WadArchive archive, String name, String location) {
        return archive.lastLumpNamed(name)
                .orElseThrow(() -> new ImportFailure(
                        "doom.material.lump-missing", location, "Required material lump is missing: " + name));
    }

    private static RgbaImage indexedImage(int width, int height, byte[] indexes, byte[] palette) {
        byte[] rgba = new byte[indexes.length * 4];
        for (int index = 0; index < indexes.length; index++) {
            int paletteOffset = Byte.toUnsignedInt(indexes[index]) * 3;
            int targetOffset = index * 4;
            rgba[targetOffset] = palette[paletteOffset];
            rgba[targetOffset + 1] = palette[paletteOffset + 1];
            rgba[targetOffset + 2] = palette[paletteOffset + 2];
            rgba[targetOffset + 3] = (byte) 0xff;
        }
        return new RgbaImage(width, height, rgba);
    }

    private static final class ImportFailure extends RuntimeException {
        private final String code;
        private final String location;

        private ImportFailure(String code, String location, String message) {
            super(message);
            this.code = code;
            this.location = location;
        }

        private String code() {
            return code;
        }

        private String location() {
            return location;
        }
    }

    private record TextureDefinition(
            String name, int width, int height, List<PatchPlacement> patches, WadLump source) {}

    private record PatchPlacement(int originX, int originY, int patchIndex) {}

    private static final class TextureCanvas {
        private final int width;
        private final int height;
        private final byte[] pixels;

        private TextureCanvas(int width, int height, byte[] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }
    }
}
