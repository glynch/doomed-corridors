/*
 * Copyright 2026 Graham Lynch
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.glynch.doomedcorridors.importing.internal;

import io.github.glynch.doomedcorridors.importing.DoomedCorridorsImportDiagnosticCode;
import io.github.glynch.doomedcorridors.material.DoomMapMaterials;
import io.github.glynch.doomedcorridors.material.DoomMaterial;
import io.github.glynch.doomedcorridors.material.RgbaImage;
import io.github.glynch.doomedcorridors.wad.DoomMapDecodeResult;
import io.github.glynch.doomedcorridors.wad.DoomMapDecoder;
import io.github.glynch.doomedcorridors.wad.DoomMaterialImportResult;
import io.github.glynch.doomedcorridors.wad.DoomMaterialImporter;
import io.github.glynch.doomedcorridors.wad.WadArchive;
import io.github.glynch.doomedcorridors.wad.WadDiagnostic;
import io.github.glynch.doomedcorridors.wad.WadLoadResult;
import io.github.glynch.doomedcorridors.wad.WadLoader;
import io.github.glynch.doomedcorridors.world.DoomGeometryBuildResult;
import io.github.glynch.doomedcorridors.world.DoomGeometryDiagnostic;
import io.github.glynch.doomedcorridors.world.DoomMeshData;
import io.github.glynch.doomedcorridors.world.DoomStaticGeometry;
import io.github.glynch.doomedcorridors.world.DoomStaticGeometryBuilder;
import io.github.glynch.doomedcorridors.world.DoomSurface;
import io.github.glynch.jscene3d.doom.map.DoomMap;
import io.github.glynch.jscene3d.geometries.BufferAttribute;
import io.github.glynch.jscene3d.geometries.BufferGeometry;
import io.github.glynch.jscene3d.materials.AlphaMode;
import io.github.glynch.jscene3d.materials.BasicMaterial;
import io.github.glynch.jscene3d.project.asset.AssetId;
import io.github.glynch.jscene3d.project.asset.DefinitionWriter;
import io.github.glynch.jscene3d.project.component.ComponentDefinition;
import io.github.glynch.jscene3d.project.component.ComponentId;
import io.github.glynch.jscene3d.project.component.ComponentType;
import io.github.glynch.jscene3d.project.component.PropertyId;
import io.github.glynch.jscene3d.project.entity.EntityDefinition;
import io.github.glynch.jscene3d.project.entity.EntityId;
import io.github.glynch.jscene3d.project.entity.LocalEntity;
import io.github.glynch.jscene3d.project.importing.ImportArtifactDescriptor;
import io.github.glynch.jscene3d.project.importing.SourceItem;
import io.github.glynch.jscene3d.project.importing.extension.ImportInspectionContext;
import io.github.glynch.jscene3d.project.importing.extension.ImportPreparationContext;
import io.github.glynch.jscene3d.project.importing.extension.ProjectImporter;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dDescriptors;
import io.github.glynch.jscene3d.project.spatial3d.Spatial3dResourceWriter;
import io.github.glynch.jscene3d.project.value.ProjectValue;
import io.github.glynch.jscene3d.project.value.ResourceReference;
import io.github.glynch.jscene3d.textures.Texture;
import io.github.glynch.jscene3d.textures.TextureCoordinateOrigin;
import io.github.glynch.jscene3d.textures.TextureFilter;
import io.github.glynch.jscene3d.textures.TextureWrap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Publishes selected Doom maps as generated project-native entity definitions and spatial resources. */
public final class DoomMapProjectImporter implements ProjectImporter {
    private static final String ITEM_KIND = "io.github.glynch.doomed-corridors/map-presentation";
    private static final String MESH_MEDIA_TYPE = "application/vnd.jscene3d.mesh-v1";
    private static final String TEXTURE_MEDIA_TYPE = "application/vnd.jscene3d.rgba8-v1";

    @Override
    public void inspect(ImportInspectionContext context) {
        loadAndDescribe(context);
    }

    @Override
    public void prepare(ImportPreparationContext context) throws IOException {
        Optional<WadArchive> loaded = loadAndDescribe(context);
        if (loaded.isEmpty()) {
            return;
        }
        WadArchive archive = loaded.orElseThrow();
        Set<String> selection = Set.copyOf(context.definition().selection());
        for (String mapName : archive.mapNames()) {
            String identity = mapIdentity(mapName);
            if (selection.contains(identity)) {
                prepareMap(context, archive, mapName, identity);
            }
        }
    }

