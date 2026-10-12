# Sluggershot v25 — boss cleanup + retry checkpoint

Boss cleanup
- When a boss is defeated, every uncollected weapon pickup still drifting on-screen is removed.
- This covers Spread, Auto, Piercing, Double and Beam.
- Non-weapon utility pickups are not forcibly deleted by this cleanup.

Pre-boss checkpoint
- A checkpoint is captured exactly when the one-second pre-boss music fade begins, immediately
  before the boss notification.
- It preserves the current run's max HULL, current HULL, fuel, bombs, charge reservoir and
  collected weapon stack.
- If the player dies during the boss fight, the normal game-over pause is retained, then the
  player is restored to this checkpoint instead of being sent back to a fresh run.
- The timeline resumes one second before the boss warning, so the music transition and boss
  notification replay naturally.
- Beam is deliberately excluded from checkpoint restoration, preserving the existing rule that
  Beam is removed when the player dies to a boss.
- The checkpoint remains available across repeated boss attempts and is discarded after victory.
