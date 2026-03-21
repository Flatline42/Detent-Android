package com.southsouthwest.framelog.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver that wires the Android App Widget system to FrameLogWidget.
 *
 * Registered in AndroidManifest.xml with the APPWIDGET_UPDATE intent filter and the
 * frame_log_widget_info.xml metadata. The Android launcher calls onUpdate when:
 *   - The user adds a new widget instance to their home screen
 *   - The system reboots (if updatePeriodMillis > 0, which ours is not)
 *   - The AppWidgetManager explicitly requests an update
 *
 * All rendering is managed by Glance. Our responsibility here is to trigger a data refresh
 * from Room/SharedPreferences whenever the system fires onUpdate or onEnabled.
 *
 * The scope is cancelled in onDisabled (no more widget instances on home screen).
 */
class FrameLogWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = FrameLogWidget()

    // A coroutine scope for data loading on the main thread. Glance actions are already
    // suspend functions, but onUpdate/onEnabled are regular BroadcastReceiver callbacks
    // that need a scope to launch coroutines from.
    private val scope = MainScope()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Trigger a full data refresh so newly-added widgets show current roll state.
        scope.launch {
            FrameLogWidgetUpdater.update(context)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // Called when the first instance of the widget is added. Same full refresh.
        scope.launch {
            FrameLogWidgetUpdater.update(context)
        }
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Called when the last instance is removed. Cancel our scope to avoid leaks.
        scope.cancel()
    }
}
