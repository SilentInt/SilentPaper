package com.silent.paper.ui

import android.app.Activity
import android.content.pm.ResolveInfo
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.silent.paper.R
import com.silent.paper.data.AppRepository
import com.silent.paper.data.LauncherConfig

/**
 * 纯文字主页：应用名列表、版式参数（字号 / 行距 / 页边距）、底部标语，
 * 以及批量管理模式下的行状态更新。
 *
 * 列表行点击/长按通过 [Callbacks] 交回 Activity，与图标网格共用启动、
 * 操作菜单和批量选择逻辑。
 */
class TextHomeController(
    private val activity: Activity,
    private val config: () -> LauncherConfig,
    private val repository: AppRepository,
    private val font: FontController,
    private val callbacks: Callbacks
) {

    interface Callbacks {
        fun launchApp(info: ResolveInfo)
        fun showActions(info: ResolveInfo)
        fun toggleManageSelection(info: ResolveInfo, row: TextView)
    }

    private lateinit var homeTextMode: View
    private lateinit var textListScroll: ScrollView
    private lateinit var textList: LinearLayout
    private lateinit var homeSloganText: TextView

    /** 列表签名：应用集合 / 字号 / 行距 / 隐藏表没变就不重建，避免墨水屏白闪一屏。 */
    private var listSignature = ""

    fun bind() {
        homeTextMode = activity.findViewById(R.id.homeTextMode)
        textListScroll = activity.findViewById(R.id.textListScroll)
        textList = activity.findViewById(R.id.textList)
        homeSloganText = activity.findViewById(R.id.homeSlogan)
    }

    fun show() {
        if (homeTextMode.visibility != View.VISIBLE) homeTextMode.visibility = View.VISIBLE
    }

    fun hide() {
        if (homeTextMode.visibility != View.GONE) homeTextMode.visibility = View.GONE
    }

    /** 应用名字号：0 = 自动（字高 ≈ 屏高 2.4%）。 */
    fun resolvedTextFontSize(): Int {
        val configured = config().textFontSize
        if (configured > 0) return configured.coerceIn(FONT_MIN_SP, FONT_MAX_SP)
        val density = activity.resources.displayMetrics.density
        val sp = activity.resources.displayMetrics.heightPixels * 0.024f / 0.9f / density
        return ((sp / 2f).toInt() * 2).coerceIn(FONT_MIN_SP, FONT_MAX_SP)
    }

    /** 行间距：0 = 自动（≈ 0.85× 字号）。 */
    fun resolvedLineSpacingDp(): Int {
        val configured = config().textLineSpacing
        if (configured > 0) return configured.coerceIn(SPACING_MIN_DP, SPACING_MAX_DP)
        val dp = resolvedTextFontSize() * 0.85f
        return ((dp / 4f).toInt() * 4).coerceIn(SPACING_MIN_DP, SPACING_MAX_DP)
    }

    /** 页边距：0 = 自动（≈ 屏宽 2.5%，内容靠左给右侧壁纸留位）。 */
    fun resolvedPaddingDp(): Int {
        val configured = config().textPadding
        if (configured > 0) return configured.coerceIn(PADDING_MIN_DP, PADDING_MAX_DP)
        val density = activity.resources.displayMetrics.density
        val dp = activity.resources.displayMetrics.widthPixels * 0.025f / density
        return ((dp / 8f).toInt() * 8).coerceIn(PADDING_MIN_DP, PADDING_MAX_DP)
    }

    /** 信息行默认字号：随应用字号缩放，约为 45%，下限 12sp。 */
    fun infoTextSizeSp(): Float =
        (resolvedTextFontSize() * 0.45f).coerceAtLeast(12f)

    /** 页边距、列表四向边距与底部标语。时钟区由 Activity 另行落位。 */
    fun applyMetrics() {
        if (!::homeTextMode.isInitialized) return
        val padDp = resolvedPaddingDp()
        val pad = dp(padDp)
        if (homeTextMode.paddingLeft != pad || homeTextMode.paddingRight != pad) {
            homeTextMode.setPadding(pad, 0, pad, 0)
        }

        val listTopPx = dp(config().listTop.coerceIn(0, 512))
        val listBottomPx = dp(config().listBottom.coerceIn(0, 128))
        val listLeftPx = dp(config().listLeft.coerceIn(-padDp, 96))
        val listRightPx = dp(config().listRight.coerceIn(0, 256))
        val lpList = textListScroll.layoutParams as? LinearLayout.LayoutParams
        if (lpList != null &&
            (lpList.topMargin != listTopPx || lpList.bottomMargin != listBottomPx ||
                    lpList.marginStart != listLeftPx || lpList.marginEnd != listRightPx)
        ) {
            lpList.topMargin = listTopPx
            lpList.bottomMargin = listBottomPx
            lpList.marginStart = listLeftPx
            lpList.marginEnd = listRightPx
            textListScroll.layoutParams = lpList
        }

        val slogan = config().homeSlogan
        val showSlogan = config().homeSloganOn && slogan.isNotBlank()
        homeSloganText.visibility = if (showSlogan) View.VISIBLE else View.GONE
        if (homeSloganText.text.toString() != slogan.orEmpty()) {
            homeSloganText.text = slogan.orEmpty()
        }
    }

    /**
     * 重建应用名列表。正常模式显示全部可见应用（固定优先 → 点击次数排序）；
     * 批量管理模式显示全部应用（含已隐藏，带 ○/●/隐 标记）。
     * 选中集合不参与签名：选中切换走行内原位更新，不整表重建。
     */
    fun populate(
        manage: Boolean,
        hidden: Set<String>,
        selected: Set<String>
    ) {
        if (!::textList.isInitialized) return
        val sorted = if (manage) repository.loadAllApps()
        else repository.sortHomeApps(repository.loadApps(), Int.MAX_VALUE)

        val sizeSp = resolvedTextFontSize()
        val spacingDp = resolvedLineSpacingDp()
        val hiddenSorted = hidden.sorted()
        val signature = "$manage|$sizeSp|$spacingDp|${config().listAlign}|" +
                sorted.joinToString(",") { it.activityInfo.packageName } + "|" +
                hiddenSorted.joinToString(",")
        if (signature == listSignature && textList.childCount > 0) return
        listSignature = signature

        textList.removeAllViews()

        val ink = color(R.color.ink)
        val sizeSpF = sizeSp.toFloat()
        val spacingPx = dp(spacingDp)
        val minTouchPx = dp(44)
        val alignment = config().listAlign.coerceIn(0, 2)

        sorted.forEachIndexed { index, info ->
            val row = TextView(activity).apply {
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                minHeight = minTouchPx
                gravity = when (alignment) {
                    1 -> Gravity.CENTER
                    2 -> Gravity.CENTER_VERTICAL or Gravity.END
                    else -> Gravity.CENTER_VERTICAL or Gravity.START
                }
                setTextColor(ink)
                textSize = sizeSpF
                layoutParams = LinearLayout.LayoutParams(
                    if (manage) ViewGroup.LayoutParams.WRAP_CONTENT
                    else ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = if (index == 0) spacingPx / 2 else spacingPx
                    if (manage) {
                        // 批量管理：点击区域收窄到文字本身，点旁边的空白不再误触选中
                        gravity = when (alignment) {
                            1 -> Gravity.CENTER
                            2 -> Gravity.CENTER_VERTICAL or Gravity.END
                            else -> Gravity.CENTER_VERTICAL or Gravity.START
                        }
                    }
                }
                if (manage) {
                    updateManageRow(this, info, hidden, selected)
                    setOnClickListener { callbacks.toggleManageSelection(info, this) }
                } else {
                    text = info.loadLabel(activity.packageManager)
                    setOnClickListener { callbacks.launchApp(info) }
                    setOnLongClickListener {
                        callbacks.showActions(info)
                        true
                    }
                }
            }
            textList.addView(row)
        }
        textListScroll.scrollTo(0, 0)
        font.applyToTree(textList)
    }

    /** 批量管理模式下刷新单行：● 选中 / ○ 未选 / 隐 已隐藏。 */
    fun updateManageRow(
        row: TextView,
        info: ResolveInfo,
        hidden: Set<String>,
        selected: Set<String>
    ) {
        val pkg = info.activityInfo.packageName
        val prefix = when {
            selected.contains(pkg) -> "● "
            hidden.contains(pkg) -> "隐 "
            else -> "○ "
        }
        row.text = prefix + info.loadLabel(activity.packageManager)
        row.setTextColor(color(R.color.ink))
        if (row.alpha != 1f) row.alpha = 1f
    }

    /** 字体/配色变化后强制重建（例如切换字体后行内测量可能变化）。 */
    fun invalidate() {
        listSignature = ""
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), activity.resources.displayMetrics
        ).toInt()

    /** minSdk 17，用不上 API 23 的 Context.getColor。 */
    @Suppress("DEPRECATION")
    private fun color(res: Int): Int = activity.resources.getColor(res)

    private companion object {
        const val FONT_MIN_SP = 12
        const val FONT_MAX_SP = 56
        const val SPACING_MIN_DP = 4
        const val SPACING_MAX_DP = 64
        const val PADDING_MIN_DP = 8
        const val PADDING_MAX_DP = 192
    }
}
