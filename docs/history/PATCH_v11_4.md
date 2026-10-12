# v11.4 compile patch

Restores cancelCurrentShot(), which the v11 ACTION_CANCEL path references.
Cancellation now clears the slingshot pull, Beam pulse/loop, haptics, Auto state,
and heavy-charge state without firing a projectile.
