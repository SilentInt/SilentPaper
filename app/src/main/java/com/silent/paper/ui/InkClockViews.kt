package com.silent.paper.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/**
 * inkOS 移植的四款自绘时钟：翻页 / 圆环 / 表盘 / 点阵。
 *
 * 基准尺寸 [sizePx]（em）：卡片、圆环直径、点距全部由它换算，
 * 由 LauncherActivity.applyClockStyleAndAlign() 按样式自动推算写入
 * （也可被用户手动「时间字号」覆盖）。
 * 墨水屏高对比：一律纯墨 #1A1A1A 直出，无半透明灰。
 */
private const val INK = 0xFF1A1A1A.toInt()
private const val PAPER = 0xFFFAFAF7.toInt()

/**
 * 翻页时钟：4 张墨色圆角卡（0.95em × 1.35em，圆角 8dp）+ 纸色数字，
 * 卡中线 2dp 反色带（带底纸色 + 数字中段墨色），冒号在卡外墨色。
 * 布局：[d0] 6dp [d1] 4dp [:] 4dp [d2] 6dp [d3]，同 inkOS Flip。
 */
class FlipClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
    private val bandPaint = Paint().apply { color = PAPER }
    private val digitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PAPER
        textAlign = Paint.Align.CENTER
    }
    private val bandDigitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK
        textAlign = Paint.Align.CENTER
    }
    private val colonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private var digits = "0000"

    /** em 基准字号（px）；时基更新时 requestLayout 重算卡宽。 */
    var sizePx = 0f
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    fun setTypeface(tf: Typeface?) {
        if (digitPaint.typeface === tf) return
        digitPaint.typeface = tf
        bandDigitPaint.typeface = tf
        colonPaint.typeface = tf
        requestLayout()
    }

    fun setTime(hh: String, mm: String) {
        val s = (hh + mm)
        if (digits == s) return
        digits = s
        invalidate()
    }

    private fun cardW() = sizePx * 0.95f
    private fun cardH() = sizePx * 1.35f

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (sizePx <= 0f) {
            setMeasuredDimension(0, 0)
            return
        }
        colonPaint.textSize = sizePx
        val colonW = colonPaint.measureText(":")
        val w = 4f * cardW() + 2f * 6f * density + 2f * 4f * density + colonW
        setMeasuredDimension(w.toInt(), cardH().toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sizePx <= 0f || digits.length != 4) return
        val w = cardW()
        val h = cardH()
        val corner = 8f * density
        val gap = 6f * density
        val colonGap = 4f * density
        colonPaint.textSize = sizePx
        val colonW = colonPaint.measureText(":")
        val fmC = colonPaint.fontMetrics
        val baseline = h / 2f - (fmC.ascent + fmC.descent) / 2f

        var x = 0f
        for (i in 0 until 4) {
            drawCard(canvas, x, digits[i], w, h, corner, baseline)
            x += w
            when (i) {
                0 -> x += gap
                1 -> {
                    x += colonGap
                    canvas.drawText(":", x + colonW / 2f, baseline, colonPaint)
                    x += colonW + colonGap
                }
                2 -> x += gap
            }
        }
    }

    /** 单张翻页卡：墨底圆角 + 纸色数字 + 中线反色带（带底纸色，数字中段墨色）。 */
    private fun drawCard(canvas: Canvas, x: Float, ch: Char, w: Float, h: Float, corner: Float, baseline: Float) {
        rect.set(x, 0f, x + w, h)
        canvas.drawRoundRect(rect, corner, corner, cardPaint)
        digitPaint.textSize = sizePx
        bandDigitPaint.textSize = sizePx
        val cx = x + w / 2f
        canvas.drawText(ch.toString(), cx, baseline, digitPaint)
        // 中线反色带：裁剪后铺纸色再重画墨色数字，只有数字中段落进带内
        val bandH = (2f * density).coerceAtLeast(1f)
        val cy = h / 2f
        canvas.save()
        canvas.clipRect(x, cy - bandH / 2f, x + w, cy + bandH / 2f)
        canvas.drawRect(x, cy - bandH / 2f, x + w, cy + bandH / 2f, bandPaint)
        canvas.drawText(ch.toString(), cx, baseline, bandDigitPaint)
        canvas.restore()
    }
}

