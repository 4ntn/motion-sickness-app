// SPDX-FileCopyrightText: 2026 David Ventura
// SPDX-License-Identifier: GPL-3.0-only

package dev.davidv.motionsickness.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Horizontal-world acceleration in m/s^2 plus angular rates, mapped onto the screen axes so the
 * renderer can move the dots along the screen (not along the ground) in response to how the
 * vehicle moves.
 *
 * World-horizontal acceleration is split along two horizontal directions tied to the screen:
 * - [x] (lateral): along the screen's +x axis (as currently displayed) flattened onto the
 *   ground plane — cornering forces.
 * - [y] (longitudinal): the horizontal direction perpendicular to that, pointing away from the
 *   viewer (the way you'd look through the screen) — accelerating and braking. Because it's measured horizontally, it works the
 *   same whether the phone lies flat, is tilted back, or is held upright.
 *
 * [outOfPlane] is the signed component of horizontal acceleration along the device's +Z axis
 * (out of the screen, toward the user).
 *
 * [yawRateRps] is the angular velocity around the world-vertical axis (rad/s). [pitchRateRps]
 * is the angular velocity around the world-horizontal axis perpendicular to the direction the
 * screen is facing. Together they describe how the phone's viewing direction rotates through
 * the world, driving the horizontal and vertical scroll of the dot grid.
 */
data class MotionVector(
    val x: Float,
    val y: Float,
    val outOfPlane: Float,
    val yawRateRps: Float,
    val pitchRateRps: Float,
) {
    companion object { val ZERO = MotionVector(0f, 0f, 0f, 0f, 0f) }
}

/**
 * Fuses linear-acceleration, rotation-vector, and gyroscope data into the inputs the renderer
 * needs to make the dot field feel like a fixed world you're moving and rotating through.
 */
class MotionEstimator(context: Context) {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val linearAccel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // Sensors report in the device's natural frame, but the overlay is drawn in the display
    // frame, which rotates with the foreground app (landscape, upside-down, ...). Track the
    // display rotation so "screen +x" means the displayed x axis. A DisplayListener is needed
    // because 90°↔270° flips don't change the overlay's size.
    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private var displayRotation = Surface.ROTATION_0
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) refreshDisplayRotation()
        }
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
    }

    private val _motion = MutableStateFlow(MotionVector.ZERO)
    val motion: StateFlow<MotionVector> = _motion.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private var hasRotation = false

    private var filteredX = 0f
    private var filteredY = 0f
    private var filteredZ = 0f
    // World-frame (east, north) unit vector of the screen's lateral direction. Held at its last
    // good value while the screen's x axis points nearly straight up/down and has no usable
    // horizontal projection.
    private var lateralE = 1f
    private var lateralN = 0f
    private var filteredYawRate = 0f
    private var filteredPitchRate = 0f
    private var lastAccelTsNs = 0L
    private var lastGyroTsNs = 0L

    // Gyro-bias state. Even "calibrated" gyros carry a small DC offset on each axis; with our
    // direct angular-rate-to-offset integration that bias becomes a perpetual scroll when the
    // phone is stationary. Bias is a hardware property of each device-frame axis, so we track
    // it in device coords and subtract *before* rotating to world/yaw/pitch.
    private var gxBias = 0f
    private var gyBias = 0f
    private var gzBias = 0f
    private var stillAccumSec = 0f
    private var lastAccelMagSq = 0f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    hasRotation = true
                }
                Sensor.TYPE_LINEAR_ACCELERATION -> {
                    if (!hasRotation) return
                    updateAccel(event.values[0], event.values[1], event.values[2], event.timestamp)
                }
                Sensor.TYPE_GYROSCOPE -> {
                    if (!hasRotation) return
                    updateGyro(event.values[0], event.values[1], event.values[2], event.timestamp)
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private fun updateAccel(ax: Float, ay: Float, az: Float, tsNs: Long) {
        // Android convention: getRotationMatrixFromVector gives R such that world = R * device,
        // with world axes (east, north, up). Row 2 of R is world-up in device coordinates.
        val r = rotationMatrix
        val ux = r[6]
        val uy = r[7]
        val uz = r[8]

        // Device-frame horizontal acceleration: only used for the out-of-plane size cue and
        // for stillness detection.
        val dot = ax * ux + ay * uy + az * uz
        val hx = ax - dot * ux
        val hy = ay - dot * uy
        val hz = az - dot * uz

        // Horizontal world acceleration (east, north). Dropping the world-up row of R is the
        // projection onto the ground plane.
        val accelE = r[0] * ax + r[1] * ay + r[2] * az
        val accelN = r[3] * ax + r[4] * ay + r[5] * az

        // Lateral direction = the displayed screen +x axis flattened onto the ground.
        val sx = screenXAxisDeviceX()
        val sy = screenXAxisDeviceY()
        val latE = r[0] * sx + r[1] * sy
        val latN = r[3] * sx + r[4] * sy
        val latLenSq = latE * latE + latN * latN
        if (latLenSq > SIDEWAYS_GUARD_SQ) {
            val invLen = 1f / sqrt(latLenSq)
            lateralE = latE * invLen
            lateralN = latN * invLen
        }
        // Longitudinal = up × lateral: horizontal, pointing away from the viewer.
        val lateral = accelE * lateralE + accelN * lateralN
        val longitudinal = -accelE * lateralN + accelN * lateralE

        val dt = if (lastAccelTsNs == 0L) 0.02f else ((tsNs - lastAccelTsNs) / 1e9f).coerceIn(0.001f, 0.2f)
        lastAccelTsNs = tsNs
        val alpha = dt / (ACCEL_TIME_CONSTANT_SEC + dt)
        filteredX += alpha * (lateral - filteredX)
        filteredY += alpha * (longitudinal - filteredY)
        filteredZ += alpha * (hz - filteredZ)

        // Cache the current linear-accel magnitude squared for stillness detection in the
        // gyro handler (it typically ticks faster than accel).
        lastAccelMagSq = hx * hx + hy * hy + hz * hz

        publish()
    }

    // The displayed screen's +x axis in device coordinates. Display rotation is the rotation of
    // the drawn content relative to the device: e.g. at ROTATION_90 (device turned CCW, top to
    // the left) the screen's +x points along the device's -y.
    private fun screenXAxisDeviceX(): Float = when (displayRotation) {
        Surface.ROTATION_90, Surface.ROTATION_270 -> 0f
        Surface.ROTATION_180 -> -1f
        else -> 1f
    }

    private fun screenXAxisDeviceY(): Float = when (displayRotation) {
        Surface.ROTATION_90 -> -1f
        Surface.ROTATION_270 -> 1f
        else -> 0f
    }

    private fun refreshDisplayRotation() {
        displayRotation = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
    }

    private fun updateGyro(gx: Float, gy: Float, gz: Float, tsNs: Long) {
        val dt = if (lastGyroTsNs == 0L) 0.01f else ((tsNs - lastGyroTsNs) / 1e9f).coerceIn(0.001f, 0.2f)
        lastGyroTsNs = tsNs

        // Stillness check uses raw device-frame gyro magnitude — orientation-invariant, so
        // "phone sitting flat on a table" and "phone upright on a shelf" are detected alike.
        val rawGyroMagSq = gx * gx + gy * gy + gz * gz
        val isStill = lastAccelMagSq < STILL_ACCEL_MAG_SQ && rawGyroMagSq < STILL_GYRO_MAG_SQ
        if (isStill) {
            stillAccumSec += dt
            if (stillAccumSec > STILL_SETTLE_SEC) {
                val biasAlpha = dt / (BIAS_TRACK_SEC + dt)
                gxBias += biasAlpha * (gx - gxBias)
                gyBias += biasAlpha * (gy - gyBias)
                gzBias += biasAlpha * (gz - gzBias)
            }
        } else {
            stillAccumSec = 0f
        }

        // De-bias in device frame, then rotate to world.
        val gxd = gx - gxBias
        val gyd = gy - gyBias
        val gzd = gz - gzBias
        val owx = rotationMatrix[0] * gxd + rotationMatrix[1] * gyd + rotationMatrix[2] * gzd
        val owy = rotationMatrix[3] * gxd + rotationMatrix[4] * gyd + rotationMatrix[5] * gzd
        val owz = rotationMatrix[6] * gxd + rotationMatrix[7] * gyd + rotationMatrix[8] * gzd

        val yawRate = owz

        val fx = -rotationMatrix[2]
        val fy = -rotationMatrix[5]
        val fLenSq = fx * fx + fy * fy
        val pitchRate = if (fLenSq > FLAT_PITCH_GUARD_SQ) {
            val invLen = 1f / sqrt(fLenSq)
            val sideX = -fy * invLen
            val sideY = fx * invLen
            owx * sideX + owy * sideY
        } else {
            0f // phone near flat — pitch axis is undefined, treat as zero
        }

        val alpha = dt / (GYRO_TIME_CONSTANT_SEC + dt)
        filteredYawRate += alpha * (yawRate - filteredYawRate)
        filteredPitchRate += alpha * (pitchRate - filteredPitchRate)

        publish()
    }

    private fun deadband(v: Float, threshold: Float): Float =
        if (v > threshold) v - threshold else if (v < -threshold) v + threshold else 0f

    private fun publish() {
        _motion.value = MotionVector(
            x = filteredX,
            y = filteredY,
            outOfPlane = filteredZ,
            yawRateRps = deadband(filteredYawRate, GYRO_DEADBAND_RPS),
            pitchRateRps = deadband(filteredPitchRate, GYRO_DEADBAND_RPS),
        )
    }

    fun start() {
        refreshDisplayRotation()
        displayManager.registerDisplayListener(displayListener, null)
        sensorManager.registerListener(listener, linearAccel, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(listener, rotationVector, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(listener, gyroscope, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(listener)
        displayManager.unregisterDisplayListener(displayListener)
        hasRotation = false
        lastAccelTsNs = 0L
        lastGyroTsNs = 0L
        filteredX = 0f
        filteredY = 0f
        filteredZ = 0f
        lateralE = 1f
        lateralN = 0f
        filteredYawRate = 0f
        filteredPitchRate = 0f
        // Don't clear gx/gy/gzBias — bias is a hardware property; persist across stop/start.
        stillAccumSec = 0f
        lastAccelMagSq = 0f
        _motion.value = MotionVector.ZERO
    }

    companion object {
        private const val ACCEL_TIME_CONSTANT_SEC = 0.08f
        private const val GYRO_TIME_CONSTANT_SEC = 0.03f

        // Screen x axis must be at least ~12° away from vertical to define the lateral direction.
        private const val SIDEWAYS_GUARD_SQ = 0.04f
        private const val FLAT_PITCH_GUARD_SQ = 0.04f

        // Stillness thresholds. Tight enough that actual motion doesn't count as still; loose
        // enough that hand-held-still does. ~0.3 m/s² accel magnitude, ~0.05 rad/s (3 deg/s)
        // rotation magnitude.
        private const val STILL_ACCEL_MAG_SQ = 0.09f
        private const val STILL_GYRO_MAG_SQ = 0.0025f
        // Require this much continuous stillness before starting to track bias.
        private const val STILL_SETTLE_SEC = 0.8f
        // How quickly the bias estimate converges while still — longer = steadier, slower.
        private const val BIAS_TRACK_SEC = 1.5f
        // Residual rate below this is rounded to zero to kill micro-drift.
        private const val GYRO_DEADBAND_RPS = 0.01f // ~0.57 deg/s
    }
}
