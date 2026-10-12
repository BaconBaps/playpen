package com.mostly.sluggershot

import android.graphics.Canvas
import android.graphics.Matrix
import kotlin.math.*

/** Presentation-only combat motion. The HUD and puzzle camera do not use this transform. */
class CombatFlightEffects {
    var intensity=0f
        private set
    var phase=0f
        private set
    private var travel=0f
    private val camera=Matrix()
    val zoom: Float get()=1f+0.12f*intensity
    fun reset() { intensity=0f;phase=0f;travel=0f }
    fun update(dt: Float,thrust: Float) {
        intensity+=(thrust.coerceIn(0f,1f)-intensity)*(1f-exp(-7f*dt))
        phase+=dt
        travel+=dt*(0.65f+2.8f*intensity)
    }
    fun matrix(width: Int,height: Int,shipX: Float): Matrix {
        val pixelScale=width/400f
        val shakeX=sin(phase*53f)*3f*pixelScale*intensity
        val shakeY=cos(phase*67f)*2.5f*pixelScale*intensity
        camera.setScale(zoom,zoom,shipX,height*0.66f)
        camera.postTranslate(shakeX,shakeY-height*0.012f*intensity)
        return camera
    }
    fun drawEdgeRush(canvas: Canvas,width: Int,height: Int) {
        // Near-field debris is confined to the edges and has no collision body.
        val s=width/400f
        repeat(12) { i ->
            val left=i%2==0
            val x=width*(if(left)0.035f+(i%3)*0.018f else 0.965f-(i%3)*0.018f)
            val fraction=(travel*(1f+(i%4)*0.22f)+i*0.083f)%1f
            val y=fraction*(height+140f)-70f
            val length=(14f+(i%4)*9f+intensity*62f)*s
            val thickness=(if(i%3==0)3f else 2f)*s
            PixelArt.rect(canvas,x,y-length,x+thickness,y,0x6631536B)
            PixelArt.rect(canvas,x,y-thickness*2f,x+thickness*2f,y,0x99709DA5.toInt())
        }
        if(intensity>0.15f) {
            val alpha=(intensity*80f).toInt()
            val color=(alpha shl 24) or 0x0055EAD4
            PixelArt.rect(canvas,0f,0f,3f*s,height.toFloat(),color)
            PixelArt.rect(canvas,width-3f*s,0f,width.toFloat(),height.toFloat(),color)
        }
    }
}
