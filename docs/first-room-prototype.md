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
entry and startup targets, authored launch presentation, legal-document references,
and authoritative asset sources. It is
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
- Publish supported manual doors as movable render and collision entities.
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

The headless material slice is also complete. It resolves only MAP01's referenced
resources, decodes the first `PLAYPAL` palette and flat namespace, reads
`PNAMES` and `TEXTURE1`/`TEXTURE2`, composes transparent column-post patches, and
retains the source lumps needed to reproduce each imported image. The pinned WAD
test independently verifies 51 wall textures and 28 non-sky flats. The launcher
writes a deterministic contact sheet under `target/smoke/` for manual inspection
without initializing windowing or native rendering.

The MAP01 project-runtime geometry and player slices are complete. The
engine Doom importer publishes textured mesh resources and independently
authored static collision behind the generic importer interface. The entry
world places that generated definition alongside a composed Player entity with
a transform, capsule, character body, generic first-person controller, and child
camera view. The generic project host constructs this graph and owns its resources.

The player controller is engine-owned safe descriptor metadata paired with reusable
Java behavior. Stable component targets bind it explicitly to the character body
and child view transform. During the declared before-physics phase it reads the
authored `move` and `look` actions, submits planar velocity to the engine-owned
character body, and applies bounded yaw and pitch to the view. Hosted-project
acceptance tests cover grounding, movement, MAP01 wall blocking and sliding, and
camera attachment. This replaces the earlier standalone movement runtime; the
descriptor-composed World is the only game runtime.

The visible-actor project-runtime slice is also complete. The project declares a
provider-owned, versioned actor catalog that assigns stable identities,
categories, and initial sprite frames to every classic thing type used by
MAP01. A game-owned project importer composes the generic WAD and map decoders
with that catalog, applies normal-skill and single-player placement flags, and
grounds visible actors through the map BSP. It publishes the 21 shared spawn
frames and reusable actor definitions plus an aggregate definition containing
the resulting 119 placements. The entry world places that definition, and the
generic 3D runtime displays the actors through alpha-masked cylindrical
billboard components with WAD-derived anchors and scale.

Configured combatants now also publish their provider-authored radius and height
as a shared capsule collision resource. Each reusable combatant definition owns
an independently positioned collision-shape component and a movable solid
character body, so every living placement blocks the player and moves through
the same collision-aware physics API. Pickups, corpses, and decorative actors do
not acquire blocking bodies merely because they have visible billboards.

The first imported actor behavior now also runs through the project runtime.
The player owns a game-specific state component initialized from the declared
actor catalog and combat-rules source assets before world activation. Pickup
definitions named by those rules contain provider-sized generic collision
shapes, non-blocking sensors, and an authored connection from the sensor's
typed overlap signal to game-owned pickup behavior. A useful health or bullet
pickup updates the player state and destroys its complete entity exactly once;
an item which cannot change the current resource remains in the world. Hosted
MAP01 tests exercise publication, composition, physical overlap, signal
delivery, state mutation, and deferred destruction without the standalone
combat session.

The player-state descriptor declares a stable player-resource capability.
Startup preparation and pickup behavior query that capability on the exact
entity they are processing, so neither depends on the authored UUID of a
particular player-state component. The capability is safe descriptor metadata
which a future editor can inspect; the Java runtime extension supplies its
implementation.

The project-runtime pistol is also active. Version-four combat rules retain its
2,048-unit range and author the horizontal auto-aim angle and maximum vertical
slope. Firing tries the exact view ray before selecting the nearest visible
damageable entity whose bounds intersect that window; physics raycasts remain
authoritative for wall occlusion. Separate `fired` and `hit` signals let the
world connect every accepted shot to imported weapon animation and sound while
showing the short centre hit marker only after health is actually removed.
Keyboard turning similarly keeps its initial rate, maximum rate, and acceleration
in the controller's authored properties rather than application Java.

Configured combatants also publish hidden pain and death billboards, positional
reaction sounds, and explicit signal connections from their damage state to a
game-owned presentation component. Non-fatal damage returns to the idle frame;
fatal damage closes the combatant's blocking body, plays the complete death
sequence, and leaves its final frame in the world as a non-blocking corpse. The
component's descriptor exposes positional attenuation, and the importer derives
its full-volume and maximum distances from Doom's 160- and 1200-map-unit sound
distances through the shared world-unit conversion.

Enemy awareness and pursuit now run through descriptor-authored project-runtime
components as well. The entry world passes its stable Player entity to the
generated actor-group definition. That definition exposes the target through a
provider component, and each reusable combatant placement receives an explicit
reference to it through its public contract. Enemy behavior therefore requires
no global entity lookup or hierarchy convention. Physics raycasts determine
line of sight, provider combat rules supply sight distance, preferred distance,
reaction delay, and speed, and the character body pursues the last observed
position while respecting map and combatant collision. The same behavior uses
the provider-authored attack range, repeat interval, and discrete damage sequence
to damage the explicitly targeted Player only while line of sight remains clear.
Player state exposes damage through descriptor metadata, emits non-fatal and
terminal signals, clamps health at zero, and drives the existing HUD without a
parallel combat runtime. Enemy behavior emits explicit alert, movement-transition,
and attack signals. The imported actor definition connects them to a presentation
component whose sight and attack audio plus idle, looping walk, attack, pain, and
terminal death frames are all explicit properties. Death and pain take visual
priority over attacks, which take priority over movement and idle. Player pain,
death presentation, and terminal control handling are separately authored
presentation and lifecycle components. Player-state signals activate their
explicit actions; terminal damage disables only the Player Controls child while
the fatal red response fades into a subtle dark shade, the view descends from the
classic 41-unit standing height to the 6-unit death height, and the weapon moves
below the viewport while the world and HUD remain visible.

