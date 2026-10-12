package com.mostly.sluggershot

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * Uses the rotation-vector sensor and captures the player's ordinary portrait
 * holding angle as neutral. Only a deliberate forward pitch beyond the dead
 * zone produces thrust.
 */
class TiltThrusterController(context: Context) : SensorEventListener {
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var neutralPitch = 0f
    private var filteredPitch = 0f
    private var sampleCount = 0
    private var registered = false

    var thrust01 = 0f
        private set

    val available: Boolean
        get() = rotationSensor != null

    fun start() {
        if (registered || rotationSensor == null) return
        sampleCount = 0
        thrust01 = 0f
        sensorManager.registerListener(
            this,
            rotationSensor,
            SensorManager.SENSOR_DELAY_GAME
        )
        registered = true
    }

    fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(this)
        registered = false
        thrust01 = 0f
    }

    fun recalibrate() {
        sampleCount = 0
        thrust01 = 0f
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientation)
        val pitch = orientation[1]

        if (sampleCount == 0) filteredPitch = pitch
        filteredPitch += (pitch - filteredPitch) * 0.10f

        // Average the first ~0.7 sec at SENSOR_DELAY_GAME as the natural hold.
        if (sampleCount < CALIBRATION_SAMPLES) {
            neutralPitch =
                if (sampleCount == 0) filteredPitch
                else neutralPitch + (filteredPitch - neutralPitch) / (sampleCount + 1f)
            sampleCount++
            thrust01 = 0f
            return
        }

        // On normal portrait Android orientation, pitching the top of the phone
        // away from the player reduces pitch. If a device reports the opposite
        // sign, the absolute guard prevents tiny neutral motion from igniting.
        // Physical-device test showed this sensor orientation was reversed:
        // thrust should engage when the player tips the TOP of the phone away.
        val forwardDelta = filteredPitch - neutralPitch
        val magnitude = if (abs(forwardDelta) < DEAD_ZONE_RAD) 0f else forwardDelta
        thrust01 =
            ((magnitude - IGNITION_RAD) / (FULL_THRUST_RAD - IGNITION_RAD))
                .coerceIn(0f, 1f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        private const val CALIBRATION_SAMPLES = 35
        private const val DEAD_ZONE_RAD = 0.07f
        private const val IGNITION_RAD = 0.11f
        private const val FULL_THRUST_RAD = 0.42f
    }
}
