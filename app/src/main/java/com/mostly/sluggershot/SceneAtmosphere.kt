package com.mostly.sluggershot

import android.content.res.Resources
import android.graphics.*
import kotlin.math.max

/** Authored background art with subdued parallax and a vignette. */
class SceneAtmosphere(resources: Resources) {
    private val backdrop = BitmapFactory.decodeResource(resources, R.drawable.nebula_backdrop)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { alpha = 165 }
    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val destination = RectF()
    private var cachedW = 0
    private var cachedH = 0
    fun draw(canvas: Canvas, w: Int, h: Int, camera: Float) {
        if (w<=0 || h<=0) return
        if (w!=cachedW || h!=cachedH) {
            cachedW=w;cachedH=h
            vignettePaint.shader=RadialGradient(w*0.5f,h*0.48f,max(w,h)*0.74f,
                intArrayOf(Color.TRANSPARENT,0x44040C18,0xCC040913.toInt()),
                floatArrayOf(0f,0.68f,1f),Shader.TileMode.CLAMP)
        }
        val s=max(w.toFloat()/backdrop.width,h.toFloat()/backdrop.height)*1.12f
        val bw=backdrop.width*s; val bh=backdrop.height*s
        val shift=(camera*0.018f).coerceAtMost(h*0.05f)
        destination.set((w-bw)/2f,(h-bh)/2f-shift,(w+bw)/2f,(h+bh)/2f-shift)
        canvas.drawBitmap(backdrop,null,destination,imagePaint)
        canvas.drawRect(0f,0f,w.toFloat(),h.toFloat(),vignettePaint)
    }
}
