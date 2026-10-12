# Sluggershot v14

DOUBLE
- New cyan DOUBLE pickup.
- Produces two parallel projectiles side-by-side.
- Fully stackable: DOUBLE+SPREAD produces 10 projectiles per volley; AUTO and PIERCE can combine with both.

BOMB
- New red BOMB pickup, weighted to roughly 5.5% of random pickup rolls.
- Bombs are inventory items, not a firing mode.
- Triple-tap the playfield to consume one bomb.
- A bomb clears enemies and destructible walls currently within the visible screen.
- Permanent side walls are unaffected.
- Bomb inventory stacks.
- Bombs do NOT eject when the player takes damage.

Power-up ejection fix
- Held SPREAD/AUTO/PIERCE/DOUBLE still eject on every player damage event.
- Ejected actors now spawn separated from the player with a stronger outward/upward impulse.
- They have a short 0.85 second recollection lockout, preventing immediate same-frame/overlap recollection.
- After that they resume normal world scrolling and can be recovered.

Beam remains separate/special.
