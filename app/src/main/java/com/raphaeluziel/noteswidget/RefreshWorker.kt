package com.raphaeluziel.noteswidget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Fetches notes, saves them, then re-renders every placed widget. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val status = NotesRepository(applicationContext).refresh()
        NotesWidget().updateAll(applicationContext)
        return when (status) {
            // A transient failure gets a couple of backed-off retries; the next period covers the rest.
            SyncStatus.Offline, SyncStatus.Error ->
                if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
            // 403 / missing config won't fix themselves, so don't hammer the server.
            else -> Result.success()
        }
    }

    companion object {
        private const val PERIODIC_WORK = "notes-refresh-periodic"
        private const val ONE_TIME_WORK = "notes-refresh-now"
        private const val MAX_RETRIES = 2

        /** Idempotent: keeps an existing schedule rather than resetting its timer. */
        fun schedulePeriodic(context: Context) {
            // 15 minutes is WorkManager's minimum periodic interval.
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        }

        /** Runs once as soon as possible. No network constraint, so being offline shows up in the UI. */
        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RefreshWorker>().build(),
            )
        }
    }
}
