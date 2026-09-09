package com.gorisse.thomas.quota.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gorisse.thomas.quota.data.StatusRepository
import java.util.concurrent.TimeUnit

/**
 * Refreshes the cached status and redraws the widget. Runs every 15 minutes — WorkManager's
 * floor for periodic work — and once whenever the app is opened or a widget is added.
 */
class RefreshWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val repository = StatusRepository.get(applicationContext)
        if (!repository.isPaired && !repository.isDemo) return Result.success()
        val outcome = repository.refresh()
        QuotaWidgetUpdater.updateAll(applicationContext)
        // A failed fetch is not a bug to retry aggressively: the next period is 15 min away
        // and the widget already greys itself out past ten minutes.
        return if (outcome.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC = "quota-refresh-periodic"
        private const val ONE_SHOT = "quota-refresh-now"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
