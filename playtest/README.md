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
