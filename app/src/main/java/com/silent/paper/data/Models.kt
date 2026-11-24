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
    val autoLineIcons: Boolean = true,      // auto-draw line-style icons（默认开）

    // ---- 纯文字主页 ----
    val homeStyle: Int = 0,                  // 主页样式：0=图标网格, 1=纯文字
    val textFontSize: Int = 0,             // 应用名字号 sp（0=自动，字高≈屏高 2.4%）
    val textLineSpacing: Int = 0,          // 行间距 dp（0=自动，约 0.85× 字号）
    val textPadding: Int = 0,              // 页边距 dp（0=自动，约屏宽 2.5%）
    val textTimeSize: Int = 0,             // 大时间字号 sp（0=自动，时间块收在屏宽 30% 内）
    // 时钟块位置：上/下为绝对 dp；左右偏移为相对页边距的增量（0=跟随，负=向左）
    val clockTop: Int = 64,
    val clockBottom: Int = 14,
    val clockX: Int = 0,
    // 时钟样式：0=分栏竖线（默认），1=冒号，2=堆叠（时上分下），3=数码管，4=胶囊反色，
    // 5=翻页，6=圆环，7=竖线分栏，8=横排，9=线框，10=表盘，11=点阵（后七种移植自 inkOS）
    val clockStyle: Int = 0,
    // 时钟对齐：0=左（默认），1=中，2=右；信息行随时钟对齐
    val clockAlign: Int = 0,
    // 列表对齐：0=左（默认），1=中，2=右（应用名文字锚点）
    val listAlign: Int = 0,
    // 应用列表位置：上/下绝对 dp；左偏移相对页边距（0=跟随）；右边距为额外缩进（0=跟随）
    val listTop: Int = 36,
    val listBottom: Int = 0,
    val listLeft: Int = 0,
    val listRight: Int = 0,
    val homeSlogan: String = "Keep it simple.", // 底部标语内容（空 = 不显示）
    val homeSloganOn: Boolean = true,      // 底部标语显示开关
    val textHomeHideBars: Boolean = false, // 纯文字主页隐藏系统状态栏与导航栏（沉浸式）

    // ---- 应用页 ----
    val drawerShowsHidden: Boolean = false, // 开：主页隐藏的应用仍显示在应用页（置灰）

    // ---- APP 字体 ----
    val appFontPath: String = "",            // 应用私有目录内的字体文件路径，空 = 系统默认
    val appFontName: String = "",            // 展示名（文件名或自定义）
    val clockFontPath: String = "",          // 时钟专用字体路径，空 = 跟随 APP 字体
    val clockFontName: String = "",
    val fontFolderUri: String = "",          // 自选字体文件夹（SAF 树 URI），空 = 未指定；Android 11+ 分区存储下的字体主入口
    val textWeight: Int = 0,                  // 全局字体粗细（描边加粗）：0=原样, 1=加粗, 2=加重, 3=特粗

    val clockContextSize: Int = 0,            // 时钟周边字号 sp（日期/状态行/快捷行/信息行统一；0=各自默认）
    val uiFontScale: Int = 100,                // 界面字号全局缩放 %（80–140；100=标准），改后重建 Activity 生效

    // ---- 壁纸 ----
    val wallpaperPath: String = "",          // 应用私有目录内的壁纸文件路径，空 = 无壁纸
    val wallpaperDimOn: Boolean = true,    // 壁纸淡化开关：关 = 原图灰度直出
    val wallpaperDim: Int = 60               // 淡化程度 %：与纸色混合比例，越大越淡（0..90）
)
