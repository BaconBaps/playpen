package com.mostly.sluggershot

import android.graphics.Canvas
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.sqrt

class ArenaWall(
    val rect: RectF
) {
    fun scrollBy(dy: Float) {
        rect.offset(0f, dy)
    }

    fun draw(canvas: Canvas) {
        PixelArt.wall(canvas,rect)
    }

    fun resolve(projectile: Projectile): Boolean {
        if (!projectile.active || projectile.piercing) return false

        val nearestX = projectile.x.coerceIn(rect.left, rect.right)
        val nearestY = projectile.y.coerceIn(rect.top, rect.bottom)
        val dx = projectile.x - nearestX
        val dy = projectile.y - nearestY
        val distanceSquared = dx * dx + dy * dy
        val radiusSquared = projectile.radius * projectile.radius

        if (distanceSquared >= radiusSquared) return false

        var nx: Float
        var ny: Float
        var penetration: Float

        if (distanceSquared > 0.0001f) {
            val distance = sqrt(distanceSquared)
            nx = dx / distance
            ny = dy / distance
            penetration = projectile.radius - distance
        } else {
            // Centre is inside the rectangle. Push toward the nearest face.
            val leftDistance = abs(projectile.x - rect.left)
            val rightDistance = abs(rect.right - projectile.x)
            val topDistance = abs(projectile.y - rect.top)
            val bottomDistance = abs(rect.bottom - projectile.y)
            val minDistance = minOf(leftDistance, rightDistance, topDistance, bottomDistance)

            when (minDistance) {
                leftDistance -> {
                    nx = -1f; ny = 0f
                    penetration = projectile.radius + leftDistance
                }
                rightDistance -> {
                    nx = 1f; ny = 0f
                    penetration = projectile.radius + rightDistance
                }
                topDistance -> {
                    nx = 0f; ny = -1f
                    penetration = projectile.radius + topDistance
                }
                else -> {
                    nx = 0f; ny = 1f
                    penetration = projectile.radius + bottomDistance
                }
            }
        }

        projectile.x += nx * (penetration + 0.05f)
        projectile.y += ny * (penetration + 0.05f)

        val velocityAlongNormal = projectile.vx * nx + projectile.vy * ny
        if (velocityAlongNormal >= 0f) return false

        projectile.vx -= 2f * velocityAlongNormal * nx
        projectile.vy -= 2f * velocityAlongNormal * ny
        return true
    }
}
