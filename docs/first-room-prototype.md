# First MAP01 combat prototype

## Goal

Deliver the smallest playable vertical slice of the eventual full Doom-compatible
game: load the pinned Freedoom Phase 2 WAD, start at `MAP01`, and preserve the
WAD as the authoritative source for level geometry, textures, things, sounds,
and map semantics.

This supersedes the earlier procedural-room proposal. We will not hand-author a
replacement room merely to avoid WAD support, because that would test the wrong
content pipeline and create work that a full game cannot reuse.

## Project boundary

The root `project.json` is the engine-native project definition. It contains stable
identity, author and catalog metadata, engine compatibility, Game Provider identity,
startup target, legal-document references, and authoritative asset sources. It is
loaded through the headless `jscene3d-project` API that a future GUI will also use.

`assets/freedoom2.wad` is the first source asset and `MAP01` is the first
target. The WAD importer and any generated cache are adapters behind the project
boundary. Cache files are disposable; the WAD and manifest remain the source of
truth.

## First playable scope

- Read and index the pinned Freedoom Phase 2 WAD.
- Import the geometry, sectors, sidedefs, linedefs, things, and material references
  needed by `MAP01`.
- Spawn the player from the map's player-start thing.
- Support Doom-style horizontal movement, collision, mouse look, and hitscan fire.
- Import enough sprites, sounds, one weapon, one enemy, health, and ammo behavior
  for a coherent encounter.
- Surface unsupported map constructs and missing lumps as structured diagnostics.

## Progress

The headless source and map-decoding slices are complete. Freedoom 0.13.0 is
pinned by release URL and SHA-256, and the ignored local `freedoom2.wad` is
verified before use. The WAD reader validates the container and directory,
preserves lump order and duplicate names, enumerates all 32 map markers, and
confirms that `MAP01` exists. The classic-map decoder then validates and decodes
the ordered `THINGS` through `BLOCKMAP` lump sequence into an immutable,
renderer-independent model. It checks record boundaries, cross-references, BSP
children, collision blocks, and visibility-table size, with explicit diagnostics
for corrupt, UDMF, and Hexen-format input. Synthetic corruption tests and a
pinned real-WAD integration test run without graphics or audio.

The next slice imports the palette and MAP01 material images, then constructs
static sector geometry from the decoded map. Collision, actor behavior, and
sector specials remain separate later vertical slices.

This is a vertical slice through the real pipeline, not the limit of the game.
Later increments expand the supported vanilla Doom II semantics and playable
maps until the complete Freedoom campaign is covered.

## Package boundaries

```text
io.github.glynch.doomedcorridors
|-- app           native host, project loading, runtime wiring, lifecycle
|-- wad           Doom WAD container access and source adapter
|-- combat        weapons, damage, health, pickups, and encounter rules
|-- input         game actions and bindings
|-- world         imported map runtime, collision, doors, lifts, and triggers
`-- presentation  camera, meshes, sprites, HUD, effects, and audio bindings
```

Game-specific rules and Doom compatibility remain in this repository. Generic
project loading belongs in `jscene3d-project`. A genuinely reusable engine gap
must be documented separately before a focused JScene3D change is proposed;
weapon, enemy, map, WAD, and Freedoom code must not enter `jscene3d-game` or
`jscene3d-physics`.

## Asset policy

The exact Freedoom release and `freedoom2.wad` digest must be pinned before
importer fixtures or gameplay depend on it. Preserve upstream license text and
record the release URL, upstream filename, digest, project path, and all
transformations. Do not download mutable content during normal builds or game
startup.

## Acceptance criteria

- `./mvnw verify` passes using locally installed JScene3D snapshots.
- `project.json` loads through `jscene3d-project`; before the WAD is installed,
  the sole expected diagnostic is the missing source-asset warning.
- With the pinned WAD installed, the importer identifies `MAP01` and rejects corrupt
  or unsupported input with structured diagnostics.
- The player starts at the WAD-defined location and can traverse an initial playable
  portion of `MAP01` without crossing collision boundaries.
- Primary fire, one enemy, damage, death, health, ammo, and basic audiovisual
  feedback operate from imported WAD data.
- Pure importer and combat behavior has automated tests that require no graphics
  or audio device.
- Every included or locally required external asset has complete provenance and
  retained license text.

## Deferred from the first slice

- The complete campaign and every vanilla Doom II special.
- Save games, multiplayer, demos, and advanced enemy/weapon behavior.
- Generated-cache optimization before importer correctness is established.
- The graphical project browser/editor. Its future workflow is supported now by
  the same headless loader and structured diagnostics used by the game.
