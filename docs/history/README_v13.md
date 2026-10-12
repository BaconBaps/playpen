# Sluggershot v13

- Each Enemy-mode run now starts from a newly randomized field state.
- Difficulty ramps continuously over roughly 3 minutes.
- Opening: 3–5 normal enemies, sparse permanent side walls, sparse destructible walls.
- Enemy population cap and spawn cadence increase with difficulty.
- Boss/tough-enemy spawn chance begins after 45 seconds and rises with difficulty.
- Permanent walls remain side-lane dodge hazards; destructible walls remain the majority geometry.
- Wall generation frequency increases with difficulty.

Power-up actors:
- SPREAD (blue), AUTO (amber), PIERCE (purple), with text labels.
- They spawn above the field and scroll at wall/world speed.
- Contact collects them.
- SPREAD, AUTO and PIERCE stack in any combination.
- AUTO+SPREAD fires automatic five-shot volleys.
- SPREAD+PIERCE makes all spread pellets piercing.
- AUTO+PIERCE and AUTO+SPREAD+PIERCE work together.
- Taking any player damage ejects all collected power-ups back into the field with a brief outward burst, after which they resume world scrolling and can be recollected.
- Beam remains a selectable special mode in this prototype.
