// SPDX-FileCopyrightText: 2026 David Ventura
// SPDX-License-Identifier: GPL-3.0-only

package dev.davidv.motionsickness.motion

import androidx.annotation.StringRes
import dev.davidv.motionsickness.R
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Where on screen the dots are shown. [label] is the name shown in the layout picker.
 * [gridDim] is the dot grid density (dots per side of the wrapping grid) the layout is drawn with.
 */
enum class CueMode(@StringRes val label: Int, val gridDim: Int) {
    /**
     * Dots fade to zero near the center and reach full size at the edges/corners, so the middle
     * of the screen stays clear for content.
     */
    Focus(R.string.cue_mode_focus, gridDim = 12),

    /**
     * Full-size dots in bands along the left and right edges, like iOS Vehicle Motion Cues and
     * Android Motion Assist. Denser grid so each band holds about two columns.
     */
    Columns(R.string.cue_mode_columns, gridDim = 20),

    /** Like [Columns], but with bands along all four edges. */
    Edges(R.string.cue_mode_edges, gridDim = 20),
}

/** Half-size of the wrapping dot grid, in iso units (1 unit = half the shorter screen side). */
internal const val GRID_EXTENT = 3.5f

/** Distance between neighboring dots in a row, in iso units. */
val CueMode.gridPitch: Float get() = 2f * GRID_EXTENT / gridDim

/**
 * Scale factor for a dot at pixel ([px], [py]) on a [width]×[height] screen: 0 hides it, 1 draws
 * it at full size. [unit] is half the shorter screen side, so edge bands have the same thickness
 * in portrait and landscape. Shared by the overlay and the layout picker preview.
 */
fun CueMode.sizeFactor(px: Float, py: Float, width: Float, height: Float, unit: Float): Float =
    when (this) {
        CueMode.Focus -> {
            val dx = px - width / 2f
            val dy = py - height / 2f
            // Normalize by half-diagonal → 0 at center, 1 at corners.
            val halfDiag = sqrt(width * width + height * height) / 2f
            smoothstep(FOCUS_INNER, FOCUS_OUTER, sqrt(dx * dx + dy * dy) / halfDiag)
        }
        CueMode.Columns -> bandFactor(min(px, width - px) / unit)
        CueMode.Edges -> bandFactor(min(min(px, width - px), min(py, height - py)) / unit)
    }

/** Full size within [BAND_FULL] of the edge, shrinking to nothing by [BAND_END]. */
private fun bandFactor(distFromEdge: Float): Float = 1f - smoothstep(BAND_FULL, BAND_END, distFromEdge)

internal fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

// Focus radial falloff: below FOCUS_INNER (normalized distance from center, 0=center 1=corner)
// dots are invisible; above FOCUS_OUTER they're full size.
private const val FOCUS_INNER = 0.25f
private const val FOCUS_OUTER = 0.9f

// Edge bands, in iso units from the nearest edge (0.38 ≈ 19% of portrait width).
private const val BAND_FULL = 0.38f
private const val BAND_END = 0.48f
