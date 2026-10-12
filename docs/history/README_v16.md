# Sluggershot v16 — Shuttle Thruster prototype

Visual direction
- Player actor is now a green, top-down shuttle inspired by the supplied/reference mockup:
  dark space field, vivid green craft, cyan fuel instrumentation, industrial hardware.
- The physical slingshot mechanism is mounted on the shuttle nose.
- Thruster exhaust grows with actual thrust.

Tilt thruster
- Uses Android TYPE_ROTATION_VECTOR.
- The first ~0.7 seconds after sensor start/recalibration establish the player's natural
  portrait holding angle as neutral.
- A deliberate forward tilt past a dead zone ignites analogue thrust.
- Thrust accelerates the shuttle upward slowly; releasing tilt lets simulated gravity
  return it toward its original position.
- Original launcher Y remains the hard lowest position; upper travel is limited to
  roughly 43% of screen height.
- Full tank gives about 7.5 seconds of continuous full thrust.
- FUEL debug button fills the tank and recalibrates neutral tilt.

Fuel economy
- Cyan FUEL gauge added at upper-right.
- FUEL pickup added to the scrolling field.
- Pickup restores 34% tank.
- Fuel pickup weighting increases substantially below 70%, especially below 35%.
- Fuel does not increase enemy-director pressure.

Existing v15.1 loadout toggles, enemy director, damage feedback, ejection, bombs,
shield, projectile safety limits and game-over reset remain.
