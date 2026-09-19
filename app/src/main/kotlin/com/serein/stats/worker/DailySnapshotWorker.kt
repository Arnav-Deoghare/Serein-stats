package com.serein.stats.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.serein.stats.ui.UsageHelper
import java.util.concurrent.TimeUnit

/**
 * Writes today's usage into the local daily_usage archive on a schedule, independent of
 * whether Serein is ever opened. Without this, a day the app isn't opened never gets a
 * local snapshot — and once Android prunes its own usage-stats history for that day
 * (which OEMs like Samsung do aggressively), that day's data is gone for good.
 *
 * Runs a few times a day rather than once, since Doze/App Standby can delay periodic
 * work by hours on some devices — more chances per day means a much smaller chance of
 * missing a calendar day entirely.
 */
class DailySnapshotWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        if (!UsageHelper.hasPermission(applicationContext)) return Result.success()
        return try {
            val apps = UsageHelper.getApps(applicationContext)
            UsageHelper.persistTodaySnapshot(applicationContext, apps)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "serein_daily_snapshot"

        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<DailySnapshotWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}