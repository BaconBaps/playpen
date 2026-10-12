# Sluggershot v29 — action-flow pass

- Horizontal MOVE is now direct finger tracking: once a horizontal gesture claims MOVE, the shuttle follows that finger's X position.
- Replaced mostly random enemy spawning with a six-pattern encounter director.
- Encounters include scout lines, crossing raiders, mine gates, guarded convoys, interceptor attacks and mixed chevrons.
- Every fourth encounter inserts a short breathing window to create pacing instead of uninterrupted random pressure.
- The five enemy sprites now have gameplay identities:
  - Scout: fast straight pass
  - Raider: lateral weaving pass
  - Mine: slow lane-denial drift
  - Guard: slow heavy lane blocker
  - Interceptor: actively tracks the player
- Ordinary enemies now flow down through and off the field rather than all continuously collapsing onto the player.
- Existing long-press thruster and simultaneous multitouch shooting remain intact.
