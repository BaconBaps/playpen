# v18.1 compile fix

Fixed unresolved `arenaSpawnTimer` in the boss-battle cleanup path.
The generated side-wall system uses `solidWallSpawnTimer`; the boss code
was referring to an obsolete/nonexistent variable name.

No gameplay tuning changed.
