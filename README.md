# Doomed Corridors

Doomed Corridors is an unofficial Doom-compatible first-person game built with
JScene3D. It uses attributed assets from Freedoom Phase 2 and aims to support its
complete 32-map campaign with classic Doom II gameplay semantics.

## Game data

Doomed Corridors reads its maps, actor artwork, audio, and other gameplay data
from a pinned Freedoom Phase 2 WAD. Its launch and menu branding is original,
project-owned artwork. The WAD is not stored in this repository.

Follow [`assets/README.md`](assets/README.md) to install the required source WAD
and verify its release and checksum. The project manifest declares the source
asset and selects [`worlds/map01.world.json`](worlds/map01.world.json) as its
entry world. [`imports/freedoom-map01.import.json`](imports/freedoom-map01.import.json)
selects `MAP01` from the WAD. The engine Doom importer publishes the selection
as a generated, read-only entity definition backed by generic JScene3D mesh,
material, texture, and independently published collision resources. The
game-owned
[`imports/freedoom-map01-actors.import.json`](imports/freedoom-map01-actors.import.json)
publishes the visible normal-skill MAP01 things as placements of reusable actor
definitions backed by generic billboard components. Configured combatants also
own provider-sized capsule shapes and movable solid bodies, while pickups use
non-blocking contact sensors. The authored world places
both generated definitions alongside a composed Player entity. The
Player owns its transform, capsule shape, character body, generic first-person controller,
and a child view containing the primary camera; the generic desktop project host
resolves and composes the complete world.

The actor catalog in
[`game/actors.json`](game/actors.json) assigns Doom II meanings and initial
sprite frames to the numeric thing types stored in classic maps.
[`game/combat.json`](game/combat.json) defines initial player
resources, the pistol's hitscan and damage rules, and the zombieman's health and
collision bounds, awareness, pursuit, attack timing, and damage. It also defines
player resource capacities and effects for stimpacks, medikits, health bonuses,
soulspheres, ammunition clips, and bullet boxes.
[`game/combat-presentation.json`](game/combat-presentation.json) binds those
identities to WAD-backed weapon, movement, attack, pain, death, pickup sound, and
HUD assets without embedding their lump names in the application. The entry
world authors its HUD as ordinary entities composed from generic screen-canvas,
screen-region, and bitmap-number components. A small Doom-specific component
binds the player state explicitly to the health and ammunition numbers; it does
not own screen layout or drawing.

The manifest also declares a one-time launch splash and a distinct startup
scene. The splash uses original Doomed Corridors artwork, an optional studio-logo
slot which is currently unset, and a project-owned Powered by JScene3D badge.
The startup scene is an ordinary descriptor-authored menu rather than launcher
code or a hidden game-specific bootstrap path.

## Building and running

Prerequisites:

- JDK 21
- The JScene3D artifacts declared by [`pom.xml`](pom.xml) available to Maven

When developing against a local JScene3D checkout, install its artifacts into
the local Maven repository before building Doomed Corridors. From the JScene3D
checkout, run:

```shell
./mvnw install
```

Build and test Doomed Corridors with:

```shell
./mvnw clean verify
```

Imported content is published automatically during Maven's `process-classes`
phase. Run the project through JScene3D's generic desktop launcher with:

```shell
./mvnw process-classes -Prun-desktop
```

Assemble and structurally verify a relocatable application directory for the
current host platform with:

```shell
./mvnw clean verify -Pexport-directory,verify-export-directory
```

Launch that output directly from the repository root with:

```shell
./target/export/doomed-corridors/bin/doomed-corridors
```

The application directory contains the compiled game and engine modules,
current-platform native libraries, authored project files, original branding,
and published imported content. It deliberately excludes Java source, tests,
the import-only source WAD, editable branding masters, and export tooling. This
format currently uses a locally installed JDK 21; native application wrapping
is a separate packaging layer.

## Current playable slice

The descriptor-authored MAP01 world currently provides:

- textured WAD geometry, imported materials, static collision, and the
  WAD-defined player-one start;
