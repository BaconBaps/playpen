# Sluggershot v27 — Bounce weapon + generated enemy sprites

Projectile redesign
- The default projectile is now half the radius of the former shot.
- It retains the same 1.0 enemy damage.
- Default shots do not ricochet from arena/destructible surfaces; they despawn on impact.
- BOUNCE is a new stackable weapon powerup.
- BOUNCE restores the former full projectile radius and the existing ricochet system (up to 4 impacts).
- Bounce can combine with Spread, Auto, Double and the other stackable weapon systems.
- Bounce appears in normal random drops, boss-battle drops, debug controls, checkpoint state,
  ejected weapon pickups and boss-victory weapon cleanup.

Enemy art
- Added five generated enemy spacecraft sprites: Scout, Raider, Mine Drone, Shield Guard and Interceptor.
- Normal enemies randomly select one of these visual variants.
- Existing collision radius, HP and enemy movement remain unchanged, so this is an art pass rather
  than an unrequested balance change.
- Boss rendering remains on the existing dedicated boss system.
