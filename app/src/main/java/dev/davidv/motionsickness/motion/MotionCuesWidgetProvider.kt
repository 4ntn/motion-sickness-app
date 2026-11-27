// SPDX-FileCopyrightText: 2026 David Ventura
// SPDX-License-Identifier: GPL-3.0-only

package dev.davidv.motionsickness.motion

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.davidv.motionsickness.R

/**
 * Home screen widget that starts/stops the MotionCuesService, mirroring the Quick Settings tile.
 * The service pushes state changes via [updateAll], so no periodic updates are needed.
 */
class MotionCuesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetManager.updateAppWidget(
            appWidgetIds,
            buildViews(context, MotionCuesService.isRunning.value),
        )
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, MotionCuesWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            manager.updateAppWidget(ids, buildViews(context, MotionCuesService.isRunning.value))
        }

        private fun buildViews(context: Context, running: Boolean): RemoteViews {
            val toggleIntent = Intent(context, MotionCuesToggleReceiver::class.java)
                .setAction(MotionCuesToggleReceiver.ACTION_TOGGLE)
            val togglePi = PendingIntent.getBroadcast(
                context, 0, toggleIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return RemoteViews(context.packageName, R.layout.widget_motion_cues).apply {
                setInt(
                    R.id.widget_icon,
                    "setBackgroundResource",
                    if (running) R.drawable.widget_bg_active else R.drawable.widget_bg_inactive,
                )
                setInt(R.id.widget_icon, "setImageAlpha", if (running) 255 else 128)
                setContentDescription(
                    R.id.widget_root,
                    context.getString(
                        if (running) R.string.widget_state_on else R.string.widget_state_off,
                    ),
                )
                setOnClickPendingIntent(R.id.widget_root, togglePi)
            }
        }
    }
}
