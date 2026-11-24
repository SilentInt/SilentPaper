package com.silent.paper.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorFilter
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.View
import com.silent.paper.R
import java.io.File
import java.io.FileOutputStream

/**
 * 主页壁纸：选择、导入与渲染。
 *
 * - 用户从系统相册/文件管理器选图（ACTION_GET_CONTENT），原始字节拷贝到
 *   应用私有目录，重装/清理数据前不会丢失引用。
 * - 渲染一次到位：壁纸铺在整窗根布局上（覆盖屏幕 cover 等比裁剪，含底部标签栏
 *   区域——标签栏透明、由壁纸透出，标签文字由 ChipTextView 纸色底片保证可读），绘制时用单条 ColorMatrix 同时完成灰度化与纸色淡化，中间只产生
 *   一块屏幕大小的 ARGB_8888 位图（K103 约 10MB），不落盘。
 * - 淡化语义：[dimPct] 是与纸色 #FAFAF7 混合的百分比。60 表示纸色占 60%，
 *   壁纸只剩 40% 浓度 —— 保证应用名黑字在任何图上都可读。
 */
class WallpaperController(private val activity: Activity) {

    companion object {
        const val REQ_PICK_WALLPAPER = 21
        private const val FILE_NAME = "wallpaper.img"
        private const val PAPER_R = 0xFA
        private const val PAPER_G = 0xFA
        private const val PAPER_B = 0xF7
        private const val BUFFER_SIZE = 64 * 1024
    }

    /** 壁纸铺在整窗根布局上（含底部标签栏区域）：底部「主页/应用/设置」标签栏
     *  透明、壁纸从其下透出，标签文字由 ChipTextView 纸色底片保证可读；
     *  应用 / 设置页自带纸色背景，切换后自动盖住壁纸。 */
    private val target: View
        get() = activity.findViewById(R.id.launcherRoot)

    /** 无壁纸时的根背景：纸色实底（与主题底色一致）。 */
    private val paperDrawable by lazy {
        ColorDrawable(0xFF000000.toInt() or (PAPER_R shl 16) or (PAPER_G shl 8) or PAPER_B)
    }

    /** 上次已渲染的参数指纹：尺寸/淡化/文件都没变就不重渲，避免墨水屏无谓闪刷。 */
    private var lastKey: String = ""

    fun wallpaperFile(): File = File(activity.filesDir, FILE_NAME)

    fun pick() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        runCatching { activity.startActivityForResult(intent, REQ_PICK_WALLPAPER) }
            .onFailure {
                // 个别 ROM 没有可打开 image/* 的组件，退回系统 chooser 再试一次
                val fallback = Intent.createChooser(intent, null)
                runCatching { activity.startActivityForResult(fallback, REQ_PICK_WALLPAPER) }
            }
    }

    /** 把用户选中的 Uri 拷进私有目录（原子替换），成功返回落盘文件。 */
    fun importPicked(uri: Uri): File? = runCatching {
        val tmp = File(activity.filesDir, "$FILE_NAME.tmp")
        val input = activity.contentResolver.openInputStream(uri) ?: return null
        input.use { src ->
            FileOutputStream(tmp).use { out -> src.copyTo(out, BUFFER_SIZE) }
        }
        if (tmp.length() <= 0L) {
            tmp.delete()
            return null
        }
        // 校验能解开再替换，防止选到损坏文件后旧壁纸也没了
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tmp.absolutePath, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            tmp.delete()
            return null
        }
        if (!tmp.renameTo(wallpaperFile())) {
            wallpaperFile().delete()
            if (!tmp.renameTo(wallpaperFile())) return null
        }
        wallpaperFile()
    }.getOrNull()

    fun remove() {
        val file = wallpaperFile()
        if (file.exists()) file.delete()
    }

    /**
     * 把当前壁纸渲染到 pageHome 背景。[widthPx] x [heightPx] 应传 view 实际尺寸，
     * 尚未布局时传 0 会直接返回不渲染。
     */
    fun apply(widthPx: Int, heightPx: Int, dimPct: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        val file = wallpaperFile()
        if (!file.exists()) {
            lastKey = ""
            clear()
            return
        }
        val key = "$widthPx|$heightPx|$dimPct|${file.lastModified()}|${file.length()}"
        if (key == lastKey && target.background is CoverDrawable) return
        val bmp = render(file, widthPx, heightPx, dimPct)
        if (bmp == null) {
            lastKey = ""
            clear()
            return
        }
        lastKey = key
        target.background = CoverDrawable(bmp)
    }

    fun clear() {
        if (target.background !is ColorDrawable) target.background = paperDrawable
    }

    // ------------------------------------------------------------------ render

    /**
     * decode → cover 裁剪 → 灰度 + 淡化，一次 draw 完成。
     * 超采样只到「两个方向都盖住目标」为止，保证低内存下也不糊成马赛克。
     */
    private fun render(file: File, w: Int, h: Int, dimPct: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val raw = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // cover 覆盖：等比缩放盖满整窗、居中裁掉溢出——壁纸铺到屏幕边缘，
        // 底部标签栏区域也在壁纸之下（标签栏透明透出，文字描边可读）。
        val scale = maxOf(w.toFloat() / raw.width, h.toFloat() / raw.height)
        val dx = (w - raw.width * scale) * 0.5f
        val dy = (h - raw.height * scale) * 0.5f
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }

        // 单条 ColorMatrix：先灰度（人眼加权），再按 dim 比例混入纸色。
        // r' = (1-t) * gray + t * paperR —— 避免逐像素脚本循环。
        val t = (dimPct.coerceIn(0, 100)) / 100f
        val keep = 1f - t
        val gray = floatArrayOf(0.213f, 0.715f, 0.072f)
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                keep * gray[0], keep * gray[1], keep * gray[2], 0f, t * PAPER_R,
                keep * gray[0], keep * gray[1], keep * gray[2], 0f, t * PAPER_G,
                keep * gray[0], keep * gray[1], keep * gray[2], 0f, t * PAPER_B,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }

        canvas.drawBitmap(raw, matrix, paint)
        if (raw != out) raw.recycle()
        return out
    }
}

/**
 * 把位图按 center-crop（cover）绘制到任意 Drawable 边界：
 * 背景视图可能比屏幕矮（系统栏不占窗口），BitmapDrawable 默认 FILL 会把图
 * 纵向压扁；这里按 max(宽比,高比) 缩放并居中裁切，窗口尺寸变化也不变形。
 */
private class CoverDrawable(private val bitmap: Bitmap) : Drawable() {

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val matrix = Matrix()

    override fun draw(canvas: Canvas) {
        if (bitmap.isRecycled) return
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0 || bitmap.width <= 0 || bitmap.height <= 0) return
        val scale = maxOf(
            b.width().toFloat() / bitmap.width,
            b.height().toFloat() / bitmap.height
        )
        val dx = b.left + (b.width() - bitmap.width * scale) * 0.5f
        val dy = b.top + (b.height() - bitmap.height * scale) * 0.5f
        matrix.setScale(scale, scale)
        matrix.postTranslate(dx, dy)
        canvas.drawBitmap(bitmap, matrix, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
