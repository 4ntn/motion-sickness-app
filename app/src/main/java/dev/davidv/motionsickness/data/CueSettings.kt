// SPDX-FileCopyrightText: 2026 David Ventura
// SPDX-License-Identifier: GPL-3.0-only

package dev.davidv.motionsickness.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.davidv.motionsickness.motion.CueMode
import dev.davidv.motionsickness.motion.DotStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DotAppearance(
    val style: DotStyle = DotStyle.White,
    val size: Float = DEFAULT_SIZE,
    val opacity: Float = DEFAULT_OPACITY,
    val mode: CueMode = CueMode.Columns,
    /** Hue in degrees for [DotStyle.Color]. */
    val hue: Float = DEFAULT_HUE,
) {
    companion object {
        const val DEFAULT_HUE = 200f
        const val MAX_HUE = 360f
        const val DEFAULT_SIZE = 1f
        const val MIN_SIZE = 0.5f
        const val MAX_SIZE = 2f
        const val DEFAULT_OPACITY = 0.85f
        const val MIN_OPACITY = 0.5f
    }
}

/**
 * User-chosen dot appearance and layout, persisted in SharedPreferences. The UI and the overlay service
 * run in the same process, so they share this singleton and its [appearance] flow, and the
 * overlay picks up changes live.
 */
class CueSettings private constructor(private val prefs: SharedPreferences) {

    private val _appearance = MutableStateFlow(
        DotAppearance(
            style = DotStyle.entries.firstOrNull { it.name == prefs.getString(KEY_STYLE, null) }
                ?: DotStyle.White,
            size = prefs.getFloat(KEY_SIZE, DotAppearance.DEFAULT_SIZE)
                .coerceIn(DotAppearance.MIN_SIZE, DotAppearance.MAX_SIZE),
            opacity = prefs.getFloat(KEY_OPACITY, DotAppearance.DEFAULT_OPACITY)
                .coerceIn(DotAppearance.MIN_OPACITY, 1f),
            mode = CueMode.entries.firstOrNull { it.name == prefs.getString(KEY_MODE, null) }
                ?: DotAppearance().mode,
            hue = prefs.getFloat(KEY_HUE, DotAppearance.DEFAULT_HUE)
                .coerceIn(0f, DotAppearance.MAX_HUE),
        ),
    )
    val appearance: StateFlow<DotAppearance> = _appearance.asStateFlow()

    fun setStyle(style: DotStyle) {
        _appearance.update { it.copy(style = style) }
        prefs.edit { putString(KEY_STYLE, style.name) }
    }

    fun setMode(mode: CueMode) {
        _appearance.update { it.copy(mode = mode) }
        prefs.edit { putString(KEY_MODE, mode.name) }
    }

    // The slider-driven setters only update the live value, so dragging doesn't write to disk
    // on every step. Call [saveSliders] once the drag ends.

    fun setSize(size: Float) {
        val clamped = size.coerceIn(DotAppearance.MIN_SIZE, DotAppearance.MAX_SIZE)
        _appearance.update { it.copy(size = clamped) }
    }

    fun setOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(DotAppearance.MIN_OPACITY, 1f)
        _appearance.update { it.copy(opacity = clamped) }
    }

    fun setHue(hue: Float) {
        val clamped = hue.coerceIn(0f, DotAppearance.MAX_HUE)
        _appearance.update { it.copy(hue = clamped) }
    }

    /** Persists the values set through [setSize], [setOpacity] and [setHue]. */
    fun saveSliders() {
        val a = _appearance.value
        prefs.edit {
            putFloat(KEY_SIZE, a.size)
            putFloat(KEY_OPACITY, a.opacity)
            putFloat(KEY_HUE, a.hue)
        }
    }

    companion object {
        private const val PREFS_NAME = "cue_settings"
        private const val KEY_STYLE = "dot_style"
        private const val KEY_SIZE = "dot_size"
        private const val KEY_OPACITY = "dot_opacity"
        private const val KEY_MODE = "cue_mode"
        private const val KEY_HUE = "dot_hue"

        @Volatile private var instance: CueSettings? = null

        fun get(context: Context): CueSettings =
            instance ?: synchronized(this) {
                instance ?: CueSettings(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                ).also { instance = it }
            }
    }
}
