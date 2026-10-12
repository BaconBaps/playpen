# Sluggershot v12

## Player health
- Player has 5 HULL health points.
- First contact with an enemy, permanent wall, or destructible wall removes 1 HP.
- Continuous overlap is treated as one collision; a new hit requires separation and re-contact.
- Enemy contact applies knockback to reduce unavoidable repeated overlap.
- Health resets when switching game mode.

## Scrolling field
- Field structures travel top-to-bottom at a common world-scroll speed.
- Permanent walls generate above the screen only in the left/right side lanes.
- They are deliberately infrequent and sized as dodge obstacles, preserving the centre corridor.
- Destructible steel generates more frequently across the field, but with spacing and width caps to preserve planning lanes.
- Old structures are removed after passing below the screen.
- The existing multi-layer starfield continues to scroll with independent parallax speeds.

## Enemy pacing
- Initial Enemy-mode actors are restricted to the upper ~46% of the screen.
- Newly spawned actors appear within the upper ~34% rather than immediately at the launcher.
- Initial enemy population reduced from 22 to 12.
- Maximum enemy population reduced from 45 to 28.
- Spawn interval increased from 1.4s to 2.1s.

v11.4 single-finger controls and all existing firing modes are retained.
