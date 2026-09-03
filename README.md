# Doomed Corridors

Doomed Corridors is an unofficial Doom-compatible first-person game built with
JScene3D. It uses attributed assets from Freedoom Phase 2 and aims to support its
complete 32-map campaign with classic Doom II gameplay semantics.

## Game data

Doomed Corridors reads its maps, artwork, audio, and other game data from a
pinned Freedoom Phase 2 WAD. The WAD is not stored in this repository.

Follow [`assets/README.md`](assets/README.md) to install the required source WAD
and verify its release and checksum. The project manifest identifies the source
asset, provider-owned [`game/actors.json`](game/actors.json) catalog, and startup
map in [`project.json`](project.json). The actor catalog assigns Doom II meanings
and initial sprite frames to the numeric thing types stored in classic maps.
Provider-owned [`game/combat.json`](game/combat.json) defines initial player
resources, the pistol's hitscan and damage rules, and the zombieman's health and
collision bounds.

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
decorations are presented as inert camera-facing sprites; their gameplay behavior
is not active yet. Use W/S or Up/Down to move, A/D to strafe, Left/Right to turn,
and the mouse to look around. Click the rendered view to capture the pointer.
Escape releases a captured pointer; press it again or close the window to stop
the game.

Run the same loading and geometry pipeline without starting windowing with:

```shell
./mvnw -Pinspect compile
```

The headless inspector also writes visual material and sprite sheets to
`target/smoke/map01-materials.png` and `target/smoke/map01-sprites.png`. Its
console summary includes the combat rules and number of initialized MAP01
combatants.

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
