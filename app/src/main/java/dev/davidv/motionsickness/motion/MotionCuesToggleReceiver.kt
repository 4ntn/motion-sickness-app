// SPDX-FileCopyrightText: 2026 David Ventura
// SPDX-License-Identifier: GPL-3.0-only

package dev.davidv.motionsickness.motion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Starts/stops the MotionCuesService when the home screen widget is tapped. Kept separate from
 * [MotionCuesWidgetProvider] because that one must be exported for the system's widget updates,
 * while this one is not exported, so other apps can't toggle the overlay.
 */
class MotionCuesToggleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        if (MotionCuesService.isRunning.value) {
            MotionCuesService.stop(context)
        } else {
            // Starting a foreground service can be refused in some background states; the
            // widget simply stays off then.
            runCatching { MotionCuesService.start(context) }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "dev.davidv.motionsickness.ACTION_TOGGLE_WIDGET"
    }
}
