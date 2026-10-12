# Sluggershot v28 — touch thruster

- Device tilt no longer drives the thruster.
- Holding a stationary finger for 250 ms engages full thrust.
- The press may drift up to 18 px during recognition; moving farther first lets the normal
  MOVE/SHOOT gesture classifier take over instead.
- Thrust remains active for exactly as long as that finger remains held.
- THRUST is an independent multitouch role: while one finger holds thrust, another finger can
  aim/fire or move horizontally.
- A thrust finger does not force the complementary MOVE/SHOOT rule on the other pointer.
- Existing fuel consumption, vertical inertia/gravity, boss supercharge and thrust visuals remain.
