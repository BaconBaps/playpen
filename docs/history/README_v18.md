# Sluggershot v18 — charged-shot reservoir

- The full charge circle is now a visible energy reservoir behind the shuttle.
- As charged energy is consumed, the circle shrinks inward toward its centre.
- A charged/heavy shot can only arm when the reservoir is completely full.
- Normal charged shot: consumes the full reservoir on firing, then refills quickly (~0.85 s).
- AUTO charged fire: once initiated from a full reservoir, charged output drains continuously
  over 1.5 seconds. At empty it stops being charged and enters a 3.5-second recharge.
- AUTO cannot initiate another charged burst until the reservoir is completely full again.
- The existing 2-second uninterrupted full-pull requirement still gates arming the charged shot.
- Ordinary uncharged firing remains available while the charge reservoir is recharging.
