// SPDX-FileCopyrightText: 2026 David Ventura
// SPDX-License-Identifier: GPL-3.0-only

package dev.davidv.motionsickness.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dev.davidv.motionsickness.motion.DotStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DotAppearance(
    val style: DotStyle = DotStyle.White,
    val opacity: Float = DEFAULT_OPACITY,
) {
    companion object {
        const val DEFAULT_OPACITY = 0.85f
        const val MIN_OPACITY = 0.1f
    }
}

/**
 * User-chosen dot appearance, persisted in SharedPreferences. The UI and the overlay service
 * run in the same process, so they share this singleton and its [appearance] flow, and the
 * overlay picks up changes live.
 */
class CueSettings private constructor(private val prefs: SharedPreferences) {

    private val _appearance = MutableStateFlow(
        DotAppearance(
            style = DotStyle.entries.firstOrNull { it.name == prefs.getString(KEY_STYLE, null) }
                ?: DotStyle.White,
            opacity = prefs.getFloat(KEY_OPACITY, DotAppearance.DEFAULT_OPACITY)
                .coerceIn(DotAppearance.MIN_OPACITY, 1f),
        ),
    )
    val appearance: StateFlow<DotAppearance> = _appearance.asStateFlow()

    fun setStyle(style: DotStyle) {
        _appearance.update { it.copy(style = style) }
        prefs.edit { putString(KEY_STYLE, style.name) }
    }

    fun setOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(DotAppearance.MIN_OPACITY, 1f)
        _appearance.update { it.copy(opacity = clamped) }
        prefs.edit { putFloat(KEY_OPACITY, clamped) }
    }

    companion object {
        private const val PREFS_NAME = "cue_settings"
        private const val KEY_STYLE = "dot_style"
        private const val KEY_OPACITY = "dot_opacity"

        @Volatile private var instance: CueSettings? = null

        fun get(context: Context): CueSettings =
            instance ?: synchronized(this) {
                instance ?: CueSettings(
                    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
                ).also { instance = it }
            }
    }
}
