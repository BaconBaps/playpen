package com.mostly.sluggershot

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface

class PowerUpActor(
    val type: ShotType,
    var x: Float,
    var y: Float,
    var burstVx: Float = 0f,
    var burstVy: Float = 0f
) {
    val radius = 31f
    var active = true
    var collectDelay = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF101214.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
    }

    fun update(dt: Float, fieldSpeed: Float, screenWidth: Float) {
        collectDelay = (collectDelay - dt).coerceAtLeast(0f)
        x += burstVx * dt
        y += (fieldSpeed + burstVy) * dt
        val damping = (1f - 2.8f * dt).coerceIn(0f, 1f)
        burstVx *= damping
        burstVy *= damping
        x = x.coerceIn(radius, screenWidth - radius)
    }

    fun draw(canvas: Canvas) {
        if (!active) return
        val color = when (type) {
            ShotType.BOUNCE -> 0xFF29B6F6.toInt()
            ShotType.SPREAD -> 0xFF42A5F5.toInt()
            ShotType.AUTO -> 0xFFFFC107.toInt()
            ShotType.PIERCING -> 0xFFAB47BC.toInt()
            ShotType.DOUBLE -> 0xFF26C6DA.toInt()
            ShotType.BOMB -> 0xFFEF5350.toInt()
            ShotType.SHIELD -> 0xFF66BB6A.toInt()
            ShotType.FUEL -> 0xFF29B6F6.toInt()
            else -> 0xFF90A4AE.toInt()
        }
        fill.color = color
        ring.color = 0xFFE8EAEC.toInt()
        canvas.drawCircle(x, y, radius, fill)
        canvas.drawCircle(x, y, radius, ring)
        val label = when (type) {
            ShotType.BOUNCE -> "BOUNCE"
            ShotType.SPREAD -> "SPREAD"
            ShotType.AUTO -> "AUTO"
            ShotType.PIERCING -> "PIERCE"
            ShotType.DOUBLE -> "DOUBLE"
            ShotType.BOMB -> "BOMB"
            ShotType.SHIELD -> "SHIELD"
            ShotType.FUEL -> "FUEL"
            else -> type.label
        }
        canvas.drawText(label, x, y + 6f, text)
    }
}
