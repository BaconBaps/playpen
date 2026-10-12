package com.mostly.sluggershot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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

    private val healthBackPaint=Paint().apply { color=0xFF07101F.toInt() }
    private val healthPaint=Paint().apply { color=0xFFFFD980.toInt() }
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
        val key=if(boss) "boss:$bossVariant" else if(maxHitPoints==1f) "beacon" else "enemy:$spriteVariant"
        val size=if(boss)radius*2.8f else radius*2f
        PixelArt.draw(canvas,key,x,y,size,size,hitFlashSeconds>0f)
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
