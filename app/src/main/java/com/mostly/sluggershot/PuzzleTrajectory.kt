package com.mostly.sluggershot

import kotlin.math.ceil

/** Predict with the game's circle/wall collision code, including projectile radius. */
object PuzzleTrajectory {
    data class Point(val x: Float, val y: Float, val bounces: Int, val distance: Float)
    fun trace(
        originX: Float, originY: Float, nx: Float, ny: Float, radius: Float,
        walls: List<ArenaWall>, length: Float, bounceLimit: Int,
        targets: List<Enemy> = emptyList()
    ): List<Point> {
        val shot = Projectile(-1, originX + nx * (radius + 18f), originY + ny * (radius + 18f),
            nx * 1000f, ny * 1000f, radius, bouncy = true,
            bounceLimit = bounceLimit, maxLifetimeSeconds = 120f)
        val result = ArrayList<Point>()
        result += Point(shot.x, shot.y, 0, 0f)
        val step = (radius * 0.3f).coerceIn(2f, 6f)
        val steps = ceil(length / step).toInt()
        for (i in 1..steps) {
            shot.integrate(step / 1000f)
            for (wall in walls) {
                if (wall.resolve(shot)) shot.registerArenaWallBounce({}, {})
                if (!shot.active) break
            }
            result += Point(shot.x, shot.y, shot.bounceCount, i * step)
            if (!shot.active || targets.any {
                it.active && (it.x-shot.x)*(it.x-shot.x)+(it.y-shot.y)*(it.y-shot.y) <=
                    (it.radius+radius)*(it.radius+radius)
            }) break
        }
        return result
    }
}
