package com.raphaeluziel.noteswidget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class NotesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NotesWidget()

    /** First widget instance placed: start the schedule and fetch immediately. */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshWorker.schedulePeriodic(context)
        RefreshWorker.refreshNow(context)
    }

    /**
     * Also fires after reboot and app updates, so re-assert the schedule here (KEEP makes it a no-op
     * when it already exists). Don't enqueue one-time work from here: WorkManager toggling its
     * internal receiver when the queue empties sends PACKAGE_CHANGED, which triggers onUpdate again
     * and can loop. Keeping the periodic job enqueued avoids that.
     */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        RefreshWorker.schedulePeriodic(context)
    }

    /** Last widget instance removed. */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshWorker.cancelPeriodic(context)
    }
}
