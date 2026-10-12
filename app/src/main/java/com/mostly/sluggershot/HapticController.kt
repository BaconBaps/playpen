package com.mostly.sluggershot

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticController(context: Context) {
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

    private var lastPulseTime = 0L

    fun updateTension(pullRatio: Float, beyondLimit: Boolean) {
        val now = System.currentTimeMillis()

        if (beyondLimit) {
            if (now - lastPulseTime > 70L) {
                vibrate(35L, 220)
                lastPulseTime = now
            }
            return
        }

        if (pullRatio >= 0.80f) {
            val normalized = ((pullRatio - 0.80f) / 0.20f).coerceIn(0f, 1f)
            val interval = (180L - normalized * 110L).toLong()

            if (now - lastPulseTime > interval) {
                val amplitude = (60 + normalized * 100).toInt().coerceIn(1, 255)
                vibrate(18L, amplitude)
                lastPulseTime = now
            }
        }
    }

    fun heavyCharged() {
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0L, 45L, 35L, 70L),
                    intArrayOf(0, 150, 0, 235),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(110L)
        }
    }

    fun modeSelected() = vibrate(16L, 85)
    fun autoShot() = vibrate(9L, 65)
    fun beamFire() = vibrate(38L, 190)
    fun release() = vibrate(30L, 200)
    fun bounce() = vibrate(12L, 80)
    fun projectileCollision() = vibrate(10L, 55)
    fun projectileBreak() = vibrate(45L, 150)
    fun playerDamaged() {
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0L, 42L, 28L, 70L),
                    intArrayOf(0, 230, 0, 180),
                    -1
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(140L)
        }
    }
    fun stop() = vibrator.cancel()

    private fun vibrate(duration: Long, amplitude: Int) {
        if (!vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(
                    duration,
                    amplitude.coerceIn(1, 255)
                )
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }
}
