# Sluggershot v22.2 — Beam scaling / boss-death cleanup

Beam width
- Minimum Beam width remains the original 10 px.
- Maximum Beam width remains 360 px: 4x the old 90 px maximum.
- Width now therefore scales from the normal narrow starting beam up to the 4x maximum.

Beam damage
- The v22 base Beam damage remains 8 damage per 0.5-second tick.
- Damage now scales with pull/tension.
- At maximum pull the Beam reaches a further 6x damage multiplier: 48 damage per tick.
- Released Beam pulses retain the power level they had at release.

Boss death
- If the player dies during a boss battle while Beam is equipped, Beam is stripped immediately
  before the game-over/reset sequence.
- The new run therefore cannot respawn with the boss Beam still equipped.
- Existing successful-boss-defeat Beam disengagement remains unchanged.
