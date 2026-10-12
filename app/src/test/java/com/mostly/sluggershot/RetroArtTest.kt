package com.mostly.sluggershot

import android.graphics.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RetroArtTest {
    private fun keys()=listOf("player","beacon","shot","shot:purple","shot:orange")+
        (0 until PixelArt.ENEMY_VARIANTS).map { "enemy:$it" }+
        (0 until PixelArt.BOSS_VARIANTS).map { "boss:$it" }+
        ShotType.entries.filter { it!=ShotType.NONE }.map { "power:${it.name}" }
    private fun pixels(b: Bitmap)=IntArray(b.width*b.height).also { b.getPixels(it,0,b.width,0,0,b.width,b.height) }
    @Test fun spritesHaveTransparentEdgesLimitedPalettesAndDistinctSilhouettes() {
        val enemies=mutableSetOf<List<Int>>()
        val bosses=mutableSetOf<List<Int>>()
        for(key in keys()) {
            val b=PixelArt.sprite(key)
            assertEquals(b.width,b.height)
            assertTrue("$key must be a low-resolution master",b.width<=40)
            val p=pixels(b)
            assertEquals(0,Color.alpha(b.getPixel(0,0)))
            assertTrue("$key must have hard alpha edges",p.all { Color.alpha(it) in listOf(0,255) })
            assertTrue("$key must have a constrained palette",p.filter { Color.alpha(it)>0 }.distinct().size<=16)
            assertTrue("$key must contain visible art",p.any { Color.alpha(it)==255 })
            assertSame(b,PixelArt.sprite(key))
            if(key.startsWith("enemy:"))enemies+=p.toList()
            if(key.startsWith("boss:"))bosses+=p.toList()
        }
        assertEquals(5,enemies.size);assertEquals(6,bosses.size)
        val gallery=Bitmap.createBitmap(600,720,Bitmap.Config.ARGB_8888)
        val c=Canvas(gallery);c.drawColor(0xFF07101F.toInt())
        keys().forEachIndexed { i,key ->
            val x=60f+(i%5)*120f;val y=70f+(i/5)*120f
            PixelArt.draw(c,key,x,y,80f)
            PixelFont.draw(c,key,x-50f,y+55f,10f,0xFFD3E9DC.toInt(),100f)
        }
        val f=File("build/flight-previews/16-pixel-sprite-gallery.png");f.parentFile!!.mkdirs()
        f.outputStream().use { gallery.compress(Bitmap.CompressFormat.PNG,100,it) }
        gallery.recycle()
    }
    @Test fun worldUpscalingIsNearestNeighbourAndDoesNotChangeInputSpace() {
        val world=RetroViewport()
        val c=world.begin(400,800)
        c.drawColor(Color.BLACK)
        val ink=Paint().apply { color=Color.RED;isAntiAlias=false }
        c.drawRect(50f,100f,151f,201f,ink)
        val output=Bitmap.createBitmap(400,800,Bitmap.Config.ARGB_8888)
        world.end(Canvas(output),400,800)
        assertEquals(Color.RED,output.getPixel(100,150))
        assertEquals(Color.BLACK,output.getPixel(200,250))
        for(y in 0 until 800 step 2) for(x in 0 until 400 step 2) {
            val color=output.getPixel(x,y)
            assertEquals(color,output.getPixel(x+1,y));assertEquals(color,output.getPixel(x,y+1))
            assertTrue(color==Color.RED||color==Color.BLACK)
        }
        output.recycle()
    }
    @Test fun backdropUsesALowResolution16ColourMaster() {
        val atmosphere=SceneAtmosphere(RuntimeEnvironment.getApplication().resources)
        val b=SceneAtmosphere::class.java.getDeclaredField("backdrop").apply { isAccessible=true }.get(atmosphere) as Bitmap
        assertEquals(160,b.width);assertEquals(240,b.height)
        assertTrue(pixels(b).distinct().size<=16)
    }
    @Test fun translucentEffectsDoNotMakeTheNextSpriteFaint() {
        val b=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888)
        val c=Canvas(b);c.drawColor(Color.BLACK)
        PixelArt.rect(c,0f,0f,100f,100f,0x3355EAD4)
        PixelArt.draw(c,"shot",50f,50f,32f)
        assertEquals(0xFFD3E9DC.toInt(),b.getPixel(50,50))
        b.recycle()
    }

}
