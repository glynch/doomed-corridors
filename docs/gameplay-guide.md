# Gameplay guide

## Controls

| Action | Control |
| --- | --- |
| Capture the pointer | Click once inside the game window |
| Move | `W`, `A`, `S`, `D` |
| Look | Mouse while the pointer is captured |
| Turn | Left and Right arrow keys |
| Fire | Left click after the pointer is captured |
| Use a supported door | `E` |
| Select pistol | `1` |
| Select shotgun | `2` after collecting it |
| Pause or resume | `Esc` |

The HUD shows health at the lower left, armor in the lower center, and ammunition
for the selected weapon at the lower right. Picking up the shotgun selects it
automatically. An unavailable weapon cannot be selected.

## MAP01 actor support

The normal single-player MAP01 import contains 119 visible actor placements. The
table records their current gameplay treatment so visible artwork is not mistaken
for implemented behavior.

| Category | Placements | Current behavior |
| --- | ---: | --- |
| Ammunition | 10 | Bullet clips and shotgun shells are collectible; each updates its corresponding ammunition pool. |
| Armor | 22 | Armor bonuses, green armor, and blue armor are collectible and absorb damage at their authored protection rate. |
| Corpse | 1 | Non-blocking scenery. |
| Decoration | 47 | Trees, stalagmites, tech pillars, floor lamps, and barrels with authored solid bounds block movement. Other decorations remain non-blocking. |
| Enemy | 18 | Zombiemen are fully active. Imps, shotgun guys, and chaingunners have solid bounds but do not yet have combat behavior. |
| Health | 19 | Stimpacks, medikits, health bonuses, and soulspheres are collectible with their ordinary or bonus limits. |
| Weapon | 2 | The shotgun is collectible and usable. The chainsaw remains visible but is not yet collectible. |

The complete type-level inventory is:

| Actor type | Count | Collision | Gameplay |
| --- | ---: | --- | --- |
| Ammunition Clip | 5 | Sensor | Collectible bullets |
| Armor Bonus | 20 | Sensor | Collectible armor |
| Blue Armor | 1 | Sensor | Collectible armor |
| Burnt Tree | 19 | Solid | Scenery |
| Chainsaw | 1 | None | Visible; weapon behavior pending |
| Dead Player | 1 | None | Scenery |
| Explosive Barrel | 6 | Solid | Scenery; damage and explosion pending |
| Floor Lamp | 3 | Solid | Scenery |
| Green Armor | 1 | Sensor | Collectible armor |
| Health Bonus | 11 | Sensor | Collectible health |
| Imp | 3 | Solid | Visible; combat behavior pending |
| Large Brown Tree | 9 | Solid | Scenery |
| Medikit | 1 | Sensor | Collectible health |
| Shotgun | 1 | Sensor | Collectible weapon and shells |
| Shotgun Guy | 4 | Solid | Visible; combat behavior pending |
| Shotgun Shells | 5 | Sensor | Collectible shells |
| Soulsphere | 1 | Sensor | Collectible health |
| Stalagmite | 9 | Solid | Scenery |
| Stimpack | 6 | Sensor | Collectible health |
| Tech Pillar | 1 | Solid | Scenery |
| Zombieman | 11 | Solid | Active enemy |

Explosive barrels are currently solid scenery; they do not yet take damage or
explode. Actor collision is authored in `game/actors.json`, while pickup effects,
resource limits, weapon ownership, ammunition use, and damage protection are
authored in `game/combat.json`. This keeps the generated world inspectable by the
editor and avoids deriving gameplay behavior from sprite names.

## Focused playtesting

Named local profiles start next to difficult-to-reach features with player damage
disabled. See [`../playtest/README.md`](../playtest/README.md) for exact commands
and expected results. These profiles are development inputs and are not included
in exported applications.
