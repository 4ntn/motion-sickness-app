// SPDX-FileCopyrightText: 2026 David Ventura
// SPDX-License-Identifier: GPL-3.0-only

package dev.davidv.motionsickness.motion

import android.graphics.Canvas
import android.graphics.Paint
import androidx.annotation.StringRes
import dev.davidv.motionsickness.R

/**
 * How each dot is painted. Different styles keep the cues visible over light content, dark
 * content, or both. [label] is an accessibility description for the style picker.
 */
enum class DotStyle(@StringRes val label: Int) {
    White(R.string.dot_style_white),
    Black(R.string.dot_style_black),
    /** Left half white, right half black. */
    Split(R.string.dot_style_split),
    /** Smaller white dot surrounded by a black ring. */
    WhiteOutlined(R.string.dot_style_white_outlined),
    /** Smaller black dot surrounded by a white ring. */
    BlackOutlined(R.string.dot_style_black_outlined),
}

private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLACK = 0xFF000000.toInt()

/** Fraction of the dot radius taken by the fill in the outlined styles; the rest is the ring. */
private const val OUTLINED_INNER_RATIO = 0.6f

/**
 * Draws one dot of this style centered at ([cx], [cy]) with outer radius [r]. Shared by the
 * overlay and the style picker so the swatches match what's drawn on screen.
 *
 * Draws with opaque colors, overwriting [paint]'s color. Apply opacity to the whole layer (e.g.
 * `View.alpha`) instead of the paint, or the overlapping fills of the outlined styles would blend.
 */
fun DotStyle.draw(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
    when (this) {
        DotStyle.White -> circle(canvas, cx, cy, r, paint, WHITE)
        DotStyle.Black -> circle(canvas, cx, cy, r, paint, BLACK)
        DotStyle.Split -> {
            // Angles are clockwise from 3 o'clock: 90°→270° sweeps the left half.
            paint.color = WHITE
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, 90f, 180f, true, paint)
            paint.color = BLACK
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, 270f, 180f, true, paint)
        }
        DotStyle.WhiteOutlined -> {
            circle(canvas, cx, cy, r, paint, BLACK)
            circle(canvas, cx, cy, r * OUTLINED_INNER_RATIO, paint, WHITE)
        }
        DotStyle.BlackOutlined -> {
            circle(canvas, cx, cy, r, paint, WHITE)
            circle(canvas, cx, cy, r * OUTLINED_INNER_RATIO, paint, BLACK)
        }
    }
}

private fun circle(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint, color: Int) {
    paint.color = color
    canvas.drawCircle(cx, cy, r, paint)
}
