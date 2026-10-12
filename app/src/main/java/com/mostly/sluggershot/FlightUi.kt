package com.mostly.sluggershot

import android.graphics.*
import kotlin.math.min

/** A shared, resolution-independent visual language for the flight deck. */
class FlightUi {
    private val ink = Paint()
    private val cyan = Color.rgb(112, 235, 226)
    private val muted = Color.rgb(140, 163, 183)
    private var scale = 1f
    private var ox = 0f
    private var oy = 0f
    private var pressed: String? = null
    private val hits = linkedMapOf<String, RectF>()

    fun hit(x: Float, y: Float): String? = hits.entries.firstOrNull {
        it.value.contains((x - ox) / scale, (y - oy) / scale)
    }?.key
    fun press(key: String?) { pressed = key }

    private fun begin(c: Canvas, w: Int, h: Int) {
        hits.clear()
        scale = min(w / 400f, h / 800f)
        ox = (w - 400f * scale) / 2f
        oy = (h - 800f * scale) / 2f
        c.save(); c.translate(ox, oy); c.scale(scale, scale)
    }
    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int = Color.WHITE, bold: Boolean = false, maxWidth: Float = (380f-x).coerceAtLeast(20f)) {
        PixelFont.draw(c,s,x,y,size,color,maxWidth)
    }
    private fun rect(c: Canvas, r: RectF, color: Int, radius: Float = 16f) {
        ink.shader = null; ink.style = Paint.Style.FILL; ink.color = color
        c.drawRect(r,ink)
        ink.color=0x6655EAD4
        c.drawRect(r.left,r.top,r.right,r.top+2f,ink)
        ink.color=0xAA07101F.toInt()
        c.drawRect(r.left,r.bottom-3f,r.right,r.bottom,ink)
    }
    private fun line(c: Canvas, x: Float, y: Float, xx: Float, yy: Float, color: Int, weight: Float = 1f) {
        ink.shader = null; ink.style = Paint.Style.STROKE; ink.color = color; ink.strokeWidth = weight
        c.drawLine(x, y, xx, yy, ink); ink.style = Paint.Style.FILL
    }
    private fun button(c: Canvas, key: String, title: String, sub: String, y: Float, primary: Boolean = false) {
        val r = RectF(28f, y, 372f, y + 76f); hits[key] = r
        rect(c, r, if (pressed == key) Color.rgb(70, 121, 132) else if (primary) cyan else Color.rgb(24, 40, 57))
        val color = if (primary) Color.rgb(8, 24, 34) else Color.WHITE
        text(c, title, 48f, y + 31f, 18f, color, true)
        text(c, sub, 48f, y + 54f, 12f, if (primary) Color.rgb(27, 69, 76) else muted, maxWidth=276f)
        text(c, "›", 340f, y + 44f, 28f, color)
    }
    fun title(c: Canvas, w: Int, h: Int, best: Int, time: Float, backdrop: SceneAtmosphere?=null) {
        c.drawColor(Color.rgb(7,15,27))
        backdrop?.draw(c,w,h,0f)
        begin(c,w,h)
        // Deterministic star positions: no per-frame randomness or texture assets.
        repeat(70) { i ->
            ink.color = if (i % 4 == 0) 0x99BCE9ED.toInt() else 0x445C809D
            val x=((i*137+17)%400).toFloat();val y=((i*97+31)%800).toFloat()
            c.drawRect(x,y,x+2f,y+2f,ink)
        }
        text(c, "S / S     FLIGHT OPERATIONS", 28f, 48f, 11f, cyan, true)
        text(c, "SLUGGER", 28f, 113f, 47f, Color.WHITE, true)
        text(c, "SHOT", 28f, 161f, 47f, Color.WHITE, true)
        text(c, "ONE PULL. A THOUSAND POSSIBILITIES.", 30f, 188f, 10f, muted)
        PixelArt.halo(c,224f,300f,112f,0xFF297DA1.toInt(),70)
        PixelArt.draw(c,"player",224f,296f,144f)
        text(c,"16-BIT FRONTIER",28f,379f,10f,cyan,true)
        text(c, "PICK YOUR FLIGHT", 28f, 428f, 11f, muted, true)
        button(c, "combat", "Into the frontier", "SURVIVAL  /  upgrades, encounters & bosses", 449f, true)
        button(c, "puzzle", "Find your angle", "PUZZLES  /  50 sectors, from novice to expert", 539f)
        line(c, 28f, 658f, 372f, 658f, 0xFF26384A.toInt())
        text(c, "PERSONAL FLIGHT RECORD", 28f, 688f, 10f, muted)
        text(c, if (best > 0) "${best}s survived" else "Your first flight awaits", 28f, 716f, 18f, Color.WHITE, true)
        text(c, "DRAG TO AIM  ·  RELEASE TO LAUNCH", 28f, 767f, 10f, cyan)
        c.restore()
    }
    fun overlay(c: Canvas, w: Int, h: Int, kind: String, puzzle: Boolean, seconds: Int, shots: Int, level: Int, checkpoint: Boolean, puzzleLevel: PuzzleCampaign.Level? = null) {
        c.drawColor(0xE8070F1B.toInt()); begin(c, w, h)
        text(c, "S / S     FLIGHT OPERATIONS", 28f, 70f, 11f, cyan, true)
        val heading = when(kind) { "briefing" -> if (puzzle) "Find your angle." else "Ready, pilot?"; "pause" -> "Flight paused."; "complete" -> "Sector cleared."; "campaign" -> "Mission complete."; else -> "Flight complete." }
        text(c, heading, 28f, 163f, 32f, Color.WHITE, true)
        text(c, when(kind) { "briefing" -> if(puzzle) "PRECISION FLIGHT / SECTOR $level" else "SURVIVAL FLIGHT / THE FRONTIER"; "pause" -> "TAKE A BREATH. YOUR FLIGHT IS SAFE."; "complete" -> "EVERY TARGET. EVERY ANGLE. WELL FLOWN."; "campaign" -> "ALL 50 SECTORS CLEARED. WELL FLOWN."; else -> "REGROUP. RELOAD. GO AGAIN." }, 28f, 197f, 10f, cyan)
        rect(c, RectF(28f, 235f, 372f, 435f), Color.rgb(18, 31, 47))
        if (kind == "briefing") {
            text(c, "01   PULL & RELEASE", 48f, 275f, 13f, Color.WHITE, true)
            text(c, "Drag down to aim up. Let go to launch.", 48f, 300f, 13f, muted)
            text(c, if(puzzle) "02   WORK THE ANGLES" else "02   KEEP MOVING", 48f, 338f, 13f, Color.WHITE, true)
            text(c, if(puzzle) puzzleLevel?.hint ?: "Follow the guide to your first beacon." else "Slide with two fingers to move your ship.", 48f, 363f, 12f, muted)
            text(c, if(puzzle) "${puzzleLevel?.bounces ?: 8} bounces per shot. Take your time." else "Hold a finger still to thrust. Watch your fuel.", 48f, 405f, 12f, cyan)
        } else {
            text(c, if(puzzle) "SECTOR" else "FLIGHT TIME", 48f, 275f, 11f, muted)
            text(c, if(puzzle) "$level" else "${seconds}s", 48f, 329f, 42f, Color.WHITE, true)
            line(c, 48f, 352f, 352f, 352f, 0xFF304356.toInt())
            text(c, "VOLLEYS LAUNCHED", 48f, 387f, 11f, muted)
            text(c, "$shots", 315f, 390f, 20f, cyan, true)
            if(checkpoint && kind == "result") text(c, "Boss checkpoint available", 48f, 416f, 11f, cyan)
        }
        val primary = when(kind) { "briefing" -> "launch"; "pause" -> "resume"; "complete" -> "next"; "campaign" -> "levels"; else -> "retry" }
        val label = when(kind) { "briefing" -> "Launch flight"; "pause" -> "Resume flight"; "complete" -> "Next sector"; "campaign" -> "Replay a sector"; else -> if(checkpoint) "Retry boss encounter" else "Fly again" }
        button(c, primary, label, when(kind) { "briefing" -> "Your flight starts when you are ready"; "pause" -> "Continue where you left off"; "complete" -> "A new layout. A new challenge."; "campaign" -> "Choose any of the 50 sectors"; else -> "Another shot at the frontier" }, 475f, true)
        button(c, "home", "Flight deck", "Return to mode selection", 568f)
        if (puzzle && kind == "pause") button(c, "restart", "Retry this sector", "Reset targets and return to the briefing", 661f)
        text(c, "SLUGGERSHOT / FRONTIER PROGRAM", 28f, 750f, 10f, muted)
        c.restore()
    }
    fun levelSelection(c: Canvas, w: Int, h: Int, unlocked: Int, completed: Int) {
        c.drawColor(Color.rgb(7,15,27)); begin(c,w,h)
        text(c,"PRECISION FLIGHT / CAMPAIGN",28f,60f,11f,cyan,true)
        text(c,"Find your angle.",28f,116f,32f,Color.WHITE,true)
        text(c,"50 sectors. One reflection at a time.",28f,149f,13f,muted)
        text(c,"${completed}/50 CLEARED    /    NEXT: ${unlocked.toString().padStart(2,'0')}",28f,184f,11f,cyan,true)
        for(n in 1..50) {
            val row=(n-1)/5; val col=(n-1)%5
            val x=28f+col*70f; val y=205f+row*49f
            val r=RectF(x,y,x+64f,y+42f)
            val open=n<=unlocked
            val key="level:$n"
            if(open)hits[key]=r
            rect(c,r,if(pressed==key)0xFF426875.toInt() else if(n==unlocked)cyan else if(open)0xFF203D4C.toInt() else 0xFF101D2C.toInt(),9f)
            text(c,n.toString().padStart(2,'0'),x+19f,y+27f,16f,if(n==unlocked)0xFF08242C.toInt() else if(open)Color.WHITE else 0xFF526277.toInt(),true)
            if(n<=completed) { line(c,x+48f,y+10f,x+52f,y+14f,cyan,2f); line(c,x+52f,y+14f,x+58f,y+7f,cyan,2f) }
        }
        hits["home"]=RectF(28f,725f,372f,783f)
        rect(c,hits.getValue("home"),0xFF182839.toInt(),12f)
        text(c,"‹  Flight deck",48f,762f,16f,Color.WHITE,true)
        c.restore()
    }

    fun encounter(c: Canvas, message: String, bossFraction: Float? = null) {
        // Called after HUD layout; retain its pause/bomb hit regions.
        c.save(); c.translate(ox, oy); c.scale(scale, scale)
        if (bossFraction != null) {
            text(c, "HOSTILE / BOSS", 26f, 123f, 10f, 0xFFFFBA66.toInt(), true)
            rect(c, RectF(26f, 132f, 374f, 138f), 0xFF304050.toInt(), 3f)
            rect(c, RectF(26f, 132f, 26f + 348f * bossFraction.coerceIn(0f, 1f), 138f), 0xFFFFBA66.toInt(), 3f)
        }
        if (message.isNotBlank()) {
            val parts = message.split("•").map { it.trim() }
            val y = if (bossFraction == null) 120f else 154f
            rect(c, RectF(26f, y, 374f, y + 70f), 0xEF142B3D.toInt(), 12f)
            text(c, parts.first().take(40), 42f, y + 27f, 14f, Color.WHITE, true)
            text(c, parts.drop(1).joinToString(" / ").take(48), 42f, y + 49f, 10f, cyan)
        }
        c.restore()
    }

    fun hud(c: Canvas, w: Int, h: Int, puzzle: Boolean, health: Int, maxHealth: Int, fuel: Float, seconds: Int, targets: Int, level: Int, loadout: String, bombs: Int, puzzleLevel: PuzzleCampaign.Level? = null, shotInFlight: Boolean = false) {
        begin(c, w, h)
        rect(c, RectF(12f, 16f, 388f, 103f), 0xEC0D1B2A.toInt(), 14f)
        text(c, if(puzzle) "SECTOR ${level.toString().padStart(2, '0')} / 50" else "FRONTIER / ${seconds}s", 26f, 41f, 11f, cyan, true)
        if(puzzle) {
            text(c, "$targets ${if(targets==1) "TARGET" else "TARGETS"} LEFT", 26f, 72f, 17f, Color.WHITE, true)
            text(c, "${puzzleLevel?.bounces ?: 8} BOUNCES / SHOT", 26f, 90f, 10f, muted)
        } else {
            text(c, "HULL", 26f, 61f, 9f, muted)
            repeat(maxHealth.coerceAtMost(10)) { i -> rect(c, RectF(26f + i * 20f, 70f, 41f + i * 20f, 77f), if(i < health) cyan else 0xFF304050.toInt(), 2f) }
            text(c, "FUEL", 26f, 93f, 9f, muted)
            rect(c, RectF(59f, 86f, 180f, 92f), 0xFF304050.toInt(), 3f)
            rect(c, RectF(59f, 86f, 59f + 121f * fuel.coerceIn(0f,1f), 92f), cyan, 3f)
        }
        hits["pause"] = RectF(324f, 24f, 380f, 90f)
        rect(c, hits.getValue("pause"), if(pressed == "pause") 0xFF38566A.toInt() else 0xFF1D3246.toInt(), 10f)
        line(c, 345f, 43f, 345f, 67f, Color.WHITE, 4f); line(c, 357f, 43f, 357f, 67f, Color.WHITE, 4f)
        rect(c, RectF(12f, 744f, 388f, 786f), 0xE80D1B2A.toInt(), 12f)
        text(c, if(puzzle) puzzleLevel?.chapter?.uppercase() ?: "PRECISION FLIGHT" else loadout.take(32), 26f, 770f, 10f, cyan, true)
        if(puzzle && shotInFlight) {
            hits["recall"] = RectF(282f,735f,390f,797f)
            rect(c,RectF(292f,749f,381f,781f),0xFF314654.toInt(),8f)
            text(c,"RECALL",305f,770f,11f,Color.WHITE,true)
        }
        if(!puzzle && bombs > 0) {
            hits["bomb"] = RectF(290f, 735f, 390f, 797f)
            rect(c, RectF(300f, 749f, 381f, 781f), 0xFF314654.toInt(), 8f)
            text(c, "BOMB ×$bombs", 310f, 770f, 10f, Color.WHITE, true)
        }
        c.restore()
    }
}
