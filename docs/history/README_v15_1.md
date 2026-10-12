# Sluggershot v15.1

Debug power-up controls
- SPREAD / AUTO / PIERCE / DOUBLE buttons are now independent toggles.
- Tap once = enable that pickup in the player's loadout.
- Tap again = disable it.
- Multiple buttons can be active simultaneously.
- NONE clears the debug weapon stack.
- BOMB toggles a one-bomb debug inventory.
- SHIELD immediately restores one HULL for debugging.
- BEAM remains a separate special-mode toggle.
- Button highlighting reflects the active debug loadout.

Damage feedback
- Taking damage now produces a short red full-screen flash and red border.
- The game world gets a brief impact shake.
- Damage uses a stronger two-stage vibration pattern on hardware.
- Wall-impact + break audio are layered for a more obvious hit cue.
- Existing power-up ejection and HULL loss still occur on the same damage event.
