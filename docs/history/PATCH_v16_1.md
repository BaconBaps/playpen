# v16.1 compile fix

GameView already had an onDetachedFromWindow() lifecycle override.
v16 added a second one while introducing the tilt sensor, producing Kotlin's
"Conflicting overloads" compile error.

v16.1 keeps a single onDetachedFromWindow() and folds tiltThruster.stop()
into that existing cleanup path. No gameplay tuning changed.
