package com.silent.paper.data

import android.content.Context

class LauncherConfigStore(context: Context) {

    private val prefs = context.getSharedPreferences("silent_paper_config", Context.MODE_PRIVATE)

    fun load(): LauncherConfig {
        return LauncherConfig(
            columns = prefs.getInt("columns", 0),          // 0 = 自动
            rows = prefs.getInt("rows", 0),                // 0 = 自动
            homeColumns = prefs.getInt("home_columns", 4),
            homeRows = prefs.getInt("home_rows", 0),       // 0 = 自动
            showHomeDividers = prefs.getBoolean("show_home_dividers", true),
            showAppDividers = prefs.getBoolean("show_app_dividers", true),
            homeTopStyle = prefs.getInt("home_top_style", 0),
            homeTopIconStyle = prefs.getInt("home_top_icon_style", 1),
            recentWindowMs = prefs.getLong("recent_window_ms", 30 * 60_000L),
            recentCancelBackToApp = prefs.getBoolean("recent_cancel_back_to_app", true),
            iconPack = prefs.getString("icon_pack", "") ?: "",
            autoLineIcons = prefs.getBoolean("auto_line_icons", true)
        )
    }

    fun isGuideShown(): Boolean = prefs.getBoolean("first_guide_shown", false)

    fun setGuideShown() {
        prefs.edit().putBoolean("first_guide_shown", true).apply()
    }

    fun incrementHomePressCount(): Int {
        val next = prefs.getInt("home_press_count", 0) + 1
        prefs.edit().putInt("home_press_count", next).apply()
        return next
    }

    fun resetHomePressCount() {
        prefs.edit().putInt("home_press_count", 0).apply()
    }

    fun isDonationPromptEnabled(): Boolean = prefs.getBoolean("donation_prompt_enabled", true)

    fun setDonationPromptEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("donation_prompt_enabled", enabled).apply()
    }

    fun save(config: LauncherConfig) {
        prefs.edit()
            .putInt("columns", config.columns)
            .putInt("rows", config.rows)
            .putInt("home_columns", config.homeColumns)
            .putInt("home_rows", config.homeRows)
            .putBoolean("show_home_dividers", config.showHomeDividers)
            .putBoolean("show_app_dividers", config.showAppDividers)
            .putInt("home_top_style", config.homeTopStyle)
            .putInt("home_top_icon_style", config.homeTopIconStyle)
            .putLong("recent_window_ms", config.recentWindowMs)
            .putBoolean("recent_cancel_back_to_app", config.recentCancelBackToApp)
            .putString("icon_pack", config.iconPack)
            .putBoolean("auto_line_icons", config.autoLineIcons)
            .apply()
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    // --- 全局清理时间戳 ---
    fun markRecentCleared() {
        prefs.edit().putLong("recent_cleared_at", System.currentTimeMillis()).apply()
    }

    fun recentClearedAt(): Long = prefs.getLong("recent_cleared_at", 0L)

    // --- 单个 App 的清理时间戳 ---
    fun markAppCleared(packageName: String) {
        prefs.edit().putLong("recent_cleared_$packageName", System.currentTimeMillis()).apply()
    }

    fun appClearedAt(packageName: String): Long {
        return prefs.getLong("recent_cleared_$packageName", 0L)
    }
}