    /** Loads the source and declares one selectable generated presentation per classic map. */
    private static Optional<WadArchive> loadAndDescribe(ImportInspectionContext context) {
        context.checkCancelled();
        WadLoadResult result =
                new WadLoader().load(context.asset().path(), context.asset().sha256());
        if (!result.isValid()) {
            report(context, result.diagnostics(), DoomedCorridorsImportDiagnosticCode.WAD_SOURCE_INVALID);
            return Optional.empty();
        }
        WadArchive archive = result.archive().orElseThrow();
        for (String mapName : archive.mapNames()) {
            context.sourceItem(new SourceItem(
                    mapIdentity(mapName),
                    ITEM_KIND,
                    mapName,
                    true,
                    Map.of("map", new ProjectValue.TextValue(mapName)),
                    List.of()));
        }
        return Optional.of(archive);
    }

    /** Decodes and publishes one selected map as a closed graph of generic project artifacts. */
    private static void prepareMap(ImportPreparationContext context, WadArchive archive, String mapName, String prefix)
            throws IOException {
        DoomMap map = decodeMap(context, archive, mapName);
        DoomMapMaterials materials = importMaterials(context, archive, map);
        DoomStaticGeometry geometry = buildGeometry(context, map, materials);
        publishMap(context, prefix, geometry, materials);
    }

    /** Requires one valid decoded map after forwarding source diagnostics. */
    private static DoomMap decodeMap(ImportPreparationContext context, WadArchive archive, String mapName) {
        DoomMapDecodeResult result = new DoomMapDecoder().decode(archive, mapName);
        if (!result.isValid()) {
            report(context, result.diagnostics(), DoomedCorridorsImportDiagnosticCode.MAP_INVALID);
            throw new IllegalStateException("selected Doom map could not be decoded: " + mapName);
        }
        return result.map().orElseThrow();
    }

    /** Requires the complete material set referenced by one map. */
    private static DoomMapMaterials importMaterials(ImportPreparationContext context, WadArchive archive, DoomMap map) {
        DoomMaterialImportResult result = new DoomMaterialImporter().importMap(archive, map);
        if (!result.isValid()) {
            report(context, result.diagnostics(), DoomedCorridorsImportDiagnosticCode.MAP_MATERIALS_INVALID);
            throw new IllegalStateException("selected Doom map materials could not be imported: " + map.name());
        }
        return result.materials().orElseThrow();
    }

    /** Requires complete renderer-independent surface geometry and forwards every warning. */
    private static DoomStaticGeometry buildGeometry(
            ImportPreparationContext context, DoomMap map, DoomMapMaterials materials) {
        DoomGeometryBuildResult result = new DoomStaticGeometryBuilder().build(map, materials);
        result.diagnostics().forEach(diagnostic -> report(context, diagnostic));
        return result.geometry()
                .orElseThrow(() ->
                        new IllegalStateException("selected Doom map geometry could not be built: " + map.name()));
    }

    /** Publishes shared textures and materials, material-batched meshes, then their generated definition. */
    private static void publishMap(
            ImportPreparationContext context,
            String prefix,
            DoomStaticGeometry geometry,
            DoomMapMaterials sourceMaterials)
            throws IOException {
        List<RenderBatch> batches = renderBatches(geometry);
        Map<MaterialKey, DoomMaterial> materials = selectedMaterials(batches, sourceMaterials);
        for (Map.Entry<MaterialKey, DoomMaterial> entry : materials.entrySet()) {
            publishMaterial(context, prefix, entry.getKey(), entry.getValue());
        }

        List<String> references = new ArrayList<>();
        List<ComponentDefinition> components = new ArrayList<>();
        components.add(component(
                context.definition().id(), prefix + "/root/transform", Spatial3dDescriptors.transformType(), Map.of()));
        for (int index = 0; index < batches.size(); index++) {
            RenderBatch batch = batches.get(index);
            String meshIdentity = meshIdentity(prefix, index);
            publishMesh(context, meshIdentity, batch.mesh());
            references.add(meshIdentity);
            components.add(meshRenderer(context.definition().id(), prefix, index, batch.material()));
        }
        materials.keySet().stream().map(key -> materialIdentity(prefix, key)).forEach(references::add);

        String definitionIdentity = prefix + "/definition";
        AssetId definitionId = assetId(context.definition().id(), definitionIdentity);
        LocalEntity root = new LocalEntity(
                entityId(context.definition().id(), prefix + "/root"),
                prefix.substring(prefix.lastIndexOf('/') + 1),
                true,
                components,
                List.of());
        EntityDefinition definition =
                new EntityDefinition(definitionId, root.name().orElseThrow(), root);
        context.artifact(
                ImportArtifactDescriptor.entityDefinition(definitionIdentity, definitionId, references),
                output -> DefinitionWriter.write(output, definition));
    }

