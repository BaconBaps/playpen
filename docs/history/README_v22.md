# Sluggershot v22 — boss combat expansion

Bosses
- Boss remains at 1400 HP (10x the original dedicated boss HP).
- Three randomized boss personalities:
  - Forge: sweeping movement, frequent shots, occasional charge.
  - Charger: tracks the shuttle more aggressively and performs stronger charge attacks.
  - Artillery: stays high, drifts broadly, fires three-shot spreads, no body-charge attack.
- Player projectiles now knock bosses back.
- Beam damage ticks also apply lighter directional knockback.
- Bosses periodically fire hostile projectiles that damage the player on contact.

Boss-battle pickups
- Shield, Spread, Auto, Piercing, Double and Beam pickups drift in from the top.
- They arrive on a dedicated 2.6–5.0 second boss-battle cadence.
- Beam pickups equip Beam immediately.

Beam
- Maximum Beam width increased 4x: 90 -> 360.
- Beam damage strength doubled: 4 -> 8 damage per 0.5-second tick.
- A fully charged Beam now enters the SAME charged reservoir drain state as Auto:
  1.5 seconds of charged output, then the existing 3.5-second recovery state.
- Beam cannot start another charged cycle until the reservoir is full again.
- If Beam is still equipped when the boss dies, it is forcibly disabled.
- A visible `BOSS DEFEATED • BEAM DISENGAGED` message tells the player when that happens.
