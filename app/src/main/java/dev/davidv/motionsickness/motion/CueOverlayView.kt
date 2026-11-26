// SPDX-FileCopyrightText: 2026 David Ventura
// SPDX-License-Identifier: GPL-3.0-only

package dev.davidv.motionsickness.motion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Choreographer
import android.view.View
import dev.davidv.motionsickness.data.DotAppearance
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Overlay view that renders the infinite dot field with a hardware-accelerated `Canvas`.
 *
 * Drawing through the View hierarchy (rather than a SurfaceView/GLSurfaceView) matters for more
 * than aesthetics: on Android 12+, only pixels that go through the window's compositor are
 * dimmed by `WindowManager.LayoutParams.alpha`. A SurfaceView renders into a separate
 * SurfaceFlinger layer at 100% opacity regardless of window alpha, so the OS still flags
 * touches as obscured and blocks them from reaching the app below. Canvas-drawn pixels are
 * part of the window and are dimmed correctly, which is what lets the touch-through trick
 * work.
 *
 * The grid is locked to the screen axes (it doesn't stay level with the ground when the phone
 * tilts): lateral vehicle acceleration moves it left/right and longitudinal acceleration
 * (speeding up / braking) moves it up/down. Positions live in an isotropic frame where one
 * unit = half the shorter screen dimension, so motion speed is the same on both axes
 * regardless of viewport aspect.
 *
 * Which dots are shown, and how big, depends on the [CueMode] layout (see [sizeFactor]).
 */
class CueOverlayView(context: Context) : View(context) {

    @Volatile private var motionX = 0f
    @Volatile private var motionY = 0f
    @Volatile private var motionOutOfPlane = 0f
    @Volatile private var rotationX = 0f
    @Volatile private var rotationY = 0f

    private var smoothedMotionX = 0f
    private var smoothedMotionY = 0f
    private var smoothedMotionOutOfPlane = 0f
    private var smoothedRotationX = 0f
    private var smoothedRotationY = 0f

    private val pixelDensity = context.resources.displayMetrics.density

    private var mode = DotAppearance().mode
    private var particles = Array(mode.gridDim * mode.gridDim) { Particle() }
    private var halfShortDim = 1f
    private var centerX = 0f
    private var centerY = 0f
    private var style: DotStyle = DotStyle.White
    private var sizeScale = DotAppearance.DEFAULT_SIZE
    private var dotColor = hueToColor(DotAppearance.DEFAULT_HUE)

    private var gridVx = 0f
    private var gridVy = 0f
    private var gridOx = 0f
    private var gridOy = 0f
    private var sizeEnvelope = 0f

