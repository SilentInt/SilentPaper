package com.silent.paper.ui

import android.app.Activity
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.silent.paper.R
import com.silent.paper.data.LauncherConfig

/**
 * 主页时钟区：12 种样式变体 + 电池/WiFi/蓝牙快捷行 + 信息行。
 *
 * 时钟区在图标主页与纯文字主页共用：[place] 按主页形态把它重挂到
 * 顶部原生时间槽位（图标主页）或 pageHome 顶层（纯文字主页），
 * [apply] 负责样式、字号、对齐与位置。
 */
class ClockAreaController(
    private val activity: Activity,
    private val pageHome: View,
    private val config: () -> LauncherConfig,
    /** 纯文字主页的页边距 dp，用于时钟宽度预算与左右偏移。 */
    private val textPaddingDp: () -> Int
) {

    private lateinit var homeClockArea: LinearLayout
    private lateinit var homeQuickRow: View
    private lateinit var homeInfoLine: TextView

    private lateinit var homeBigTimeRow: View
    private lateinit var homeBigTimeHour: TextView
    private lateinit var homeBigTimeMin: TextView
    private lateinit var homeBigTimeSep: View
    private lateinit var homeBigTimeColon: TextView
    private lateinit var homeBigTimeStack: View
    private lateinit var homeBigTimeHourStack: TextView
    private lateinit var homeBigTimeMinStack: TextView
    private lateinit var homeClockSeg: SevenSegmentClockView
    private lateinit var homeClockFlip: FlipClockView
    private lateinit var homeClockRound: RoundClockView
    private lateinit var homeClockVSplit: View
    private lateinit var homeVSplitHour: TextView
    private lateinit var homeVSplitMin: TextView
    private lateinit var homeVSplitDate: TextView
    private lateinit var homeVSplitBatt: TextView
    private lateinit var homeClockHorizontal: View
    private lateinit var homeHorizTime: TextView
    private lateinit var homeHorizDate: TextView
    private lateinit var homeHorizBatt: TextView
    private lateinit var homeClockAnalog: AnalogClockView
    private lateinit var homeClockMatrix: MatrixClockView

    // 落位锚点：经典/分栏顶部结构与状态行
    private lateinit var homeTopClassic: ViewGroup
    private lateinit var homeTopSplit: ViewGroup
    private lateinit var homeTopSplitLeftInner: ViewGroup
    private lateinit var statusBarRow: View

    /** 当前已设置的时钟行背景（-1 = 未设置），避免每次重设引发墨水屏闪刷。 */
    private var appliedClockStyleBg = -1

    /** 所有时钟变体：同一时刻只显示一个，也共享位置与点击。 */
    private val variants: List<View>
        get() = listOf(
            homeBigTimeRow, homeBigTimeStack, homeClockSeg, homeClockFlip, homeClockRound,
            homeClockVSplit, homeClockHorizontal, homeClockAnalog, homeClockMatrix
        )

    fun bind() {
        homeClockArea = activity.findViewById(R.id.homeClockArea)
        homeQuickRow = activity.findViewById(R.id.homeQuickRow)
        homeInfoLine = activity.findViewById(R.id.homeInfoLine)
        homeBigTimeRow = activity.findViewById(R.id.homeBigTimeRow)
        homeBigTimeHour = activity.findViewById(R.id.homeBigTimeHour)
        homeBigTimeMin = activity.findViewById(R.id.homeBigTimeMin)
        homeBigTimeSep = activity.findViewById(R.id.homeBigTimeSep)
        homeBigTimeColon = activity.findViewById(R.id.homeBigTimeColon)
        homeBigTimeStack = activity.findViewById(R.id.homeBigTimeStack)
        homeBigTimeHourStack = activity.findViewById(R.id.homeBigTimeHourStack)
        homeBigTimeMinStack = activity.findViewById(R.id.homeBigTimeMinStack)
        homeClockSeg = activity.findViewById(R.id.homeClockSeg)
        homeClockFlip = activity.findViewById(R.id.homeClockFlip)
        homeClockRound = activity.findViewById(R.id.homeClockRound)
        homeClockVSplit = activity.findViewById(R.id.homeClockVSplit)
        homeVSplitHour = activity.findViewById(R.id.homeVSplitHour)
        homeVSplitMin = activity.findViewById(R.id.homeVSplitMin)
        homeVSplitDate = activity.findViewById(R.id.homeVSplitDate)
        homeVSplitBatt = activity.findViewById(R.id.homeVSplitBatt)
        homeClockHorizontal = activity.findViewById(R.id.homeClockHorizontal)
        homeHorizTime = activity.findViewById(R.id.homeHorizTime)
        homeHorizDate = activity.findViewById(R.id.homeHorizDate)
        homeHorizBatt = activity.findViewById(R.id.homeHorizBatt)
        homeClockAnalog = activity.findViewById(R.id.homeClockAnalog)
        homeClockMatrix = activity.findViewById(R.id.homeClockMatrix)
        homeTopClassic = activity.findViewById(R.id.homeTopClassic)
        homeTopSplit = activity.findViewById(R.id.homeTopSplit)
        homeTopSplitLeftInner = activity.findViewById(R.id.homeTopSplitLeftInner)
        statusBarRow = activity.findViewById(R.id.statusBar)
        hoist()
    }

    fun setOnClockClickListener(listener: () -> Unit) {
        for (v in variants) v.setOnClickListener { listener() }
    }

    fun show() {
        if (homeClockArea.visibility != View.VISIBLE) homeClockArea.visibility = View.VISIBLE
    }

    /** 时间/日期/电量刷新：12 种样式与信息行同步。 */
    fun update(hh: String, mm: String, date: String, battery: Int) {
        if (!::homeBigTimeHour.isInitialized) return
        homeBigTimeHour.text = hh
        homeBigTimeMin.text = mm
        homeBigTimeHourStack.text = hh
        homeBigTimeMinStack.text = mm
        homeClockSeg.setTime(hh + mm)
        if (homeInfoLine.text.toString() != date) homeInfoLine.text = date

        val batt = if (battery >= 0) "$battery%" else ""
        homeClockFlip.setTime(hh, mm)
        homeClockRound.setTime(hh, mm)
        homeVSplitHour.text = hh
        homeVSplitMin.text = mm
        homeVSplitDate.text = date
        homeVSplitBatt.text = batt
        homeHorizTime.text = "$hh:$mm"
        homeHorizDate.text = date
        homeHorizBatt.text = batt
        homeClockAnalog.setTime(hh, mm)
        homeClockMatrix.setTime(hh, mm)
        homeClockMatrix.setBattery(battery)
    }

    /** 时钟字体：文本变体直接换字面，自绘变体交给各自 View。 */
    fun applyTypeface(tf: Typeface?) {
        if (!::homeBigTimeHour.isInitialized) return
        if (tf != null) {
            homeBigTimeHour.typeface = tf
            homeBigTimeMin.typeface = tf
            homeBigTimeColon.typeface = tf
            homeBigTimeHourStack.typeface = tf
            homeBigTimeMinStack.typeface = tf
            homeVSplitHour.typeface = tf
            homeVSplitMin.typeface = tf
            homeHorizTime.typeface = tf
        }
        homeClockFlip.setTypeface(tf)
        homeClockRound.setTypeface(tf)
    }

    /** 信息行默认字号（SP，跟随界面字号缩放）；「时钟周边字号」开启后会覆盖它。 */
    fun applyInfoTextSize(sp: Float) {
        if (!::homeInfoLine.isInitialized) return
        homeInfoLine.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    }

    /** 时钟周边字号作用于信息行（DIP，不受界面字号缩放影响）。 */
    fun applyContextTextSize(sp: Int) {
        if (!::homeInfoLine.isInitialized || sp <= 0) return
        homeInfoLine.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp.toFloat())
    }

    /** 落位 + 内衬清零 + 样式/字号/对齐。 */
    fun apply() {
        if (!::homeClockArea.isInitialized) return
        place()
        if (homeClockArea.paddingLeft != 0 || homeClockArea.paddingRight != 0) {
            homeClockArea.setPadding(0, 0, 0, 0)
        }
        applyStyleAndAlign()
    }

    /**
     * 把 12 样式时钟变体与信息行从 homeTextMode 重挂到 homeClockArea，
     * 顺序：变体 → WiFi/蓝牙快捷行（XML 原生）→ 信息行。
     */
    private fun hoist() {
        if (homeClockArea.childCount > 1) return
        for (v in variants) {
            (v.parent as? ViewGroup)?.removeView(v)
            homeClockArea.addView(v, homeClockArea.indexOfChild(homeQuickRow))
        }
        (homeInfoLine.parent as? ViewGroup)?.removeView(homeInfoLine)
        homeClockArea.addView(homeInfoLine)
    }

    /**
     * 时钟区落位：纯文字主页挂在 pageHome 顶层（页边距、clockTop/clockX/对齐全套生效）；
     * 图标主页挪进顶部原生结构、接管「原时间」槽位——经典居中插在日期与状态行之间，
     * 左右分栏挪进左列。重挂幂等：parent 没变不动。
     */
    private fun place() {
        val textMode = config().homeStyle == 1
        val classic = !textMode && config().homeTopStyle == 0
        val parent: ViewGroup = when {
            textMode -> pageHome as? ViewGroup ?: return
            classic -> homeTopClassic
            else -> homeTopSplitLeftInner
        }
        val linear = parent as? LinearLayout ?: return
        // 经典居中容器吃满宽（子块整体居中）；纯文字 / 分栏左列必须 wrap_content，
        // 整组锚定（gravity/边距）才起作用。
        val desiredW =
            if (classic) ViewGroup.LayoutParams.MATCH_PARENT
            else ViewGroup.LayoutParams.WRAP_CONTENT
        if (homeClockArea.parent !== parent) {
            (homeClockArea.parent as? ViewGroup)?.removeView(homeClockArea)
            val lp = LinearLayout.LayoutParams(desiredW, ViewGroup.LayoutParams.WRAP_CONTENT)
            when {
                classic -> linear.addView(
                    homeClockArea,
                    linear.indexOfChild(statusBarRow).coerceAtLeast(0),
                    lp
                )
                textMode -> linear.addView(
                    homeClockArea,
                    (linear.indexOfChild(homeTopSplit) + 1).coerceAtLeast(0),
                    lp
                )
                else -> linear.addView(homeClockArea, 0, lp)
            }
        } else {
            // 已在目标容器（冷启动 XML 直挂 pageHome）：只纠宽度不重挂。
            val lp = homeClockArea.layoutParams
            if (lp.width != desiredW) {
                lp.width = desiredW
                homeClockArea.layoutParams = lp
            }
        }
    }

    /**
     * 时钟样式（0 分栏 / 1 冒号 / 2 堆叠 / 3 数码管 / 4 胶囊 / 5 翻页 /
     * 6 圆环 / 7 竖线 / 8 横排 / 9 线框 / 10 表盘 / 11 点阵）切换与对齐。
     */
    private fun applyStyleAndAlign() {
        val style = config().clockStyle.coerceIn(0, 11)
        val align = config().clockAlign.coerceIn(0, 2)
        val density = activity.resources.displayMetrics.density
        val textMode = config().homeStyle == 1
        // 图标主页：字号固定原版一档（经典 76sp / 分栏 60sp，堆叠两行折半），
        // 自绘样式按「块高 ≈ 原版时间字高」换算 em —— 只换外观，不占更大的地盘。
        val gridBaseSp = if (config().homeTopStyle == 1) 60f else 76f
        val timeSp = if (textMode) resolvedBigTimeSizeSp()
        else if (style == 2) gridBaseSp * 0.48f
        else gridBaseSp
        val timePx = timeSp * density

        // 自绘/复合样式防溢出：纯文字主页按内容宽度收顶，图标主页按原版槽位换算 em。
        val widthPx = activity.resources.displayMetrics.widthPixels
        val contentW = widthPx - 2f * dp(textPaddingDp())
        val budget = if (textMode) contentW
        else if (config().homeTopStyle == 1) widthPx * 0.45f
        else widthPx * 0.92f
        val emMax = when (style) {
            5 -> (budget - 20f * density) / 4.30f
            6, 10 -> budget / 4.0f
            7 -> budget / 2.12f
            8 -> (budget - 20f * density) / 3.10f
            11 -> budget / 7.26f
            else -> Float.MAX_VALUE
        }
        val gridEmPx = when (style) {
            5 -> gridBaseSp / 1.35f * density
            6, 10 -> gridBaseSp / 2f * density
            7 -> gridBaseSp * 0.70f * density
            11 -> gridBaseSp / 1.1f * density
            else -> timePx
        }
        val emPx = if (style in 5..11) {
            (if (textMode) timePx else gridEmPx).coerceAtMost(emMax)
        } else timePx
        val emSp = emPx / density

        // 变体显隐：同一时刻只显示一个
        val rowVisible = style == 0 || style == 1 || style == 4 || style == 9
        setVisible(homeBigTimeRow, rowVisible)
        setVisible(homeBigTimeStack, style == 2)
        setVisible(homeClockSeg, style == 3)
        setVisible(homeClockFlip, style == 5)
        setVisible(homeClockRound, style == 6)
        setVisible(homeClockVSplit, style == 7)
        setVisible(homeClockHorizontal, style == 8)
        setVisible(homeClockAnalog, style == 10)
        setVisible(homeClockMatrix, style == 11)

        // 行内分隔符：分栏用竖线，其余用冒号
        val showSep = style == 0
        setVisible(homeBigTimeSep, showSep)
        setVisible(homeBigTimeColon, !showSep)

        // 行背景：胶囊黑底白字；线框 2dp 描边圆角盒；其余无底
        val pill = style == 4
        val outline = style == 9
        val row = homeBigTimeRow as? LinearLayout ?: return
        if (appliedClockStyleBg != style) {
            appliedClockStyleBg = style
            when {
                pill -> row.setBackgroundResource(R.drawable.bg_clock_pill)
                outline -> row.setBackgroundResource(R.drawable.bg_clock_outline)
                else -> row.background = null
            }
        }
        val desiredTextColor = color(if (pill) R.color.paper else R.color.ink)
        if (homeBigTimeHour.currentTextColor != desiredTextColor) {
            homeBigTimeHour.setTextColor(desiredTextColor)
            homeBigTimeMin.setTextColor(desiredTextColor)
            homeBigTimeColon.setTextColor(desiredTextColor)
        }
        val padH = if (pill) (timePx * 0.20f).toInt() else if (outline) dp(16) else 0
        val padV = if (pill) (timePx * 0.10f).toInt() else if (outline) dp(4) else 0
        if (row.paddingLeft != padH || row.paddingTop != padV) {
            row.setPadding(padH, padV, padH, padV)
        }

        // 字号：文本类变体用 DIP 写入（时钟自有「时间字号」旋钮，不随界面字号缩放）
        if (homeBigTimeHour.textSize != timePx) {
            for (tv in listOf(
                homeBigTimeHour, homeBigTimeMin, homeBigTimeColon,
                homeBigTimeHourStack, homeBigTimeMinStack
            )) {
                tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, timeSp)
            }
            homeVSplitHour.setTextSize(TypedValue.COMPLEX_UNIT_DIP, emSp)
            homeVSplitMin.setTextSize(TypedValue.COMPLEX_UNIT_DIP, emSp)
            homeHorizTime.setTextSize(TypedValue.COMPLEX_UNIT_DIP, emSp)
        }
        if (homeClockSeg.digitHeight != timePx) homeClockSeg.digitHeight = timePx
        if (homeClockFlip.sizePx != emPx) homeClockFlip.sizePx = emPx
        if (homeClockRound.sizePx != emPx) homeClockRound.sizePx = emPx
        if (homeClockAnalog.sizePx != emPx) homeClockAnalog.sizePx = emPx
        if (homeClockMatrix.sizePx != emPx) homeClockMatrix.sizePx = emPx
        val smallSp = (emSp * 0.35f).coerceAtLeast(14f)
        if (homeVSplitDate.textSize != smallSp * density) {
            homeVSplitDate.setTextSize(TypedValue.COMPLEX_UNIT_DIP, smallSp)
            homeVSplitBatt.setTextSize(TypedValue.COMPLEX_UNIT_DIP, smallSp)
            homeHorizDate.setTextSize(TypedValue.COMPLEX_UNIT_DIP, smallSp)
            homeHorizBatt.setTextSize(TypedValue.COMPLEX_UNIT_DIP, smallSp)
        }

        // 信息行：图标主页与自带日期电量的样式（圆环/竖线/横排/表盘/点阵）隐藏
        val infoGone = !textMode || style == 6 || style == 7 || style == 8 ||
                style == 10 || style == 11
        setVisible(homeInfoLine, !infoGone)

        // WiFi/蓝牙快捷行：仅纯文字主页显示（图标主页顶部已有状态行）
        setVisible(homeQuickRow, textMode)

        // 位置：上边距 + 对齐。纯文字主页按用户设置；图标主页回原版位。
        val clockTopPx = dp(config().clockTop.coerceIn(8, 256))
        val clockBottomPx = dp(config().clockBottom.coerceIn(0, 96))
        val clockXPx = dp(config().clockX.coerceIn(-textPaddingDp(), 96))
        val gravity = if (textMode) Gravity.CENTER_HORIZONTAL
        else if (config().homeTopStyle == 1) Gravity.START else Gravity.CENTER_HORIZONTAL
        val clockTopEffective = if (textMode) clockTopPx
        else if (config().homeTopStyle == 1) 0 else dp(8)
        for (v in variants) {
            val lp = v.layoutParams as? LinearLayout.LayoutParams ?: continue
            if (lp.topMargin != clockTopEffective || lp.gravity != gravity ||
                lp.marginStart != 0 || lp.marginEnd != 0
            ) {
                lp.topMargin = clockTopEffective
                lp.gravity = gravity
                lp.marginStart = 0
                lp.marginEnd = 0
                v.layoutParams = lp
            }
        }
        // 快捷行与信息行跟随时钟锚点
        val lpQuick = homeQuickRow.layoutParams as? LinearLayout.LayoutParams
        if (lpQuick != null && (lpQuick.gravity != gravity ||
                lpQuick.marginStart != 0 || lpQuick.marginEnd != 0)
        ) {
            lpQuick.gravity = gravity
            lpQuick.marginStart = 0
            lpQuick.marginEnd = 0
            homeQuickRow.layoutParams = lpQuick
        }
        val lpInfo = homeInfoLine.layoutParams as? LinearLayout.LayoutParams
        if (lpInfo != null && (lpInfo.topMargin != clockBottomPx || lpInfo.gravity != gravity ||
                lpInfo.marginStart != 0 || lpInfo.marginEnd != 0)
        ) {
            lpInfo.topMargin = clockBottomPx
            lpInfo.gravity = gravity
            lpInfo.marginStart = 0
            lpInfo.marginEnd = 0
            homeInfoLine.layoutParams = lpInfo
        }

        // 纯文字主页：时钟区整组锚定。容器 wrap_content，宽度=最宽子元素（通常是时钟），
        // 用容器 LP 的 gravity/边距对齐整组；页边距与左右偏移合入边距。
        val padPx = dp(textPaddingDp())
        val areaLp = homeClockArea.layoutParams as? LinearLayout.LayoutParams
        if (textMode && areaLp != null) {
            when (align) {
                1 -> {
                    areaLp.gravity = Gravity.CENTER_HORIZONTAL
                    areaLp.marginStart = 2 * clockXPx
                    areaLp.marginEnd = 0
                }
                2 -> {
                    areaLp.gravity = Gravity.END
                    areaLp.marginStart = 0
                    areaLp.marginEnd = padPx - clockXPx
                }
                else -> {
                    areaLp.gravity = Gravity.START
                    areaLp.marginStart = padPx + clockXPx
                    areaLp.marginEnd = 0
                }
            }
            homeClockArea.layoutParams = areaLp
        }
    }

    /** 大时间字号：0 = 自动（按样式限宽推算，时间块不遮挡中上部壁纸）；>0 = 手动。 */
    private fun resolvedBigTimeSizeSp(): Float {
        val configured = config().textTimeSize
        val density = activity.resources.displayMetrics.density
        if (configured > 0) return configured.coerceIn(TIME_MIN_SP, TIME_MAX_SP).toFloat()
        val style = config().clockStyle.coerceIn(0, 11)
        val paddingPx = textPaddingDp() * density
        val widthPx = activity.resources.displayMetrics.widthPixels
        val budget = when (style) {
            2 -> widthPx * 0.166f - paddingPx
            6, 10 -> widthPx * 0.40f
            11 -> widthPx * 0.42f - paddingPx
            else -> widthPx * 0.30f - paddingPx
        }
        val emPx = when (style) {
            0 -> budget / 2.12f
            1 -> budget / 2.38f
            2 -> budget / 1.06f
            3 -> budget / 2.74f
            4 -> budget / 2.88f
            5 -> (budget - 20f * density) / 4.30f
            6, 10 -> budget / 4.0f
            7 -> budget / 2.12f
            8 -> (budget - 20f * density) / 3.10f
            9 -> (budget - 32f * density) / 2.38f
            11 -> budget / 7.26f
            else -> budget / 2.88f
        }
        val floorSp = when (style) {
            5 -> 24f
            6, 10 -> 16f
            11 -> 12f
            else -> 48f
        }
        return (emPx / density).coerceAtLeast(floorSp)
    }

    private fun setVisible(view: View, visible: Boolean) {
        val desired = if (visible) View.VISIBLE else View.GONE
        if (view.visibility != desired) view.visibility = desired
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), activity.resources.displayMetrics
        ).toInt()

    /** minSdk 17，用不上 API 23 的 Context.getColor。 */
    @Suppress("DEPRECATION")
    private fun color(res: Int): Int = activity.resources.getColor(res)

    private companion object {
        const val TIME_MIN_SP = 12
        const val TIME_MAX_SP = 200
    }
}