    /** Publishes one shared image, texture resource, and unlit material resource. */
    private static void publishMaterial(
            ImportPreparationContext context, String prefix, MaterialKey key, DoomMaterial source) throws IOException {
        String textureIdentity = textureIdentity(prefix, key);
        String payloadIdentity = textureIdentity + ".rgba8";
        String materialIdentity = materialIdentity(prefix, key);
        RgbaImage image = source.image();
        try (Texture texture = createTexture(image)) {
            context.artifact(
                    ImportArtifactDescriptor.payload(payloadIdentity, TEXTURE_MEDIA_TYPE),
                    output -> Spatial3dResourceWriter.writeTexturePayload(output, texture));
            ResourceReference payload = imported(context, payloadIdentity);
            context.artifact(
                    ImportArtifactDescriptor.resource(
                            textureIdentity, Spatial3dDescriptors.textureResourceType(), List.of(payloadIdentity)),
                    output -> Spatial3dResourceWriter.writeTextureDefinition(output, texture, payload));
            try (BasicMaterial material = createMaterial(texture, image)) {
                ResourceReference colorMap = imported(context, textureIdentity);
                context.artifact(
                        ImportArtifactDescriptor.resource(
                                materialIdentity,
                                Spatial3dDescriptors.basicMaterialResourceType(),
                                List.of(textureIdentity)),
                        output -> Spatial3dResourceWriter.writeBasicMaterial(output, material, colorMap));
            }
        }
    }

    /** Publishes one mesh document and opaque binary payload. */
    private static void publishMesh(ImportPreparationContext context, String identity, DoomMeshData mesh)
            throws IOException {
        String payloadIdentity = identity.replace("/resources/meshes/", "/payloads/meshes/") + ".mesh";
        try (BufferGeometry geometry = bufferGeometry(mesh)) {
            context.artifact(
                    ImportArtifactDescriptor.payload(payloadIdentity, MESH_MEDIA_TYPE),
                    output -> Spatial3dResourceWriter.writeMeshPayload(output, geometry));
            ResourceReference payload = imported(context, payloadIdentity);
            context.artifact(
                    ImportArtifactDescriptor.resource(
                            identity, Spatial3dDescriptors.meshResourceType(), List.of(payloadIdentity)),
                    output -> Spatial3dResourceWriter.writeMeshDefinition(output, payload));
        }
    }

    /** Creates one mesh-renderer component referencing a batched mesh and shared material. */
    private static ComponentDefinition meshRenderer(String importId, String prefix, int index, MaterialKey material) {
        Map<PropertyId, ProjectValue> properties = new LinkedHashMap<>();
        properties.put(Spatial3dDescriptors.meshProperty(), reference(importId, meshIdentity(prefix, index)));
        properties.put(
                Spatial3dDescriptors.materialProperty(), reference(importId, materialIdentity(prefix, material)));
        return component(
                importId,
                prefix + "/root/mesh-renderers/" + formatted(index),
                Spatial3dDescriptors.meshRendererType(),
                properties);
    }

    /** Groups immutable static surfaces by material to avoid one draw resource per source surface. */
    private static List<RenderBatch> renderBatches(DoomStaticGeometry geometry) {
        Map<MaterialKey, List<DoomMeshData>> meshesByMaterial = new LinkedHashMap<>();
        for (DoomSurface surface : geometry.surfaces()) {
            meshesByMaterial
                    .computeIfAbsent(materialKey(surface), ignored -> new ArrayList<>())
                    .add(surface.mesh());
        }
        return meshesByMaterial.entrySet().stream()
                .map(entry -> new RenderBatch(entry.getKey(), combine(entry.getValue())))
                .toList();
    }

