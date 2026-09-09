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
HUD assets without embedding their lump names in the application. The startup
world authors its HUD as ordinary entities composed from generic screen-canvas,
screen-region, and bitmap-number components. A small Doom-specific component
binds the player state explicitly to the health and ammunition numbers; it does
not own screen layout or drawing.

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
WAD-defined player-one start, registers its generated static collision mesh
with the world physics module, and displays 119 item, enemy, corpse, and
decoration actors. Authored `move` and `look` actions drive the
descriptor-declared player controller, which moves the capsule through the
engine character-body API, resolves floor and wall collision, slides along
obstacles, and keeps the child camera attached. Health and bullet pickups use
authored sensors and signal connections to update the player-resource
capability and disappear only when useful. Solid enemies own descriptor-declared
damageable state as well as collision. The player's authored hitscan-weapon
component consumes the configured bullet cost, tries the exact view ray first,
then selects a visible damageable entity within the provider-authored auto-aim
window. It applies the configured discrete pistol damage and destroys an enemy
when its health reaches zero. Authored signal connections drive the imported
pistol animation and sound for every accepted shot plus a short red centre marker
only when damage is applied. The authored HUD displays live health at the lower
left and bullet ammunition at the lower right. Enemy behavior and doors have not
yet been connected to the new entity-component runtime.

Click the game window to capture the pointer; Escape releases it without
closing the application. W/A/S/D move, the mouse looks while captured, and the
left/right arrow keys turn. The left mouse button fires while the pointer is
captured. Held keyboard turning accelerates from the authored initial rate to its
authored maximum; releasing or reversing the key resets that rate. Close the
application with the native window close control.

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
