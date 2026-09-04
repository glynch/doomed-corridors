# Doomed Corridors

Doomed Corridors is an unofficial Doom-compatible first-person game built with
JScene3D. It uses attributed assets from Freedoom Phase 2 and aims to support its
complete 32-map campaign with classic Doom II gameplay semantics.

## Game data

Doomed Corridors reads its maps, artwork, audio, and other game data from a
pinned Freedoom Phase 2 WAD. The WAD is not stored in this repository.

Follow [`assets/README.md`](assets/README.md) to install the required source WAD
and verify its release and checksum. The project manifest declares the source
asset and selects [`application/main.scene.json`](application/main.scene.json)
as its entry scene. That scene contains a typed Doom level whose properties
reference `MAP01` through [`imports/freedoom-maps.import.json`](imports/freedoom-maps.import.json).
The import definition selects the map from the WAD through JScene3D's generic
import framework, allowing the source, selection, and generated native resource
to be inspected and edited by future tooling. The actor catalog in
[`game/actors.json`](game/actors.json) assigns Doom II meanings and initial
sprite frames to the numeric thing types stored in classic maps.
[`game/combat.json`](game/combat.json) defines initial player
resources, the pistol's hitscan and damage rules, and the zombieman's health and
collision bounds, awareness, pursuit, attack timing, and damage. It also defines
player resource capacities and effects for stimpacks, medikits, health bonuses,
soulspheres, ammunition clips, and bullet boxes.
[`game/combat-presentation.json`](game/combat-presentation.json) binds those
identities to WAD-backed weapon, movement, attack, pain, death, pickup sound, and
HUD assets without embedding their lump names in the application.

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

Run the application with:

```shell
./mvnw -Prun compile
```

With the source WAD installed, the application loads the manifest-selected map,
builds its static floors, ceilings, and walls, and opens the view at the
WAD-defined player-one start. Normal-skill single-player enemies, pickups, and
decorations are presented as camera-facing sprites. Zombiemen can be shot and
killed; when they see the player, they pursue and return fire. Use W/S or Up/Down
to move, A/D to strafe, Left/Right to turn, the mouse to look around, and the
left mouse button to fire the pistol. Click inside the window to capture the
pointer; this initial click does not fire. The WAD-backed HUD shows health and
remaining bullets; damage flashes the view red and zero health ends player
movement. Walking over a useful configured health or bullet pickup applies its
effect, plays its sound, updates the HUD, and removes it from the map. Pickups
remain available while their resource is at that item's limit. Escape releases
the pointer. Press Escape again or close the window to stop the game.

Run the same loading and geometry pipeline without starting windowing with:

```shell
./mvnw -Pinspect compile
```

The headless inspector also validates the combat presentation's 28 patches and
11 DMX sounds, and writes visual material and sprite sheets to
`target/smoke/map01-materials.png` and `target/smoke/map01-sprites.png`. Its
console summary includes the combat rules and number of initialized MAP01
combatants.

Run the declarative import and project-runtime path headlessly with:

```shell
./mvnw -Pinspect-project-runtime compile
```

This updates the disposable import cache when required, resolves the scene's
`import:freedoom-maps/maps/MAP01` reference, creates a typed JScene3D `DoomMap`,
and supplies it to the application extension's `doom-level-3d` factory. The
console summary reports the imported map's thing, linedef, and sector counts.

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
      "path": "./threejs-java"
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
