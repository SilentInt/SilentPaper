package com.silent.paper.data

data class LauncherConfig(
    // 0 = 自动（按屏幕大小与行列数计算），>0 = 用户手动指定
    val columns: Int = 0,
    val rows: Int = 0,
    val homeColumns: Int = 0,
    val homeRows: Int = 0,
    val showHomeDividers: Boolean = true,   // home grid only
    val showAppDividers: Boolean = true,    // app drawer grid only
    val homeTopStyle: Int = 0,              // 主页顶部：0=经典居中, 1=左右分栏
    val homeTopIconStyle: Int = 1,          // 顶部状态图标：0=线条, 1=实心
    val recentWindowMs: Long = 30 * 60_000L, // recent tasks: 0 = all, else time window
    val recentCancelBackToApp: Boolean = true, // recent tasks cancel action
    val iconPack: String = "",              // empty = system default icons
    val autoLineIcons: Boolean = true      // auto-draw line-style icons（默认开）
)
