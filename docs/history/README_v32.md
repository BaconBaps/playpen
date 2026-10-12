# Sluggershot v32 — emissive lighting pass

- Added a screen-space radial lighting pass over gameplay objects.
- Player projectiles now cast cyan/electric-blue/violet/green light depending on weapon state.
- Heavy rounds have substantially larger bloom.
- Projectiles now render with saturated bodies, clipped white-hot cores and layered motion trails.
- Boss plasma has warm red/orange illumination and a white-hot center.
- Beam weapon gets a broad translucent halo beneath both charging and fired beam passes.
- Active thrusters cast cyan light around/behind the shuttle.
- Power-ups now emit colour-coded light into the surrounding scene.
- Bosses have pulsing reactor glows unique to their six variants.
- Bomb detonation briefly floods the scene with blue-white illumination.

Implementation uses Canvas RadialGradient/alpha layers rather than expensive real-time physical lights.
