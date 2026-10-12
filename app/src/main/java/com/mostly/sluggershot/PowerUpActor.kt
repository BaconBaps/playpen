package com.mostly.sluggershot

import android.graphics.Canvas

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
        if(!active)return
        PixelArt.draw(canvas,"power:${type.name}",x,y,radius*2f)
        val label=type.label
        val width=label.length*6f*(11f/8f)
        PixelFont.draw(canvas,label,x-width/2f,y+radius+10f,11f,0xFFD3E9DC.toInt(),radius*2.8f)
    }
}
