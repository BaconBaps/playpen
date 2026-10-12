package com.mostly.sluggershot

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.abs
import kotlin.math.sqrt

class Slingshot {
    var x = 0f
    var y = 0f

    var pullX = 0f
        private set
    var pullY = 0f
        private set
    var pullRatio = 0f
        private set
    var rawPullDistance = 0f
        private set
    var isPulled = false
        private set

    private var recoil = 0f
    private var recoilVelocity = 0f

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8D9398.toInt()
        strokeWidth = 24f
        strokeCap = Paint.Cap.ROUND
    }

    private val elasticPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF70EBE2.toInt()
        strokeWidth = 10f
        strokeCap = Paint.Cap.ROUND
    }

    private val pouchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF303236.toInt()
    }

    private val loadedProjectilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE0E0E0.toInt()
    }

    private val jointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4A4E52.toInt()
    }

    private val hotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF70EBE2.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    fun initialise(screenWidth: Float, screenHeight: Float) {
        x = screenWidth / 2f
        y = screenHeight * 0.78f
    }

    fun moveBy(deltaX: Float, screenWidth: Float) {
        x = (x + deltaX).coerceIn(SIDE_MARGIN, screenWidth - SIDE_MARGIN)
    }

    fun trackHorizontal(touchX: Float, screenWidth: Float) {
        x = touchX.coerceIn(SIDE_MARGIN, screenWidth - SIDE_MARGIN)
    }

    fun moveVerticalBy(deltaY: Float, screenHeight: Float) {
        val bottom = screenHeight * 0.78f
        val top = screenHeight * 0.43f
        y = (y + deltaY).coerceIn(top, bottom)
    }

    fun updatePull(dx: Float, dy: Float) {
        isPulled = true
        rawPullDistance = sqrt(dx * dx + dy * dy)

        if (rawPullDistance <= 0f) {
            pullX = 0f
            pullY = 0f
            pullRatio = 0f
            return
        }

        val resistanceStart = MAX_PULL_DISTANCE * 0.75f
        val visualDistance =
            (if (rawPullDistance <= resistanceStart) {
                rawPullDistance
            } else {
                resistanceStart + (rawPullDistance - resistanceStart) * 0.35f
            }).coerceAtMost(MAX_PULL_DISTANCE)

        val scale = visualDistance / rawPullDistance
        pullX = dx * scale
        pullY = dy * scale
        pullRatio = (visualDistance / MAX_PULL_DISTANCE).coerceIn(0f, 1f)
    }

    fun update(dt: Float) {
        if (isPulled) return

        recoilVelocity +=
            (-SPRING_STIFFNESS * recoil - SPRING_DAMPING * recoilVelocity) * dt
        recoil += recoilVelocity * dt

        if (abs(recoil) < 0.15f && abs(recoilVelocity) < 0.15f) {
            recoil = 0f
            recoilVelocity = 0f
        }
    }

    fun fireRelease() {
        clearPull()
        recoil = -28f
        recoilVelocity = -180f
    }

    fun cancelPull() {
        clearPull()
    }

    private fun clearPull() {
        isPulled = false
        pullX = 0f
        pullY = 0f
        pullRatio = 0f
        rawPullDistance = 0f
    }

    fun getPouchX(): Float = if (isPulled) x + pullX else x

    fun getPouchY(): Float =
        if (isPulled) y + pullY else y - 20f + recoil


    private val shuttlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF16A34A.toInt()
    }
    private val shuttleDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF075E2C.toInt()
    }
    private val shuttleHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF4ADE80.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val canopyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF123A4A.toInt()
    }
    private val enginePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF263238.toInt()
    }
    private val exhaustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF69F0AE.toInt()
    }

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0B7A3A.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val metalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB0BEC5.toInt()
    }
    private val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF64D8FF.toInt()
    }
    private val hotExhaustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF4FF81.toInt()
    }

    fun draw(canvas: Canvas, heavyLoaded: Boolean, thrust01: Float = 0f, autoChargeVisualScale: Float = 1f) {

        val leftForkX = x - 42f
        val rightForkX = x + 42f
        val forkY = y - 88f
        val pouchX = getPouchX()
        val pouchY = getPouchY() - 24f

        // Green top-down shuttle: broad delta wings, compact fuselage, dark canopy.
        val shuttle = Path().apply {
            moveTo(x, y - 68f)
            lineTo(x + 23f, y - 20f)
            lineTo(x + 66f, y + 42f)
            lineTo(x + 24f, y + 30f)
            lineTo(x + 16f, y + 76f)
            lineTo(x, y + 64f)
            lineTo(x - 16f, y + 76f)
            lineTo(x - 24f, y + 30f)
            lineTo(x - 66f, y + 42f)
            lineTo(x - 23f, y - 20f)
            close()
        }
        canvas.drawPath(shuttle, shuttleDarkPaint)
        val fuselage = Path().apply {
            moveTo(x, y - 72f)
            lineTo(x + 21f, y + 54f)
            lineTo(x, y + 72f)
            lineTo(x - 21f, y + 54f)
            close()
        }
        canvas.drawPath(fuselage, shuttlePaint)
        canvas.drawPath(shuttle, shuttleHighlightPaint)
        canvas.drawOval(x - 12f, y - 34f, x + 12f, y - 2f, canopyPaint)
        canvas.drawOval(x - 7f, y - 29f, x + 7f, y - 9f, cyanPaint)

        // Forged panel seams, wing hardware and twin auxiliary engine bells.
        canvas.drawLine(x, y - 60f, x, y + 52f, panelPaint)
        canvas.drawLine(x - 43f, y + 27f, x - 18f, y + 18f, panelPaint)
        canvas.drawLine(x + 43f, y + 27f, x + 18f, y + 18f, panelPaint)
        canvas.drawCircle(x - 28f, y + 39f, 6f, metalPaint)
        canvas.drawCircle(x + 28f, y + 39f, 6f, metalPaint)
        canvas.drawCircle(x, y + 61f, 13f, enginePaint)
        canvas.drawCircle(x, y + 61f, 6f, cyanPaint)

        if (thrust01 > 0.01f) {
            val flame = Path().apply {
                moveTo(x - 10f, y + 70f)
                lineTo(x, y + 76f + 62f * thrust01)
                lineTo(x + 10f, y + 70f)
                close()
            }
            exhaustPaint.alpha = (150 + 105 * thrust01).toInt()
            canvas.drawPath(flame, exhaustPaint)

            val core = Path().apply {
                moveTo(x - 5f, y + 70f)
                lineTo(x, y + 75f + 39f * thrust01)
                lineTo(x + 5f, y + 70f)
                close()
            }
            hotExhaustPaint.alpha = (170 + 85 * thrust01).toInt()
            canvas.drawPath(core, hotExhaustPaint)
        }

        // Slingshot mechanism is bolted onto the shuttle nose.
        canvas.drawLine(leftForkX, forkY, pouchX, pouchY, elasticPaint)
        canvas.drawLine(rightForkX, forkY, pouchX, pouchY, elasticPaint)
        canvas.drawLine(leftForkX, forkY, x - 15f, y - 42f, framePaint)
        canvas.drawLine(rightForkX, forkY, x + 15f, y - 42f, framePaint)
        canvas.drawCircle(leftForkX, forkY, 13f, jointPaint)
        canvas.drawCircle(rightForkX, forkY, 13f, jointPaint)
        canvas.drawCircle(pouchX, pouchY, 18f, pouchPaint)

        if (isPulled) {
            val radius =
                if (heavyLoaded) PROJECTILE_RADIUS * HEAVY_RADIUS_MULTIPLIER
                else PROJECTILE_RADIUS

            val projectileY = pouchY - radius - 3f
            canvas.drawCircle(pouchX, projectileY, radius, loadedProjectilePaint)
            canvas.drawCircle(pouchX, projectileY, radius * 0.42f, jointPaint)

            if (rawPullDistance >= MAX_PULL_DISTANCE) {
                canvas.drawCircle(
                    pouchX,
                    projectileY,
                    (radius + 8f) * autoChargeVisualScale.coerceIn(0f, 1f),
                    hotPaint
                )
            }
        }
    }

    companion object {
        const val MAX_PULL_DISTANCE = 360f
        const val PROJECTILE_RADIUS = 22f
        const val HEAVY_RADIUS_MULTIPLIER = 3f

        private const val SIDE_MARGIN = 90f
        private const val SPRING_STIFFNESS = 210f
        private const val SPRING_DAMPING = 24f
    }
}
