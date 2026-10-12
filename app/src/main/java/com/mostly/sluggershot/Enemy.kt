package com.mostly.sluggershot

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Path
import kotlin.math.sqrt

class Enemy(
    val id: Long,
    var x: Float,
    var y: Float,
    val radius: Float,
    val maxHitPoints: Float = NORMAL_HIT_POINTS,
    val boss: Boolean = false,
    val bossVariant: Int = 0,
    val spriteVariant: Int = 0,
    val sprite: Bitmap? = null
) {
    var active = true
        private set

    var hitPoints: Float = maxHitPoints
        private set

    var vx = 0f
    var vy = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color =
            if (!boss) 0xFFE53935.toInt()
            else when (bossVariant) {
                0 -> 0xFFFF8F00.toInt() // Forge
                1 -> 0xFFAB47BC.toInt() // Charger
                else -> 0xFF26C6DA.toInt() // Artillery
            }
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF151719.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val healthBackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAA111111.toInt()
    }
    private val healthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFB300.toInt()
    }
    private val targetCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, radius, intArrayOf(0xFFFFF1C0.toInt(), 0xFFFFBA66.toInt(), 0xFF613B25.toInt()), floatArrayOf(0f, 0.32f, 1f), Shader.TileMode.CLAMP)
    }
    private val targetRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF70EBE2.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val targetBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF142B3D.toInt() }
    private val shipHull = Path().apply {
        if (!boss && spriteVariant == 2) {
            // Mine: radial spikes, visibly distinct from moving ships.
            repeat(16) { i ->
                val angle = i * kotlin.math.PI / 8
                val reach = radius * if (i % 2 == 0) 1.15f else 0.62f
                val px = kotlin.math.cos(angle).toFloat() * reach
                val py = kotlin.math.sin(angle).toFloat() * reach
                if (i == 0) moveTo(px, py) else lineTo(px, py)
            }
        } else {
            val wing = radius * if (boss) 1.35f else when (spriteVariant) { 1 -> 1.1f; 3 -> 1.0f; 4 -> 1.25f; else -> 0.85f }
            moveTo(0f, radius * 1.25f)
            lineTo(-radius * 0.35f, radius * 0.48f)
            lineTo(-wing, radius * 0.72f)
            lineTo(-wing * 0.8f, -radius * 0.55f)
            lineTo(-radius * 0.42f, -radius * 0.18f)
            lineTo(-radius * 0.28f, -radius)
            lineTo(radius * 0.28f, -radius)
            lineTo(radius * 0.42f, -radius * 0.18f)
            lineTo(wing * 0.8f, -radius * 0.55f)
            lineTo(wing, radius * 0.72f)
            lineTo(radius * 0.35f, radius * 0.48f)
        }
        close()
    }
    private val shipBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF293A4C.toInt() }
    private val shipDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = if (boss) 3.5f else 2f
        color = if (boss) when (bossVariant) {
            1 -> 0xFFC895FF.toInt()
            2 -> 0xFF83D9FA.toInt()
            3 -> 0xFFFFBA66.toInt()
            4 -> 0xFFA6B7FF.toInt()
            5 -> 0xFF8BEBAC.toInt()
            else -> 0xFFFF846E.toInt()
        } else 0xFFFFAA89.toInt()
    }
    private val reactorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE8C4.toInt() }
    private var hitFlashSeconds = 0f
    private var ageSeconds = 0f
    private val homeX = x

    fun update(
        dt: Float,
        targetX: Float,
        targetY: Float,
        walls: List<ArenaWall>,
        screenWidth: Float,
        screenHeight: Float
    ) {
        if (!active) return
        hitFlashSeconds = (hitFlashSeconds - dt).coerceAtLeast(0f)
        ageSeconds += dt

        val roleTargetX: Float
        val roleTargetY: Float
        val roleSpeed: Float
        if (boss) {
            roleTargetX = targetX
            roleTargetY = targetY
            roleSpeed = MOVE_SPEED
        } else {
            when (spriteVariant) {
                0 -> { // Scout: fast, readable straight pass
                    roleTargetX = homeX
                    roleTargetY = screenHeight + radius * 3f
                    roleSpeed = 235f
                }
                1 -> { // Raider: broad lateral weave while advancing
                    roleTargetX = screenWidth * 0.5f +
                        kotlin.math.sin(ageSeconds * 2.1f + id * 0.37f) * screenWidth * 0.38f
                    roleTargetY = screenHeight + radius * 3f
                    roleSpeed = 185f
                }
                2 -> { // Mine: slow space-denial drift
                    roleTargetX = homeX
                    roleTargetY = screenHeight + radius * 3f
                    roleSpeed = 78f
                }
                3 -> { // Guard: heavy, slow lane blocker
                    roleTargetX = homeX
                    roleTargetY = screenHeight + radius * 3f
                    roleSpeed = 112f
                }
                else -> { // Interceptor: actively hunts the player
                    roleTargetX = targetX
                    roleTargetY = targetY
                    roleSpeed = 165f
                }
            }
        }

        var desiredX = roleTargetX - x
        var desiredY = roleTargetY - y
        val desiredLength = sqrt(desiredX * desiredX + desiredY * desiredY)

        if (desiredLength > 0.001f) {
            desiredX /= desiredLength
            desiredY /= desiredLength
        }

        var steerX = desiredX
        var steerY = desiredY

        for (wall in walls) {
            val expanded = RectF(
                wall.rect.left - radius - WALL_LOOKAHEAD,
                wall.rect.top - radius - WALL_LOOKAHEAD,
                wall.rect.right + radius + WALL_LOOKAHEAD,
                wall.rect.bottom + radius + WALL_LOOKAHEAD
            )

            val predictedX = x + desiredX * PATH_LOOKAHEAD
            val predictedY = y + desiredY * PATH_LOOKAHEAD

            if (expanded.contains(predictedX, predictedY)) {
                val nearestX = x.coerceIn(wall.rect.left, wall.rect.right)
                val nearestY = y.coerceIn(wall.rect.top, wall.rect.bottom)
                var awayX = x - nearestX
                var awayY = y - nearestY
                var awayLength = sqrt(awayX * awayX + awayY * awayY)

                if (awayLength < 0.001f) {
                    awayX = if (x < wall.rect.centerX()) -1f else 1f
                    awayY = 0f
                    awayLength = 1f
                }

                awayX /= awayLength
                awayY /= awayLength

                val tangentX = -awayY
                val tangentY = awayX
                val tangentSign =
                    if (
                        tangentX * desiredX + tangentY * desiredY >=
                        -tangentX * desiredX + -tangentY * desiredY
                    ) 1f else -1f

                steerX += awayX * WALL_REPULSION
                steerY += awayY * WALL_REPULSION
                steerX += tangentX * tangentSign * WALL_TANGENT
                steerY += tangentY * tangentSign * WALL_TANGENT
            }
        }

        val steerLength = sqrt(steerX * steerX + steerY * steerY)
        if (steerLength > 0.001f) {
            steerX /= steerLength
            steerY /= steerLength
        }

        vx += (steerX * roleSpeed - vx) * ACCELERATION * dt
        vy += (steerY * roleSpeed - vy) * ACCELERATION * dt

        x += vx * dt
        y += vy * dt

        x = x.coerceIn(radius, screenWidth - radius)
        if (boss) {
            y = y.coerceIn(radius, screenHeight - radius)
        } else if (y - radius > screenHeight + 80f) {
            active = false
            return
        }

        resolveWalls(walls)
    }

    private fun resolveWalls(walls: List<ArenaWall>) {
        for (wall in walls) {
            val nearestX = x.coerceIn(wall.rect.left, wall.rect.right)
            val nearestY = y.coerceIn(wall.rect.top, wall.rect.bottom)
            val dx = x - nearestX
            val dy = y - nearestY
            val distanceSquared = dx * dx + dy * dy

            if (distanceSquared >= radius * radius) continue

            val distance = sqrt(distanceSquared)
            val nx: Float
            val ny: Float
            val penetration: Float

            if (distance > 0.001f) {
                nx = dx / distance
                ny = dy / distance
                penetration = radius - distance
            } else {
                val leftGap = kotlin.math.abs(x - wall.rect.left)
                val rightGap = kotlin.math.abs(wall.rect.right - x)
                val topGap = kotlin.math.abs(y - wall.rect.top)
                val bottomGap = kotlin.math.abs(wall.rect.bottom - y)
                val minGap = minOf(leftGap, rightGap, topGap, bottomGap)

                when (minGap) {
                    leftGap -> { nx = -1f; ny = 0f; penetration = radius + leftGap }
                    rightGap -> { nx = 1f; ny = 0f; penetration = radius + rightGap }
                    topGap -> { nx = 0f; ny = -1f; penetration = radius + topGap }
                    else -> { nx = 0f; ny = 1f; penetration = radius + bottomGap }
                }
            }

            x += nx * (penetration + 0.1f)
            y += ny * (penetration + 0.1f)

            val velocityAlongNormal = vx * nx + vy * ny
            if (velocityAlongNormal < 0f) {
                vx -= velocityAlongNormal * nx
                vy -= velocityAlongNormal * ny
            }
        }
    }

    fun takeDamage(amount: Float) {
        if (!active || amount <= 0f) return
        hitPoints -= amount
        hitFlashSeconds = 0.10f
        if (hitPoints <= 0f) {
            hitPoints = 0f
            active = false
        }
    }

    fun takeHit() = takeDamage(NORMAL_SHOT_DAMAGE)

    fun draw(canvas: Canvas) {
        if (!active) return
        val oldColor = paint.color
        if (boss || maxHitPoints != 1f) {
            // Draw clean silhouettes. The supplied image crops contain logos
            // and sheet labels, so they cannot serve as isolated ship sprites.
            canvas.save()
            canvas.translate(x, y)
            shipBodyPaint.color = if (hitFlashSeconds > 0f) 0xFFE9F6FF.toInt() else 0xFF293A4C.toInt()
            canvas.drawPath(shipHull, shipBodyPaint)
            canvas.drawPath(shipHull, shipDetailPaint)
            canvas.drawLine(0f, -radius * 0.72f, 0f, radius * 0.85f, shipDetailPaint)
            canvas.drawCircle(0f, -radius * 0.15f, radius * if (boss) 0.28f else 0.19f, reactorPaint)
            canvas.drawCircle(0f, -radius * 0.15f, radius * if (boss) 0.42f else 0.3f, shipDetailPaint)
            if (boss) {
                canvas.drawCircle(-radius * 0.68f, radius * 0.18f, radius * 0.14f, reactorPaint)
                canvas.drawCircle(radius * 0.68f, radius * 0.18f, radius * 0.14f, reactorPaint)
            }
            canvas.restore()
        } else if (!boss && maxHitPoints == 1f) {
            // Puzzle beacons have a distinct reactor silhouette, rather than
            // borrowing the combat fallback's red circle and health bar.
            canvas.save()
            canvas.translate(x, y)
            canvas.drawCircle(0f, 0f, radius, targetBodyPaint)
            canvas.drawCircle(0f, 0f, radius * 0.55f, targetCorePaint)
            canvas.drawCircle(0f, 0f, radius * 0.72f, targetRingPaint)
            canvas.drawCircle(0f, 0f, radius, targetRingPaint)
            repeat(4) {
                canvas.drawLine(0f, -radius * 0.86f, 0f, -radius * 0.64f, targetRingPaint)
                canvas.rotate(90f)
            }
            canvas.restore()
        } else {
            if (hitFlashSeconds > 0f) paint.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(x, y, radius, paint)
            paint.color = oldColor
            canvas.drawCircle(x, y, radius, ringPaint)
        }
        if (boss) {
            ringPaint.strokeWidth = 3f
            ringPaint.color = 0x99FFFFFF.toInt()
            canvas.drawCircle(x, y, radius * 1.05f, ringPaint)
            ringPaint.strokeWidth = 5f
            ringPaint.color = 0xFF151719.toInt()
        }

        if (!boss && maxHitPoints == 1f) return

        val barWidth = if (boss) radius * 2.6f else radius * 1.8f
        val barHeight = if (boss) 8f else 5f
        val left = x - barWidth * 0.5f
        val top = y - radius - 15f
        canvas.drawRect(left, top, left + barWidth, top + barHeight, healthBackPaint)
        canvas.drawRect(
            left, top,
            left + barWidth * (hitPoints / maxHitPoints).coerceIn(0f, 1f),
            top + barHeight,
            healthPaint
        )
    }

    companion object {
        const val NORMAL_SHOT_DAMAGE = 1f
        const val NORMAL_HIT_POINTS = 2f
        const val BOSS_HIT_POINTS = NORMAL_HIT_POINTS * 20f

        private const val MOVE_SPEED = 105f
        private const val ACCELERATION = 4.2f
        private const val PATH_LOOKAHEAD = 90f
        private const val WALL_LOOKAHEAD = 18f
        private const val WALL_REPULSION = 2.2f
        private const val WALL_TANGENT = 1.4f
    }
}
