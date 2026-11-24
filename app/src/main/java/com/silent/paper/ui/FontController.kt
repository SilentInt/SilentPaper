package com.silent.paper.ui

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.silent.paper.R
import com.silent.paper.data.LauncherConfig
import java.io.File

/**
 * 全局字体与时钟字体的选择、导入和应用。
 *
 * - 字体文件统一拷入应用私有目录，源文件删除不受影响；
 * - 「从文件夹选择」走 SAF 树授权（Android 11+ 分区存储下的主入口），
 *   「从文件选择」优先系统 DocumentsUI；
 * - 选择结果通过 [onConfigChanged] 写回配置，由 Activity 统一刷新页面。
 */
class FontController(
    private val activity: Activity,
    private val config: () -> LauncherConfig,
    private val onConfigChanged: (LauncherConfig, Int) -> Unit
) {

    /** 字体设置目标：全局 = 所有界面；时钟 = 仅大时间。 */
    enum class Target { GLOBAL, CLOCK }

    private companion object {
        const val REQ_PICK_FONT = 22
        const val REQ_PICK_FONT_FOLDER = 23
        const val REQ_READ_STORAGE = 9
        const val BUFFER_SIZE = 64 * 1024

        // 字体扫描目录：墨水屏字体管理器的常规位置 + 下载目录
        val FONT_SCAN_DIRS = arrayOf("/sdcard/Fonts", "/sdcard/Download", "/sdcard/Downloads")
        val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")
    }

    // 缓存按「路径+mtime+大小」去重；换回默认时还原每个 TextView 原始字面。
    private var cachedTypeface: Typeface? = null
    private var cachedTypefaceKey = ""
    private var cachedClockTypeface: Typeface? = null
    private var cachedClockTypefaceKey = ""
    private val originalTypefaces = java.util.WeakHashMap<View, Typeface>()

    /** 权限弹窗期间暂存的字体目标（返回后继续打开对应对话框）。 */
    private var pendingTarget = Target.GLOBAL

    // ---------------------------------------------------------------- typeface

    /** 当前全局字体；未选或文件丢失返回 null（系统默认）。 */
    fun launcherTypeface(): Typeface? {
        val path = config().appFontPath
        if (path.isBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        val key = "$path|${file.lastModified()}|${file.length()}"
        if (key == cachedTypefaceKey && cachedTypeface != null) return cachedTypeface
        val tf = runCatching { Typeface.createFromFile(file) }.getOrNull()
        if (tf != null) {
            cachedTypeface = tf
            cachedTypefaceKey = key
        }
        return tf
    }

    /** 时钟专用字体；未选时返回 null（回退跟随全局字体）。 */
    fun clockTypeface(): Typeface? {
        val path = config().clockFontPath
        if (path.isBlank()) return null
        val file = File(path)
        if (!file.exists()) return null
        val key = "$path|${file.lastModified()}|${file.length()}"
        if (key == cachedClockTypefaceKey && cachedClockTypeface != null) return cachedClockTypeface
        val tf = runCatching { Typeface.createFromFile(file) }.getOrNull()
        if (tf != null) {
            cachedClockTypeface = tf
            cachedClockTypefaceKey = key
        }
        return tf
    }

    /** 字体粗细（0–3）→ 描边宽度 px：0 = 原样，其余逐级加粗（任意字体均生效）。 */
    fun strokePx(): Float {
        val density = activity.resources.displayMetrics.density
        return when (config().textWeight.coerceIn(0, 3)) {
            1 -> 0.35f * density
            2 -> 0.8f * density
            3 -> 1.4f * density
            else -> 0f
        }
    }

    /**
     * 全局换字体：遍历视图树，把每个 TextView 换成所选字体；
     * 换回默认（null）时从 [originalTypefaces] 还原各自原始字面（含加粗变体）。
     */
    fun applyToTree(view: View) {
        val tf = launcherTypeface()
        if (view is TextView) {
            if (!originalTypefaces.containsKey(view)) originalTypefaces[view] = view.typeface
            view.typeface = tf ?: originalTypefaces[view] ?: Typeface.DEFAULT
            applyWeight(view)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyToTree(view.getChildAt(i))
        }
    }

    /** 对整个内容树应用一次字体（静态文本、设置行、底部栏等）。 */
    fun refreshAll() = applyToTree(activity.findViewById(android.R.id.content))

    private fun applyWeight(view: TextView) {
        val p = view.paint
        val w = strokePx()
        if (w > 0f) {
            p.style = android.graphics.Paint.Style.FILL_AND_STROKE
            p.strokeWidth = w
        } else {
            p.style = android.graphics.Paint.Style.FILL
            p.strokeWidth = 0f
        }
    }

    // ---------------------------------------------------------------- picker

    /** 设置入口：扫描 /sdcard 需要存储权限（API 23+），拒绝时仍可「从文件选择」。 */
    fun showPicker(target: Target) {
        if (Build.VERSION.SDK_INT in 23..32 &&
            activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingTarget = target
            activity.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQ_READ_STORAGE)
            return
        }
        showDialog(target)
    }

    /**
     * 字体选择对话框：目录扫描结果逐行预览（滚动时懒加载），另支持
     * 从自选文件夹（SAF）或系统文件选择器挑单个字体。
     */
    private fun showDialog(target: Target) {
        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setWindowAnimations(0)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
        val density = activity.resources.displayMetrics.density
        fun dpx(v: Int) = (v * density).toInt()

        val isClock = target == Target.CLOCK
        val titleRes = if (isClock) R.string.settings_clock_font else R.string.settings_app_font
        val defaultLabelRes = if (isClock) R.string.clock_font_follow else R.string.app_font_default
        val currentFontName = if (isClock) config().clockFontName else config().appFontName

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(color(R.color.paper))
                setStroke(dpx(1), color(R.color.ink))
                cornerRadius = dpx(10).toFloat()
            }
            setPadding(dpx(24), dpx(24), dpx(24), dpx(16))
            layoutParams = ViewGroup.LayoutParams(
                (activity.resources.displayMetrics.widthPixels * 0.9).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(TextView(activity).apply {
            text = activity.getString(titleRes)
            textSize = 20f
            setTextColor(color(R.color.ink))
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dpx(6))
        })
        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.app_font_scan_hint)
            textSize = 13f
            setTextColor(color(R.color.gray))
            setPadding(0, 0, 0, dpx(12))
        })

        val scroll = ScrollView(activity)
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(
            list,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        // 列表过长时限高滚动（约屏高 55%），宽度随对话框
        scroll.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (activity.resources.displayMetrics.heightPixels * 0.55f).toInt()
        )

        val pendingPreviews = mutableListOf<Pair<TextView, Any>>()
        // SAF 预览临时文件：每次打开对话框清一遍（content URI 需先复制落盘才能解码）
        activity.cacheDir.listFiles { f -> f.name.startsWith("font_preview_") }?.forEach { it.delete() }

        fun divider() {
            list.addView(View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dpx(1)
                ).apply { topMargin = dpx(2) }
                setBackgroundColor(color(R.color.hairline))
            })
        }

        fun addRow(
            label: String,
            checked: Boolean,
            previewTypeface: Typeface?,
            preview: Any?,   // File（目录扫描）或 Uri（SAF 文件夹）
            onClick: () -> Unit
        ) {
            val row = TextView(activity).apply {
                text = if (checked) "● $label" else "○ $label"
                textSize = 16f
                setTextColor(color(if (checked) R.color.ink else R.color.gray))
                minHeight = dpx(48)
                gravity = Gravity.CENTER_VERTICAL
                setOnClickListener {
                    dialog.dismiss()
                    onClick()
                }
            }
            if (previewTypeface != null) row.typeface = previewTypeface
            else if (preview != null) pendingPreviews.add(row to preview)
            list.addView(row)
            divider()
        }

        // 1. 默认 / 跟随
        addRow(activity.getString(defaultLabelRes), currentFontName.isBlank(), null, null) {
            apply(target, "", "")
        }
        // 2. 目录扫描结果（行内预览懒加载；Android 11+ 分区存储下可能为空）
        for (file in scanFontFiles()) {
            val name = file.nameWithoutExtension
            addRow(name, name == currentFontName, null, file) {
                importFont(file, name, target)
            }
        }
        // 3. 自选字体文件夹（SAF）：指定任意目录后每次打开自动列出；
        //    新系统 /sdcard 直扫拿不到字体，这条是主路径
        for (f in scanFolderFonts()) {
            addRow(f.name, f.name == currentFontName, null, f.uri) {
                importFontFromUri(f.uri, f.name, target)
            }
        }
        addRow(activity.getString(R.string.app_font_pick_folder), false, null, null) {
            pickFontFolder(target)
        }
        // 4. 从文件选择（单个文件）
        addRow(activity.getString(R.string.app_font_pick_file), false, null, null) {
            pickFontFile(target)
        }

        root.addView(scroll)
        dialog.setContentView(root)
        dialog.show()

        // 预览懒加载：只为进入可视区的行解码字体文件，墨水屏上不卡顿
        fun loadVisiblePreviews() {
            val visibleTop = scroll.scrollY - dpx(120)
            val visibleBottom = scroll.scrollY + scroll.height + dpx(120)
            val iterator = pendingPreviews.iterator()
            while (iterator.hasNext()) {
                val (row, source) = iterator.next()
                if (row.top in visibleTop..visibleBottom) {
                    val tf = when (source) {
                        is File -> runCatching { Typeface.createFromFile(source) }.getOrNull()
                        is Uri -> typefaceFromUri(source)
                        else -> null
                    }
                    tf?.let { row.typeface = it }
                    iterator.remove()
                }
            }
        }
        scroll.viewTreeObserver.addOnScrollChangedListener { loadVisiblePreviews() }
        scroll.post { loadVisiblePreviews() }
    }

    private fun scanFontFiles(): List<File> {
        val result = linkedSetOf<File>()
        for (dir in FONT_SCAN_DIRS) {
            val folder = File(dir)
            val files = runCatching {
                folder.listFiles { f -> f.isFile && f.extension.lowercase() in FONT_EXTENSIONS }
            }.getOrNull()
            if (files != null) result.addAll(files)
            if (result.size >= 80) break
        }
        return result.sortedBy { it.name.lowercase() }
    }

    /** 把字体拷进私有目录并立即生效；校验失败回滚并提示。 */
    private fun importFont(source: File, displayName: String, target: Target) {
        val fontsDir = File(activity.filesDir, "fonts").apply { mkdirs() }
        val ext = source.extension.lowercase().ifEmpty { "ttf" }
        val dest = File(fontsDir, if (target == Target.CLOCK) "clock.$ext" else "launcher.$ext")
        val copied = runCatching {
            source.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output, BUFFER_SIZE) }
            }
            dest.length() > 0
        }.getOrDefault(false)
        if (!copied) {
            dest.delete()
            toast(activity.getString(R.string.app_font_invalid))
            return
        }
        val tf = runCatching { Typeface.createFromFile(dest) }.getOrNull()
        if (tf == null) {
            dest.delete()
            toast(activity.getString(R.string.app_font_invalid))
            return
        }
        apply(target, dest.absolutePath, displayName)
        toast(activity.getString(R.string.app_font_applied, displayName))
    }

    /** 从系统文件选择器挑单个字体（不需要存储权限）：优先 DocumentsUI 的
     *  OPEN_DOCUMENT，失败再退 GET_CONTENT / chooser，全打不开时明确提示。 */
    private fun pickFontFile(target: Target) {
        pendingTarget = target
        val mimeTypes = arrayOf("font/ttf", "font/otf", "application/octet-stream", "application/x-font-ttf")
        val open = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        if (runCatching { activity.startActivityForResult(open, REQ_PICK_FONT) }.isSuccess) return
        val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        if (runCatching { activity.startActivityForResult(getContent, REQ_PICK_FONT) }.isSuccess) return
        if (runCatching {
                activity.startActivityForResult(Intent.createChooser(getContent, null), REQ_PICK_FONT)
            }.isSuccess
        ) return
        toast(activity.getString(R.string.font_picker_unavailable))
    }

    /** 让用户自选字体文件夹（SAF 树权限，跨重启保留）：选完保存配置并重开字体对话框。 */
    private fun pickFontFolder(target: Target) {
        // SAF 树选择从 API 21 开始提供；更早版本只能走「从文件选择」
        if (Build.VERSION.SDK_INT < 21) {
            toast(activity.getString(R.string.font_folder_pick_failed))
            return
        }
        pendingTarget = target
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            // READ 让本次可读；PERSISTABLE 让授权跨重启保留（否则重开应用后扫描为空）
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        if (runCatching { activity.startActivityForResult(intent, REQ_PICK_FONT_FOLDER) }.isSuccess) return
        if (runCatching {
                activity.startActivityForResult(Intent.createChooser(intent, null), REQ_PICK_FONT_FOLDER)
            }.isSuccess
        ) return
        toast(activity.getString(R.string.font_folder_pick_failed))
    }

    /** 自选文件夹里的一个字体（SAF 文档 URI + 展示名）。 */
    private data class FolderFont(val uri: Uri, val name: String)

    /** 扫描用户自选的字体文件夹（DocumentsContract 列子文档，按扩展名过滤）。 */
    private fun scanFolderFonts(): List<FolderFont> {
        if (Build.VERSION.SDK_INT < 21) return emptyList()
        val uriStr = config().fontFolderUri
        if (uriStr.isBlank()) return emptyList()
        return runCatching {
            val tree = Uri.parse(uriStr)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree)
            )
            val result = mutableListOf<FolderFont>()
            activity.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null, null, null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIdx) ?: continue
                    val ext = name.substringAfterLast('.', "").lowercase()
                    if (ext !in FONT_EXTENSIONS) continue
                    result.add(
                        FolderFont(
                            DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(idIdx)),
                            name.substringBeforeLast('.')
                        )
                    )
                }
            }
            result.sortBy { it.name.lowercase() }
            result
        }.getOrDefault(emptyList())
    }

    /** SAF 文档字体预览：Typeface 不认 content URI，复制到 cache 临时文件再解码。 */
    private fun typefaceFromUri(uri: Uri): Typeface? {
        val tmp = File(activity.cacheDir, "font_preview_${uri.hashCode()}.ttf")
        return runCatching {
            activity.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output, BUFFER_SIZE) }
            }
            Typeface.createFromFile(tmp)
        }.getOrNull()
    }

    /** 从 SAF 文档导入字体：流复制进私有字体目录，校验后立即生效。 */
    private fun importFontFromUri(uri: Uri, displayName: String, target: Target) {
        val fontsDir = File(activity.filesDir, "fonts").apply { mkdirs() }
        val name = displayName.substringBeforeLast('.')
        val ext = displayName.substringAfterLast('.', "ttf").lowercase()
            .takeIf { it in FONT_EXTENSIONS } ?: "ttf"
        val dest = File(fontsDir, if (target == Target.CLOCK) "clock.$ext" else "launcher.$ext")
        val copied = runCatching {
            activity.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output, BUFFER_SIZE) }
            } != null && dest.length() > 0
        }.getOrDefault(false)
        if (!copied) {
            dest.delete()
            toast(activity.getString(R.string.app_font_invalid))
            return
        }
        val tf = runCatching { Typeface.createFromFile(dest) }.getOrNull()
        if (tf == null) {
            dest.delete()
            toast(activity.getString(R.string.app_font_invalid))
            return
        }
        apply(target, dest.absolutePath, name)
        toast(activity.getString(R.string.app_font_applied, name))
    }

    private fun apply(target: Target, path: String, displayName: String) {
        val newConfig = when (target) {
            Target.CLOCK -> config().copy(clockFontPath = path, clockFontName = displayName)
            Target.GLOBAL -> config().copy(appFontPath = path, appFontName = displayName)
        }
        onConfigChanged(newConfig, SettingsController.AFFECT_FONT)
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()

    // ---------------------------------------------------------------- results

    /** @return true 表示已消费；Activity 不再处理。 */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == REQ_PICK_FONT) {
            val uri = data?.data
            if (resultCode == Activity.RESULT_OK && uri != null) {
                val display = queryDisplayName(uri)
                val name = display?.substringBeforeLast('.') ?: "自定义字体"
                val ext = display?.substringAfterLast('.', "ttf")?.lowercase()
                    ?.takeIf { it in FONT_EXTENSIONS } ?: "ttf"
                val tmp = File(activity.cacheDir, "font_pick.$ext")
                val ok = runCatching {
                    activity.contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output, BUFFER_SIZE) }
                    } != null && tmp.length() > 0
                }.getOrDefault(false)
                val valid = ok && runCatching { Typeface.createFromFile(tmp) }.getOrNull() != null
                if (valid) {
                    importFont(tmp, name, pendingTarget)
                } else {
                    toast(activity.getString(R.string.app_font_invalid))
                }
                tmp.delete()
            }
            return true
        }
        if (requestCode == REQ_PICK_FONT_FOLDER) {
            val uri = data?.data
            if (resultCode == Activity.RESULT_OK && uri != null) {
                if (Build.VERSION.SDK_INT >= 19) {
                    runCatching {
                        activity.contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }
                onConfigChanged(config().copy(fontFolderUri = uri.toString()), 0)
                if (scanFolderFonts().isEmpty()) toast(activity.getString(R.string.font_folder_empty))
                showDialog(pendingTarget)
            }
            return true
        }
        return false
    }

    fun onRequestPermissionsResult(requestCode: Int, grantResults: IntArray): Boolean {
        if (requestCode != REQ_READ_STORAGE) return false
        if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            // 没有目录扫描权限，仍可走文件选择器
            toast(activity.getString(R.string.app_font_need_permission))
        }
        showDialog(pendingTarget)
        return true
    }

    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()

    /** minSdk 17，用不上 API 23 的 Context.getColor。 */
    @Suppress("DEPRECATION")
    private fun color(res: Int): Int = activity.resources.getColor(res)
}
