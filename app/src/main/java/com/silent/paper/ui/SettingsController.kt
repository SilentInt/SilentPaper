package com.silent.paper.ui

import android.app.Activity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.silent.paper.R
import com.silent.paper.data.LauncherConfig
import com.silent.paper.data.grid.GridSizing

/**
 * Owns the settings rows. It only computes the next config and reports it
 * through [onConfigChanged]; the activity persists the value and refreshes
 * whichever part of the UI is affected.
 */
class SettingsController(
    private val activity: Activity,
    private val currentConfig: () -> LauncherConfig,
    private val onConfigChanged: (LauncherConfig, Int) -> Unit,
    private val showSelector: (title: String, options: List<String>, selectedIndex: Int, onPicked: (Int) -> Unit) -> Unit,
    private val onPickWallpaper: () -> Unit,
    private val onRemoveWallpaper: () -> Unit,
    private val onPickFont: () -> Unit,
    private val onPickClockFont: () -> Unit
) {

    companion object {
        const val AFFECT_APPS = 1
        const val AFFECT_HOME = 2
        const val AFFECT_ICONS = 4
        const val AFFECT_TEXTHOME = 8
        const val AFFECT_WALLPAPER = 16
        const val AFFECT_FONT = 32
        const val AFFECT_UI = 64

        private val RECENT_WINDOW_OPTIONS_MS = longArrayOf(
            0L, 5 * 60_000L, 30 * 60_000L, 60 * 60_000L, 6 * 60 * 60_000L, 24 * 60 * 60_000L
        )

        // 纯文字模式可调范围：0 = 自动（按屏幕比例），步进值见 setupStepper
        private const val FONT_MIN = 12
        private const val FONT_MAX = 56
        private const val SPACING_MIN = 4
        private const val SPACING_MAX = 64
        private const val PADDING_MIN = 8
        private const val PADDING_MAX = 192
        private const val TIME_MIN = 12
        private const val TIME_MAX = 200
        // 时钟周边字号 / 界面字号缩放可调范围
        private const val CLOCKCTX_MIN = 10
        private const val CLOCKCTX_MAX = 32
        private const val UI_SCALE_MIN = 80
        private const val UI_SCALE_MAX = 140
        // 时钟块 / 列表位置可调范围（dp）
        private const val CLOCK_TOP_DEFAULT = 64
        private const val CLOCK_TOP_MIN = 8
        private const val CLOCK_TOP_MAX = 256
        private const val CLOCK_BOTTOM_DEFAULT = 14
        private const val CLOCK_BOTTOM_MIN = 0
        private const val CLOCK_BOTTOM_MAX = 96
        private const val OFFSET_MIN = -96
        private const val OFFSET_MAX = 96
        private const val LIST_TOP_DEFAULT = 36
        private const val LIST_TOP_MIN = 0
        private const val LIST_TOP_MAX = 512
        private const val LIST_BOTTOM_MIN = 0
        private const val LIST_BOTTOM_MAX = 128
        private const val LIST_RIGHT_MIN = 0
        private const val LIST_RIGHT_MAX = 256
        private const val DIM_MIN = 0
        private const val DIM_MAX = 90
        private const val DIM_DEFAULT = 60
    }

    private lateinit var colsValue: TextView
    private lateinit var rowsValue: TextView
    private lateinit var homeColumnsValue: TextView
    private lateinit var homeRowsValue: TextView
    private lateinit var homeDividersValue: TextView
    private lateinit var homeTopStyleValue: TextView
    private lateinit var homeTopIconsValue: TextView
    private lateinit var appDividersValue: TextView
    private lateinit var recentWindowValue: TextView
    private lateinit var recentCancelValue: TextView
    private lateinit var iconPackValue: TextView
    private lateinit var lineIconsValue: TextView

    // 纯文字主页与壁纸
    private lateinit var homeStyleValue: TextView
    private lateinit var textFontValue: TextView
    private lateinit var textSpacingValue: TextView
    private lateinit var textPaddingValue: TextView
    private lateinit var timeSizeValue: TextView
    private lateinit var clockStyleValue: TextView
    private lateinit var clockAlignValue: TextView
    private lateinit var clockContextValue: TextView
    private lateinit var uiScaleValue: TextView
    private lateinit var clockTopValue: TextView
    private lateinit var clockBottomValue: TextView
    private lateinit var clockOffsetValue: TextView
    private lateinit var listTopValue: TextView
    private lateinit var listBottomValue: TextView
    private lateinit var listLeftValue: TextView
    private lateinit var listRightValue: TextView
    private lateinit var listAlignValue: TextView
    private lateinit var sloganValue: TextView
    private lateinit var sloganOnValue: TextView
    private lateinit var textHideBarsValue: TextView
    private lateinit var wallpaperStateValue: TextView
    private lateinit var wallpaperDimOnValue: TextView
    private lateinit var wallpaperDimValue: TextView
    private lateinit var hiddenInDrawerValue: TextView
    private lateinit var appFontValue: TextView
    private lateinit var clockFontValue: TextView
    private lateinit var textWeightValue: TextView

    private lateinit var rowGroupGrid: View
    private lateinit var groupGridChevron: TextView
    private lateinit var groupGridContent: View
    private lateinit var rowGroupIcons: View
    private lateinit var groupIconsChevron: TextView
    private lateinit var groupIconsContent: View
    private lateinit var rowGroupRecent: View
    private lateinit var groupRecentChevron: TextView
    private lateinit var groupRecentContent: View
    private lateinit var rowGroupText: View
    private lateinit var groupTextChevron: TextView
    private lateinit var groupTextContent: View
    private lateinit var rowGroupClock: View
    private lateinit var groupClockChevron: TextView
    private lateinit var groupClockContent: View
    private lateinit var rowGroupFont: View
    private lateinit var groupFontChevron: TextView
    private lateinit var groupFontContent: View
    private lateinit var rowGroupWallpaper: View
    private lateinit var groupWallpaperChevron: TextView
    private lateinit var groupWallpaperContent: View

    fun bind() {
        colsValue = activity.findViewById(R.id.settingsColsValue)
        rowsValue = activity.findViewById(R.id.settingsRowsValue)
        homeColumnsValue = activity.findViewById(R.id.settingsHomeColumnsValue)
        homeRowsValue = activity.findViewById(R.id.settingsHomeRowsValue)
        homeDividersValue = activity.findViewById(R.id.settingsHomeDividersValue)
        homeTopStyleValue = activity.findViewById(R.id.settingsHomeTopStyleValue)
        homeTopIconsValue = activity.findViewById(R.id.settingsHomeTopIconsValue)
        appDividersValue = activity.findViewById(R.id.settingsAppDividersValue)
        recentWindowValue = activity.findViewById(R.id.settingsRecentWindowValue)
        recentCancelValue = activity.findViewById(R.id.settingsRecentCancelValue)
        iconPackValue = activity.findViewById(R.id.settingsIconPackValue)
        lineIconsValue = activity.findViewById(R.id.settingsLineIconsValue)

        rowGroupGrid = activity.findViewById(R.id.rowGroupGrid)
        groupGridChevron = activity.findViewById(R.id.groupGridChevron)
        groupGridContent = activity.findViewById(R.id.groupGridContent)
        rowGroupIcons = activity.findViewById(R.id.rowGroupIcons)
        groupIconsChevron = activity.findViewById(R.id.groupIconsChevron)
        groupIconsContent = activity.findViewById(R.id.groupIconsContent)
        rowGroupRecent = activity.findViewById(R.id.rowGroupRecent)
        groupRecentChevron = activity.findViewById(R.id.groupRecentChevron)
        groupRecentContent = activity.findViewById(R.id.groupRecentContent)

        homeStyleValue = activity.findViewById(R.id.settingsHomeStyleValue)
        textFontValue = activity.findViewById(R.id.settingsTextFontValue)
        textSpacingValue = activity.findViewById(R.id.settingsTextSpacingValue)
        textPaddingValue = activity.findViewById(R.id.settingsTextPaddingValue)
        timeSizeValue = activity.findViewById(R.id.settingsTimeSizeValue)
        clockStyleValue = activity.findViewById(R.id.settingsClockStyleValue)
        clockAlignValue = activity.findViewById(R.id.settingsClockAlignValue)
        clockContextValue = activity.findViewById(R.id.settingsClockCtxValue)
        uiScaleValue = activity.findViewById(R.id.settingsUiScaleValue)
        clockTopValue = activity.findViewById(R.id.settingsClockTopValue)
        clockBottomValue = activity.findViewById(R.id.settingsClockBottomValue)
        clockOffsetValue = activity.findViewById(R.id.settingsClockOffsetValue)
        listTopValue = activity.findViewById(R.id.settingsListTopValue)
        listBottomValue = activity.findViewById(R.id.settingsListBottomValue)
        listLeftValue = activity.findViewById(R.id.settingsListLeftValue)
        listRightValue = activity.findViewById(R.id.settingsListRightValue)
        listAlignValue = activity.findViewById(R.id.settingsListAlignValue)
        sloganValue = activity.findViewById(R.id.settingsSloganValue)
        sloganOnValue = activity.findViewById(R.id.settingsSloganOnValue)
        textHideBarsValue = activity.findViewById(R.id.settingsTextHideBarsValue)
        wallpaperStateValue = activity.findViewById(R.id.settingsWallpaperStateValue)
        wallpaperDimOnValue = activity.findViewById(R.id.settingsWallpaperDimOnValue)
        wallpaperDimValue = activity.findViewById(R.id.settingsWallpaperDimValue)
        hiddenInDrawerValue = activity.findViewById(R.id.settingsHiddenInDrawerValue)
        appFontValue = activity.findViewById(R.id.settingsAppFontValue)
        clockFontValue = activity.findViewById(R.id.settingsClockFontValue)
        textWeightValue = activity.findViewById(R.id.settingsTextWeightValue)

        rowGroupText = activity.findViewById(R.id.rowGroupText)
        groupTextChevron = activity.findViewById(R.id.groupTextChevron)
        groupTextContent = activity.findViewById(R.id.groupTextContent)
        rowGroupClock = activity.findViewById(R.id.rowGroupClock)
        groupClockChevron = activity.findViewById(R.id.groupClockChevron)
        groupClockContent = activity.findViewById(R.id.groupClockContent)
        rowGroupFont = activity.findViewById(R.id.rowGroupFont)
        groupFontChevron = activity.findViewById(R.id.groupFontChevron)
        groupFontContent = activity.findViewById(R.id.groupFontContent)
        rowGroupWallpaper = activity.findViewById(R.id.rowGroupWallpaper)
        groupWallpaperChevron = activity.findViewById(R.id.groupWallpaperChevron)
        groupWallpaperContent = activity.findViewById(R.id.groupWallpaperContent)
    }

    fun setup(initial: LauncherConfig) {
        // 只提供行列数选择；图标大小与文字大小都由屏幕和行列数自动决定。
        activity.findViewById<View>(R.id.rowCols).setOnClickListener {
            val cfg = currentConfig()
            val options = GridSizing.columnOptions(screenColumnCap()).toList()
            showSelector(
                activity.getString(R.string.settings_columns), options.map { it.toString() },
                optionIndex(options, cfg.columns)
            ) { index ->
                val picked = options.getOrNull(index) ?: return@showSelector
                onConfigChanged(currentConfig().copy(columns = picked), AFFECT_APPS)
            }
        }
        activity.findViewById<View>(R.id.rowRows).setOnClickListener {
            val cfg = currentConfig()
            val options = GridSizing.rowOptions(screenRowCap()).toList()
            showSelector(
                activity.getString(R.string.settings_rows), options.map { it.toString() },
                optionIndex(options, cfg.rows)
            ) { index ->
                val picked = options.getOrNull(index) ?: return@showSelector
                onConfigChanged(currentConfig().copy(rows = picked), AFFECT_APPS)
            }
        }
        activity.findViewById<View>(R.id.rowHomeColumns).setOnClickListener {
            val cfg = currentConfig()
            val options = GridSizing.columnOptions(screenColumnCap()).toList()
            showSelector(
                activity.getString(R.string.settings_home_columns), options.map { it.toString() },
                optionIndex(options, cfg.homeColumns)
            ) { index ->
                val picked = options.getOrNull(index) ?: return@showSelector
                onConfigChanged(currentConfig().copy(homeColumns = picked), AFFECT_HOME)
            }
        }
        activity.findViewById<View>(R.id.rowHomeRows).setOnClickListener {
            val cfg = currentConfig()
            val options = GridSizing.homeRowOptions(screenHomeRowCap()).toList()
            showSelector(
                activity.getString(R.string.settings_home_rows), options.map { it.toString() },
                optionIndex(options, cfg.homeRows)
            ) { index ->
                val picked = options.getOrNull(index) ?: return@showSelector
                onConfigChanged(currentConfig().copy(homeRows = picked), AFFECT_HOME)
            }
        }
        activity.findViewById<View>(R.id.rowHomeDividers).setOnClickListener {
            val cfg = currentConfig()
            onConfigChanged(cfg.copy(showHomeDividers = !cfg.showHomeDividers), AFFECT_HOME)
        }
        activity.findViewById<View>(R.id.rowHomeTopStyle).setOnClickListener {
            val cfg = currentConfig()
            val options = listOf(
                activity.getString(R.string.home_top_style_classic),
                activity.getString(R.string.home_top_style_split)
            )
            showSelector(
                activity.getString(R.string.settings_home_top_style), options, cfg.homeTopStyle
            ) { index ->
                onConfigChanged(currentConfig().copy(homeTopStyle = index), AFFECT_HOME)
            }
        }
        activity.findViewById<View>(R.id.rowHomeTopIcons).setOnClickListener {
            val cfg = currentConfig()
            val options = listOf(
                activity.getString(R.string.icon_style_line),
                activity.getString(R.string.icon_style_solid)
            )
            showSelector(
                activity.getString(R.string.settings_home_top_icons), options,
                cfg.homeTopIconStyle.coerceIn(0, 1)
            ) { index ->
                onConfigChanged(currentConfig().copy(homeTopIconStyle = index), AFFECT_HOME)
            }
        }
        activity.findViewById<View>(R.id.rowAppDividers).setOnClickListener {
            val cfg = currentConfig()
            onConfigChanged(cfg.copy(showAppDividers = !cfg.showAppDividers), AFFECT_APPS)
        }
        // 主页隐藏的应用保留在应用页可见
        activity.findViewById<View>(R.id.rowHiddenInDrawer).setOnClickListener {
            val cfg = currentConfig()
            onConfigChanged(
                cfg.copy(drawerShowsHidden = !cfg.drawerShowsHidden),
                AFFECT_APPS
            )
        }
        activity.findViewById<View>(R.id.rowIconPack).setOnClickListener {
            val cfg = currentConfig()
            val packs = IconLoader.findIconPacks(activity.packageManager)
            val options = listOf(activity.getString(R.string.icon_pack_default)) +
                    packs.map { it.label }
            val selected = packs.indexOfFirst { it.packageName == cfg.iconPack }.let { if (it >= 0) it + 1 else 0 }
            showSelector(
                activity.getString(R.string.settings_icon_pack), options, selected
            ) { index ->
                val chosen = if (index <= 0) "" else packs.getOrNull(index - 1)?.packageName ?: ""
                onConfigChanged(currentConfig().copy(iconPack = chosen), AFFECT_ICONS)
            }
        }

        activity.findViewById<View>(R.id.rowLineIcons).setOnClickListener {
            val cfg = currentConfig()
            onConfigChanged(cfg.copy(autoLineIcons = !cfg.autoLineIcons), AFFECT_ICONS)
        }
        activity.findViewById<View>(R.id.rowRecentWindow).setOnClickListener {
            val cfg = currentConfig()
            val options = RECENT_WINDOW_OPTIONS_MS.map { activity.getString(windowLabel(it)) }
            showSelector(
                activity.getString(R.string.settings_recent_recent_only), options,
                RECENT_WINDOW_OPTIONS_MS.indexOf(cfg.recentWindowMs)
            ) { index ->
                onConfigChanged(currentConfig().copy(recentWindowMs = RECENT_WINDOW_OPTIONS_MS[index]), 0)
            }
        }
        activity.findViewById<View>(R.id.rowRecentCancel).setOnClickListener {
            val cfg = currentConfig()
            val options = listOf(
                activity.getString(R.string.recent_cancel_back_to_app),
                activity.getString(R.string.recent_cancel_back_to_home)
            )
            showSelector(
                activity.getString(R.string.settings_recent_cancel), options,
                if (cfg.recentCancelBackToApp) 0 else 1
            ) { index ->
                onConfigChanged(currentConfig().copy(recentCancelBackToApp = index == 0), 0)
            }
        }

        // ---------------- 纯文字主页 ----------------
        activity.findViewById<View>(R.id.rowHomeStyle).setOnClickListener {
            val cfg = currentConfig()
            val options = listOf(
                activity.getString(R.string.home_style_grid),
                activity.getString(R.string.home_style_text)
            )
            showSelector(
                activity.getString(R.string.settings_home_style), options,
                cfg.homeStyle.coerceIn(0, 1)
            ) { index ->
                onConfigChanged(currentConfig().copy(homeStyle = index), AFFECT_HOME)
            }
        }
        setupStepper(
            R.id.settingsTimeSizeMinus, R.id.settingsTimeSizePlus, R.id.settingsTimeSizeValue,
            TIME_MIN, TIME_MAX, 4, ::autoTimeSize,
            { currentConfig().textTimeSize },
            { onConfigChanged(currentConfig().copy(textTimeSize = it), AFFECT_TEXTHOME) }
        )

        // ---------------- 时钟样式与对齐 ----------------
        activity.findViewById<View>(R.id.rowClockStyle).setOnClickListener {
            val cfg = currentConfig()
            val options = listOf(
                activity.getString(R.string.clock_style_split),
                activity.getString(R.string.clock_style_colon),
                activity.getString(R.string.clock_style_stack),
                activity.getString(R.string.clock_style_seg),
                activity.getString(R.string.clock_style_pill),
                activity.getString(R.string.clock_style_flip),
                activity.getString(R.string.clock_style_round),
                activity.getString(R.string.clock_style_vsplit),
                activity.getString(R.string.clock_style_horizontal),
                activity.getString(R.string.clock_style_outline),
                activity.getString(R.string.clock_style_analog),
                activity.getString(R.string.clock_style_matrix)
            )
            showSelector(
                activity.getString(R.string.settings_clock_style), options,
                cfg.clockStyle.coerceIn(0, 11)
            ) { index ->
                onConfigChanged(currentConfig().copy(clockStyle = index), AFFECT_TEXTHOME)
            }
        }
        activity.findViewById<View>(R.id.rowClockAlign).setOnClickListener {
            val options = listOf(
                activity.getString(R.string.align_left),
                activity.getString(R.string.align_center),
                activity.getString(R.string.align_right)
            )
            showSelector(
                activity.getString(R.string.settings_clock_align), options,
                currentConfig().clockAlign.coerceIn(0, 2)
            ) { index ->
                onConfigChanged(currentConfig().copy(clockAlign = index), AFFECT_TEXTHOME)
            }
        }
        activity.findViewById<View>(R.id.rowListAlign).setOnClickListener {
            val options = listOf(
                activity.getString(R.string.align_left),
                activity.getString(R.string.align_center),
                activity.getString(R.string.align_right)
            )
            showSelector(
                activity.getString(R.string.settings_list_align), options,
                currentConfig().listAlign.coerceIn(0, 2)
            ) { index ->
                onConfigChanged(currentConfig().copy(listAlign = index), AFFECT_TEXTHOME)
            }
        }

        // ---------------- 时钟块位置 ----------------
        setupStepper(
            R.id.settingsClockTopMinus, R.id.settingsClockTopPlus, R.id.settingsClockTopValue,
            CLOCK_TOP_MIN, CLOCK_TOP_MAX, 4, { CLOCK_TOP_DEFAULT },
            { currentConfig().clockTop },
            { onConfigChanged(currentConfig().copy(clockTop = it), AFFECT_TEXTHOME) },
            resetTo = CLOCK_TOP_DEFAULT
        )
        setupStepper(
            R.id.settingsClockBottomMinus, R.id.settingsClockBottomPlus, R.id.settingsClockBottomValue,
            CLOCK_BOTTOM_MIN, CLOCK_BOTTOM_MAX, 2, { CLOCK_BOTTOM_DEFAULT },
            { currentConfig().clockBottom },
            { onConfigChanged(currentConfig().copy(clockBottom = it), AFFECT_TEXTHOME) },
            resetTo = CLOCK_BOTTOM_DEFAULT
        )
        setupStepper(
            R.id.settingsClockOffsetMinus, R.id.settingsClockOffsetPlus, R.id.settingsClockOffsetValue,
            OFFSET_MIN, OFFSET_MAX, 4, { 0 },
            { currentConfig().clockX },
            { onConfigChanged(currentConfig().copy(clockX = it), AFFECT_TEXTHOME) }
        )

        // ---------------- 时钟周边字号（日期/状态行/快捷行/信息行统一） ----------------
        setupStepper(
            R.id.settingsClockCtxMinus, R.id.settingsClockCtxPlus, R.id.settingsClockCtxValue,
            CLOCKCTX_MIN, CLOCKCTX_MAX, 2, { 13 },
            { currentConfig().clockContextSize },
            { onConfigChanged(currentConfig().copy(clockContextSize = it), AFFECT_TEXTHOME) }
        )

        // ---------------- 列表位置 ----------------
        setupStepper(
            R.id.settingsListTopMinus, R.id.settingsListTopPlus, R.id.settingsListTopValue,
            LIST_TOP_MIN, LIST_TOP_MAX, 4, { LIST_TOP_DEFAULT },
            { currentConfig().listTop },
            { onConfigChanged(currentConfig().copy(listTop = it), AFFECT_TEXTHOME) },
            resetTo = LIST_TOP_DEFAULT
        )
        setupStepper(
            R.id.settingsListBottomMinus, R.id.settingsListBottomPlus, R.id.settingsListBottomValue,
            LIST_BOTTOM_MIN, LIST_BOTTOM_MAX, 4, { 0 },
            { currentConfig().listBottom },
            { onConfigChanged(currentConfig().copy(listBottom = it), AFFECT_TEXTHOME) }
        )
        setupStepper(
            R.id.settingsListLeftMinus, R.id.settingsListLeftPlus, R.id.settingsListLeftValue,
            OFFSET_MIN, OFFSET_MAX, 4, { 0 },
            { currentConfig().listLeft },
            { onConfigChanged(currentConfig().copy(listLeft = it), AFFECT_TEXTHOME) }
        )
        setupStepper(
            R.id.settingsListRightMinus, R.id.settingsListRightPlus, R.id.settingsListRightValue,
            LIST_RIGHT_MIN, LIST_RIGHT_MAX, 4, { 0 },
            { currentConfig().listRight },
            { onConfigChanged(currentConfig().copy(listRight = it), AFFECT_TEXTHOME) }
        )

        // ---------------- 列表文字与整页边距 ----------------
        setupStepper(
            R.id.settingsTextFontMinus, R.id.settingsTextFontPlus, R.id.settingsTextFontValue,
            FONT_MIN, FONT_MAX, 2, ::autoFontSize,
            { currentConfig().textFontSize },
            { onConfigChanged(currentConfig().copy(textFontSize = it), AFFECT_TEXTHOME) }
        )
        setupStepper(
            R.id.settingsTextSpacingMinus, R.id.settingsTextSpacingPlus, R.id.settingsTextSpacingValue,
            SPACING_MIN, SPACING_MAX, 4, ::autoSpacing,
            { currentConfig().textLineSpacing },
            { onConfigChanged(currentConfig().copy(textLineSpacing = it), AFFECT_TEXTHOME) }
        )
        setupStepper(
            R.id.settingsTextPaddingMinus, R.id.settingsTextPaddingPlus, R.id.settingsTextPaddingValue,
            PADDING_MIN, PADDING_MAX, 8, ::autoPadding,
            { currentConfig().textPadding },
            { onConfigChanged(currentConfig().copy(textPadding = it), AFFECT_TEXTHOME) }
        )

        activity.findViewById<View>(R.id.rowSlogan).setOnClickListener {
            showSloganDialog()
        }
        activity.findViewById<View>(R.id.rowSloganOn).setOnClickListener {
            val cfg = currentConfig()
            onConfigChanged(
                cfg.copy(homeSloganOn = !cfg.homeSloganOn),
                AFFECT_TEXTHOME
            )
        }
        activity.findViewById<View>(R.id.rowTextHideBars).setOnClickListener {
            val cfg = currentConfig()
            onConfigChanged(
                cfg.copy(textHomeHideBars = !cfg.textHomeHideBars),
                AFFECT_TEXTHOME
            )
        }

        // ---------------- 壁纸 ----------------
        activity.findViewById<View>(R.id.rowWallpaperPick).setOnClickListener {
            onPickWallpaper()
        }
        activity.findViewById<View>(R.id.rowWallpaperDimOn).setOnClickListener {
            val cfg = currentConfig()
            onConfigChanged(
                cfg.copy(wallpaperDimOn = !cfg.wallpaperDimOn),
                AFFECT_WALLPAPER
            )
        }
        setupStepper(
            R.id.settingsWallpaperDimMinus, R.id.settingsWallpaperDimPlus, R.id.settingsWallpaperDimValue,
            DIM_MIN, DIM_MAX, 10, { 0 },
            { currentConfig().wallpaperDim },
            { onConfigChanged(currentConfig().copy(wallpaperDim = it), AFFECT_WALLPAPER) },
            resetTo = DIM_DEFAULT,
            onValueClick = { if (currentConfig().wallpaperDim != DIM_DEFAULT) onConfigChanged(currentConfig().copy(wallpaperDim = DIM_DEFAULT), AFFECT_WALLPAPER) }
        )
        activity.findViewById<View>(R.id.rowWallpaperRemove).setOnClickListener {
            if (currentConfig().wallpaperPath.isBlank()) {
                android.widget.Toast.makeText(activity, activity.getString(R.string.wallpaper_none), android.widget.Toast.LENGTH_SHORT).show()
            } else {
                onRemoveWallpaper()
            }
        }

        // ---------------- APP 字体 ----------------
        activity.findViewById<View>(R.id.rowAppFont).setOnClickListener {
            onPickFont()
        }
        activity.findViewById<View>(R.id.rowClockFont).setOnClickListener {
            onPickClockFont()
        }

        // ---------------- 字体粗细（全局描边加粗） ----------------
        activity.findViewById<View>(R.id.rowTextWeight).setOnClickListener {
            val options = listOf(
                activity.getString(R.string.weight_normal),
                activity.getString(R.string.weight_bold),
                activity.getString(R.string.weight_heavy),
                activity.getString(R.string.weight_ultra)
            )
            showSelector(
                activity.getString(R.string.settings_text_weight), options,
                currentConfig().textWeight.coerceIn(0, 3)
            ) { index ->
                onConfigChanged(currentConfig().copy(textWeight = index), AFFECT_FONT)
            }
        }

        // ---------------- 界面字号（全局缩放，改后 LauncherActivity recreate） ----------------
        setupStepper(
            R.id.settingsUiScaleMinus, R.id.settingsUiScalePlus, R.id.settingsUiScaleValue,
            UI_SCALE_MIN, UI_SCALE_MAX, 10, { 100 },
            { currentConfig().uiFontScale },
            { onConfigChanged(currentConfig().copy(uiFontScale = it), AFFECT_UI) },
            resetTo = 100
        )

        // 分组折叠逻辑
        rowGroupGrid.setOnClickListener {
            toggleGroup(groupGridContent, groupGridChevron)
        }
        rowGroupClock.setOnClickListener {
            toggleGroup(groupClockContent, groupClockChevron)
        }
        rowGroupFont.setOnClickListener {
            toggleGroup(groupFontContent, groupFontChevron)
        }
        rowGroupIcons.setOnClickListener {
            toggleGroup(groupIconsContent, groupIconsChevron)
        }
        rowGroupRecent.setOnClickListener {
            toggleGroup(groupRecentContent, groupRecentChevron)
        }
        rowGroupText.setOnClickListener {
            toggleGroup(groupTextContent, groupTextChevron)
        }
        rowGroupWallpaper.setOnClickListener {
            toggleGroup(groupWallpaperContent, groupWallpaperChevron)
        }

        activity.findViewById<View>(R.id.settingsReset).setOnClickListener {
            showResetDialog()
        }

        collapseAllGroups()
        updateValues(initial)
    }

    fun updateValues(config: LauncherConfig) {
        // 显示实际生效的行列数：优先用网格已测量的真实尺寸（与桌面完全一致），
        // 网格尚未布局时才退回按屏幕尺寸估算。
        val appGrid = activity.findViewById<View>(R.id.gridApps)
        val homeGrid = activity.findViewById<View>(R.id.gridHome)
        val density = activity.resources.displayMetrics.density
        val appW = gridWidthDp(appGrid, density, 48f)
        val appH = gridHeightDp(appGrid, density, screenHeightDp())
        val homeW = gridWidthDp(homeGrid, density, 52f)
        val homeH = gridHeightDp(homeGrid, density, (screenHeightDp() - 240f).coerceAtLeast(120f))
        colsValue.text = GridSizing.resolveColumns(config.columns, appW, GridSizing.columnCap(appW)).toString()
        rowsValue.text = GridSizing.resolveRows(config.rows, appH, GridSizing.rowCap(appH)).toString()
        homeColumnsValue.text = GridSizing.resolveColumns(config.homeColumns, homeW, GridSizing.columnCap(homeW)).toString()
        homeRowsValue.text = GridSizing.resolveHomeRows(config.homeRows, homeH, GridSizing.homeRowCap(homeH)).toString()
        homeDividersValue.text = activity.getString(if (config.showHomeDividers) R.string.divider_on else R.string.divider_off)
        homeTopStyleValue.text = activity.getString(
            if (config.homeTopStyle == 1) R.string.home_top_style_split else R.string.home_top_style_classic
        )
        homeTopIconsValue.text = activity.getString(
            if (config.homeTopIconStyle == 1) R.string.icon_style_solid else R.string.icon_style_line
        )
        appDividersValue.text = activity.getString(if (config.showAppDividers) R.string.divider_on else R.string.divider_off)
        recentWindowValue.text = activity.getString(windowLabel(config.recentWindowMs))
        recentCancelValue.text = activity.getString(if (config.recentCancelBackToApp) R.string.recent_cancel_back_to_app else R.string.recent_cancel_back_to_home)
        iconPackValue.text = iconPackLabel(config.iconPack)
        lineIconsValue.text = activity.getString(if (config.autoLineIcons) R.string.divider_on else R.string.divider_off)

        homeStyleValue.text = activity.getString(
            if (config.homeStyle == 1) R.string.home_style_text else R.string.home_style_grid
        )
        val autoLabel = activity.getString(R.string.settings_auto)
        val followLabel = activity.getString(R.string.settings_follow)
        textFontValue.text = if (config.textFontSize == 0) autoLabel else "${config.textFontSize}sp"
        textSpacingValue.text = if (config.textLineSpacing == 0) autoLabel else "${config.textLineSpacing}dp"
        textPaddingValue.text = if (config.textPadding == 0) autoLabel else "${config.textPadding}dp"
        timeSizeValue.text = if (config.textTimeSize == 0) autoLabel else "${config.textTimeSize}sp"
        clockContextValue.text = if (config.clockContextSize == 0) autoLabel else "${config.clockContextSize}sp"
        uiScaleValue.text = "${config.uiFontScale.coerceIn(UI_SCALE_MIN, UI_SCALE_MAX)}%"
        clockStyleValue.text = activity.getString(
            listOf(
                R.string.clock_style_split, R.string.clock_style_colon, R.string.clock_style_stack,
                R.string.clock_style_seg, R.string.clock_style_pill, R.string.clock_style_flip,
                R.string.clock_style_round, R.string.clock_style_vsplit, R.string.clock_style_horizontal,
                R.string.clock_style_outline, R.string.clock_style_analog, R.string.clock_style_matrix
            )[config.clockStyle.coerceIn(0, 11)]
        )
        clockAlignValue.text = activity.getString(
            listOf(R.string.align_left, R.string.align_center, R.string.align_right)[config.clockAlign.coerceIn(0, 2)]
        )
        clockTopValue.text = "${config.clockTop}dp"
        clockBottomValue.text = "${config.clockBottom}dp"
        clockOffsetValue.text = offsetLabel(config.clockX, followLabel)
        listTopValue.text = "${config.listTop}dp"
        listBottomValue.text = "${config.listBottom}dp"
        listLeftValue.text = offsetLabel(config.listLeft, followLabel)
        listRightValue.text = if (config.listRight == 0) followLabel else "${config.listRight}dp"
        listAlignValue.text = activity.getString(
            listOf(R.string.align_left, R.string.align_center, R.string.align_right)[config.listAlign.coerceIn(0, 2)]
        )
        sloganValue.text =
            if (config.homeSlogan.isBlank()) activity.getString(R.string.settings_slogan_hint)
            else config.homeSlogan
        sloganOnValue.text = activity.getString(
            if (config.homeSloganOn) R.string.divider_on else R.string.divider_off
        )
        textHideBarsValue.text = activity.getString(
            if (config.textHomeHideBars) R.string.divider_on else R.string.divider_off
        )
        hiddenInDrawerValue.text = activity.getString(
            if (config.drawerShowsHidden) R.string.divider_on else R.string.divider_off
        )
        wallpaperStateValue.text = activity.getString(
            if (config.wallpaperPath.isBlank()) R.string.wallpaper_none else R.string.divider_on
        )
        wallpaperDimOnValue.text = activity.getString(
            if (config.wallpaperDimOn) R.string.divider_on else R.string.divider_off
        )
        wallpaperDimValue.text = "${config.wallpaperDim}%"
        appFontValue.text =
            config.appFontName.ifBlank { activity.getString(R.string.icon_pack_default) }
        clockFontValue.text =
            config.clockFontName.ifBlank { activity.getString(R.string.clock_font_follow) }
        textWeightValue.text = activity.getString(
            listOf(
                R.string.weight_normal, R.string.weight_bold,
                R.string.weight_heavy, R.string.weight_ultra
            )[config.textWeight.coerceIn(0, 3)]
        )
    }

    // ------------------------------------------------------------------ stepper helpers

    /** 「自动」基准（与 LauncherActivity.resolvedXxx 同公式）：首次步进从此起算。 */
    private fun autoFontSize(): Int {
        val dm = activity.resources.displayMetrics
        val sp = dm.heightPixels * 0.024f / 0.9f / dm.density
        return ((sp / 2f).toInt() * 2).coerceIn(FONT_MIN, FONT_MAX)
    }

    private fun autoSpacing(): Int =
        ((autoFontSize() * 0.85f / 4f).toInt() * 4).coerceIn(SPACING_MIN, SPACING_MAX)

    private fun autoPadding(): Int {
        val dm = activity.resources.displayMetrics
        val pad = dm.widthPixels * 0.025f / dm.density
        return ((pad / 8f).toInt() * 8).coerceIn(PADDING_MIN, PADDING_MAX)
    }

    /** 偏移量显示：0 = 跟随（页边距），正值带 +，负值自带 -。 */
    private fun offsetLabel(v: Int, followLabel: String): String =
        when {
            v == 0 -> followLabel
            v > 0 -> "+${v}dp"
            else -> "${v}dp"
        }

    /** 大时间自动字号：时间块收在屏宽 30% 内（与 LauncherActivity 同公式）。 */
    private fun autoTimeSize(): Int {
        val dm = activity.resources.displayMetrics
        val paddingPx = autoPadding() * dm.density
        val sepBlockPx = 42f * dm.density
        val budget = dm.widthPixels * 0.30f - paddingPx - sepBlockPx
        val emPx = budget / 2.12f
        val sp = emPx / dm.density
        return ((sp / 4f).toInt() * 4).coerceIn(TIME_MIN, TIME_MAX)
    }

    /**
     * 通用「− 值 +」步进行：
     * - 0 仅在「自动基准非 0」时视为未设置并回退到 [auto]；偏移类行基准为 0，
     *   当前值（含负值）直接作为步进起点；
     * - 单击 ±[step]，长按一次 ±[step]×8 快速逼近，大范围不再按到手酸；
     * - 点击中间数值恢复默认（[resetTo]，默认 0 = 自动/跟随；[onValueClick] 可覆盖）。
     */
    private fun setupStepper(
        minusId: Int,
        plusId: Int,
        valueId: Int,
        min: Int,
        max: Int,
        step: Int,
        auto: () -> Int,
        read: () -> Int,
        write: (Int) -> Unit,
        resetTo: Int = 0,
        onValueClick: (() -> Unit)? = null
    ) {
        fun adjust(delta: Int) {
            val current = read()
            val base = if (current == 0 && auto() != 0) auto() else current
            val next = (base + delta).coerceIn(min, max)
            if (next != current) write(next)
        }
        val minus = activity.findViewById<View>(minusId)
        val plus = activity.findViewById<View>(plusId)
        minus.setOnClickListener { adjust(-step) }
        plus.setOnClickListener { adjust(step) }
        minus.setOnLongClickListener { adjust(-step * 8); true }
        plus.setOnLongClickListener { adjust(step * 8); true }
        activity.findViewById<View>(valueId).setOnClickListener {
            if (onValueClick != null) onValueClick()
            else if (read() != resetTo) write(resetTo)
        }
    }

    // ------------------------------------------------------------------ slogan dialog

    private fun showSloganDialog() {
        val dialog = android.app.Dialog(activity)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        dialog.window?.apply {
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setWindowAnimations(0)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

        val dp = { v: Int -> (v * activity.resources.displayMetrics.density).toInt() }

        val root = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#FAFAF7")) // @color/paper
                setStroke(dp(1), android.graphics.Color.parseColor("#1A1A1A")) // @color/ink
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(24), dp(24), dp(24), dp(16))
            layoutParams = android.view.ViewGroup.LayoutParams(
                (activity.resources.displayMetrics.widthPixels * 0.85).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(android.widget.TextView(activity).apply {
            text = activity.getString(R.string.settings_slogan)
            textSize = 20f
            setTextColor(android.graphics.Color.parseColor("#1A1A1A"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(16))
        })

        val input = android.widget.EditText(activity).apply {
            setText(currentConfig().homeSlogan)
            hint = activity.getString(R.string.settings_slogan_hint)
            textSize = 15f
            setTextColor(activity.resources.getColor(R.color.ink))
            setHintTextColor(activity.resources.getColor(R.color.gray))
            isSingleLine = true
            background = android.graphics.drawable.GradientDrawable().apply {
                setStroke(dp(1), activity.resources.getColor(R.color.hairline))
                cornerRadius = dp(4).toFloat()
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        root.addView(input, android.widget.LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(24) })

        val btnRow = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
        }

        btnRow.addView(android.widget.TextView(activity).apply {
            text = activity.getString(R.string.slogan_cancel)
            textSize = 16f
            setTextColor(activity.resources.getColor(R.color.gray))
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { dialog.dismiss() }
        })

        btnRow.addView(android.widget.TextView(activity).apply {
            text = activity.getString(R.string.slogan_confirm)
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#1A1A1A"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(16), dp(8), 0, dp(8))
            setOnClickListener {
                dialog.dismiss()
                val next = input.text.toString().trim()
                if (next != currentConfig().homeSlogan) {
                    onConfigChanged(currentConfig().copy(homeSlogan = next), AFFECT_TEXTHOME)
                }
            }
        })

        root.addView(btnRow)
        dialog.setContentView(root)
        dialog.show()
        input.post {
            runCatching {
                input.setSelection(input.text.length)
                input.requestFocus()
            }
        }
    }

    private fun showResetDialog() {
        val dialog = android.app.Dialog(activity)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        dialog.window?.apply {
            // 清除对话框底部的半透明灰色遮罩
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // 禁用对话框的原生淡入淡出动画
            setWindowAnimations(0)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }

        val dp = { v: Int -> (v * activity.resources.displayMetrics.density).toInt() }

        val root = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#FAFAF7")) // @color/paper
                setStroke(dp(1), android.graphics.Color.parseColor("#1A1A1A")) // @color/ink
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(24), dp(24), dp(24), dp(16))
            layoutParams = android.view.ViewGroup.LayoutParams(
                (activity.resources.displayMetrics.widthPixels * 0.85).toInt(),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        root.addView(android.widget.TextView(activity).apply {
            text = "恢复默认"
            textSize = 20f
            setTextColor(android.graphics.Color.parseColor("#1A1A1A"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(16))
        })

        root.addView(android.widget.TextView(activity).apply {
            text = "确定要将所有设置恢复为默认状态吗？"
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#1A1A1A"))
            setPadding(0, 0, 0, dp(32))
        })

        val btnRow = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
        }

        btnRow.addView(android.widget.TextView(activity).apply {
            text = "取消"
            textSize = 16f
            setTextColor(activity.resources.getColor(R.color.gray)) // 次要操作
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { dialog.dismiss() }
        })

        btnRow.addView(android.widget.TextView(activity).apply {
            text = "确定"
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#1A1A1A"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(dp(16), dp(8), 0, dp(8))
            setOnClickListener {
                dialog.dismiss()
                Toast.makeText(activity, "已恢复默认", Toast.LENGTH_SHORT).show()
                onConfigChanged(LauncherConfig(), AFFECT_APPS or AFFECT_HOME or AFFECT_ICONS or AFFECT_FONT)
            }
        })

        root.addView(btnRow)
        dialog.setContentView(root)
        dialog.show()
    }

    // ------------------------------------------------------------------ groups

    private fun toggleGroup(content: View, chevron: TextView) {
        val expanded = content.visibility == View.VISIBLE
        collapseAllGroups()
        if (!expanded) setGroupExpanded(content, chevron, true)
    }

    private fun collapseAllGroups() {
        setGroupExpanded(groupGridContent, groupGridChevron, false)
        setGroupExpanded(groupIconsContent, groupIconsChevron, false)
        setGroupExpanded(groupRecentContent, groupRecentChevron, false)
        setGroupExpanded(groupTextContent, groupTextChevron, false)
        setGroupExpanded(groupClockContent, groupClockChevron, false)
        setGroupExpanded(groupFontContent, groupFontChevron, false)
        setGroupExpanded(groupWallpaperContent, groupWallpaperChevron, false)
    }

    private fun setGroupExpanded(content: View, chevron: TextView, expanded: Boolean) {
        content.visibility = if (expanded) View.VISIBLE else View.GONE
        chevron.text = activity.getString(if (expanded) R.string.settings_collapse else R.string.settings_expand)
    }

    private fun iconPackLabel(packageName: String): String {
        if (packageName.isBlank()) return activity.getString(R.string.icon_pack_default)
        val label = runCatching {
            val app = activity.packageManager.getApplicationInfo(packageName, 0)
            app.loadLabel(activity.packageManager).toString()
        }.getOrNull()
        return label ?: packageName
    }

    // ------------------------------------------------------------------ 自适应网格辅助

    /** 当前值在选项列表中的下标（不在范围内时取最接近的一项）。 */
    private fun optionIndex(options: List<Int>, value: Int): Int {
        if (options.isEmpty()) return 0
        val direct = options.indexOf(value)
        if (direct >= 0) return direct
        return options.indices.minByOrNull { kotlin.math.abs(options[it] - value) } ?: 0
    }

    private fun screenWidthDp(): Float = activity.resources.displayMetrics.widthPixels /
            activity.resources.displayMetrics.density

    private fun screenHeightDp(): Float = activity.resources.displayMetrics.heightPixels /
            activity.resources.displayMetrics.density

    /** 网格实际宽度（dp）；未布局时按屏幕宽度减边距估算。 */
    private fun gridWidthDp(grid: View?, density: Float, marginsDp: Float): Float {
        val w = grid?.width ?: 0
        if (w > 0) return w / density
        return (screenWidthDp() - marginsDp).coerceAtLeast(120f)
    }

    /** 网格实际高度（dp）；未布局时退回给定估算值。 */
    private fun gridHeightDp(grid: View?, density: Float, fallbackDp: Float): Float {
        val h = grid?.height ?: 0
        if (h > 0) return h / density
        return fallbackDp.coerceAtLeast(120f)
    }

    private fun screenColumnCap(): Int = GridSizing.columnCap(screenWidthDp() - 48f)

    private fun screenRowCap(): Int = GridSizing.rowCap(screenHeightDp())

    private fun screenHomeRowCap(): Int = GridSizing.homeRowCap(screenHeightDp() - 260f)

    private fun windowLabel(ms: Long): Int = when (ms) {
        0L -> R.string.recent_window_off
        5 * 60_000L -> R.string.recent_window_5min
        30 * 60_000L -> R.string.recent_window_30min
        60 * 60_000L -> R.string.recent_window_1h
        6 * 60 * 60_000L -> R.string.recent_window_6h
        24 * 60 * 60_000L -> R.string.recent_window_1d
        else -> R.string.recent_window_off
    }
}
