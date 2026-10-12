package com.mostly.sluggershot

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.abs

class Projectile(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val piercing: Boolean = false,
    val bouncy: Boolean = false,
    val bounceLimit: Int = MAX_BOUNCES - 1,
    val maxLifetimeSeconds: Float = MAX_LIFETIME_SECONDS
) {
    var active = true
        private set

    var bounceCount = 0
        private set

    private var ageSeconds = 0f

    val mass: Float = radius * radius

    val damageMultiplier: Float
        get() = 1f + bounceCount * 0.45f

    fun damage(): Float = damageMultiplier

    fun environmentDamage(): Float {
        val heavyFactor =
            if (radius >= Slingshot.PROJECTILE_RADIUS * 2f) 3.5f else 1f
        return damageMultiplier * heavyFactor
    }

    fun speed(): Float = kotlin.math.sqrt(vx * vx + vy * vy)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (piercing) 0xFF80DEEA.toInt() else 0xFFE0E0E0.toInt()
    }
    private val heatPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
    }
    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun integrate(dt: Float) {
        if (!active) return
        ageSeconds += dt
        if (ageSeconds >= maxLifetimeSeconds) {
            active = false
            return
        }
        x += vx * dt
        y += vy * dt
    }

    fun resolveScreenWalls(
        screenWidth: Float,
        screenHeight: Float,
        onBounce: () -> Unit,
        onBreak: () -> Unit
    ) {
        if (!active) return
        val margin = radius * 2f
        if (x < -margin || x > screenWidth + margin || y < -margin || y > screenHeight + margin) {
            active = false
        }
    }

    fun registerArenaWallBounce(
        onBounce: () -> Unit,
        onBreak: () -> Unit
    ) {
        if (!active || piercing) return
        if (!bouncy) {
            active = false
            onBreak()
            return
        }
        registerBounce(onBounce, onBreak)
    }

    private fun registerBounce(
        onBounce: () -> Unit,
        onBreak: () -> Unit
    ) {
        bounceCount++
        if (bounceCount > bounceLimit) {
            active = false
            onBreak()
        } else onBounce()
    }

    fun deactivate() {
        active = false
    }

    fun draw(canvas: Canvas) {
        if (!active) return

        val shotColor = when {
            piercing -> 0xFFC86BFF.toInt()
            bouncy -> 0xFF29B6F6.toInt()
            else -> 0xFF64D8FF.toInt()
        }
        val velocity = speed().coerceAtLeast(1f)
        val nx = vx / velocity
        val ny = vy / velocity
        val trailLength = (radius * 5.8f + velocity * 0.025f).coerceAtMost(radius * 11f)

        // Three-stage luminous trail: broad colour, hot inner streak, white tip.
        trailPaint.color = shotColor
        trailPaint.alpha = 58
        trailPaint.strokeWidth = radius * 2.3f
        canvas.drawLine(x, y, x - nx * trailLength, y - ny * trailLength, trailPaint)
        trailPaint.alpha = 150
        trailPaint.strokeWidth = radius * 0.82f
        canvas.drawLine(x, y, x - nx * trailLength * 0.72f, y - ny * trailLength * 0.72f, trailPaint)

        paint.color = shotColor
        paint.alpha = 255
        canvas.drawCircle(x, y, radius, paint)
        corePaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(x, y, radius * 0.42f, corePaint)
        corePaint.color = 0xFFE8FFFF.toInt()
        canvas.drawCircle(x - nx * radius * 0.10f, y - ny * radius * 0.10f, radius * 0.22f, corePaint)

        if (bounceCount > 0) {
            heatPaint.color =
                if (bounceCount >= 3) 0xFFFF3D00.toInt()
                else 0xFFFF8F00.toInt()
            heatPaint.alpha = (100 + bounceCount * 38).coerceAtMost(255)
            canvas.drawCircle(x, y, radius + 5f + bounceCount * 2f, heatPaint)
        }
    }

    companion object {
        private const val MAX_LIFETIME_SECONDS = 5.0f
        const val MAX_BOUNCES = 4
    }
}
