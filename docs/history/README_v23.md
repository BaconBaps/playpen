# Sluggershot v23 — simultaneous move + shoot multitouch

- Two independent touch pointers are now tracked.
- Either finger may claim the first role, regardless of touch order.
- If one finger locks horizontal MOVE, the other finger is constrained to vertical SHOOT.
- If one finger locks vertical SHOOT, the other finger is constrained to horizontal MOVE.
- The complementary finger still has to make the correct directional gesture before its role engages.
- MOVE and SHOOT then operate simultaneously: the shuttle can translate horizontally while the
  slingshot remains pulled, aimed, charged, Auto-firing, or Beam-firing.
- Releasing the shooting finger fires/releases the current shot without cancelling the remaining
  movement finger.
- Releasing the movement finger leaves the shooting gesture active.
- One-finger controls remain compatible with the existing direction classifier.
- Two-finger input no longer uses the obsolete centre-of-two-fingers slide behaviour.
