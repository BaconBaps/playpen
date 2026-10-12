package com.mostly.sluggershot

import android.graphics.Canvas
import android.graphics.Paint
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

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val crackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF8F00.toInt()
        strokeWidth = 3f
    }

    fun scrollBy(dy: Float) {
        rect.offset(0f, dy)
    }

    fun draw(canvas: Canvas) {
        if (!active) return

        val ratio = (health / maxHealth).coerceIn(0f, 1f)
        fillPaint.color = when {
            ratio > 0.66f -> 0xFF34383C.toInt()
            ratio > 0.33f -> 0xFF4A3E34.toInt()
            else -> 0xFF5A3022.toInt()
        }
        edgePaint.color = if (ratio > 0.33f) 0xFF9EA3A6.toInt() else 0xFFFF8F00.toInt()

        canvas.drawRoundRect(rect, 5f, 5f, fillPaint)
        canvas.drawRoundRect(rect, 5f, 5f, edgePaint)

        if (ratio < 0.67f) {
            val cx = rect.centerX()
            val cy = rect.centerY()
            canvas.drawLine(cx - 22f, cy - 12f, cx + 4f, cy + 2f, crackPaint)
            canvas.drawLine(cx + 4f, cy + 2f, cx - 8f, cy + 18f, crackPaint)
            canvas.drawLine(cx + 4f, cy + 2f, cx + 24f, cy + 14f, crackPaint)
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
