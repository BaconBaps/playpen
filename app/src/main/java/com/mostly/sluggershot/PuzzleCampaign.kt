package com.mostly.sluggershot

import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Fifty repeatable layouts; target locations carry a verified reachable shot. */
object PuzzleCampaign {
    const val LEVEL_COUNT = 50
    val chapters = listOf("First light", "First reflections", "Cross currents", "Orbital gates",
        "The long way", "Split horizons", "Narrow passage", "Deep reflections", "The gauntlet", "Final approach")
    data class Level(val number: Int, val chapter: String, val screens: Float, val targets: Int,
        val bounces: Int, val guideScreens: Float, val ledges: Int, val pillars: Int,
        val targetSize: Float, val hint: String)
    data class Target(val x: Float, val y: Float, val aimX: Float, val aimY: Float, val requiredBounces: Int)
    data class Layout(val level: Level, val height: Float, val launcherX: Float, val launcherY: Float,
        val shotRadius: Float, val targetRadius: Float, val walls: List<ArenaWall>, val targets: List<Target>)
    val levels: List<Level> = (1..LEVEL_COUNT).map { n ->
        val chapter = (n-1)/5
        val step = (n-1)%5
        val heights = floatArrayOf(1f,1f,1.15f,1.4f,1.7f,2f,2.35f,2.7f,3.1f,3.5f)
        val counts = intArrayOf(1,2,3,4,5,6,7,8,9,10)
        val bounceLimits = intArrayOf(8,7,6,6,5,5,4,4,3,3)
        val guides = floatArrayOf(2.7f,2.2f,1.8f,1.5f,1.25f,1f,0.85f,0.7f,0.6f,0.5f)
        Level(n,chapters[chapter],heights[chapter]+if(chapter>1)step*0.025f else 0f,
            counts[chapter]+if(step>=3)1 else 0,bounceLimits[chapter],guides[chapter],
            if(chapter==0)0 else chapter+if(step>=2)1 else 0,
            if(chapter<4)0 else (chapter-3)/2,
            0.085f-chapter*0.0045f,
            when(chapter) { 0 -> "Aim directly at the large beacons."; 1 -> "Try a side-wall bank shot.";
                2,3 -> "Read the guide around the first gates."; 4,5 -> "Look ahead. The chamber is getting longer.";
                6,7 -> "Plan your reflections before you release."; else -> "Three reflections. Make each one count." })
    }
    fun level(number: Int) = levels[number.coerceIn(1,LEVEL_COUNT)-1]
    fun layout(number: Int, width: Float, screenHeight: Float): Layout {
        val level = level(number)
        val worldHeight = screenHeight*level.screens
        val launcherX = width*0.5f
        val launcherY = worldHeight-screenHeight*0.20f
        val shotRadius = (width*0.035f).coerceIn(8f,24f)
        val targetRadius = width*level.targetSize
        val thickness = (width*0.035f).coerceIn(8f,30f)
        val walls = mutableListOf(
            ArenaWall(RectF(0f,0f,thickness,worldHeight)),
            ArenaWall(RectF(width-thickness,0f,width,worldHeight)),
            ArenaWall(RectF(0f,0f,width,thickness)),
            ArenaWall(RectF(0f,worldHeight-thickness,width,worldHeight)))
        val bottom = launcherY-screenHeight*0.26f
        val top = screenHeight*0.20f
        repeat(level.ledges) { i ->
            val y = bottom-(bottom-top)*(i+1)/(level.ledges+1)
            val left = (i+number)%2==0
            val fraction = 0.26f+(number-1)/49f*0.12f+((number+i)%3)*0.015f
            val span = width*fraction
            walls += ArenaWall(RectF(if(left)0f else width-span,y,if(left)span else width,y+thickness))
        }
        repeat(level.pillars) { i ->
            val x = width*if((number+i)%2==0)0.25f else 0.75f
            val y = top+(bottom-top)*(i+0.5f)/level.pillars
            walls += ArenaWall(RectF(x-thickness*0.5f,y-thickness*2f,x+thickness*0.5f,y+thickness*2f))
        }
        val candidates = mutableListOf<Target>()
        // Sample known shots; targets come from their unobstructed trajectories,
        // instead of random positions that may sit in an unreachable pocket.
        for (degrees in -84..84 step 3) {
            val radians = Math.toRadians(degrees.toDouble())
            val nx = sin(radians).toFloat(); val ny = -cos(radians).toFloat()
            val path = PuzzleTrajectory.trace(launcherX,launcherY-50f,nx,ny,shotRadius,walls,
                worldHeight*2f+width*level.bounces,level.bounces)
            for (point in path.filterIndexed { i,_ -> i%8==0 }) {
                if (point.bounces>level.bounces || point.y < top || point.y>launcherY-screenHeight*0.27f) continue
                if (point.x<targetRadius+thickness || point.x>width-targetRadius-thickness) continue
                if (walls.any { wall ->
                    val dx=point.x-point.x.coerceIn(wall.rect.left,wall.rect.right)
                    val dy=point.y-point.y.coerceIn(wall.rect.top,wall.rect.bottom)
                    dx*dx+dy*dy < (targetRadius+8f)*(targetRadius+8f)
                }) continue
                candidates += Target(point.x,point.y,nx,ny,point.bounces)
            }
        }
        val targets = mutableListOf<Target>()
        repeat(level.targets) { index ->
            val desiredY = if(level.number<=5) screenHeight*(0.31f+index*0.095f)
                else top+(bottom-top)*(index+0.5f)/level.targets
            val desiredX = width*when((index+number)%3) { 0 -> 0.28f; 1 -> 0.5f; else -> 0.72f }
            val available = candidates.filter { candidate -> targets.none {
                val dx=it.x-candidate.x; val dy=it.y-candidate.y
                dx*dx+dy*dy < (targetRadius*2.2f)*(targetRadius*2.2f)
            } }
            val chosen = available.minByOrNull {
                abs(it.y-desiredY)+abs(it.x-desiredX)*0.9f+
                    if(level.number<=5)it.requiredBounces*screenHeight else 0f
            } ?: error("No reachable target for level $number at ${width}x$screenHeight")
            targets += chosen
        }
        return Layout(level,worldHeight,launcherX,launcherY,shotRadius,targetRadius,walls,targets)
    }
}