    /** Combines compatible indexed meshes while preserving their vertex and triangle order. */
    private static DoomMeshData combine(List<DoomMeshData> meshes) {
        int vertexCount = meshes.stream().mapToInt(DoomMeshData::vertexCount).reduce(0, Math::addExact);
        int indexCount = meshes.stream()
                .mapToInt(mesh -> Math.multiplyExact(mesh.triangleCount(), 3))
                .reduce(0, Math::addExact);
        float[] positions = new float[Math.multiplyExact(vertexCount, 3)];
        float[] normals = new float[positions.length];
        float[] textureCoordinates = new float[Math.multiplyExact(vertexCount, 2)];
        int[] indices = new int[indexCount];
        int vertexOffset = 0;
        int indexOffset = 0;
        for (DoomMeshData mesh : meshes) {
            copy(mesh.positions(), positions, Math.multiplyExact(vertexOffset, 3));
            copy(mesh.normals(), normals, Math.multiplyExact(vertexOffset, 3));
            copy(mesh.textureCoordinates(), textureCoordinates, Math.multiplyExact(vertexOffset, 2));
            int[] sourceIndices = mesh.indices();
            for (int index = 0; index < sourceIndices.length; index++) {
                indices[indexOffset + index] = Math.addExact(sourceIndices[index], vertexOffset);
            }
            vertexOffset = Math.addExact(vertexOffset, mesh.vertexCount());
            indexOffset = Math.addExact(indexOffset, sourceIndices.length);
        }
        return new DoomMeshData(positions, normals, textureCoordinates, indices);
    }

    /** Copies one primitive source array into its preallocated destination. */
    private static void copy(float[] source, float[] destination, int offset) {
        System.arraycopy(source, 0, destination, offset, source.length);
    }

    /** Collects only referenced materials while preserving first-surface order. */
    private static Map<MaterialKey, DoomMaterial> selectedMaterials(
            List<RenderBatch> batches, DoomMapMaterials sourceMaterials) {
        Map<MaterialKey, DoomMaterial> selected = new LinkedHashMap<>();
        for (RenderBatch batch : batches) {
            MaterialKey key = batch.material();
            selected.computeIfAbsent(key, ignored -> sourceMaterial(key, sourceMaterials));
        }
        return selected;
    }

    /** Resolves one material from the flat or wall-texture namespace. */
    private static DoomMaterial sourceMaterial(MaterialKey key, DoomMapMaterials materials) {
        Map<String, DoomMaterial> namespace =
                key.kind() == DoomMaterial.Kind.FLAT ? materials.flats() : materials.wallTextures();
        DoomMaterial material = namespace.get(key.name());
        if (material == null) {
            throw new IllegalArgumentException("missing presentation material: " + key.name());
        }
        return material;
    }

    /** Creates Doom's nearest-filtered repeating texture description. */
    private static Texture createTexture(RgbaImage image) {
        Texture texture = Texture.baseColor(image.width(), image.height(), image.pixels());
        texture.setCoordinateOrigin(TextureCoordinateOrigin.TOP_LEFT);
        texture.setHorizontalWrap(TextureWrap.REPEAT);
        texture.setVerticalWrap(TextureWrap.REPEAT);
        texture.setMinificationFilter(TextureFilter.NEAREST_MIPMAP_NEAREST);
        texture.setMagnificationFilter(TextureFilter.NEAREST);
        return texture;
    }

    /** Creates Doom's unlit map material and preserves masked source pixels. */
    private static BasicMaterial createMaterial(Texture texture, RgbaImage image) {
        BasicMaterial material = new BasicMaterial();
        material.setColorMap(texture);
        if (hasTransparentPixel(image.pixels())) {
            material.setAlphaMode(AlphaMode.MASK);
            material.setAlphaCutoff(0.5F);
        }
        return material;
    }

    /** Converts immutable Doom triangle data to one owned project mesh input. */
    private static BufferGeometry bufferGeometry(DoomMeshData mesh) {
        return BufferGeometry.builder()
                .attribute(BufferGeometry.POSITION, BufferAttribute.of(mesh.positions(), 3))
                .normals(mesh.normals())
                .attribute(BufferGeometry.UV, BufferAttribute.of(mesh.textureCoordinates(), 2))
                .indices(mesh.indices())
                .build();
    }

    /** Creates one typed component with a deterministic source-derived identity. */
    private static ComponentDefinition component(
            String importId, String locator, ComponentType type, Map<PropertyId, ProjectValue> properties) {
        return new ComponentDefinition(
                new ComponentId(stableId(importId, locator)), type.id(), type.version(), properties);
    }

