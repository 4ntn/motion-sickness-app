// SPDX-FileCopyrightText: 2026 David Ventura
// SPDX-License-Identifier: GPL-3.0-only

package dev.davidv.motionsickness.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CueLayoutTest {
    // Portrait phone-ish screen; unit = half the shorter side.
    private val w = 1080f
    private val h = 2400f
    private val unit = w / 2f

    private fun CueMode.at(x: Float, y: Float, width: Float = w, height: Float = h) =
        sizeFactor(x, y, width, height, minOf(width, height) / 2f)

    @Test
    fun columns_fullSizeAtSideEdges_hiddenInMiddle() {
        assertEquals(1f, CueMode.Columns.at(10f, h / 2), 0f)
        assertEquals(1f, CueMode.Columns.at(w - 10f, h / 2), 0f)
        assertEquals(0f, CueMode.Columns.at(w / 2, h / 2), 0f)
        assertEquals(0f, CueMode.Columns.at(w / 2, 10f), 0f)
    }

    @Test
    fun edges_fullSizeAtAllEdges_hiddenInMiddle() {
        assertEquals(1f, CueMode.Edges.at(10f, h / 2), 0f)
        assertEquals(1f, CueMode.Edges.at(w / 2, 10f), 0f)
        assertEquals(1f, CueMode.Edges.at(w / 2, h - 10f), 0f)
        assertEquals(0f, CueMode.Edges.at(w / 2, h / 2), 0f)
    }

    @Test
    fun focus_hiddenAtCenter_fullSizeAtCorner() {
        assertEquals(0f, CueMode.Focus.at(w / 2, h / 2), 0f)
        assertEquals(1f, CueMode.Focus.at(0f, 0f), 0f)
    }

    @Test
    fun bandThickness_sameInPortraitAndLandscape() {
        // Find where the left band ends, in px, in both orientations.
        fun bandEnd(width: Float, height: Float): Float {
            var x = 0f
            while (CueMode.Columns.at(x, height / 2, width, height) > 0f) x += 1f
            return x
        }
        assertEquals(bandEnd(w, h), bandEnd(h, w), 1f)
        // Band stays well clear of the center.
        assertTrue(bandEnd(w, h) < unit * 0.6f)
    }
}