- normal-skill items, decorations, corpses, pickups, and solid enemies published
  as placements of reusable generated entity definitions;
- capsule-based first-person movement with wall sliding, floor changes, and
  bounded step traversal;
- a hitscan pistol with ammunition consumption, imported animation and sound,
  auto-aim within authored limits, and a hit indicator;
- enemies with authored sight, pursuit, collision, attacks, pain, death,
  positional audio, and non-blocking corpses;
- useful-only health and bullet pickup collection through authored sensors;
- a descriptor-authored HUD showing live health and bullet ammunition;
- a descriptor-authored startup and pause menu using project-owned background
  and title artwork, with Resume, New Game, and Quit application transitions;
- a project-authored launch splash with a two-second minimum presentation,
  Powered by JScene3D branding, and determinate project-load feedback;
- player pain and death presentation, including local audio, damage flashes, a
  lowered death view, hidden weapon, disabled controls, and retained world and
  HUD presentation;
- manual open-stay and blaze raise/wait/close doors published as independently
  movable render and collision entities;
- MAP01's classic type-19 walk-once floor, published independently from static
  geometry with a player-only crossing trigger and synchronized render and
  collision movement.

Component participation, references, signals, actions, and update phases come
from project and generated descriptors. Game-specific Java code is supplied by
the manifest-selected Doomed Corridors runtime extension; the desktop launcher
contains no knowledge of this game.

Only MAP01 is currently selected. Additional lift and moving-floor profiles,
navigation beyond last-visible-position pursuit, and other sector specials remain
later vertical slices.

On process launch, the splash appears before world composition and reports the
real loading phase. It remains until both the authored two-second minimum and
successful menu readiness are satisfied; it is not replayed by New Game. A
startup failure remains visible instead of silently closing the window.

The game then opens on its main menu. Use Up/Down or W/S, the gamepad D-pad, or
pointer hover to select an item. Enter, the gamepad south button, or clicking an
actual menu row activates it; clicking outside the rows does nothing. New Game
starts a fresh MAP01 session and Quit Game closes the application. During play,
Escape or gamepad Start pauses the current session and opens the menu; Resume or
the same menu action returns to that retained session. Starting another New
Game keeps the menu visible under a compact real-progress indicator while a
replacement world is built; a load failure preserves the existing session.

Click the game window to capture the pointer; that acquisition click does not
fire. W/A/S/D move, the mouse looks while captured, and the left/right arrow
keys turn. Subsequent left mouse clicks fire while the pointer is captured.
Held keyboard turning accelerates from the
authored initial rate to its authored maximum; releasing or reversing the key
resets that rate. E activates the nearest unobstructed supported door within the
authored interaction range.

## Previewing in the editor

From a JScene3D checkout, open this project in the current read-only editor with:

```shell
./tools/scripts/run-editor.sh /path/to/doomed-corridors
```

The editor reads the manifest, extension descriptors, authored assets, and
published imports, then composes the entry world for a live viewport preview.
It presents the hierarchy, asset catalog, diagnostics, and project-open timing.
Application behavior is not executed in preview mode, and the editor does not
yet save project changes.

## Development

Implementation plans and architectural decisions are documented under
[`docs/`](docs/). The first playable milestone is described in
[`docs/first-room-prototype.md`](docs/first-room-prototype.md).

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
      "path": "./jscene3d"
    }
  ],
  "settings": {
    "java.configuration.updateBuildConfiguration": "automatic",
    "java.compile.nullAnalysis.mode": "disabled"
  }
}
```

Opening this workspace lets the Java extension import both Maven builds. The
workspace file is local development configuration and should not be stored in
either repository.

## Assets and attribution

The WAD remains an authoritative source asset and is never modified by the
import process. Generated output is disposable and can be reproduced from the
source WAD.

Asset provenance and checksums are recorded in
[`src/main/resources/assets/ATTRIBUTION.md`](src/main/resources/assets/ATTRIBUTION.md).
The upstream license and credits are retained in [`assets/`](assets/).

Freedoom and Doom are not affiliated with or endorsed by this project.
