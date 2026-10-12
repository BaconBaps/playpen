# v11.2 compile patch

The v11 single-finger handler was written against temporary gesture-state names
that did not exist in the retained GameView.

This patch maps the new gesture classifier onto GameView's existing state:
- shotPointerId
- touchStartX / touchStartY
- currentTouchX / currentTouchY
- resetCharge()

It also removes stray shotStartX/shotStartY references.

Known v11 temporary symbols were audited and are no longer present.
