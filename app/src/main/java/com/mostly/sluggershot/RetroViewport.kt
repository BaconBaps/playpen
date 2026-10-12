package com.mostly.sluggershot

import android.graphics.*
import kotlin.math.ceil

/** Pixel presentation only. Physics and touch coordinates remain full precision. */
class RetroViewport {
    private var bitmap: Bitmap?=null
    private var buffer: Canvas?=null
    private val output=Paint().apply { isAntiAlias=false;isFilterBitmap=false;isDither=false }
    private val destination=RectF()
    fun begin(width: Int,height: Int): Canvas {
        val bw=(width/2).coerceIn(200,360)
        val bh=ceil(height.toDouble()/width*bw).toInt().coerceAtLeast(1)
        if(bitmap?.width!=bw || bitmap?.height!=bh) {
            bitmap?.recycle();bitmap=Bitmap.createBitmap(bw,bh,Bitmap.Config.ARGB_8888)
            buffer=Canvas(bitmap!!)
        }
        val c=buffer!!
        c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR)
        c.save();c.scale(bw.toFloat()/width,bh.toFloat()/height)
        return c
    }
    fun end(canvas: Canvas,width: Int,height: Int) {
        buffer!!.restore()
        destination.set(0f,0f,width.toFloat(),height.toFloat())
        canvas.drawBitmap(bitmap!!,null,destination,output)
    }
}
