# v11.1 compile patch

v11's touch-handler replacement accidentally removed four helper methods that
the new ACTION_DOWN path still calls:

- gameModeAt
- selectGameMode
- modeAt
- selectMode

They have been restored. No gameplay parameters were changed.
