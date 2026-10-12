package com.mostly.sluggershot

import android.graphics.*
import android.view.MotionEvent
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
class CombatSpeedTest {
    private fun field(g: GameView,n: String)=GameView::class.java.getDeclaredField(n).apply { isAccessible=true }.get(g)!!
    private fun set(g: GameView,n: String,v: Any)=GameView::class.java.getDeclaredField(n).apply { isAccessible=true }.set(g,v)
    private fun tick(g: GameView,method: String,dt: Float)=GameView::class.java.getDeclaredMethod(method,Float::class.javaPrimitiveType).apply { isAccessible=true }.invoke(g,dt)
    private fun render(g: GameView,name: String?=null) {
        val b=Bitmap.createBitmap(400,800,Bitmap.Config.ARGB_8888);g.draw(Canvas(b))
        if(name!=null) {
            val f=File("build/flight-previews/$name.png");f.parentFile!!.mkdirs()
            f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
        };b.recycle()
    }
    private fun event(g: GameView,a: Int,x: Float,y: Float) {
        val e=MotionEvent.obtain(0,0,a,x,y,0);g.onTouchEvent(e);e.recycle()
    }
    private fun tap(g: GameView,x: Float,y: Float) { event(g,MotionEvent.ACTION_DOWN,x,y);event(g,MotionEvent.ACTION_UP,x,y);render(g) }
    private fun game(puzzle: Boolean=false)=GameView(RuntimeEnvironment.getApplication()).apply {
        layout(0,0,400,800);render(this)
        if(puzzle) { tap(this,100f,570f);tap(this,55f,225f) } else tap(this,100f,480f)
        tap(this,100f,500f)
    }
    private fun hold(g: GameView) {
        event(g,MotionEvent.ACTION_DOWN,200f,450f)
        val track=(field(g,"touchTracks") as Map<*,*>).values.first()!!
        val role=track.javaClass.getDeclaredField("role").apply { isAccessible=true }
        role.set(track,role.type.enumConstants.first { it.toString()=="THRUST" })
        set(g,"currentThrust",1f)
    }
    @Test fun fullThrustIsFourTimesFasterAndConsumesHalfTheFuel() {
        val g=game();hold(g);set(g,"verticalVelocity",0f);set(g,"fuel",1f)
        val dt=0.016f
        val previousVelocity=(560f-790f)*dt*(1f-1.65f*dt)
        tick(g,"updateThruster",dt)
        assertEquals(previousVelocity*4f,field(g,"verticalVelocity") as Float,0.0001f)
        assertEquals(1f-dt/15f,field(g,"fuel") as Float,0.000001f)
        assertEquals(2640f,GameView::class.java.getDeclaredField("THRUSTER_MAX_UP_SPEED").apply { isAccessible=true }.getFloat(null),0f)
    }
    @Test fun gravityWithoutThrustAndBossFuelExemptionRemainUnchanged() {
        val g=game();(field(g,"slingshot") as Slingshot).y=500f;set(g,"verticalVelocity",0f);set(g,"currentThrust",0f)
        tick(g,"updateThruster",0.016f)
        assertEquals(560f*0.016f*(1f-1.65f*0.016f),field(g,"verticalVelocity") as Float,0.0001f)
        hold(g);set(g,"bossBattle",true);set(g,"fuel",0.4f)
        tick(g,"updateThruster",0.016f)
        assertEquals(0.4f,field(g,"fuel") as Float,0f)
    }
    @Test fun dedicatedBombsSpawnInCombatAndBossesButNeverPuzzles() {
        for(boss in listOf(false,true)) {
            val g=game();set(g,"bossBattle",boss);set(g,"bombSupplyTimer",0f)
            val before=(field(g,"powerUps") as List<*>).size
            tick(g,"updateBombSupply",0.016f)
            val drops=field(g,"powerUps") as List<*>
            assertEquals(before+1,drops.size);assertEquals(ShotType.BOMB,(drops.last() as PowerUpActor).type)
            assertTrue((field(g,"bombSupplyTimer") as Float) in 12f..18f)
        }
        val puzzle=game(true);set(puzzle,"bombSupplyTimer",0f);tick(puzzle,"updateBombSupply",1f)
        assertTrue((field(puzzle,"powerUps") as List<*>).isEmpty())
    }
    @Test fun bombSupplyDoesNotFloodTheScreen() {
        val g=game()
        @Suppress("UNCHECKED_CAST")
        val drops=field(g,"powerUps") as MutableList<PowerUpActor>
        drops.clear();repeat(2) { drops+=PowerUpActor(ShotType.BOMB,100f,200f) }
        set(g,"bombSupplyTimer",0f);tick(g,"updateBombSupply",1f)
        assertEquals(2,drops.size)
    }
    @Test fun cameraZoomAndShakeAreInvertibleAndRelaxWhenThrustEnds() {
        val fx=CombatFlightEffects();repeat(120) { fx.update(1f/60f,1f) }
        assertTrue(fx.zoom>1.11f)
        val matrix=Matrix(fx.matrix(400,800,200f));val inverse=Matrix();assertTrue(matrix.invert(inverse))
        val point=floatArrayOf(120f,550f);matrix.mapPoints(point);inverse.mapPoints(point)
        assertEquals(120f,point[0],0.001f);assertEquals(550f,point[1],0.001f)
        repeat(120) { fx.update(1f/60f,0f) }
        assertEquals(1f,fx.zoom,0.0001f)
    }
    @Test fun zoomedGesturesStillAimInWorldSpaceAndHudStaysInScreenSpace() {
        val g=game();val fx=CombatFlightEffects();fx.update(2f,1f)
        val matrix=Matrix(fx.matrix(400,800,200f));val inverse=field(g,"combatInputInverse") as Matrix
        matrix.invert(inverse)
        fun send(a: Int,x: Float,y: Float) {
            val point=floatArrayOf(x,y);matrix.mapPoints(point);event(g,a,point[0],point[1])
        }
        send(MotionEvent.ACTION_DOWN,200f,450f);send(MotionEvent.ACTION_MOVE,120f,550f)
        val sling=field(g,"slingshot") as Slingshot
        assertEquals(-80f,sling.pullX,0.001f);assertEquals(100f,sling.pullY,0.001f)
        event(g,MotionEvent.ACTION_CANCEL,0f,0f)
        tap(g,350f,50f);assertEquals("PAUSED",field(g,"flightScreen").toString())
    }
    @Test fun speedEffectsFreezeOnPauseAndPuzzleModeRemainsCalm() {
        val g=game();hold(g)
        val fx=field(g,"combatFlight") as CombatFlightEffects;fx.update(2f,1f)
        (field(g,"slingshot") as Slingshot).y=480f
        render(g,"17-combat-full-thrust")
        g.pauseFlight();val phase=fx.phase;val zoom=fx.zoom
        repeat(3) { render(g) }
        assertEquals(phase,fx.phase,0f);assertEquals(zoom,fx.zoom,0f)
        val p=game(true);repeat(5) { render(p) }
        val puzzleFx=field(p,"combatFlight") as CombatFlightEffects
        assertEquals(1f,puzzleFx.zoom,0f);assertEquals(0f,puzzleFx.phase,0f)
        val matrix=field(p,"combatInputInverse") as Matrix;assertTrue(matrix.isIdentity)
    }
    @Test fun twoFingerSteeringUsesBothInverseTransformedPointers() {
        val g=game();val fx=CombatFlightEffects();fx.update(2f,1f)
        val matrix=Matrix(fx.matrix(400,800,200f));matrix.invert(field(g,"combatInputInverse") as Matrix)
        val down=floatArrayOf(150f,500f);matrix.mapPoints(down)
        event(g,MotionEvent.ACTION_DOWN,down[0],down[1])
        fun multi(action: Int,delta: Float) {
            val props=Array(2) { i -> MotionEvent.PointerProperties().apply { id=i;toolType=MotionEvent.TOOL_TYPE_FINGER } }
            val coords=Array(2) { i ->
                val p=floatArrayOf(150f+i*100f+delta,500f);matrix.mapPoints(p)
                MotionEvent.PointerCoords().apply { x=p[0];y=p[1];pressure=1f;size=1f }
            }
            val e=MotionEvent.obtain(0,0,action,2,props,coords,0,0,1f,1f,0,0,android.view.InputDevice.SOURCE_TOUCHSCREEN,0)
            g.onTouchEvent(e);e.recycle()
        }
        multi(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),0f)
        multi(MotionEvent.ACTION_MOVE,40f)
        assertEquals(240f,(field(g,"slingshot") as Slingshot).x,0.001f)
    }

}
