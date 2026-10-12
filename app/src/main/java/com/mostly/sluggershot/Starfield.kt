package com.mostly.sluggershot

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kotlin.random.Random

class Starfield {
    private data class Star(
        var x: Float,
        var y: Float,
        val radius: Float,
        val speed: Float,
        val alpha: Int
    )

    private val farStars = mutableListOf<Star>()
    private val midStars = mutableListOf<Star>()
    private val nearStars = mutableListOf<Star>()

    private data class SpaceObject(
        var x: Float,
        var y: Float,
        val radius: Float,
        val speed: Float,
        val type: Int,
        val rotation: Float
    )

    private val spaceObjects = mutableListOf<SpaceObject>()
    private var nextSpaceObjectSeconds = 1.8f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
    }

    private var fieldWidth = 0f
    private var fieldHeight = 0f

    fun resize(width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return
        if (width == fieldWidth && height == fieldHeight && farStars.isNotEmpty()) return

        fieldWidth = width
        fieldHeight = height
        farStars.clear()
        midStars.clear()
        nearStars.clear()
        spaceObjects.clear()
        nextSpaceObjectSeconds = randomObjectDelay()

        populate(farStars, 70, 0.7f, 1.25f, 20f, 38f, 75, 125)
        populate(midStars, 42, 1.0f, 1.8f, 48f, 78f, 110, 175)
        populate(nearStars, 22, 1.4f, 2.5f, 95f, 155f, 160, 235)
    }

    private fun populate(
        target: MutableList<Star>,
        count: Int,
        minRadius: Float,
        maxRadius: Float,
        minSpeed: Float,
        maxSpeed: Float,
        minAlpha: Int,
        maxAlpha: Int
    ) {
        repeat(count) {
            target += Star(
                x = Random.nextFloat() * fieldWidth,
                y = Random.nextFloat() * fieldHeight,
                radius = lerp(minRadius, maxRadius, Random.nextFloat()),
                speed = lerp(minSpeed, maxSpeed, Random.nextFloat()),
                alpha = Random.nextInt(minAlpha, maxAlpha + 1)
            )
        }
    }

    fun update(dt: Float, boost: Float = 0f) {
        updateLayer(farStars, dt*(1f+boost*0.25f))
        updateLayer(midStars, dt*(1f+boost))
        updateLayer(nearStars, dt*(1f+boost*2.5f))
        updateSpaceObjects(dt*(1f+boost*1.5f))
    }

    private fun updateLayer(stars: MutableList<Star>, dt: Float) {
        for (star in stars) {
            star.y += star.speed * dt * BACKGROUND_SPEED_MULTIPLIER
            if (star.y - star.radius > fieldHeight) {
                star.y = -star.radius
                star.x = Random.nextFloat() * fieldWidth
            }
        }
    }

    fun draw(canvas: Canvas, calm: Boolean = false, boost: Float = 0f) {
        if (!calm) drawSpaceObjects(canvas)
        drawLayer(canvas, farStars, calm, boost*0.25f)
        drawLayer(canvas, midStars, calm, boost*0.6f)
        drawLayer(canvas, nearStars, calm, boost)
    }

    private fun drawLayer(canvas: Canvas, stars: List<Star>, calm: Boolean, boost: Float) {
        for (star in stars) {
            paint.alpha = (star.alpha * if(calm)0.42f else 0.68f).toInt()
            if (calm) {
                canvas.drawCircle(star.x,star.y,star.radius,paint)
                continue
            }
            val streak = (star.speed * BACKGROUND_SPEED_MULTIPLIER * (0.045f+boost*0.055f))
                .coerceIn(star.radius * 2f, 34f+boost*56f)
            paint.strokeWidth = star.radius.coerceAtLeast(1f)
            canvas.drawLine(star.x, star.y - streak, star.x, star.y, paint)
        }
        paint.alpha = 255
    }

    private fun updateSpaceObjects(dt: Float) {
        nextSpaceObjectSeconds -= dt
        if (nextSpaceObjectSeconds <= 0f && spaceObjects.size < 3) {
            val radius = lerp(18f, 72f, Random.nextFloat())
            spaceObjects += SpaceObject(
                x = radius + Random.nextFloat() * (fieldWidth - radius * 2f).coerceAtLeast(1f),
                y = -radius * 2f,
                radius = radius,
                speed = lerp(120f, 255f, Random.nextFloat()) * BACKGROUND_SPEED_MULTIPLIER,
                type = Random.nextInt(4),
                rotation = Random.nextFloat() * 360f
            )
            nextSpaceObjectSeconds = randomObjectDelay()
        }

        spaceObjects.forEach { it.y += it.speed * dt }
        spaceObjects.removeAll { it.y - it.radius > fieldHeight }
    }

    private fun randomObjectDelay(): Float = lerp(2.0f, 6.5f, Random.nextFloat())

    private fun drawSpaceObjects(canvas: Canvas) {
        for (obj in spaceObjects) {
            canvas.save()
            canvas.rotate(obj.rotation, obj.x, obj.y)
            when (obj.type) {
                0 -> { // distant planet
                    paint.style = Paint.Style.FILL
                    paint.color = 0xFF28384A.toInt()
                    paint.alpha = 145
                    canvas.drawCircle(obj.x, obj.y, obj.radius, paint)
                    paint.color = 0xFF7592A6.toInt()
                    paint.alpha = 75
                    canvas.drawCircle(
                        obj.x - obj.radius * 0.25f,
                        obj.y - obj.radius * 0.2f,
                        obj.radius * 0.72f,
                        paint
                    )
                }
                1 -> { // asteroid
                    paint.style = Paint.Style.FILL
                    paint.color = 0xFF5B5651.toInt()
                    paint.alpha = 175
                    val r = obj.radius
                    val rect = RectF(obj.x-r, obj.y-r*0.72f, obj.x+r, obj.y+r*0.72f)
                    canvas.drawOval(rect, paint)
                    paint.color = 0xFF25282B.toInt()
                    paint.alpha = 130
                    canvas.drawCircle(obj.x-r*0.3f, obj.y-r*0.1f, r*0.18f, paint)
                    canvas.drawCircle(obj.x+r*0.28f, obj.y+r*0.18f, r*0.12f, paint)
                }
                2 -> { // ringed world
                    val r = obj.radius
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = (r * 0.12f).coerceAtLeast(3f)
                    paint.color = 0xFF967F69.toInt()
                    paint.alpha = 110
                    canvas.drawOval(RectF(obj.x-r*1.35f,obj.y-r*0.35f,obj.x+r*1.35f,obj.y+r*0.35f),paint)
                    paint.style = Paint.Style.FILL
                    paint.color = 0xFF564A65.toInt()
                    paint.alpha = 165
                    canvas.drawCircle(obj.x,obj.y,r*0.72f,paint)
                }
                else -> { // fast comet / debris flare
                    val r = obj.radius * 0.45f
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = r * 0.7f
                    paint.color = 0xFF7FB7C9.toInt()
                    paint.alpha = 75
                    canvas.drawLine(obj.x, obj.y-r*4f, obj.x, obj.y, paint)
                    paint.style = Paint.Style.FILL
                    paint.color = 0xFFC5E7EF.toInt()
                    paint.alpha = 165
                    canvas.drawCircle(obj.x,obj.y,r,paint)
                }
            }
            canvas.restore()
        }
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFFFFF.toInt()
        paint.alpha = 255
        paint.strokeWidth = 1f
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    companion object {
        private const val BACKGROUND_SPEED_MULTIPLIER = 16f
    }
}
