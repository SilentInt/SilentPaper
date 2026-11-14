package com.silent.paper.data

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Process

/**
 * API 21+ only. Kept in its own class so the launcher activity never has to
 * resolve [UsageStatsManager] / [AppOpsManager] on Android 4.x.
 *
 * LauncherActivity calls these methods only after a `SDK_INT >= 21` check, so
 * this class is never loaded on older Dalvik runtimes.
 */
@SuppressLint("NewApi")
object RecentTasks {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return if (mode == AppOpsManager.MODE_DEFAULT) {
            context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED
        } else {
            mode == AppOpsManager.MODE_ALLOWED
        }
    }

    fun load(
        context: Context,
        selfPackage: String,
        since: Long,
        clearedAt: Long,
        appClearedAt: (String) -> Long,
        limit: Int = 12
    ): List<ResolveInfo> {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val begin = now - 7L * 24 * 60 * 60 * 1000
        val stats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, begin, now)
        }.getOrDefault(emptyList())

        val pm = context.packageManager
        val apps = ArrayList<ResolveInfo>()
        for (stat in stats.sortedByDescending { it.lastTimeUsed }) {
            if (stat.lastTimeUsed <= 0) continue
            if (stat.lastTimeUsed <= clearedAt) continue
            if (since > 0L && stat.lastTimeUsed < since) continue

            val pkg = stat.packageName
            if (pkg == selfPackage) continue

            // 根据单个 App 的清理时间戳过滤历史记录
            if (stat.lastTimeUsed <= appClearedAt(pkg)) continue

            val launchIntent = pm.getLaunchIntentForPackage(pkg) ?: continue
            val info = runCatching { pm.resolveActivity(launchIntent, 0) }.getOrNull()
                ?: continue
            if (apps.any { it.activityInfo.packageName == pkg }) continue
            apps.add(info)
            if (apps.size >= limit) break
        }
        return apps
    }
}
