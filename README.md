# Doomed Corridors

Doomed Corridors is an unofficial Doom-compatible first-person game built with
JScene3D. It uses attributed assets from Freedoom Phase 2 and aims to support its
complete 32-map campaign with classic Doom II gameplay semantics.

## Game data

Doomed Corridors reads its maps, artwork, audio, and other game data from a
pinned Freedoom Phase 2 WAD. The WAD is not stored in this repository.

Follow [`assets/README.md`](assets/README.md) to install the required source WAD
and verify its release and checksum. The project manifest declares the source
asset and selects [`worlds/map01.world.json`](worlds/map01.world.json) as its
entry world. [`imports/freedoom-map01.import.json`](imports/freedoom-map01.import.json)
selects `MAP01` from the WAD. The application importer publishes the selection
as a generated, read-only entity definition backed by generic JScene3D mesh,
material, and texture resources. The authored world places that definition and
a camera; the generic desktop project host resolves and composes both.

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

Publish the imported content and run the project through JScene3D's generic
desktop launcher with:

```shell
./mvnw process-classes -Prun-desktop
```

The current migration slice renders textured static MAP01 geometry from the
WAD-defined player-one start. Player movement, collision, doors, actors, combat,
audio, and the HUD have not yet been connected to the new entity-component
runtime.

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
