package com.hanifedma.waterline.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * The last line of defence, every fifteen minutes.
 *
 * Alarms and the foreground service cover the ordinary cases. This covers the
 * ones that are not: an OEM "battery optimiser" that kills the process and
 * drops its alarms, a force-stop followed by an unrelated launch, a
 * notification cleared by a system update. WorkManager survives all of those —
 * its queue is on disk and the system owns the scheduling.
 *
 * Fifteen minutes is WorkManager's floor for periodic work, and it is plenty:
 * the elapsed time on the lock screen is drawn by SystemUI and never goes
 * stale, so the worst case this repairs is a stale "3h 20m left" line.
 */
class WatchdogWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        FastingCoordinator.sync(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "waterline.watchdog"

        /** KEEP, so calling this from sync() on every pass is free. */
        fun ensureScheduled(context: Context) {
            runCatching {
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES)
                        // No constraints on purpose. Battery-low is exactly
                        // when an aggressive power manager is most likely to
                        // have killed the timer, so it is the last moment to
                        // stop checking on it.
                        .setConstraints(Constraints.NONE)
                        .build(),
                )
            }
        }
    }
}