The combat-model slice is also complete. A project-declared, versioned combat
document defines the player's initial health and ammunition, the pistol's range
and discrete damage values, and the zombieman's health, collision cylinder,
awareness, movement, reaction time, attack cadence, and damage. Descriptor-selected
World components consume that provider data, use the engine physics module for
pitched hitscan and collision-aware pursuit, and communicate through declared
capabilities, signals, and actions. Unit tests exercise their deterministic rules
without a graphics or audio device; hosted-project tests exercise their composition.

The combat-presentation slice is also complete. Its separately versioned project
asset binds combat identities to exact WAD patches, sounds, animation timing, and
HUD glyphs. The WAD adapter decodes pistol, walk, attack, pain, death, and numeric
patches plus classic DMX effects without graphics or audio initialization. The
standalone host captures the pointer on an initial click inside the game window;
that click does not fire. It presents moving and attacking enemies, spatial alert
and attack sounds, player-local pain and death sounds, a red damage response, live
health and ammunition, and terminal player and enemy death.

The application lifecycle slice is complete. The manifest selects a distinct
descriptor-authored startup scene and original project-owned launch artwork. The
desktop host creates its native window before composing the world, presents one
process-launch splash for at least the authored duration, and reports ordered
manifest, extension, asset, input, content, module, world, application, and ready
milestones without moving OpenGL or audio realization off their required thread.
The main menu supports semantic keyboard and gamepad navigation plus absolute
pointer hover and exact-row activation. New Game transactionally replaces prior
gameplay with a fresh entry world, Resume retains paused state, Quit closes the
host, and a later New Game uses a compact progress treatment instead of replaying
the launch splash. Startup failures remain visible, and replacement-load failures
leave the retained session intact.

The application-directory slice is also complete for the current host platform.
It packages compiled application and engine modules, native libraries, authored
worlds and resources, original branding, and published import output behind the
generic desktop launcher. Recursive project references inside runtime resource
definitions are included, while source Java, tests, import-only WAD data, editable
branding masters, and exporter implementation classes remain absent.

The health-and-ammunition pickup slice is complete. Versioned provider rules
declare the player's absolute health and bullet capacities plus per-actor amounts,
ordinary or bonus limits, and contact radii. Descriptor-authored pickup sensors
collect useful overlapping items once by entity identity and signal the applied
resource amount without depending on rendering. Presentation hides the collected
billboard, updates the descriptor-authored HUD numbers, and plays the imported
`DSITEMUP` effect. The HUD is an ordinary entity hierarchy built from
generic screen-canvas, screen-region, and bitmap-number components; its
Doom-specific binding component only copies player health and ammunition into
explicitly targeted number components. Stimpacks, medikits, health bonuses,
soulspheres, ammunition clips, and bullet boxes are active; shell, rocket, and
cell inventory follows with the weapons that consume those resources.

Supported manual doors are also descriptor-driven. The engine Doom importer
recognizes classic open-stay and blaze raise specials, removes their surfaces from
the static map batches, and publishes a child entity with its own transform,
material-batched meshes, collision body, and Doom door component. The authored
Player Controls entity owns an interaction component whose `interact` action
activates only the nearest unobstructed entity declaring the Doom door capability.
Normal doors remain open; blaze doors open, wait, and close at source-derived
speeds. Generated character-filtered obstruction sensors reopen a closing blaze
door around the player or a combatant, while descriptor-authored positional
presentation plays the imported normal or blaze opening and closing effects.
Hosted-project tests prove interaction wall obstruction, collision-free traversal
while open, safe reopening, collision restoration after closing, sound-handle
cleanup, and closed initial editor-preview state.
Lifts, navigation beyond last-visible-position pursuit, and other sector specials
remain later vertical slices.

This is a vertical slice through the real pipeline, not the limit of the game.
Later increments expand the supported vanilla Doom II semantics and playable
maps until the complete Freedoom campaign is covered.

## Package boundaries

```text
io.github.glynch.doomedcorridors
|-- (root)        descriptor-selected gameplay components and runtime extension
|-- actor         provider definitions, resolved placements, skill filtering, sprites
|-- wad           Doom WAD container access and source adapter
|-- map           immutable decoded classic-map records
|-- material      renderer-independent imported images and smoke outputs
|-- combat        immutable weapons, damage, health, pickup, and encounter rules
|-- input         game actions and bindings
|-- importing     provider composition over generic Doom and project importers
|-- world         import-time actor grounding against decoded map BSP data
`-- presentation  immutable sprite, HUD, animation, and audio import rules
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

## Remaining campaign scope

- The complete campaign and every vanilla Doom II special.
- Save games, multiplayer, demos, and advanced enemy/weapon behavior.
- Generated-cache optimization before importer correctness is established.
- The graphical project browser/editor. Its future workflow is supported now by
  the same headless loader and structured diagnostics used by the game.
