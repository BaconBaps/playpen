package com.mostly.sluggershot

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
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
class PuzzleCampaignTest {
    @Test fun campaignHas50DistinctLevelsWithGradualDifficulty() {
        val levels=PuzzleCampaign.levels
        assertEquals(50,levels.size)
        assertEquals((1..50).toList(),levels.map { it.number })
        assertEquals(10,levels.map { it.chapter }.distinct().size)
        for((a,b) in levels.zipWithNext()) {
            assertTrue(b.screens>=a.screens)
            assertTrue(b.targets>=a.targets)
            assertTrue(b.bounces<=a.bounces)
            assertTrue(b.guideScreens<=a.guideScreens)
        }
        assertEquals(1,levels.first().targets)
        assertEquals(1f,levels.first().screens)
        assertEquals(0,levels.first().ledges)
        assertEquals(8,levels.first().bounces)
        assertEquals(3,levels.last().bounces)
        assertTrue(levels.last().screens>3f)
    }

    @Test fun all50LayoutsHaveReachableTargetsWithinTheirBounceBudget() {
        val fingerprints=mutableSetOf<String>()
        for(n in 1..50) {
            val layout=PuzzleCampaign.layout(n,400f,800f)
            assertEquals("Level $n has the intended target count",layout.level.targets,layout.targets.size)
            fingerprints += layout.targets.toString()+layout.walls.map { it.rect.toString() }
            val remaining=layout.targets.mapIndexed { i,t ->
                Enemy(i.toLong(),t.x,t.y,layout.targetRadius,maxHitPoints=1f)
            }.toMutableList()
            var shots=0
            while(remaining.isNotEmpty()) {
                val target=layout.targets.first { t -> remaining.any { it.x==t.x && it.y==t.y } }
                val path=PuzzleTrajectory.trace(layout.launcherX,layout.launcherY-50f,
                    target.aimX,target.aimY,layout.shotRadius,layout.walls,
                    layout.height*2f+400f*layout.level.bounces,layout.level.bounces,remaining)
                val last=path.last()
                val hit=remaining.firstOrNull {
                    val dx=last.x-it.x;val dy=last.y-it.y
                    dx*dx+dy*dy <= (layout.shotRadius+it.radius)*(layout.shotRadius+it.radius)
                }
                assertNotNull("Level $n must have a reachable next target",hit)
                assertTrue("Level $n must respect its bounce allowance",last.bounces<=layout.level.bounces)
                remaining.remove(hit)
                shots++
            }
            assertEquals(layout.targets.size,shots)
        }
        assertEquals(50,fingerprints.size)
    }

    @Test fun firstChaptersAreVisibleAndLayoutsRepeatOnReplay() {
        for(n in 1..10) {
            val a=PuzzleCampaign.layout(n,400f,800f)
            val b=PuzzleCampaign.layout(n,400f,800f)
            assertEquals(a.targets,b.targets)
            assertEquals(a.walls.map { it.rect },b.walls.map { it.rect })
            assertEquals(800f,a.height)
            assertTrue(a.targets.all { it.y>100f && it.y<a.launcherY-100f })
        }
        for((w,h) in listOf(320f to 640f,1080f to 2400f,800f to 600f)) {
            for(n in listOf(1,10,25,50)) {
                val layout=PuzzleCampaign.layout(n,w,h)
                assertEquals(layout.level.targets,layout.targets.size)
                assertTrue(layout.targets.all { it.x>0f && it.x<w && it.y>0f && it.y<layout.height })
            }
        }
    }

    @Test fun bounceAllowanceCountsSuccessfulReflectionsAndGuideRespectsRadius() {
        val walls=listOf(ArenaWall(RectF(0f,0f,14f,800f)),ArenaWall(RectF(386f,0f,400f,800f)))
        val path=PuzzleTrajectory.trace(200f,400f,1f,0f,14f,walls,900f,1)
        val turn=path.first { it.bounces==1 }
        assertTrue(turn.x<=372.1f)
        assertEquals(2,path.last().bounces) // expires on the next, disallowed contact
        val p=Projectile(1,200f,400f,1000f,0f,14f,bouncy=true,bounceLimit=1)
        var bounceSounds=0;var breaks=0
        repeat(300) {
            p.integrate(0.004f)
            walls.forEach { wall -> if(wall.resolve(p))p.registerArenaWallBounce({bounceSounds++},{breaks++}) }
        }
        assertFalse(p.active);assertEquals(1,bounceSounds);assertEquals(1,breaks)
        assertEquals(2,p.bounceCount)
    }

