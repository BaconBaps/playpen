package com.mostly.sluggershot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import org.robolectric.RuntimeEnvironment
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FlightExperienceTest {
    private fun game(w: Int = 400, h: Int = 800): GameView =
        GameView(RuntimeEnvironment.getApplication()).apply { layout(0, 0, w, h); render() }

    private fun GameView.render(name: String? = null) {
        val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        draw(Canvas(b))
        if (name != null) {
            val f = File(System.getProperty("user.dir"), "build/flight-previews/$name.png")
            f.parentFile!!.mkdirs()
            f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        b.recycle()
    }
    private fun GameView.tap(x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        for (a in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val e = MotionEvent.obtain(now, now, a, x, y, 0)
            onTouchEvent(e); e.recycle()
        }
        render()
    }
    private fun value(g: GameView, name: String): Any? =
        GameView::class.java.getDeclaredField(name).apply { isAccessible = true }.get(g)
    private fun set(g: GameView, name: String, v: Any) =
        GameView::class.java.getDeclaredField(name).apply { isAccessible = true }.set(g, v)
    private fun call(g: GameView, name: String) =
        GameView::class.java.getDeclaredMethod(name).apply { isAccessible = true }.invoke(g)
    private fun state(g: GameView) = value(g, "flightScreen").toString()

    @Test fun combatRequiresExplicitLaunchAndPauseFreezesSimulation() {
        val g = game(); g.render("01-flight-deck")
        g.tap(100f, 480f)
        assertEquals("BRIEFING", state(g)); assertEquals(0f, value(g,"runTimeSeconds"))
        g.render("02-combat-briefing")
        g.tap(100f, 500f); assertEquals("PLAYING", state(g))
        g.pauseFlight(); val t = value(g, "runTimeSeconds")
        repeat(5) { g.render() }
        assertEquals(t, value(g, "runTimeSeconds")); assertEquals("PAUSED", state(g))
        g.render("03-pause")
        g.tap(100f, 500f); assertEquals("PLAYING", state(g))
    }

    @Test fun defeatWaitsForPlayerAndRetryResetsFlight() {
        val g = game(); g.tap(100f,480f); g.tap(100f,500f)
        set(g,"runTimeSeconds",42f); set(g,"playerHealth",0)
        call(g,"triggerGameOver"); g.render("04-flight-result")
        repeat(5) { g.render() }
        assertEquals("RESULT",state(g)); assertEquals(42f,value(g,"runTimeSeconds"))
        g.tap(100f,500f)
        assertEquals("PLAYING",state(g)); assertTrue((value(g,"playerHealth") as Int) > 0)
        assertTrue((value(g,"runTimeSeconds") as Float) < 1f)
    }

    @Test fun puzzleCompletionRequiresNextAndReturnsToBriefing() {
        val g = game(); g.tap(100f,570f); g.tap(55f,225f); g.tap(100f,500f)
        @Suppress("UNCHECKED_CAST")
        (value(g,"enemies") as MutableList<Enemy>).clear()
        set(g,"puzzleResetDelay",1f)
        g.render(); assertEquals("COMPLETE",state(g)); assertEquals(1,value(g,"puzzleLevel"))
        g.render("05-sector-complete")
        g.tap(100f,500f)
        assertEquals("BRIEFING",state(g)); assertEquals(2,value(g,"puzzleLevel"))
        g.tap(100f,500f); assertEquals("PLAYING",state(g))
        assertTrue((value(g,"enemies") as List<*>).isNotEmpty())
        g.render("06-puzzle-flight")
    }

    @Test fun newCombatFlightDoesNotInheritDeadHullBossOrPuzzlePosition() {
        val g = game(); g.tap(100f,570f); g.tap(55f,225f); g.tap(100f,500f)
        g.pauseFlight(); g.render(); g.tap(100f,600f)
        assertTrue(g.isAtFlightDeck())
        set(g,"playerHealth",0); set(g,"bossCheckpointAvailable",true); set(g,"bossBattle",true)
        g.tap(100f,480f)
        assertEquals("BRIEFING",state(g)); assertEquals(false,value(g,"bossCheckpointAvailable"))
        assertEquals(false,value(g,"bossBattle")); assertTrue((value(g,"playerHealth") as Int)>0)
        val sling=value(g,"slingshot") as Slingshot
        assertTrue(sling.y < g.height)
    }

    @Test fun menuReleaseWithoutMatchingPressCannotLaunch() {
        val g=game()
        val e=MotionEvent.obtain(0,0,MotionEvent.ACTION_UP,100f,480f,0)
        g.onTouchEvent(e); e.recycle(); g.render()
        assertTrue(g.isAtFlightDeck())
    }

    @Test fun controlsScaleAndLetterboxOnDifferentScreens() {
        for ((w,h) in listOf(320 to 640, 1080 to 2400, 800 to 600)) {
            val g=game(w,h)
            val s=minOf(w/400f,h/800f); val ox=(w-400*s)/2; val oy=(h-800*s)/2
            g.tap(ox+100*s,oy+480*s)
            assertEquals("BRIEFING",state(g))
            g.tap(ox+100*s,oy+500*s); assertEquals("PLAYING",state(g))
            g.render("screen-${w}x${h}")
        }
    }

    @Test fun backPausesThenReturnsToDeck() {
        val g=game(); g.tap(100f,480f); g.tap(100f,500f)
        g.handleBack(); g.render(); assertEquals("PAUSED",state(g))
        g.handleBack(); g.render(); assertTrue(g.isAtFlightDeck())
    }

    @Test fun checkpointRetryPreservesCheckpointAndRestoresHull() {
        val g=game(); g.tap(100f,480f); g.tap(100f,500f)
        call(g,"saveBossCheckpoint"); set(g,"playerHealth",0); call(g,"triggerGameOver"); g.render()
        g.tap(100f,500f)
        assertEquals("PLAYING",state(g)); assertTrue((value(g,"playerHealth") as Int)>0)
        assertEquals(true,value(g,"bossCheckpointAvailable"))
    }
    @Test fun pullAndReleaseLaunchesAProjectileAndHudPauseCancelsAim() {
        val g=game(); g.tap(100f,480f); g.tap(100f,500f)
        fun gesture(action: Int, x: Float, y: Float) {
            val e=MotionEvent.obtain(0,0,action,x,y,0)
            g.onTouchEvent(e); e.recycle()
        }
        gesture(MotionEvent.ACTION_DOWN,200f,450f)
        gesture(MotionEvent.ACTION_MOVE,200f,550f)
        gesture(MotionEvent.ACTION_UP,200f,550f)
        @Suppress("UNCHECKED_CAST")
        val shots=value(g,"projectiles") as List<Projectile>
        assertTrue("Pull and release must launch a real projectile", shots.isNotEmpty())
        assertTrue("Dragging down must fire upward", shots.last().vy < 0f)
        val count=shots.size
        g.render("07-combat-flight")
        g.tap(350f,50f)
        assertEquals("PAUSED",state(g))
        assertFalse((value(g,"slingshot") as Slingshot).isPulled)
        g.tap(100f,500f)
        assertEquals("PLAYING",state(g))
        assertEquals(count,(value(g,"projectiles") as List<*>).size)
    }

}
