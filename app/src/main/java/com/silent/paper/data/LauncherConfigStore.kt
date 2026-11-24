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
            autoLineIcons = prefs.getBoolean("auto_line_icons", true),
            homeStyle = prefs.getInt("home_style", 0),
            textFontSize = prefs.getInt("text_font_size", 0),
            textLineSpacing = prefs.getInt("text_line_spacing", 0),
            textPadding = prefs.getInt("text_padding", 0),
            textTimeSize = prefs.getInt("text_time_size", 0),
            clockTop = prefs.getInt("clock_top", 64),
            clockBottom = prefs.getInt("clock_bottom", 14),
            clockX = prefs.getInt("clock_x", 0),
            clockStyle = prefs.getInt("clock_style", 0),
            clockAlign = prefs.getInt("clock_align", 0),
            listAlign = prefs.getInt("list_align", 0),
            listTop = prefs.getInt("list_top", 36),
            listBottom = prefs.getInt("list_bottom", 0),
            listLeft = prefs.getInt("list_left", 0),
            listRight = prefs.getInt("list_right", 0),
            homeSlogan = prefs.getString("home_slogan", "Keep it simple.") ?: "",
            homeSloganOn = prefs.getBoolean("home_slogan_on", true),
            textHomeHideBars = prefs.getBoolean("text_home_hide_bars", false),
            clockContextSize = prefs.getInt("clock_context_size", 0),
            uiFontScale = prefs.getInt("ui_font_scale", 100),
            drawerShowsHidden = prefs.getBoolean("drawer_shows_hidden", false),
            appFontPath = prefs.getString("app_font_path", "") ?: "",
            appFontName = prefs.getString("app_font_name", "") ?: "",
            clockFontPath = prefs.getString("clock_font_path", "") ?: "",
            clockFontName = prefs.getString("clock_font_name", "") ?: "",
            fontFolderUri = prefs.getString("font_folder_uri", "") ?: "",
            textWeight = prefs.getInt("text_weight", 0),
            wallpaperPath = prefs.getString("wallpaper_path", "") ?: "",
            wallpaperDimOn = prefs.getBoolean("wallpaper_dim_on", true),
            wallpaperDim = prefs.getInt("wallpaper_dim", 60)
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
            .putInt("home_style", config.homeStyle)
            .putInt("text_font_size", config.textFontSize)
            .putInt("text_line_spacing", config.textLineSpacing)
            .putInt("text_padding", config.textPadding)
            .putInt("text_time_size", config.textTimeSize)
            .putInt("clock_top", config.clockTop)
            .putInt("clock_bottom", config.clockBottom)
            .putInt("clock_x", config.clockX)
            .putInt("clock_style", config.clockStyle)
            .putInt("clock_align", config.clockAlign)
            .putInt("list_align", config.listAlign)
            .putInt("list_top", config.listTop)
            .putInt("list_bottom", config.listBottom)
            .putInt("list_left", config.listLeft)
            .putInt("list_right", config.listRight)
            .putString("home_slogan", config.homeSlogan)
            .putBoolean("home_slogan_on", config.homeSloganOn)
            .putBoolean("text_home_hide_bars", config.textHomeHideBars)
            .putInt("clock_context_size", config.clockContextSize)
            .putInt("ui_font_scale", config.uiFontScale)
            .putBoolean("drawer_shows_hidden", config.drawerShowsHidden)
            .putString("app_font_path", config.appFontPath)
            .putString("app_font_name", config.appFontName)
            .putString("clock_font_path", config.clockFontPath)
            .putString("clock_font_name", config.clockFontName)
            .putString("font_folder_uri", config.fontFolderUri)
            .putInt("text_weight", config.textWeight)
            .putString("wallpaper_path", config.wallpaperPath)
            .putBoolean("wallpaper_dim_on", config.wallpaperDimOn)
            .putInt("wallpaper_dim", config.wallpaperDim)
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
