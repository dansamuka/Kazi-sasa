package com.kazisasa.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.Constraints
import com.kazisasa.app.di.AppContainer
import java.util.concurrent.TimeUnit

/**
 * Background refresh so the feed is current without the user having to remember to
 * pull-to-refresh (spec §7.7 still applies on launch/manual refresh regardless -
 * this is purely an additive convenience, and its failure is silent/harmless
 * because [FeedRepository.refresh] already degrades gracefully on its own).
 */
class FeedRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = AppContainer.getInstance(applicationContext)
        container.feedRepository.refresh()
        return Result.success() // always succeeds - refresh() itself never throws and always leaves usable data
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "periodic_feed_refresh"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
