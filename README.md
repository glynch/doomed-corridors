# Doomed Corridors

Doomed Corridors is an unofficial Doom-compatible first-person game built as a
standalone Java 21 application on JScene3D. It targets a full campaign using
attributed Freedoom assets, developed incrementally from `MAP01`.

The project is deliberately separate from the JScene3D reactor. Game rules,
world construction, asset integration, and presentation belong here; reusable
engine code remains in JScene3D.

## Current status

The repository contains a versioned JScene3D [`project.json`](project.json), a
headless launcher that validates it, and a bounded WAD adapter that verifies and
indexes the pinned Freedoom Phase 2 source. It decodes classic Doom map records
and referenced flats and composite wall textures into immutable,
renderer-independent models without initializing graphics or audio. The first
playable vertical slice is documented in
[`docs/first-room-prototype.md`](docs/first-room-prototype.md).

The scaffold consumes locally installed `0.1.0-SNAPSHOT` builds of:

- `jscene3d-game` for the game loop, semantic input, and character movement
- `jscene3d-project` for versioned project loading and GUI-ready diagnostics
- `jscene3d-audio` for effects and listener control
- `jscene3d-gui` for overlays

`jscene3d-game` supplies its JScene3D core, physics, and LWJGL dependencies
transitively. The application supplies platform-native LWJGL runtime artifacts.

## Prerequisites

- JDK 21
- JScene3D `0.1.0-SNAPSHOT` installed in the local Maven repository

### Optional VS Code multi-root workspace

Developers changing Doomed Corridors and JScene3D together can create a local
multi-root workspace outside both repositories. If the checkouts are sibling
directories, place a `.code-workspace` file in their common parent directory:

```json
{
  "folders": [
    {
      "name": "Doomed Corridors",
      "path": "./doomed-corridors"
    },
    {
      "name": "JScene3D",
      "path": "./threejs-java"
    }
  ],
  "settings": {
    "java.configuration.updateBuildConfiguration": "automatic",
    "java.compile.nullAnalysis.mode": "disabled"
  }
}
```

Opening this workspace lets the Java extension import both independent Maven
builds. The workspace file is local development configuration and should not be
stored in either repository.

From the JScene3D repository:

```shell
./mvnw install
```

Then verify this project:

```shell
./mvnw verify
```

The headless project-loading entry point can be run with:

```shell
./mvnw -Prun compile
```

The `run` profile adds `-XstartOnFirstThread` automatically on macOS. Until
`assets/freedoom2.wad` is installed, it reports one expected missing-asset
warning and confirms that the project targets `freedoom:MAP01`. With the pinned
WAD installed, it also verifies the digest, indexes the directory, enumerates the
32 map markers, decodes `MAP01`, imports its 51 wall textures and 28 non-sky
flats, and writes `target/smoke/map01-materials.png` for manual inspection. The
contact sheet orders wall textures alphabetically, followed by flats
alphabetically.

## Project definition and WAD source

The manifest identifies `assets/freedoom2.wad` as the authoritative game-data
source and `MAP01` as the startup target. The classic-map decoder reads the
ordered `THINGS` through `BLOCKMAP` lump sequence, validates fixed record sizes,
cross-references, BSP children, `REJECT`, and `BLOCKMAP`, and reports unsupported
UDMF and Hexen maps explicitly. The material importer resolves only the map's
referenced resources, applies the first `PLAYPAL` palette, observes the flat
namespace, reads `PNAMES` and `TEXTURE1`/`TEXTURE2`, and composes transparent
Doom patch columns while retaining source-lump provenance. Generated imported
data will be treated as a cache, so deleting and rebuilding it never loses
authored state. The same
headless project loader used here is intended to support a later Godot-like
project browser/editor. The manifest uses the vendored
[`schema/project-1.schema.json`](schema/project-1.schema.json) for offline editor
validation; an automated test keeps that copy identical to the schema bundled
in `jscene3d-project`.

## Assets and attribution

Freedoom 0.13.0 is the pinned source release. The WAD itself remains ignored
while distribution policy is undecided; follow
[`assets/README.md`](assets/README.md) to install the exact local source. Its
provenance and digests are recorded in
[`src/main/resources/assets/ATTRIBUTION.md`](src/main/resources/assets/ATTRIBUTION.md),
and the upstream license and credits are retained in `assets/`.

Freedoom and Doom are not affiliated with or endorsed by this project. The
working title and presentation must not imply otherwise.