    /** Creates one imported resource property. */
    private static ProjectValue.ReferenceValue reference(String importId, String identity) {
        return new ProjectValue.ReferenceValue(ResourceReference.imported(importId + '/' + identity));
    }

    /** Creates one imported resource reference for the active recipe. */
    private static ResourceReference imported(ImportPreparationContext context, String identity) {
        return ResourceReference.imported(context.definition().id() + '/' + identity);
    }

    /** Reports legacy WAD decoder details through one stable project-import diagnostic. */
    private static void report(
            ImportInspectionContext context,
            List<WadDiagnostic> diagnostics,
            DoomedCorridorsImportDiagnosticCode code) {
        for (WadDiagnostic diagnostic : diagnostics) {
            Map<String, String> details = Map.of(
                    "sourceCode", diagnostic.code(),
                    "message", diagnostic.message());
            if (diagnostic.severity() == WadDiagnostic.Severity.ERROR) {
                context.error(code, diagnostic.location(), details);
            } else {
                context.warning(code, diagnostic.location(), details);
            }
        }
    }

    /** Forwards one geometry diagnostic through its stable import code. */
    private static void report(ImportInspectionContext context, DoomGeometryDiagnostic diagnostic) {
        Map<String, String> details = Map.of(
                "sourceCode", diagnostic.code(),
                "message", diagnostic.message());
        if (diagnostic.severity() == DoomGeometryDiagnostic.Severity.ERROR) {
            context.error(DoomedCorridorsImportDiagnosticCode.MAP_GEOMETRY_INVALID, diagnostic.location(), details);
        } else {
            context.warning(DoomedCorridorsImportDiagnosticCode.MAP_GEOMETRY_INVALID, diagnostic.location(), details);
        }
    }

    /** Returns whether any source pixel requires alpha masking. */
    private static boolean hasTransparentPixel(byte[] pixels) {
        for (int alpha = 3; alpha < pixels.length; alpha += 4) {
            if (Byte.toUnsignedInt(pixels[alpha]) < 255) {
                return true;
            }
        }
        return false;
    }

    /** Returns the material namespace and normalized source name for one surface. */
    private static MaterialKey materialKey(DoomSurface surface) {
        DoomMaterial.Kind kind =
                switch (surface.type()) {
                    case FLOOR, CEILING -> DoomMaterial.Kind.FLAT;
                    case MIDDLE_WALL, UPPER_WALL, LOWER_WALL, MASKED_MIDDLE_WALL -> DoomMaterial.Kind.WALL_TEXTURE;
                };
        return new MaterialKey(kind, surface.materialName());
    }

    /** Returns the stable source-item prefix for one map. */
    private static String mapIdentity(String mapName) {
        return "maps/" + mapName;
    }

    /** Returns one deterministic surface-mesh identity. */
    private static String meshIdentity(String prefix, int index) {
        return prefix + "/resources/meshes/" + formatted(index);
    }

    /** Returns one deterministic shared-texture identity. */
    private static String textureIdentity(String prefix, MaterialKey key) {
        return prefix + "/resources/textures/" + key.path();
    }

    /** Returns one deterministic shared-material identity. */
    private static String materialIdentity(String prefix, MaterialKey key) {
        return prefix + "/resources/materials/" + key.path();
    }

    /** Formats stable surface indices for lexical and source ordering to agree. */
    private static String formatted(int index) {
        return String.format(Locale.ROOT, "%05d", index);
    }

    /** Creates one deterministic source-derived entity identity. */
    private static EntityId entityId(String importId, String locator) {
        return new EntityId(stableId(importId, locator));
    }

    /** Creates one deterministic source-derived definition identity. */
    private static AssetId assetId(String importId, String locator) {
        return new AssetId(stableId(importId, locator));
    }

    /** Uses standard name UUIDs so reimport preserves identity for unchanged source locators. */
    private static UUID stableId(String importId, String locator) {
        return UUID.nameUUIDFromBytes((importId + ':' + locator).getBytes(StandardCharsets.UTF_8));
    }

    /** Separates equal names in the flat and wall-texture namespaces. */
    private record MaterialKey(DoomMaterial.Kind kind, String name) {
        /** Returns one portable namespace-qualified identity suffix. */
        private String path() {
            return kind.name().toLowerCase(Locale.ROOT).replace('_', '-') + '/' + name.toLowerCase(Locale.ROOT);
        }
    }

    /** One renderer-ready static mesh and its shared material. */
    private record RenderBatch(MaterialKey material, DoomMeshData mesh) {}
}
