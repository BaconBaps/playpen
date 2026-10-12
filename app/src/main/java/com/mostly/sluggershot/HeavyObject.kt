package com.mostly.sluggershot

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.sqrt

class HeavyObject(
    var x: Float,
    var y: Float,
    val radius: Float
) {
    var vx = 0f
    var vy = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF555A5E.toInt()
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB0B4B7.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 7f
    }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF202224.toInt()
    }

    fun update(dt: Float, width: Float, height: Float, walls: List<ArenaWall>) {
        x += vx * dt
        y += vy * dt

        val damping = (1f - 0.75f * dt).coerceIn(0f, 1f)
        vx *= damping
        vy *= damping

        if (x - radius < 0f) { x = radius; vx = abs(vx) * 0.65f }
        if (x + radius > width) { x = width - radius; vx = -abs(vx) * 0.65f }
        if (y - radius < 0f) { y = radius; vy = abs(vy) * 0.65f }
        if (y + radius > height) { y = height - radius; vy = -abs(vy) * 0.65f }

        for (wall in walls) resolveWall(wall)
    }

    private fun resolveWall(wall: ArenaWall) {
        val rect = wall.rect
        val nearestX = x.coerceIn(rect.left, rect.right)
        val nearestY = y.coerceIn(rect.top, rect.bottom)
        val dx = x - nearestX
        val dy = y - nearestY
        val d2 = dx * dx + dy * dy
        if (d2 >= radius * radius) return

        val d = sqrt(d2)
        if (d > 0.001f) {
            val nx = dx / d
            val ny = dy / d
            val penetration = radius - d
            x += nx * penetration
            y += ny * penetration
            val vn = vx * nx + vy * ny
            if (vn < 0f) {
                vx -= 1.55f * vn * nx
                vy -= 1.55f * vn * ny
            }
        }
    }

    fun hitBy(projectile: Projectile) {
        val dx = x - projectile.x
        val dy = y - projectile.y
        val d = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
        val impulse = projectile.mass * projectile.speed() * 0.010f
        vx += dx / d * impulse
        vy += dy / d * impulse
        projectile.vx *= 0.45f
        projectile.vy *= 0.45f
    }

    fun speed(): Float = sqrt(vx * vx + vy * vy)

    fun draw(canvas: Canvas) {
        canvas.drawCircle(x, y, radius, fillPaint)
        canvas.drawCircle(x, y, radius, rimPaint)
        canvas.drawCircle(x, y, radius * 0.38f, hubPaint)
    }
}
