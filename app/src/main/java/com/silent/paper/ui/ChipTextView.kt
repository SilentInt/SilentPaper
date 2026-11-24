package com.silent.paper.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.Gravity
import android.widget.TextView
import com.silent.paper.R
import kotlin.math.min

/**
 * 带纸色圆角底片（反色衬底）的 TextView：文字铺在黑色壁纸区域上时，
 * 底片提供整块纸色背景保证可读（描边方案在细字上会看成同色发胖，故弃用）。
 *
 * - 底片横向贴合文字实际宽度（按 gravity 定位）、纵向占满视图高度，
 *   不依赖视图宽度（网格标签是 match_parent，底片仍只裹住文字）；
 * - 纸色底片在纸色背景上不可见，无壁纸时零副作用；
 * - 不触碰画笔的 style / strokeWidth / color，字体粗细（FILL_AND_STROKE
 *   描边加粗）与文字颜色原样保留；
 * - 用于底部标签栏（时间 / 主页 / 应用 / 设置）与网格应用名、隐藏标记
 *   ——壁纸铺满整窗后这些文字直接坐在壁纸上。
 */
class ChipTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    @Suppress("DEPRECATION")
    private val paperColor = context.resources.getColor(R.color.paper)
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = paperColor }
    private val chipRect = RectF()

    override fun onDraw(canvas: Canvas) {
        val content = text?.toString()
        if (!content.isNullOrEmpty() && width > 0 && height > 0) {
            val textSize = paint.textSize
            if (textSize > 0f) {
                val padH = textSize * 0.30f
                ensureChipPadding(padH)
                // 优先取 Layout 实测行宽：长名被 ellipsize 截断时，
                // getText() 仍是完整字符串，直接 measureText 会把底片撑过格宽
                val textW = layout?.let { l ->
                    if (l.lineCount > 0) l.getLineWidth(0) else null
                } ?: paint.measureText(content)
                val contentW = width - paddingLeft - paddingRight
                val startX = when (gravity and Gravity.HORIZONTAL_GRAVITY_MASK) {
                    Gravity.END -> (width - paddingRight - textW).toFloat()
                    Gravity.CENTER_HORIZONTAL -> paddingLeft + (contentW - textW) / 2f
                    else -> paddingLeft.toFloat()
                }
                chipRect.set(startX - padH, 0f, startX + textW + padH, height.toFloat())
                val radius = min(height / 2f, padH).coerceAtLeast(2f)
                canvas.drawRoundRect(chipRect, radius, radius, chipPaint)
            }
        }
        super.onDraw(canvas)
    }

    /** 底片向文字左右各探出 padH，需保证视图有同量内边距，避免底片被裁边。 */
    private fun ensureChipPadding(padH: Float) {
        val want = padH.toInt().coerceAtLeast(4)
        if (paddingLeft < want || paddingRight < want) {
            setPadding(
                maxOf(paddingLeft, want),
                paddingTop,
                maxOf(paddingRight, want),
                paddingBottom
            )
        }
    }
}
