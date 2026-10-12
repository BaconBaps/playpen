package com.mostly.sluggershot

import android.graphics.Canvas
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.sqrt

class DestructibleSteel(
    val rect: RectF,
    private val maxHealth: Float = 10f
) {
    var health = maxHealth
        private set

    val active: Boolean
        get() = health > 0f

    fun scrollBy(dy: Float) {
        rect.offset(0f, dy)
    }

    fun draw(canvas: Canvas) {
        if(!active)return
        val ratio=(health/maxHealth).coerceIn(0f,1f)
        PixelArt.wall(canvas,rect,ratio)
        if(ratio<0.67f) {
            val x=rect.centerX();val y=rect.centerY()
            PixelArt.rect(canvas,x-12f,y-6f,x+4f,y-2f,0xFFFF9A54.toInt())
            PixelArt.rect(canvas,x,y-2f,x+4f,y+10f,0xFFFF9A54.toInt())
            PixelArt.rect(canvas,x-8f,y+10f,x+4f,y+14f,0xFFAF4850.toInt())
        }
    }

    fun resolve(projectile: Projectile): Boolean {
        if (!active || !projectile.active || projectile.piercing) return false

        val nearestX = projectile.x.coerceIn(rect.left, rect.right)
        val nearestY = projectile.y.coerceIn(rect.top, rect.bottom)
        val dx = projectile.x - nearestX
        val dy = projectile.y - nearestY
        val distanceSquared = dx * dx + dy * dy
        if (distanceSquared >= projectile.radius * projectile.radius) return false

        val distance = sqrt(distanceSquared)
        var nx: Float
        var ny: Float
        var penetration: Float

        if (distance > 0.001f) {
            nx = dx / distance
            ny = dy / distance
            penetration = projectile.radius - distance
        } else {
            val leftGap = abs(projectile.x - rect.left)
            val rightGap = abs(rect.right - projectile.x)
            val topGap = abs(projectile.y - rect.top)
            val bottomGap = abs(rect.bottom - projectile.y)
            val minGap = minOf(leftGap, rightGap, topGap, bottomGap)
            when (minGap) {
                leftGap -> { nx = -1f; ny = 0f; penetration = projectile.radius + leftGap }
                rightGap -> { nx = 1f; ny = 0f; penetration = projectile.radius + rightGap }
                topGap -> { nx = 0f; ny = -1f; penetration = projectile.radius + topGap }
                else -> { nx = 0f; ny = 1f; penetration = projectile.radius + bottomGap }
            }
        }

        projectile.x += nx * (penetration + 0.05f)
        projectile.y += ny * (penetration + 0.05f)

        val velocityAlongNormal = projectile.vx * nx + projectile.vy * ny
        if (velocityAlongNormal < 0f) {
            projectile.vx -= 2f * velocityAlongNormal * nx
            projectile.vy -= 2f * velocityAlongNormal * ny
        }

        takeDamage(projectile.environmentDamage())
        return true
    }

    fun takeBeamDamage(amount: Float) {
        takeDamage(amount * 0.45f)
    }

    private fun takeDamage(amount: Float) {
        health = (health - amount).coerceAtLeast(0f)
    }
}