    private fun render(g: GameView,name: String?=null) {
        val b=Bitmap.createBitmap(g.width,g.height,Bitmap.Config.ARGB_8888)
        g.draw(Canvas(b))
        if(name!=null) {
            val f=File("build/flight-previews/$name.png");f.parentFile!!.mkdirs()
            f.outputStream().use { b.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
        b.recycle()
    }
    private fun tap(g: GameView,x: Float,y: Float) {
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val e=MotionEvent.obtain(0,0,action,x,y,0);g.onTouchEvent(e);e.recycle()
        }
        render(g)
    }
    private fun field(g: GameView,n: String)=GameView::class.java.getDeclaredField(n).apply { isAccessible=true }.get(g)!!
    private fun set(g: GameView,n: String,v: Any)=GameView::class.java.getDeclaredField(n).apply { isAccessible=true }.set(g,v)
    private fun game()=GameView(RuntimeEnvironment.getApplication()).apply { layout(0,0,400,800);render(this) }

    @Test fun firstLevelCanBeClearedWithASimpleStraightShotAndProgressIsSaved() {
        val g=game();tap(g,100f,570f);render(g,"08-level-selector")
        tap(g,125f,225f) // level 2 is locked
        assertEquals(true,field(g,"puzzleSelection"))
        tap(g,55f,225f);tap(g,100f,500f)
        render(g,"09-puzzle-level-01")
        for((a,y) in listOf(MotionEvent.ACTION_DOWN to 450f,MotionEvent.ACTION_MOVE to 550f)) {
            val e=MotionEvent.obtain(0,0,a,200f,y,0);g.onTouchEvent(e);e.recycle()
        }
        render(g,"10-puzzle-long-guide")
        val up=MotionEvent.obtain(0,0,MotionEvent.ACTION_UP,200f,550f,0);g.onTouchEvent(up);up.recycle()
        val update=GameView::class.java.getDeclaredMethod("updateGame",Float::class.javaPrimitiveType).apply { isAccessible=true }
        repeat(140) { if(field(g,"flightScreen").toString()=="PLAYING")update.invoke(g,0.016f) }
        assertEquals("COMPLETE",field(g,"flightScreen").toString())
        assertEquals(2,field(g,"puzzleUnlocked"))
        val recreated=game();assertEquals(2,field(recreated,"puzzleUnlocked"))
        render(g,"11-sector-results")
    }

    @Test fun lastSectorEndsCampaignWithoutCreatingLevel51AndPauseCanRestart() {
        val g=game();set(g,"puzzleUnlocked",50)
        tap(g,100f,570f);render(g,"12-all-50-levels")
        tap(g,335f,665f) // last row, last tile
        assertEquals(50,field(g,"puzzleLevel"));tap(g,100f,500f)
        render(g,"13-puzzle-level-50")
        g.pauseFlight();render(g);tap(g,100f,690f)
        assertEquals("BRIEFING",field(g,"flightScreen").toString())
        assertEquals(50,field(g,"puzzleLevel"));tap(g,100f,500f)
        @Suppress("UNCHECKED_CAST")
        (field(g,"enemies") as MutableList<Enemy>).clear()
        set(g,"puzzleResetDelay",1f)
        render(g);assertEquals("COMPLETE",field(g,"flightScreen").toString())
        render(g,"14-campaign-complete")
        tap(g,100f,500f)
        assertEquals(true,field(g,"puzzleSelection"));assertEquals(50,field(g,"puzzleLevel"))
    }

    @Test fun recallReturnsToLauncherWithoutResettingTargets() {
        val g=game();tap(g,100f,570f);tap(g,55f,225f);tap(g,100f,500f)
        @Suppress("UNCHECKED_CAST")
        val shots=field(g,"projectiles") as MutableList<Projectile>
        for((a,x,y) in listOf(Triple(MotionEvent.ACTION_DOWN,200f,450f),Triple(MotionEvent.ACTION_MOVE,100f,570f))) {
            val e=MotionEvent.obtain(0,0,a,x,y,0);g.onTouchEvent(e);e.recycle()
        }
        render(g,"15-reflected-guide")
        assertTrue((field(g,"guideCache") as List<PuzzleTrajectory.Point>).any { it.bounces>0 })
        val cancel=MotionEvent.obtain(0,0,MotionEvent.ACTION_CANCEL,100f,570f,0);g.onTouchEvent(cancel);cancel.recycle()
        shots += Projectile(100,200f,500f,0f,-500f,14f,bouncy=true)
        val targets=(field(g,"enemies") as List<*>).size
        render(g);tap(g,330f,765f)
        assertTrue(shots.isEmpty());assertEquals(targets,(field(g,"enemies") as List<*>).size)
        assertEquals("PLAYING",field(g,"flightScreen").toString())
    }
}