/**
 * 圆环时钟：直径 = 4em，4dp 圆弧从 12 点方向画当日进度（(h×60+m)/1440），
 * 圆心居中 HH:MM（纯时钟，不含日期电量，同 inkOS Round 去掉附带信息）。
 */
class RoundClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK
        style = Paint.Style.STROKE
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private var hh = "00"
    private var mm = "00"

    var sizePx = 0f
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    fun setTypeface(tf: Typeface?) {
        if (timePaint.typeface === tf) return
        timePaint.typeface = tf
        invalidate()
    }

    fun setTime(h: String, m: String) {
        if (hh == h && mm == m) return
        hh = h
        mm = m
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = sizePx * 4f
        if (sizePx <= 0f) {
            setMeasuredDimension(0, 0)
            return
        }
        setMeasuredDimension(d.toInt(), d.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sizePx <= 0f) return
        val d = sizePx * 4f
        val half = d / 2f
        val stroke = 4f * density
        arcPaint.strokeWidth = stroke
        rect.set(stroke / 2f, stroke / 2f, d - stroke / 2f, d - stroke / 2f)
        val h24 = hh.toIntOrNull()?.coerceIn(0, 23) ?: 0
        val m = mm.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val progress = (h24 * 60 + m) / 1440f
        canvas.drawArc(rect, 270f, progress * 360f, false, arcPaint)

        timePaint.textSize = sizePx
        val fmT = timePaint.fontMetrics
        val timeBaseline = half - (fmT.ascent + fmT.descent) / 2f
        canvas.drawText("$hh:$mm", half, timeBaseline, timePaint)
    }
}

/**
 * 表盘时钟：直径 = 4em，2dp 圆环轮廓；时针 55% 半径 4dp 粗、分针 80% 半径 2dp，
 * 圆端帽，4dp 圆心点，同 inkOS Analog。时针角度 = (h12 + m/60) × 30°。
 */
class AnalogClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK
        style = Paint.Style.STROKE
    }
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
    private var hh = "00"
    private var mm = "00"

    var sizePx = 0f
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    fun setTime(h: String, m: String) {
        if (hh == h && mm == m) return
        hh = h
        mm = m
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (sizePx <= 0f) {
            setMeasuredDimension(0, 0)
            return
        }
        val d = sizePx * 4f
        setMeasuredDimension(d.toInt(), d.toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sizePx <= 0f) return
        val d = sizePx * 4f
        val half = d / 2f
        val stroke = 2f * density
        ringPaint.strokeWidth = stroke
        canvas.drawCircle(half, half, half - stroke, ringPaint)

        val h12 = ((hh.toIntOrNull() ?: 0) % 12)
        val m = (mm.toIntOrNull() ?: 0).coerceIn(0, 59)
        val radius = half - stroke
        val hourAngle = (h12 + m / 60f) * 30f - 90f
        val minuteAngle = m * 6f - 90f

        handPaint.strokeWidth = 4f * density
        val hourLen = radius * 0.55f
        val hourRad = Math.toRadians(hourAngle.toDouble())
        canvas.drawLine(half, half,
            half + hourLen * cos(hourRad).toFloat(),
            half + hourLen * sin(hourRad).toFloat(), handPaint)

        handPaint.strokeWidth = 2f * density
        val minLen = radius * 0.80f
        val minRad = Math.toRadians(minuteAngle.toDouble())
        canvas.drawLine(half, half,
            half + minLen * cos(minRad).toFloat(),
            half + minLen * sin(minRad).toFloat(), handPaint)

        canvas.drawCircle(half, half, 4f * density, dotPaint)
    }
}

/**
 * 点阵时钟：月历 7×5（已过实心、未到虚点）+ 时钟 17×5（3×5 数字点阵）+ 电量 7×5，
 * 三块横排，块间空 1 列；点距 = 0.22em，点径 = 0.65 点距，虚点 = 1/3 径，
 * 同 inkOS Matrix（墨水屏上虚点等效稀疏点阵 shader）。
 */
class MatrixClockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        /** 3×5 数字点阵图案，每行 3 位（bit2=左，bit0=右），同 inkOS DOT_MATRIX_DIGITS。 */
        private val DOT_MATRIX_DIGITS = arrayOf(
            intArrayOf(0b111, 0b101, 0b101, 0b101, 0b111), // 0
            intArrayOf(0b010, 0b110, 0b010, 0b010, 0b010), // 1
            intArrayOf(0b111, 0b001, 0b111, 0b100, 0b111), // 2
            intArrayOf(0b111, 0b001, 0b111, 0b001, 0b111), // 3
            intArrayOf(0b101, 0b101, 0b111, 0b001, 0b001), // 4
            intArrayOf(0b111, 0b100, 0b111, 0b001, 0b111), // 5
            intArrayOf(0b111, 0b100, 0b111, 0b101, 0b111), // 6
            intArrayOf(0b111, 0b001, 0b010, 0b010, 0b010), // 7
            intArrayOf(0b111, 0b101, 0b111, 0b101, 0b111), // 8
            intArrayOf(0b111, 0b101, 0b111, 0b001, 0b111)  // 9
        )

        /** HH:MM 的 17×5 点阵：数字(3)+空(1)+数字(3)+空(1)+冒号(1)+空(1)+数字(3)+空(1)+数字(3)。 */
        private fun buildClockGrid(h1: Int, h2: Int, m1: Int, m2: Int): Array<BooleanArray> {
            val grid = Array(5) { BooleanArray(17) }
            fun place(digit: Int, colOffset: Int) {
                val p = DOT_MATRIX_DIGITS[digit.coerceIn(0, 9)]
                for (row in 0 until 5) for (col in 0 until 3) {
                    grid[row][colOffset + col] = (p[row] shr (2 - col)) and 1 == 1
                }
            }
            place(h1, 0)
            place(h2, 4)
            grid[1][8] = true
            grid[3][8] = true
            place(m1, 10)
            place(m2, 14)
            return grid
        }
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
    private var hh = "00"
    private var mm = "00"
    private var batteryPct = -1

    var sizePx = 0f
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
        }

    fun setTime(h: String, m: String) {
        if (hh == h && mm == m) return
        hh = h
        mm = m
        invalidate()
    }

    /** 电量 0..100；< 0 = 未知，不画电量块（整块少 8 列）。 */
    fun setBattery(pct: Int) {
        if (batteryPct == pct) return
        batteryPct = pct
        requestLayout()
        invalidate()
    }

    private fun pitch() = sizePx * 0.22f
    private fun columns() = if (batteryPct >= 0) 7 + 1 + 17 + 1 + 7 else 7 + 1 + 17

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (sizePx <= 0f) {
            setMeasuredDimension(0, 0)
            return
        }
        val p = pitch()
        setMeasuredDimension((columns() * p).toInt(), (5 * p).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sizePx <= 0f) return
        val p = pitch()
        val dotR = p * 0.65f / 2f
        val ghostR = dotR / 3f
        var colOffset = 0

        // ---- 月历 7×5：本月 1 日对齐周一列，已过实心、未到虚点 ----
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val first = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        val firstDow = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7   // 周一 = 0
        for (row in 0 until 5) for (col in 0 until 7) {
            val dayNum = row * 7 + col - firstDow + 1
            val r = when {
                dayNum in 1..today -> dotR
                dayNum in (today + 1)..daysInMonth -> ghostR
                else -> 0f
            }
            if (r > 0f) drawDot(canvas, colOffset + col, row, r, p)
        }
        colOffset += 8    // 月历 + 1 空列

        // ---- 时钟 17×5 ----
        val h1 = hh.getOrNull(0)?.let { it - '0' } ?: 0
        val h2 = hh.getOrNull(1)?.let { it - '0' } ?: 0
        val m1 = mm.getOrNull(0)?.let { it - '0' } ?: 0
        val m2 = mm.getOrNull(1)?.let { it - '0' } ?: 0
        val grid = buildClockGrid(h1, h2, m1, m2)
        for (row in 0 until 5) for (col in 0 until 17) {
            if (grid[row][col]) drawDot(canvas, colOffset + col, row, dotR, p)
        }
        colOffset += 17

        // ---- 电量 7×5：35 点量表，未达的画虚点 ----
        if (batteryPct >= 0) {
            colOffset += 1
            val filled = (batteryPct * 35 / 100).coerceIn(0, 35)
            for (row in 0 until 5) for (col in 0 until 7) {
                val idx = row * 7 + col
                drawDot(canvas, colOffset + col, row, if (idx < filled) dotR else ghostR, p)
            }
        }
    }

    private fun drawDot(canvas: Canvas, col: Int, row: Int, radius: Float, pitch: Float) {
        canvas.drawCircle(col * pitch + pitch / 2f, row * pitch + pitch / 2f, radius, dotPaint)
    }
}
