# Sluggershot v11

## Single-finger controls
The two-finger launcher movement model has been replaced.

- Touch and predominantly drag left/right: MOVE the launcher.
- Touch and drag downward or diagonally downward: lock into AIM/FIRE.
- Gesture intent is classified after 28 px of travel to reduce accidental mode changes.
- Once classified, the gesture stays locked until release.
- Weapon and game-mode buttons remain taps.
- Additional fingers are ignored.

## Field systems
Three destructible fabricated-steel structures have been added.
- Ordinary projectiles damage and bounce from them.
- Heavy projectiles do substantially more structural damage.
- Piercing rounds cut through them.
- Beam progressively cuts them while in contact.
- Steel changes appearance as it is damaged.

Three movable heavy weight objects have been added.
- Projectile momentum pushes them.
- Heavy projectiles transfer much more momentum because projectile mass scales with radius squared.
- Fast-moving weights can damage enemies.

## Ricochet energy
Every projectile wall/steel bounce increases projectile damage by 45%.
Ricocheted projectiles gain a visible orange/red heat ring.

## Parallax starfield
The playfield now has three independently scrolling star layers:
- far: small, dim, slow;
- middle: medium speed;
- near: larger, brighter, fast.

All layers continuously travel top-to-bottom to imply forward motion through space.

## Retained
Enemy and Target modes, boss, tension-scaled Auto, Beam contact damage, heavy charging,
sounds/haptics, forged-steel theme, and the v10.1 hardware-safe fullscreen startup.
