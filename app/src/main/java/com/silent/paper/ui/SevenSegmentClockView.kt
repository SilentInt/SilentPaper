package com.silent.paper.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * 七段数码管时钟：HH:MM，段条自绘（不走系统字体），墨水屏高对比。
 * [digitHeight] 决定整体尺寸：数字宽 = 0.55h，冒号列 = 0.22h，字距 = 0.08h，
 * 总宽 ≈ 2.74 × digitHeight。
 */
class SevenSegmentClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        // 段编号：0=a 顶、1=b 右上、2=c 右下、3=d 底、4=e 左下、5=f 左上、6=g 中
        private val DIGITS = arrayOf(
            booleanArrayOf(true, true, true, true, true, true, false),   // 0
            booleanArrayOf(false, true, true, false, false, false, false), // 1
            booleanArrayOf(true, true, false, true, true, false, true),  // 2
            booleanArrayOf(true, true, true, true, false, false, true),   // 3
            booleanArrayOf(false, true, true, false, false, true, true),  // 4
            booleanArrayOf(true, false, true, true, false, true, true),   // 5
            booleanArrayOf(true, false, true, true, true, true, true),    // 6
            booleanArrayOf(true, true, true, false, false, false, false),  // 7
            booleanArrayOf(true, true, true, true, true, true, true),      // 8
            booleanArrayOf(true, true, true, true, false, true, true)      // 9
        )
        private const val INK = 0xFF1A1A1A.toInt()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
    private val rect = RectF()
    private var digits = "0000"

    var digitHeight = 0f
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    fun setTime(hhmm: String) {
        if (digits != hhmm) {
            digits = hhmm
            invalidate()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = digitHeight
        if (h <= 0f) {
            setMeasuredDimension(0, 0)
            return
        }
        setMeasuredDimension((2.74f * h).toInt(), h.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = digitHeight
        if (h <= 0f || digits.length != 4) return
        val t = 0.14f * h            // 段条厚度
        val w = 0.55f * h            // 数字宽
        val g = 0.08f * h            // 字距
        val cw = 0.22f * h           // 冒号列宽
        val r = t * 0.32f            // 段条圆角
        val inset = t * 0.28f        // 段条间留缝

        var x = 0f
        for (i in 0 until 4) {
            drawDigit(canvas, x, digits[i], w, h, t, r, inset)
            x += w
            if (i == 1) {
                val dot = t * 1.1f
                val dotX = x + (cw - dot) / 2f
                rect.set(dotX, h * 0.30f - dot / 2f, dotX + dot, h * 0.30f + dot / 2f)
                canvas.drawRoundRect(rect, dot / 2f, dot / 2f, paint)
                rect.set(dotX, h * 0.70f - dot / 2f, dotX + dot, h * 0.70f + dot / 2f)
                canvas.drawRoundRect(rect, dot / 2f, dot / 2f, paint)
                x += cw
            }
            x += g
        }
    }

    private fun drawDigit(canvas: Canvas, ox: Float, ch: Char, w: Float, h: Float, t: Float, r: Float, inset: Float) {
        val d = ch - '0'
        if (d !in 0..9) return
        val segs = DIGITS[d]
        val halfT = t / 2f
        val topY = halfT
        val midY = h / 2f
        val botY = h - halfT
        val leftX = ox + halfT
        val rightX = ox + w - halfT
        val vTop = topY + halfT
        val vMid1 = midY - halfT - inset
        val vMid2 = midY + halfT + inset
        val vBot = botY - halfT
        // 横段：a / g / d
        if (segs[0]) hSeg(canvas, leftX + halfT + inset, rightX - halfT - inset, topY, t, r)
        if (segs[6]) hSeg(canvas, leftX + halfT + inset, rightX - halfT - inset, midY, t, r)
        if (segs[3]) hSeg(canvas, leftX + halfT + inset, rightX - halfT - inset, botY, t, r)
        // 竖段：f / b / e / c
        if (segs[5]) vSeg(canvas, leftX, vTop, vMid1, t, r)
        if (segs[1]) vSeg(canvas, rightX, vTop, vMid1, t, r)
        if (segs[4]) vSeg(canvas, leftX, vMid2, vBot, t, r)
        if (segs[2]) vSeg(canvas, rightX, vMid2, vBot, t, r)
    }

    private fun hSeg(canvas: Canvas, x1: Float, x2: Float, cy: Float, t: Float, r: Float) {
        rect.set(x1, cy - t / 2f, x2, cy + t / 2f)
        canvas.drawRoundRect(rect, r, r, paint)
    }

    private fun vSeg(canvas: Canvas, cx: Float, y1: Float, y2: Float, t: Float, r: Float) {
        rect.set(cx - t / 2f, y1, cx + t / 2f, y2)
        canvas.drawRoundRect(rect, r, r, paint)
    }
}
