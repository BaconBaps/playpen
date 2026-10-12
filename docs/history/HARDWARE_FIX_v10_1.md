# Sluggershot v10.1 hardware launch fix

The physical-device crash occurred in MainActivity before GameView started:

`DecorView.getWindowInsetsController()` was invoked through the platform
`window.insetsController` path before the device's decor view was ready.

v10.1 changes only startup/fullscreen handling:

- GameView is attached before immersive-mode configuration.
- Fullscreen setup is deferred through `window.decorView.post`.
- Uses AndroidX `WindowCompat` / `WindowInsetsControllerCompat`.
- Immersive mode is restored after window focus returns.
- Fullscreen setup is non-fatal: a vendor/device-specific failure can no longer
  terminate the game.

All v10 gameplay, Beam damage, audio, enemies, target mode and forged-steel
visual changes are otherwise retained.
