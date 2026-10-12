# Sluggershot v15

- Removed the movable/bouncing ball objects.
- Enemy pressure now combines the existing elapsed-time difficulty ramp with the player's loadout.
  Normal = sparse; SPREAD = broad waves; DOUBLE = larger/doubled groups; PIERCE = vertical queues;
  AUTO = much faster spawn cadence. Effects stack, with safety caps.
- Pickup cadence reduced to ~15–25s and becomes slower as the loadout grows.
- Already-held weapons no longer reappear normally.
- SPREAD makes AUTO much rarer, and AUTO makes SPREAD much rarer.
- At HULL < 3, green SHIELD pickups enter the pool at a moderately elevated weight.
  SHIELD immediately restores 1 HULL, max 5.
- Bomb remains rare.
- Projectile storm fix: maximum 180 active projectiles and a hard 5-second projectile lifetime,
  including piercing projectiles. This prevents full-charge piercing/spread/auto shots persisting.
- Existing ejection, bombs, game-over reset, procedural field and single-finger controls retained.
