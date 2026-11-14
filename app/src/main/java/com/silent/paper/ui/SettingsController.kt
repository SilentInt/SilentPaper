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
    private val showSelector: (title: String, options: List<String>, selectedIndex: Int, onPicked: (Int) -> Unit) -> Unit
) {

    companion object {
        const val AFFECT_APPS = 1
        const val AFFECT_HOME = 2
        const val AFFECT_ICONS = 4

        private val RECENT_WINDOW_OPTIONS_MS = longArrayOf(
            0L, 5 * 60_000L, 30 * 60_000L, 60 * 60_000L, 6 * 60 * 60_000L, 24 * 60 * 60_000L
        )
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

    private lateinit var rowGroupGrid: View
    private lateinit var groupGridChevron: TextView
    private lateinit var groupGridContent: View
    private lateinit var rowGroupIcons: View
    private lateinit var groupIconsChevron: TextView
    private lateinit var groupIconsContent: View
    private lateinit var rowGroupRecent: View
    private lateinit var groupRecentChevron: TextView
    private lateinit var groupRecentContent: View

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

        // 分组折叠逻辑
        rowGroupGrid.setOnClickListener {
            toggleGroup(groupGridContent, groupGridChevron)
        }
        rowGroupIcons.setOnClickListener {
            toggleGroup(groupIconsContent, groupIconsChevron)
        }
        rowGroupRecent.setOnClickListener {
            toggleGroup(groupRecentContent, groupRecentChevron)
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
    }

    // ------------------------------------------------------------------ reset dialog

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
            setTextColor(android.graphics.Color.parseColor("#8A8A8A")) // @color/gray
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
                onConfigChanged(LauncherConfig(), AFFECT_APPS or AFFECT_HOME or AFFECT_ICONS)
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
