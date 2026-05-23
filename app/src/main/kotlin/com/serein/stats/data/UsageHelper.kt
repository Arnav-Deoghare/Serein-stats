package com.serein.stats.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.*

data class AppUsage(
    val packageName: String,
    val label: String,
    val todayMinutes: Long,
    val weekMinutes: Long
)

object UsageHelper {

    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openPermissionSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun getTodayUsage(context: Context): List<AppUsage> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val pm = context.packageManager

        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        val startOfWeek = cal.apply { add(Calendar.DAY_OF_YEAR, -6) }.timeInMillis
        val now = System.currentTimeMillis()

        val todayStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
        val weekStats  = usm.queryUsageStats(UsageStatsManager.INTERVAL_WEEKLY, startOfWeek, now)

        val weekMap = weekStats?.associate { it.packageName to it.totalTimeInForeground } ?: emptyMap()

        return todayStats
            ?.filter { it.totalTimeInForeground > 60_000 } // at least 1 min
            ?.filter { it.packageName != "com.serein.stats" }
            ?.map { stat ->
                val label = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(stat.packageName, 0)).toString()
                } catch (e: Exception) { stat.packageName.substringAfterLast(".") }

                AppUsage(
                    packageName = stat.packageName,
                    label = label,
                    todayMinutes = stat.totalTimeInForeground / 60_000,
                    weekMinutes = (weekMap[stat.packageName] ?: 0L) / 60_000
                )
            }
            ?.sortedByDescending { it.todayMinutes }
            ?: emptyList()
    }

    fun todayString(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}
