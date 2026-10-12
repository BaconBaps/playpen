package com.mostly.sluggershot

import android.graphics.Canvas
import kotlin.math.abs

class Projectile(
    val id: Long,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val piercing: Boolean = false,
    val bouncy: Boolean = false,
    val bounceLimit: Int = MAX_BOUNCES - 1,
    val maxLifetimeSeconds: Float = MAX_LIFETIME_SECONDS
) {
    var active = true
        private set

    var bounceCount = 0
        private set

    private var ageSeconds = 0f

    val mass: Float = radius * radius

    val damageMultiplier: Float
        get() = 1f + bounceCount * 0.45f

    fun damage(): Float = damageMultiplier

    fun environmentDamage(): Float {
        val heavyFactor =
            if (radius >= Slingshot.PROJECTILE_RADIUS * 2f) 3.5f else 1f
        return damageMultiplier * heavyFactor
    }

    fun speed(): Float = kotlin.math.sqrt(vx * vx + vy * vy)

    fun integrate(dt: Float) {
        if (!active) return
        ageSeconds += dt
        if (ageSeconds >= maxLifetimeSeconds) {
            active = false
            return
        }
        x += vx * dt
        y += vy * dt
    }

    fun resolveScreenWalls(
        screenWidth: Float,
        screenHeight: Float,
        onBounce: () -> Unit,
        onBreak: () -> Unit
    ) {
        if (!active) return
        val margin = radius * 2f
        if (x < -margin || x > screenWidth + margin || y < -margin || y > screenHeight + margin) {
            active = false
        }
    }

    fun registerArenaWallBounce(
        onBounce: () -> Unit,
        onBreak: () -> Unit
    ) {
        if (!active || piercing) return
        if (!bouncy) {
            active = false
            onBreak()
            return
        }
        registerBounce(onBounce, onBreak)
    }

    private fun registerBounce(
        onBounce: () -> Unit,
        onBreak: () -> Unit
    ) {
        bounceCount++
        if (bounceCount > bounceLimit) {
            active = false
            onBreak()
        } else onBounce()
    }

    fun deactivate() {
        active = false
    }

    fun draw(canvas: Canvas) {
        if(!active)return
        val velocity=speed().coerceAtLeast(1f)
        val nx=vx/velocity;val ny=vy/velocity
        val color=if(piercing)0xFFCE8FFF.toInt() else 0xFF55EAD4.toInt()
        for(i in 4 downTo 1) {
            val offset=radius*i*1.3f
            val size=radius*(1f-i*0.16f)
            val alpha=150-i*25
            PixelArt.rect(canvas,x-nx*offset-size/2f,y-ny*offset-size/2f,
                x-nx*offset+size/2f,y-ny*offset+size/2f,(alpha shl 24) or (color and 0xFFFFFF))
        }
        PixelArt.draw(canvas,if(piercing)"shot:purple" else "shot",x,y,radius*2f)
        if(bounceCount>0)PixelArt.halo(canvas,x,y,radius+4f+bounceCount*2f,0xFFFF9A54.toInt(),90)
    }

    companion object {
        private const val MAX_LIFETIME_SECONDS = 5.0f
        const val MAX_BOUNCES = 4
    }
}
