package com.mostly.sluggershot

import android.graphics.Canvas
import android.graphics.Paint
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

    private val elasticPaint=Paint()

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


    fun draw(canvas: Canvas, heavyLoaded: Boolean, thrust01: Float = 0f, autoChargeVisualScale: Float = 1f) {
        PixelArt.draw(canvas,"player",x,y,144f)
        repeat(2) { i ->
            val dx=if(i==0)-42f else 42f
            PixelArt.rect(canvas,x+dx-3f,y-88f,x+dx+3f,y-48f,0xFF31536B.toInt())
            PixelArt.rect(canvas,x+dx-7f,y-94f,x+dx+7f,y-82f,0xFF07101F.toInt())
            PixelArt.rect(canvas,x+dx-5f,y-92f,x+dx+5f,y-84f,0xFF709DA5.toInt())
            PixelArt.rect(canvas,x+dx-2f,y-90f,x+dx+2f,y-86f,0xFF55EAD4.toInt())
        }
        val pouchX=getPouchX();val pouchY=getPouchY()-24f
        if(isPulled) {
            elasticPaint.isAntiAlias=false;elasticPaint.strokeWidth=4f
            elasticPaint.color=0xFF55EAD4.toInt()
            canvas.drawLine(x-42f,y-88f,pouchX,pouchY,elasticPaint)
            canvas.drawLine(x+42f,y-88f,pouchX,pouchY,elasticPaint)
            val radius=if(heavyLoaded)PROJECTILE_RADIUS*HEAVY_RADIUS_MULTIPLIER else PROJECTILE_RADIUS
            PixelArt.draw(canvas,"shot",pouchX,pouchY-radius-3f,radius*2f)
            if(rawPullDistance>=MAX_PULL_DISTANCE)PixelArt.halo(canvas,pouchX,pouchY-radius-3f,
                (radius+8f)*autoChargeVisualScale,0xFF55EAD4.toInt(),100)
        }
        if(thrust01>0.01f) {
            val flame=(38f+thrust01*110f)
            PixelArt.halo(canvas,x,y+76f,26f+thrust01*34f,0xFFFF9A54.toInt(),100)
            PixelArt.rect(canvas,x-8f,y+65f,x+8f,y+65f+flame,0xFFAF4850.toInt())
            PixelArt.rect(canvas,x-5f,y+65f,x+5f,y+65f+flame*0.75f,0xFFFF9A54.toInt())
            PixelArt.rect(canvas,x-2f,y+65f,x+2f,y+65f+flame*0.45f,0xFFFFD980.toInt())
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
