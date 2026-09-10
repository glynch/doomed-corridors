# Local playtest profiles

These profiles launch a gameplay world directly at a named, repeatable test location. They are development inputs,
not project assets, so application export does not include them.

Run the moving-floor profile with:

```shell
./mvnw process-classes -Prun-desktop -Ddoomed.corridors.playtest=moving-floor-34
```

Available profiles:

- `map01-start-invulnerable` starts at MAP01's normal spawn without accepting player damage.
- `moving-floor-34` starts immediately in front of moving floor sector 34, facing away from it. Click once to capture
  the pointer, do not turn, then hold `S`. The player backs into the walk-over trigger while the ledge remains visible;
  the ledge should lower to the surrounding floor height.
- `moving-floor-staircase` starts at the bottom center of the four steps beyond moving floor sector 34. Click once to
  capture the pointer, do not turn, then hold `W`. The player should climb all four steps and enter the raised room.
- `shotgun-pickup` starts on the raised platform east of the MAP01 shotgun, facing it. Click once to capture the
  pointer, then hold `W` briefly. The shotgun should disappear, the first-person weapon should change to the shotgun,
  and the HUD should show `8` shells. A shot should respond immediately, complete its recoil and pump sequence, and
  ignore further clicks until it returns to the ready frame. Press `1` for the pistol and `2` for the shotgun.
- `green-armor-pickup` starts immediately west of the green armor, facing it. Click once to capture the pointer, then
  hold `W` briefly. The armor should disappear and the centered HUD value should become `100%`. This profile remains
  invulnerable so the pickup can be inspected without having to survive the surrounding encounter.
