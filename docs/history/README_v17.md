# Sluggershot v17

Movement
- Descent is substantially faster: gravity 335 -> 560 and max drop speed 390 -> 620 px/s.
- Normal climb remains responsive.
- Boss battles supercharge the thruster: infinite fuel and 2x thrust/climb speed.

Enemy collisions
- A standard enemy colliding with the shuttle deals one HULL damage and is immediately despawned.
- Boss contact still deals damage but the boss survives and is knocked away.

Boss battle prototype
- First dedicated boss encounter begins at ~75 seconds in Enemy mode.
- Normal random mini-boss spawning is disabled so bosses read as events.
- Starting the encounter clears normal enemies, walls, destructible walls, projectiles and field pickups.
- Boss is much larger (92 px radius) and has 140 HP.
- A large full-width BOSS health bar appears near the top.
- Boss fuel reads FUEL SUPERCHARGED!!, does not drain, and thrust is doubled.
- When the boss dies, normal procedural stage spawning resumes.
- This first boss intentionally has no adds/walls; later bosses can define their own encounter criteria.