    private var lastFrameNs = 0L
    private var running = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        setBackgroundColor(0)
        isClickable = false
        isFocusable = false
        alpha = DotAppearance.DEFAULT_OPACITY
    }

    /**
     * Applies the user's dot style, size and opacity. Opacity goes on the whole view rather than the
     * paint, so styles made of overlapping fills (outlined dots) stay crisp at any opacity.
     */
    fun setAppearance(appearance: DotAppearance) {
        style = appearance.style
        sizeScale = appearance.size
        dotColor = hueToColor(appearance.hue)
        alpha = appearance.opacity
        if (appearance.mode != mode) {
            val oldDim = mode.gridDim
            mode = appearance.mode
            if (mode.gridDim != oldDim) resetParticles()
        }
        invalidate()
    }

    fun setMotion(m: MotionVector) {
        motionX = m.x
        motionY = m.y
        motionOutOfPlane = m.outOfPlane
        rotationX = m.rotationX
        rotationY = m.rotationY
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        halfShortDim = min(w, h) / 2f
        centerX = w / 2f
        centerY = h / 2f
        resetParticles()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        lastFrameNs = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    private val frameCallback: Choreographer.FrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val dt = if (lastFrameNs == 0L) 1f / 60f
                     else ((frameTimeNanos - lastFrameNs) / 1e9f).coerceIn(0.001f, 0.1f)
            lastFrameNs = frameTimeNanos
            applyInputSmoothing(dt)
            step(dt)
            updateSizeEnvelope(dt)
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val sizeBoost = (1f + sizeEnvelope).coerceIn(1f, SIZE_BOOST_MAX)
        val ext = GRID_EXTENT
        val span = 2f * ext

        val w = width.toFloat()
        val h = height.toFloat()
        for (p in particles) {
            // Apply global grid offset and wrap into the toroidal iso square.
            val lx = wrap(p.homeX + gridOx, ext, span)
            val ly = wrap(p.homeY + gridOy, ext, span)
            // Iso → pixels via uniform scale by halfShortDim. Y flipped (pixel +y is down;
            // iso +y is up).
            val px = centerX + lx * halfShortDim
            val py = centerY - ly * halfShortDim

            val factor = mode.sizeFactor(px, py, w, h, halfShortDim)
            if (factor <= 0.01f) continue
            val radius = p.size * pixelDensity * sizeScale * sizeBoost * 0.5f * factor
            style.draw(canvas, px, py, radius, paint, dotColor)
        }
    }

    private fun step(dt: Float) {
        // Dots drift opposite to the acceleration, like loose objects in the car: speeding up
        // pushes them down the screen, braking pushes them up, a right turn pushes them left.
        gridVx += (-smoothedMotionX * DRIVE_GAIN) * dt
        gridVy += (-smoothedMotionY * DRIVE_GAIN) * dt
        val damping = exp(-DAMP * dt)
        gridVx *= damping
        gridVy *= damping
        gridOx += gridVx * dt
        gridOy += gridVy * dt

        // Rotation scrolls the grid directly — sustained rotation → sustained flow,
        // stop rotating → flow stops.
        gridOx += smoothedRotationX * ROTATION_GAIN * dt
        gridOy += smoothedRotationY * ROTATION_GAIN * dt

        val wrapSpan = 2f * GRID_EXTENT
        if (gridOx > GRID_EXTENT) gridOx -= wrapSpan
        else if (gridOx < -GRID_EXTENT) gridOx += wrapSpan
        if (gridOy > GRID_EXTENT) gridOy -= wrapSpan
        else if (gridOy < -GRID_EXTENT) gridOy += wrapSpan
    }

    private fun updateSizeEnvelope(dt: Float) {
        val target = max(0f, smoothedMotionOutOfPlane) * SIZE_OUT_OF_PLANE_GAIN
        if (target > sizeEnvelope) sizeEnvelope = target
        sizeEnvelope *= exp(-dt / SIZE_RELEASE_SEC)
    }

    private fun applyInputSmoothing(dt: Float) {
        val alpha = dt / (SMOOTH_TIME_SEC + dt)
        smoothedMotionX += alpha * (motionX - smoothedMotionX)
        smoothedMotionY += alpha * (motionY - smoothedMotionY)
        smoothedMotionOutOfPlane += alpha * (motionOutOfPlane - smoothedMotionOutOfPlane)

        smoothedRotationX += alpha * (rotationX - smoothedRotationX)
        smoothedRotationY += alpha * (rotationY - smoothedRotationY)
    }

    private fun resetParticles() {
        val dim = mode.gridDim
        if (particles.size != dim * dim) particles = Array(dim * dim) { Particle() }
        val rng = Random(42)
        val spacing = mode.gridPitch
        var i = 0
        // Staggered layout: odd rows shifted by half a column → hex-like pattern.
        for (row in 0 until dim) {
            val rowOffsetX = if (row % 2 == 1) spacing * 0.5f else 0f
            for (col in 0 until dim) {
                val nx = (col + 0.5f) * spacing - GRID_EXTENT + rowOffsetX
                val ny = (row + 0.5f) * spacing - GRID_EXTENT
                val jitterX = (rng.nextFloat() - 0.5f) * 0.04f
                val jitterY = (rng.nextFloat() - 0.5f) * 0.04f
                val p = particles[i++]
                p.homeX = nx + jitterX
                p.homeY = ny + jitterY
                p.size = DOT_SIZE_PX * (0.85f + rng.nextFloat() * 0.3f)
            }
        }
    }

    private class Particle {
        var homeX = 0f
        var homeY = 0f
        var size = 0f
    }

    companion object {
        private const val DRIVE_GAIN = 0.6f
        private const val DAMP = 2.7f
        private const val ROTATION_GAIN = 1.2f
        private const val DOT_SIZE_PX = 8f

        private const val SIZE_OUT_OF_PLANE_GAIN = 1.2f
        private const val SIZE_RELEASE_SEC = 0.9f
        private const val SIZE_BOOST_MAX = 8f

        private const val SMOOTH_TIME_SEC = 0.05f

        private fun wrap(v: Float, ext: Float, span: Float): Float {
            var r = (v + ext) % span
            if (r < 0f) r += span
            return r - ext
        }
    }
}
