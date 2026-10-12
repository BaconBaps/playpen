package com.mostly.sluggershot

import android.content.res.Resources
import android.graphics.*
import kotlin.math.max

/** A 160x240 palette-constrained background master; never bilinear-filtered. */
class SceneAtmosphere(resources: Resources) {
    private val backdrop: Bitmap
    private val imagePaint=Paint().apply { isAntiAlias=false;isFilterBitmap=false;isDither=false;alpha=180 }
    private val destination=RectF()
    init {
        val raw=BitmapFactory.decodeResource(resources,R.drawable.nebula_backdrop)
        val small=Bitmap.createScaledBitmap(raw,160,240,false)
        if(raw!==small)raw.recycle()
        val palette=intArrayOf(0xFF07101F.toInt(),0xFF0B1631.toInt(),0xFF142446.toInt(),0xFF213A52.toInt(),
            0xFF31536B.toInt(),0xFF1C366F.toInt(),0xFF23578B.toInt(),0xFF297DA1.toInt(),
            0xFF15555C.toInt(),0xFF287E80.toInt(),0xFF42ADA7.toInt(),0xFF70CBB7.toInt(),
            0xFF29204F.toInt(),0xFF49336F.toInt(),0xFF775395.toInt(),0xFFB28BB4.toInt())
        val pixels=IntArray(160*240);small.getPixels(pixels,0,160,0,0,160,240)
        for(i in pixels.indices) {
            val color=pixels[i]
            pixels[i]=palette.minBy { candidate ->
                val dr=Color.red(color)-Color.red(candidate)
                val dg=Color.green(color)-Color.green(candidate)
                val db=Color.blue(color)-Color.blue(candidate)
                dr*dr+dg*dg+db*db
            }
        }
        backdrop=Bitmap.createBitmap(pixels,160,240,Bitmap.Config.ARGB_8888)
        small.recycle()
    }
    fun draw(canvas: Canvas,w: Int,h: Int,camera: Float) {
        if(w<=0||h<=0)return
        val s=max(w/160f,h/240f)*1.12f
        val bw=160*s;val bh=240*s
        val shift=(camera*0.018f).coerceAtMost(h*0.05f)
        destination.set((w-bw)/2f,(h-bh)/2f-shift,(w+bw)/2f,(h+bh)/2f-shift)
        canvas.drawBitmap(backdrop,null,destination,imagePaint)
    }
}
