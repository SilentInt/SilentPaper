package com.silent.paper

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Typeface
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewStub
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.app.Activity
import com.silent.paper.data.AppRepository
import com.silent.paper.data.LauncherConfig
import com.silent.paper.data.LauncherConfigStore
import com.silent.paper.data.RecentTasks
import com.silent.paper.data.grid.GridSizing
import com.silent.paper.ui.EInkLauncherView
import com.silent.paper.ui.IconLoader
import com.silent.paper.ui.SettingsController
import com.silent.paper.ui.SevenSegmentClockView
import com.silent.paper.ui.AnalogClockView
import com.silent.paper.ui.FlipClockView
import com.silent.paper.ui.MatrixClockView
import com.silent.paper.ui.RoundClockView
import com.silent.paper.ui.StatusBarController
import com.silent.paper.ui.WallpaperController
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

object StartupTracer {
    private const val TAG = "StartupTrace"
    private var last = 0L

    fun start() {
        last = android.os.SystemClock.elapsedRealtime()
    }

    fun stage(name: String) {
        val now = android.os.SystemClock.elapsedRealtime()
        Log.i(TAG, "[$name] +${now - last}ms")
        last = now
    }
}

class LauncherActivity : Activity() {

    private lateinit var store: LauncherConfigStore
    private lateinit var repository: AppRepository
    private lateinit var config: LauncherConfig

    private lateinit var statusController: StatusBarController
    private lateinit var settingsController: SettingsController

    // Pages
    private lateinit var pageHome: View
    private lateinit var pageApps: View
    private lateinit var pageSettings: View

    // Home
    private lateinit var textTime: TextView
    private lateinit var textDate: TextView
    private lateinit var gridHome: EInkLauncherView

    // 两套顶部布局（经典居中 / 左右分栏），同一份 page_home 里各一份，靠 GONE 切换
    private lateinit var homeTopClassic: View
    private lateinit var homeTopSplit: View
    private lateinit var textTimeSplit: TextView
    private lateinit var textDateSplit: TextView

    // 纯文字主页模式（homeStyle=1）：大时间 + 信息行 + 应用名列表 + 底部标语
    private lateinit var homeTextMode: View
    /** 时钟区（图标/纯文字主页共用）：12 样式变体 + 电池/WiFi/蓝牙快捷行 + 信息行，onCreate 时从 homeTextMode 重挂进来 */
    private lateinit var homeClockArea: LinearLayout
    private lateinit var homeQuickRow: View
    /** 时钟区落位重挂目标：左右分栏的左列内层；经典状态行作为插入位置基准 */
    private lateinit var homeTopSplitLeftInner: ViewGroup
    private lateinit var statusBarRow: View
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
    private lateinit var homeInfoLine: TextView
    private lateinit var textListScroll: ScrollView
    private lateinit var textList: LinearLayout
    private lateinit var homeSloganText: TextView

    // 壁纸
    private lateinit var wallpaperController: WallpaperController

    // APP 字体：全局换字，缓存按「路径+mtime+大小」去重；换回默认时还原每个 TextView 原始字面。
    private var cachedTypeface: Typeface? = null
    private var cachedTypefaceKey: String = ""
    private var cachedClockTypeface: Typeface? = null
    private var cachedClockTypefaceKey: String = ""
    /** 当前已设背景的时钟样式（-1 = 未没置），避免每次都重设背景引发墨水屏闪刷。 */
    private var appliedClockStyleBg = -1
    /** 权限弹窗期间暂存的字体目标（返回后继续打开对应对话框）。 */
    private var pendingFontTarget: FontTarget = FontTarget.GLOBAL
    private val originalTypefaces = java.util.WeakHashMap<View, Typeface>()

    /** 字体设置目标：全局 = 所有界面；时钟 = 仅纯文字主页的大时间。 */
    private enum class FontTarget { GLOBAL, CLOCK }

    // 状态栏与网格之间的横线。跟 showHomeDividers 一起开关。
    private lateinit var homeTopDivider: View

    // Apps
    private lateinit var gridApps: EInkLauncherView
    private lateinit var textPage: TextView
    private lateinit var manageToggle: TextView
    private var appsAll: List<ResolveInfo> = emptyList()
    private var appsPageIndex = 0
    private var appsPageCount = 0
    private var currentPage = -1

    private var manageMode = false
    // manageMode 时区分两个批量管理入口：false = 应用页网格，true = 纯文字主页列表
    private var homeManage = false
    private val manageSelected = mutableSetOf<String>()
    private val uninstallQueue = mutableListOf<String>()

    // Bottom bar
    private lateinit var bottomBar: View
    private lateinit var infoTime: TextView
    private lateinit var tabHome: View
    private lateinit var tabApps: View
    private lateinit var tabSettings: View
    private lateinit var tabHomeLabel: TextView
    private lateinit var tabAppsLabel: TextView
    private lateinit var tabSettingsLabel: TextView
    private lateinit var dotHome: View
    private lateinit var dotApps: View
    private lateinit var dotSettings: View
    private lateinit var btnLock: View

    // Manage bar
    private lateinit var manageBar: View
    private lateinit var manageHide: TextView
    private lateinit var manageShow: TextView
    private lateinit var manageUninstall: TextView
    private lateinit var manageDone: TextView

    // Action sheet
    private lateinit var actionOverlay: View
    private lateinit var actionTitle: TextView
    private lateinit var actionPin: TextView
    private lateinit var actionHide: TextView
    private lateinit var actionBatch: TextView
    private lateinit var actionAppInfo: TextView
    private var selectedApp: ResolveInfo? = null

    // Recent tasks overlay
    private lateinit var recentOverlay: View
    private lateinit var recentPanel: View
    private lateinit var recentMessage: TextView
    private lateinit var recentGrid: EInkLauncherView
    private lateinit var recentCancel: TextView
    private lateinit var recentClear: TextView
    private var previousApp: ResolveInfo? = null
    private var lastHomeIntentTime = 0L
    private var lastHomeClickTime = 0L

    // 纯文字列表签名：应用集合 / 字号 / 行距 / 隐藏表没变就不重建，避免墨水屏白闪一屏
    private var textListSignature = ""

    // Info overlay
    private lateinit var infoOverlay: View
    private lateinit var infoTitle: TextView
    private lateinit var infoText: TextView
    // 原作者赞赏二维码（仅关于页显示，首次打开时生成一次）
    private lateinit var aboutDonationBlock: View
    private lateinit var aboutDonationQr: ImageView
    private var aboutDonationQrReady = false
    private lateinit var infoClose: View

    // Selection overlay
    private lateinit var selectOverlay: View
    private lateinit var selectTitle: TextView
    private lateinit var selectList: LinearLayout
    private var selectCallback: ((Int) -> Unit)? = null

    // WiFi overlay
    private lateinit var wifiOverlay: View
    private lateinit var wifiQr: ImageView
    private lateinit var wifiUrlText: TextView
    private lateinit var wifiHintText: TextView
    private var wifiServer: WifiTransferServer? = null

    // First-use guide overlay
    private lateinit var guideOverlay: View
    private lateinit var guideStart: View

    private var settingsInflated = false
    private var appsInflated = false
    private var appsLoaded = false
    private var coldStartResume = true

    // 最近一次实际使用的自适应行列（屏幕尺寸变化时用于判断是否需要重绑）
    private var currentHomeGrid = 0 to 0
    private var currentAppGrid = 0 to 0

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = updateClock()
    }

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val pkg = intent?.data?.schemeSpecificPart ?: return
            Log.i(TAG, "package changed: $pkg")
            repository.invalidatePackage(pkg)
            IconLoader.invalidatePackage(pkg)
            refreshHome()
            if (appsLoaded) refreshApps()
        }
    }

    /** 界面字号全局缩放：fontScale 作用到全部 sp 文本（含网格应用名与设置页）；
     *  改设置后由 onConfigChanged recreate() 重建时重新读取。prefs 名与
     *  LauncherConfigStore 一致（attachBaseContext 阶段拿不到 store 实例）。 */
    override fun attachBaseContext(newBase: Context) {
        val scale = runCatching {
            newBase.getSharedPreferences("silent_paper_config", Context.MODE_PRIVATE)
                .getInt("ui_font_scale", 100)
        }.getOrDefault(100).coerceIn(80, 140)
        val ctx = if (scale != 100) {
            val config = android.content.res.Configuration(newBase.resources.configuration)
            config.fontScale = config.fontScale * scale / 100f
            newBase.createConfigurationContext(config)
        } else newBase
        super.attachBaseContext(ctx)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTracer.start()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
        StartupTracer.stage("setContentView")
        // 状态栏与程序底色一致：纸色底 + 深色图标（API 23+；更早版本保持系统默认）。
        // 必须在 setContentView 之后：API 30+ 的 insetsController 依赖已安装的 DecorView，
        // 提前调用在 Android 11+ 直接 NPE（N3U / Android 14 实测崩溃根因）。
        if (Build.VERSION.SDK_INT >= 23) {
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            @Suppress("DEPRECATION")
            window.statusBarColor = color(R.color.paper)
            if (Build.VERSION.SDK_INT >= 30) {
                window.insetsController?.setSystemBarsAppearance(
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }
        installGridLayoutHook()

        store = LauncherConfigStore(this)
        repository = AppRepository(this)
        config = store.load()
        wallpaperController = WallpaperController(this)
        StartupTracer.stage("store/repo/config")

        IconLoader.initialize(applicationContext)
        IconLoader.configure(config)
        StartupTracer.stage("iconloader-init")

        bindViews()
        StartupTracer.stage("bindViews")

        setupControllers()
        StartupTracer.stage("setupControllers")

        setupTabs()
        StartupTracer.stage("setupTabs")

        setupLock()
        StartupTracer.stage("setupLock")

        setupActionSheet()
        StartupTracer.stage("setupActionSheet")

        setupRecentOverlay()
        StartupTracer.stage("setupRecentOverlay")

        setupInfoOverlay()
        StartupTracer.stage("setupInfoOverlay")

        setupSelectOverlay()
        StartupTracer.stage("setupSelectOverlay")

        setupWifiTransfer()
        StartupTracer.stage("setupWifiTransfer")

        setupManage()
        StartupTracer.stage("setupManage")

        setupGrids()
        StartupTracer.stage("setupGrids")

        refreshHome()
        StartupTracer.stage("refreshHome")

        window.decorView.postDelayed({
            if (!appsLoaded) refreshApps()
        }, 500)
        StartupTracer.stage("postRefreshApps")

        selectPage(PAGE_HOME, animate = false)
        StartupTracer.stage("selectPage")

        // 静态文本（底部栏、浮层、引导页）首次套用全局字体
        refreshAppFont()
        StartupTracer.stage("refreshAppFont")

        registerTimeReceiver()
        registerPackageReceiver()
        updateClock()
        statusController.update()
        StartupTracer.stage("clock/status")

        lastHomeIntentTime = System.currentTimeMillis()

        maybeShowGuide()
        StartupTracer.stage("guide")
    }

    // -------------------- 原有方法 --------------------
    private fun maybeShowGuide() {
        if (store.isGuideShown()) return
        guideOverlay.visibility = View.VISIBLE
        guideStart.setOnClickListener {
            guideOverlay.visibility = View.GONE
            store.setGuideShown()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        statusController.destroy()
        runCatching { unregisterReceiver(timeReceiver) }
        runCatching { unregisterReceiver(packageReceiver) }
        wifiServer?.stop()
        wifiServer = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleHomeIntent(intent)
    }

    private fun handleHomeIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_MAIN) return
        val isHome = intent.categories?.contains(Intent.CATEGORY_HOME) == true
        if (!isHome) return

        val now = System.currentTimeMillis()
        if (recentOverlay.visibility == View.VISIBLE) {
            hideRecentTasks()
            lastHomeIntentTime = 0L
            return
        }
        if (now - lastHomeIntentTime in 1..799) {
            lastHomeIntentTime = 0L
            showRecentTasks()
        } else {
            lastHomeIntentTime = now
        }
    }

    override fun onResume() {
        StartupTracer.stage("onResume-enter")
        super.onResume()
        val newConfig = store.load()
        val configChanged = newConfig != config
        if (configChanged) {
            config = newConfig
            settingsController.updateValues(config)
            refreshHome()
            refreshApps()
        } else {
            if (!coldStartResume) refreshHome()
        }
        updateClock()
        statusController.update()
        coldStartResume = false
    }

    // ------------------------------------------------------------------ Setup

    private fun bindViews() {
        pageHome = findViewById(R.id.pageHome)

        textTime = findViewById(R.id.textTime)
        textDate = findViewById(R.id.textDate)
        gridHome = findViewById(R.id.gridHome)
        homeTopDivider = findViewById(R.id.homeTopDivider)
        homeTopClassic = findViewById(R.id.homeTopClassic)
        homeTopSplit = findViewById(R.id.homeTopSplit)
        textTimeSplit = findViewById(R.id.textTimeSplit)
        textDateSplit = findViewById(R.id.textDateSplit)
        homeTopSplitLeftInner = findViewById(R.id.homeTopSplitLeftInner)
        statusBarRow = findViewById(R.id.statusBar)

        homeTextMode = findViewById(R.id.homeTextMode)
        homeBigTimeRow = findViewById(R.id.homeBigTimeRow)
        homeBigTimeHour = findViewById(R.id.homeBigTimeHour)
        homeBigTimeMin = findViewById(R.id.homeBigTimeMin)
        homeBigTimeSep = findViewById(R.id.homeBigTimeSep)
        homeBigTimeColon = findViewById(R.id.homeBigTimeColon)
        homeBigTimeStack = findViewById(R.id.homeBigTimeStack)
        homeBigTimeHourStack = findViewById(R.id.homeBigTimeHourStack)
        homeBigTimeMinStack = findViewById(R.id.homeBigTimeMinStack)
        homeClockSeg = findViewById(R.id.homeClockSeg)
        homeClockFlip = findViewById(R.id.homeClockFlip)
        homeClockRound = findViewById(R.id.homeClockRound)
        homeClockVSplit = findViewById(R.id.homeClockVSplit)
        homeVSplitHour = findViewById(R.id.homeVSplitHour)
        homeVSplitMin = findViewById(R.id.homeVSplitMin)
        homeVSplitDate = findViewById(R.id.homeVSplitDate)
        homeVSplitBatt = findViewById(R.id.homeVSplitBatt)
        homeClockHorizontal = findViewById(R.id.homeClockHorizontal)
        homeHorizTime = findViewById(R.id.homeHorizTime)
        homeHorizDate = findViewById(R.id.homeHorizDate)
        homeHorizBatt = findViewById(R.id.homeHorizBatt)
        homeClockAnalog = findViewById(R.id.homeClockAnalog)
        homeClockMatrix = findViewById(R.id.homeClockMatrix)
        homeInfoLine = findViewById(R.id.homeInfoLine)
        homeClockArea = findViewById(R.id.homeClockArea)
        homeQuickRow = findViewById(R.id.homeQuickRow)
        hoistClockViews()
        textListScroll = findViewById(R.id.textListScroll)
        textList = findViewById(R.id.textList)
        homeSloganText = findViewById(R.id.homeSlogan)

        bottomBar = findViewById(R.id.bottomBar)
        infoTime = findViewById(R.id.infoTime)
        tabHome = findViewById(R.id.tabHome)
        tabApps = findViewById(R.id.tabApps)
        tabSettings = findViewById(R.id.tabSettings)
        tabHomeLabel = findViewById(R.id.tabHomeLabel)
        tabAppsLabel = findViewById(R.id.tabAppsLabel)
        tabSettingsLabel = findViewById(R.id.tabSettingsLabel)
        dotHome = findViewById(R.id.dotHome)
        dotApps = findViewById(R.id.dotApps)
        dotSettings = findViewById(R.id.dotSettings)
        btnLock = findViewById(R.id.btnLock)

        manageBar = findViewById(R.id.manageBar)
        manageHide = findViewById(R.id.manageHide)
        manageShow = findViewById(R.id.manageShow)
        manageUninstall = findViewById(R.id.manageUninstall)
        manageDone = findViewById(R.id.manageDone)

        actionOverlay = findViewById(R.id.actionOverlay)
        actionTitle = findViewById(R.id.actionTitle)
        actionPin = findViewById(R.id.actionPin)
        actionHide = findViewById(R.id.actionHide)
        actionBatch = findViewById(R.id.actionBatch)
        actionAppInfo = findViewById(R.id.actionAppInfo)

        recentOverlay = findViewById(R.id.recentOverlay)
        recentPanel = findViewById(R.id.recentPanel)
        recentMessage = findViewById(R.id.recentMessage)
        recentGrid = findViewById(R.id.recentGrid)
        recentCancel = findViewById(R.id.recentCancel)
        recentClear = findViewById(R.id.recentClear)

        infoOverlay = findViewById(R.id.infoOverlay)
        infoTitle = findViewById(R.id.infoTitle)
        infoText = findViewById(R.id.infoText)
        aboutDonationBlock = findViewById(R.id.aboutDonationBlock)
        aboutDonationQr = findViewById(R.id.aboutDonationQr)
        infoClose = findViewById(R.id.infoClose)

        selectOverlay = findViewById(R.id.selectOverlay)
        selectTitle = findViewById(R.id.selectTitle)
        selectList = findViewById(R.id.selectList)

        wifiOverlay = findViewById(R.id.wifiOverlay)
        wifiQr = findViewById(R.id.wifiQr)
        wifiUrlText = findViewById(R.id.wifiUrlText)
        wifiHintText = findViewById(R.id.wifiHintText)

        guideOverlay = findViewById(R.id.guideOverlay)
        guideStart = findViewById(R.id.guideStart)
    }

    private fun setupControllers() {
        statusController = StatusBarController(this).also {
            it.bind()
            it.setup()
            it.setIconStyle(config.homeTopIconStyle)
        }
    }

    private fun ensureAppsPage() {
        if (appsInflated) return
        findViewById<ViewStub>(R.id.pageAppsStub).inflate()
        pageApps = findViewById(R.id.pageApps)
        gridApps = findViewById(R.id.gridApps)
        textPage = findViewById(R.id.textPage)
        manageToggle = findViewById(R.id.manageToggle)
        appsInflated = true

        manageToggle.setOnClickListener {
            if (manageMode) exitManageMode() else enterManageMode()
        }
        gridApps.setOnItemInteractionListener(object : EInkLauncherView.OnItemInteractionListener {
            override fun onItemClick(info: ResolveInfo) {
                if (manageMode) toggleManageSelect(info) else launchApp(info)
            }

            override fun onItemLongClick(info: ResolveInfo) {
                if (!manageMode) showActionSheet(info)
            }
        })

        gridApps.setOnPageChangeListener(object : EInkLauncherView.OnPageChangeListener {
            override fun onNextPage() {
                if (appsPageIndex < appsPageCount - 1) showAppsPage(appsPageIndex + 1)
                else selectPage(PAGE_SETTINGS, animate = true)
            }
            override fun onPrevPage() {
                if (appsPageIndex > 0) showAppsPage(appsPageIndex - 1)
                else selectPage(PAGE_HOME, animate = true)
            }
        })

        // 应用页静态行（管理开关、页码）套一次全局字体
        refreshAppFont()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun ensureSettingsPage() {
        if (settingsInflated) return
        findViewById<ViewStub>(R.id.pageSettingsStub).inflate()
        pageSettings = findViewById(R.id.pageSettings)
        settingsInflated = true

        settingsController = SettingsController(
            this,
            { config },
            ::onConfigChanged,
            ::showSelectOverlay,
            ::pickWallpaper,
            ::removeWallpaper,
            { showFontPicker(FontTarget.GLOBAL) },
            { showFontPicker(FontTarget.CLOCK) }
        ).also {
            it.bind()
            it.setup(config)
        }

        findViewById<View>(R.id.rowHelp).setOnClickListener {
            showInfo(getString(R.string.settings_help), getString(R.string.help_text))
        }
        findViewById<View>(R.id.rowAbout).setOnClickListener {
            showInfo(getString(R.string.settings_about), getString(R.string.about_text))
        }
        // 设置页静态行（含新增分组）套一次全局字体
        refreshAppFont()

        val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 80
            private val SWIPE_VELOCITY_THRESHOLD = 100
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        selectPage(PAGE_APPS, animate = true)
                        return true
                    }
                }
                return false
            }
        })
        pageSettings.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }
    }

    private fun onConfigChanged(newConfig: LauncherConfig, affected: Int) {
        val oldScale = config.uiFontScale
        config = newConfig
        store.save(config)
        settingsController.updateValues(config)

        // 界面字号缩放变化：重建 Activity 让 fontScale 重新套满整树
        // （含图标网格标签与设置页），无需逐视图手动改尺寸。
        if (newConfig.uiFontScale != oldScale) {
            recreate()
            return
        }

        if (affected and SettingsController.AFFECT_ICONS != 0) {
            IconLoader.configure(config)
            refreshHome()
            refreshApps()
        }
        if (affected and SettingsController.AFFECT_HOME != 0) refreshHome()
        if (affected and SettingsController.AFFECT_APPS != 0) refreshApps()
        if (affected and SettingsController.AFFECT_TEXTHOME != 0) {
            // 沉浸式开关立即生效；纯文字模式边距/标语走 metrics、字号/行距重建列表；
            // 图标主页即时重排时钟区（修复改时钟样式/对齐需切页才生效）；
            // 时钟周边字号两主页通用，放最后覆盖 metrics 的信息行公式。
            applyImmersiveBars()
            if (config.homeStyle == 1) {
                applyTextHomeMetrics()
                populateTextList()
            } else {
                applyClockArea()
            }
            applyClockContextSize()
        }
        if (affected and SettingsController.AFFECT_WALLPAPER != 0) applyWallpaper()
        if (affected and SettingsController.AFFECT_FONT != 0) {
            // 全局换字：重建首页/应用页（网格 bind 时套字体），再整树补静态文本
            refreshHome()
            if (::gridApps.isInitialized) refreshApps()
            refreshAppFont()
        }
    }

    // ------------------------------------------------------------------ Home / Apps grids

    private fun setupGrids() {
        gridHome.setOnItemInteractionListener(object : EInkLauncherView.OnItemInteractionListener {
            override fun onItemClick(info: ResolveInfo) = launchApp(info)
            override fun onItemLongClick(info: ResolveInfo) = showActionSheet(info)
        })
        gridHome.setOnPageChangeListener(object : EInkLauncherView.OnPageChangeListener {
            override fun onNextPage() = selectPage(PAGE_APPS, animate = true)
            override fun onPrevPage() = Unit
        })
    }

    /**
     * Resolved home grid for the current screen. A stored value of 0 means "auto":
     * the launcher picks the most suitable grid for the measured panel
     * (see [GridSizing]) and keeps it until the user picks a specific value.
     */
    private fun resolvedHomeGrid(): Pair<Int, Int> {
        val widthDp = gridWidthDp(gridHome, 52f)
        val heightDp = gridHeightDp(gridHome, contentHeightDp() - 240f)
        val cols = GridSizing.resolveColumns(config.homeColumns, widthDp, GridSizing.columnCap(widthDp))
        // 主页行数上限按网格实际高度计算，避免选了过多行导致每行过窄。
        val homeRowCap = GridSizing.homeRowCap(heightDp)
        val rows = GridSizing.resolveHomeRows(config.homeRows, heightDp, cap = homeRowCap)
        return cols to rows
    }

    private fun resolvedAppGrid(): Pair<Int, Int> {
        val widthDp = gridWidthDp(gridApps, 48f)
        val heightDp = gridHeightDp(gridApps, contentHeightDp() - 40f)
        val cols = GridSizing.resolveColumns(config.columns, widthDp, GridSizing.columnCap(widthDp))
        val rows = GridSizing.resolveRows(config.rows, heightDp, cap = GridSizing.rowCap(heightDp))
        return cols to rows
    }

    /** 优先用网格自身的已测量尺寸，退化时用屏幕宽度减去布局边距。 */
    private fun gridWidthDp(grid: View, horizontalMarginsDp: Float): Float {
        val w = grid.width
        if (w > 0) return pxToDp(w)
        return (displayWidthDp() - horizontalMarginsDp).coerceAtLeast(160f)
    }

    /** 优先用网格自身的已测量高度；尚未布局时（冷启动）用估算值。 */
    private fun gridHeightDp(grid: View, fallbackDp: Float): Float {
        val h = grid.height
        if (h > 0) return pxToDp(h)
        return fallbackDp.coerceAtLeast(120f)
    }

    private fun displayWidthDp(): Float {
        val w = resources.displayMetrics.widthPixels
        return if (w > 0) pxToDp(w) else 360f
    }

    private fun contentHeightDp(): Float {
        val h = resources.displayMetrics.heightPixels
        return if (h > 0) pxToDp(h) else 480f
    }

    private fun displayHeightDp(): Float = contentHeightDp()

    /** 屏幕尺寸或方向变化后，重算自适应行列 */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::config.isInitialized) return
        // 布局尺寸在配置变化后才更新，等一帧再算
        window.decorView.post {
            if (isFinishing) return@post
            // 视图可能尚未绑定（初始化早期收到配置变化），此时直接跳过。
            if (!::gridHome.isInitialized || !::gridApps.isInitialized) return@post
            val homeGrid = resolvedHomeGrid()
            if (homeGrid.first != currentHomeGrid.first || homeGrid.second != currentHomeGrid.second) {
                currentHomeGrid = homeGrid
                refreshHome()
            }
            val appGrid = resolvedAppGrid()
            if (appsLoaded && (appGrid.first != currentAppGrid.first || appGrid.second != currentAppGrid.second)) {
                currentAppGrid = appGrid
                refreshApps()
            }
            if (::settingsController.isInitialized) settingsController.updateValues(config)
        }
    }

    /**
     * Cold start is measured before the grids exist, so the first grid uses a
     * conservative estimate. Once the window has been laid out, resolve the real
     * panel size and re-apply the automatic grid if it changed.
     */
    private fun installGridLayoutHook() {
        val target = window.decorView
        target.viewTreeObserver.addOnGlobalLayoutListener(object :
            android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (!::config.isInitialized) return
                if (target.viewTreeObserver.isAlive) {
                    target.viewTreeObserver.removeOnGlobalLayoutListener(this)
                }
                window.decorView.post {
                    if (isFinishing) return@post
                    // 冷启动时该回调可能早于 setupControllers() 执行，
                    // 控制器未创建前不要触碰它（lateinit 未初始化会崩溃）。
                    if (!::settingsController.isInitialized) return@post
                    val homeGrid = resolvedHomeGrid()
                    if (homeGrid != currentHomeGrid) {
                        currentHomeGrid = homeGrid
                        refreshHome()
                    }
                    if (appsLoaded) {
                        val appGrid = resolvedAppGrid()
                        if (appGrid != currentAppGrid) {
                            currentAppGrid = appGrid
                            refreshApps()
                        }
                    }
                    // 网格尺寸确定后，设置页显示的实际行列数才准确。
                    settingsController.updateValues(config)
                }
            }
        })
    }

    private fun pxToDp(px: Int): Float = px / resources.displayMetrics.density

    private fun refreshHome() {
        applyWallpaper()
        gridHome.labelStrokePx = resolvedTextStrokePx()
        gridHome.labelTypeface = launcherTypeface()

        // 纯文字模式：顶部两套块 / 横线 / 网格全部隐藏，列表吃满剩余空间
        if (config.homeStyle == 1) {
            homeTopClassic.visibility = View.GONE
            homeTopSplit.visibility = View.GONE
            homeTopDivider.visibility = View.GONE
            gridHome.visibility = View.GONE
            homeTextMode.visibility = View.VISIBLE
            homeClockArea.visibility = View.VISIBLE
            applyTextHomeMetrics()
            StartupTracer.stage("refreshHome-textMetrics")
            populateTextList()
            StartupTracer.stage("refreshHome-textList")
            applyFontTree(pageHome)
            applyClockFont()
            applyClockContextSize()
            applyImmersiveBars()
            return
        }

        homeTextMode.visibility = View.GONE
        gridHome.visibility = View.VISIBLE
        // 时钟区与图标主页共用：顶部原生时间文字隐藏，12 种样式时钟接管显示
            homeClockArea.visibility = View.VISIBLE
            if (textTime.visibility != View.GONE) textTime.visibility = View.GONE
            if (textTimeSplit.visibility != View.GONE) textTimeSplit.visibility = View.GONE
            applyClockArea()
            applyClockContextSize()

        val apps = repository.loadApps()
        StartupTracer.stage("refreshHome-loadApps")
        val (columns, rows) = resolvedHomeGrid()
        currentHomeGrid = columns to rows
        gridHome.configure(columns, rows)
        // 横线跟着主页分割线开关走。用 INVISIBLE 而不是 GONE：GONE 的子 View 在
        // LinearLayout 里会被跳过 measure/layout，边距跟着失效，网格会整体上移。
        homeTopDivider.visibility =
            if (config.showHomeDividers) View.VISIBLE else View.INVISIBLE
        applyHomeTopStyle()
        // 图标风格与版式同在顶部这一块，跟着一起刷新；setIconStyle 内部有等值早退。
        if (::statusController.isInitialized) statusController.setIconStyle(config.homeTopIconStyle)
        val limit = columns * rows
        val sorted = repository.sortHomeApps(apps, limit)
        StartupTracer.stage("refreshHome-sort")
        gridHome.setApps(
            pageApps = sorted,
            selection = emptySet(),
            manageMode = false,
            showDividers = config.showHomeDividers,
            hidden = emptySet()
        )
        StartupTracer.stage("refreshHome-setApps")
        applyFontTree(pageHome)
        applyClockFont()
        applyImmersiveBars()
    }

    // ------------------------------------------------------------------ 纯文字主页

    /** 纯文字模式三项参数的「自动」基准：与参考版式同源。0 = 自动。 */
    private fun resolvedTextFontSize(): Int {
        val configured = config.textFontSize
        if (configured > 0) return configured.coerceIn(FONT_MIN_SP, FONT_MAX_SP)
        // 应用名字高 ≈ 屏高 2.4%；CJK 字形占 em 约 0.9 → sp = h×0.024/0.9/density
        val density = resources.displayMetrics.density
        val sp = resources.displayMetrics.heightPixels * 0.024f / 0.9f / density
        return ((sp / 2f).toInt() * 2).coerceIn(FONT_MIN_SP, FONT_MAX_SP)
    }

    private fun resolvedTextLineSpacing(): Int {
        val configured = config.textLineSpacing
        if (configured > 0) return configured.coerceIn(SPACING_MIN_DP, SPACING_MAX_DP)
        // 行间留白 ≈ 字高（0.85×字号，sp↔dp 在 density 上等价）
        val dp = resolvedTextFontSize() * 0.85f
        return ((dp / 4f).toInt() * 4).coerceIn(SPACING_MIN_DP, SPACING_MAX_DP)
    }

    private fun resolvedTextPadding(): Int {
        val configured = config.textPadding
        if (configured > 0) return configured.coerceIn(PADDING_MIN_DP, PADDING_MAX_DP)
        // 页边距 ≈ 屏宽 2.5%，步进 8 取整：内容整体靠左，给右侧壁画画作留位
        val density = resources.displayMetrics.density
        val dp = resources.displayMetrics.widthPixels * 0.025f / density
        return ((dp / 8f).toInt() * 8).coerceIn(PADDING_MIN_DP, PADDING_MAX_DP)
    }

    /** 大时间字号：0 = 自动（按样式限宽推算，时间块不遮挡中上部壁纸）；>0 = 手动。 */
    private fun resolvedBigTimeSizeSp(): Float {
        val configured = config.textTimeSize
        val density = resources.displayMetrics.density
        if (configured > 0) return configured.coerceIn(TIME_MIN_SP, TIME_MAX_SP).toFloat()
        val style = config.clockStyle.coerceIn(0, 11)
        val paddingPx = resolvedTextPadding() * density
        val widthPx = resources.displayMetrics.widthPixels
        // 堆叠样式块窄（仅 2 位数字一列），按参考图收在屏宽 1/6；圆环/表盘放宽到 40%，
        // 点阵横条收在屏宽 42%；其余样式收在屏宽 30%
        val budget = when (style) {
            2 -> widthPx * 0.166f - paddingPx
            6, 10 -> widthPx * 0.40f
            11 -> widthPx * 0.42f - paddingPx
            else -> widthPx * 0.30f - paddingPx
        }
        // 各样式的「块宽 ÷ em」系数：数码管的 em 即段高（总宽 2.74×段高）；
        // 翻页 = 4 卡(0.95em) + 缝 20dp + 卡外冒号；圆环/表盘直径 = 4em；
        // 竖线同分栏；横排 = 冒号时间 + 20dp + 信息列；线框 = 冒号 + 左右 16dp 内衬；
        // 点阵 = 33 列 × 0.22em 点距
        val emPx = when (style) {
            0 -> budget / 2.12f          // 4 位数字 + 竖线块
            1 -> budget / 2.38f          // 4 位数字 + 冒号
            2 -> budget / 1.06f          // 2 位数字一列（时上分下）
            3 -> budget / 2.74f          // 数码管总宽 = 2.74 × 字高
            4 -> budget / 2.88f          // 胶囊：4 位数字 + 冒号 + 两侧内衬
            5 -> (budget - 20f * density) / 4.30f
            6, 10 -> budget / 4.0f
            7 -> budget / 2.12f
            8 -> (budget - 20f * density) / 3.10f
            9 -> (budget - 32f * density) / 2.38f
            11 -> budget / 7.26f
            else -> budget / 2.88f
        }
        // 文本类样式 48sp 保底可读；自绘样式 em 是几何基准（卡高/直径/点距），各自放宽下限
        val floorSp = when (style) {
            5 -> 24f
            6, 10 -> 16f
            11 -> 12f
            else -> 48f
        }
        return (emPx / density).coerceAtLeast(floorSp)
    }

    /**
     * 纯文字模式版式总控：页边距、大时间字号、时钟块位置（上/下/左右偏移）、
     * 列表位置（上/下/左偏移/右缩进）、信息行字号与底部标语开关。
     * 行内字号与行距在 populateTextList 里逐行写。
     */
    /** 把 12 样式时钟变体与信息行从 homeTextMode 重挂到 pageHome 层级的 homeClockArea，
     *  使时钟在图标主页与纯文字主页共用；顺序：9 个变体 → WiFi/蓝牙行（XML 原生）→ 信息行。 */
    private fun hoistClockViews() {
        if (!::homeClockArea.isInitialized || homeClockArea.childCount > 1) return
        val ordered = listOf(
            homeBigTimeRow, homeBigTimeStack, homeClockSeg, homeClockFlip, homeClockRound,
            homeClockVSplit, homeClockHorizontal, homeClockAnalog, homeClockMatrix
        )
        for (v in ordered) {
            (v.parent as? ViewGroup)?.removeView(v)
            homeClockArea.addView(v, homeClockArea.indexOfChild(homeQuickRow))
        }
        (homeInfoLine.parent as? ViewGroup)?.removeView(homeInfoLine)
        homeClockArea.addView(homeInfoLine)
    }

    /**
     * 时钟区落位：纯文字主页挂在 pageHome 顶层（页边距内衬、clockTop/clockX/对齐
     * 全套生效）；图标主页挪进顶部原生结构、接管「原时间」槽位 —— 经典居中插在
     * 日期与状态行之间（match_parent、子块居中、8dp 上距、76sp 一档），左右分栏
     * 挪进左列（wrap_content，日期仍居中于时钟宽度、60sp 一档）。图标主页下时钟区
     * 无内衬，样式只换外观，不吃纯文字主页的边距设置（重挂幂等：parent 没变不动）。
     */
    private fun placeClockArea() {
        if (!::homeClockArea.isInitialized || !::homeTopSplitLeftInner.isInitialized) return
        val textMode = config.homeStyle == 1
        val classic = !textMode && config.homeTopStyle == 0
        val parent: ViewGroup = when {
            textMode -> pageHome as ViewGroup
            classic -> homeTopClassic as ViewGroup
            else -> homeTopSplitLeftInner
        }
        val linear = parent as? LinearLayout ?: return
        // 宽度策略：经典居中容器吃满宽（子块整体居中）；纯文字 / 分栏左列
        // 必须 wrap_content，整组锚定（gravity/边距）才起作用
        val desiredW = if (classic) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        if (homeClockArea.parent !== parent) {
            (homeClockArea.parent as? ViewGroup)?.removeView(homeClockArea)
            val lp = LinearLayout.LayoutParams(desiredW, ViewGroup.LayoutParams.WRAP_CONTENT)
            when {
                classic -> linear.addView(homeClockArea, linear.indexOfChild(statusBarRow).coerceAtLeast(0), lp)
                textMode -> linear.addView(homeClockArea, (linear.indexOfChild(homeTopSplit) + 1).coerceAtLeast(0), lp)
                else -> linear.addView(homeClockArea, 0, lp)
            }
        } else {
            // 已在目标容器（冷启动 XML 直挂 pageHome）：只纠宽度不重挂。
            // XML 宽度是 match_parent，若不纠正，文字模式的整组锚定
            // 对 match_parent 子项无效——左/右对齐全部失灵。
            val lp = homeClockArea.layoutParams
            if (lp.width != desiredW) {
                lp.width = desiredW
                homeClockArea.layoutParams = lp
            }
        }
    }

    /** 时钟区（两主页共用）：落位（纯文字主页整组 wrap_content 锚定，页边距与偏移
     *  写在容器 LP 上）+ 样式/字号/对齐。 */
    private fun applyClockArea() {
        if (!::homeClockArea.isInitialized) return
        placeClockArea()
        if (homeClockArea.paddingLeft != 0 || homeClockArea.paddingRight != 0) {
            homeClockArea.setPadding(0, 0, 0, 0)
        }
        applyClockStyleAndAlign()
    }

    /** 纯文字主页沉浸式：开关开启 + 纯文字主页 + 主页页 → 隐藏系统状态栏与导航栏；
     *  其余场景恢复显示（API 30+ 走 WindowInsetsController，旧版本走 systemUiVisibility）。 */
    private fun applyImmersiveBars() {
        if (!::homeClockArea.isInitialized) return
        val hide = config.textHomeHideBars && config.homeStyle == 1 && currentPage == PAGE_HOME
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let { c ->
                if (hide) {
                    c.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    c.hide(android.view.WindowInsets.Type.systemBars())
                } else {
                    c.show(android.view.WindowInsets.Type.systemBars())
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val immersive = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            @Suppress("DEPRECATION")
            val lightBars = if (Build.VERSION.SDK_INT >= 23) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                if (hide) immersive else View.SYSTEM_UI_FLAG_LAYOUT_STABLE or lightBars
        }
    }

    private fun applyTextHomeMetrics() {
        if (!::homeTextMode.isInitialized) return
        val density = resources.displayMetrics.density
        val padDp = resolvedTextPadding()
        val pad = dp(padDp)
        if (homeTextMode.paddingLeft != pad || homeTextMode.paddingRight != pad) {
            homeTextMode.setPadding(pad, 0, pad, 0)
        }
        // ---- 时钟区（两主页共用）：内衬 + 12 种样式 + 字号 + 对齐 ----
        applyClockArea()

        // 信息行随应用字号缩放，约为 45%，下限 12sp
        val infoSp = (resolvedTextFontSize() * 0.45f).coerceAtLeast(12f)
        if (homeInfoLine.textSize != infoSp * density) {
            homeInfoLine.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, infoSp)
        }

        // ---- 列表：上/下边距 + 左偏移（可负，最多退到屏幕边）+ 右缩进 ----
        val listTopPx = dp(config.listTop.coerceIn(0, 512))
        val listBottomPx = dp(config.listBottom.coerceIn(0, 128))
        val listLeftPx = dp(config.listLeft.coerceIn(-padDp, 96))
        val listRightPx = dp(config.listRight.coerceIn(0, 256))
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

        // ---- 底部标语：开关 + 文字 ----
        val slogan = config.homeSlogan
        val showSlogan = config.homeSloganOn && slogan.isNotBlank()
        val desired = if (showSlogan) slogan else ""
        if (homeSloganText.visibility != (if (showSlogan) View.VISIBLE else View.GONE)) {
            homeSloganText.visibility = if (showSlogan) View.VISIBLE else View.GONE
        }
        if (homeSloganText.text.toString() != desired) homeSloganText.text = desired

        // 大时间用时钟字体（未选时钟字体时跟随全局，两者都没有则保持原样）
        applyClockFont()
    }

    /** 时钟周边内容字号（0 = 自动：日期 14sp / 状态行 13sp / 信息行按比例）。 */
    private fun resolvedClockContextSp(): Int = config.clockContextSize.coerceIn(0, 32)

    /**
     * 时钟周边字号总控：日期、状态行、纯文字快捷行与信息行统一到同一字号，
     * 图标主页与纯文字主页共用。用 DIP 写入——不受「界面字号」全局缩放影响，
     * 该旋钮独立生效；自动（0）时不动任何视图，保持各自默认尺寸。
     */
    private fun applyClockContextSize() {
        if (!::homeInfoLine.isInitialized) return
        val sp = resolvedClockContextSp()
        if (sp <= 0) return
        val unit = android.util.TypedValue.COMPLEX_UNIT_DIP
        textDate.setTextSize(unit, sp.toFloat())
        textDateSplit.setTextSize(unit, sp.toFloat())
        homeInfoLine.setTextSize(unit, sp.toFloat())
        if (::statusController.isInitialized) statusController.applyContextTextSize(sp)
    }

    /**
     * 时钟渲染总控：12 种样式（0 分栏 / 1 冒号 / 2 堆叠 / 3 数码管 / 4 胶囊 / 5 翻页 /
     * 6 圆环 / 7 竖线 / 8 横排 / 9 线框 / 10 表盘 / 11 点阵，后七种移植自 inkOS）
     * 切换显隐与配色，字号按样式自适应；对齐（0 左 / 1 中 / 2 右）作用于时钟与信息行，
     * 左右偏移在对齐锚点基础上叠加（右对齐时镜像到 marginEnd）。
     * 圆环 / 表盘为纯时钟，竖线 / 横排 / 点阵自带日期电量：这几类样式隐藏信息行。
     */
    private fun applyClockStyleAndAlign() {
        if (!::homeBigTimeHour.isInitialized) return
        val style = config.clockStyle.coerceIn(0, 11)
        val align = config.clockAlign.coerceIn(0, 2)
        val density = resources.displayMetrics.density
        val textMode = config.homeStyle == 1
        // 图标主页：字号固定原版一档（经典 76sp / 分栏 60sp，堆叠两行折半），
        // 自绘样式按「块高 ≈ 原版时间字高」换算 em —— 只换外观，不占更大的地盘
        val gridBaseSp = if (config.homeTopStyle == 1) 60f else 76f
        val timeSp = if (textMode) resolvedBigTimeSizeSp()
                     else if (style == 2) gridBaseSp * 0.48f
                     else gridBaseSp
        val timePx = timeSp * density

        // ---- 自绘/复合样式防溢出：纯文字主页按内容宽度收顶（自动字号不会触顶，
        //      文本行样式 0-4/9 保持 v1.7 行为不动）；图标主页按原版槽位换算 em，
        //      分栏时预算收窄到左列 ----
        val widthPx = resources.displayMetrics.widthPixels
        val contentW = widthPx - 2f * dp(resolvedTextPadding())
        val budget = if (textMode) contentW
                     else if (config.homeTopStyle == 1) widthPx * 0.45f
                     else widthPx * 0.92f
        val emMax = when (style) {
            5 -> (budget - 20f * density) / 4.30f
            6, 10 -> budget / 4.0f
            7 -> budget / 2.12f
            8 -> (budget - 20f * density) / 3.10f
            11 -> budget / 7.26f
            else -> Float.MAX_VALUE
        }
        // 图标主页的目标块高：翻页卡高 1.35em；圆环/表盘直径 2×base（圆件最小可读）；
        // 竖线 0.7base + 小字行；点阵五行 1.1em。纯文字主页仍用 timePx（用户字号）。
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

        // ---- 变体显隐：行（分栏/冒号/胶囊/线框共用）/ 堆叠 / 数码管 / 翻页 / 圆环 /
        //      竖线 / 横排 / 表盘 / 点阵，同一时刻只显示一个 ----
        val rowVisible = style == 0 || style == 1 || style == 4 || style == 9
        if (homeBigTimeRow.visibility != (if (rowVisible) View.VISIBLE else View.GONE))
            homeBigTimeRow.visibility = if (rowVisible) View.VISIBLE else View.GONE
        if (homeBigTimeStack.visibility != (if (style == 2) View.VISIBLE else View.GONE))
            homeBigTimeStack.visibility = if (style == 2) View.VISIBLE else View.GONE
        if (homeClockSeg.visibility != (if (style == 3) View.VISIBLE else View.GONE))
            homeClockSeg.visibility = if (style == 3) View.VISIBLE else View.GONE
        if (homeClockFlip.visibility != (if (style == 5) View.VISIBLE else View.GONE))
            homeClockFlip.visibility = if (style == 5) View.VISIBLE else View.GONE
        if (homeClockRound.visibility != (if (style == 6) View.VISIBLE else View.GONE))
            homeClockRound.visibility = if (style == 6) View.VISIBLE else View.GONE
        if (homeClockVSplit.visibility != (if (style == 7) View.VISIBLE else View.GONE))
            homeClockVSplit.visibility = if (style == 7) View.VISIBLE else View.GONE
        if (homeClockHorizontal.visibility != (if (style == 8) View.VISIBLE else View.GONE))
            homeClockHorizontal.visibility = if (style == 8) View.VISIBLE else View.GONE
        if (homeClockAnalog.visibility != (if (style == 10) View.VISIBLE else View.GONE))
            homeClockAnalog.visibility = if (style == 10) View.VISIBLE else View.GONE
        if (homeClockMatrix.visibility != (if (style == 11) View.VISIBLE else View.GONE))
            homeClockMatrix.visibility = if (style == 11) View.VISIBLE else View.GONE

        // ---- 行内分隔符：分栏用竖线，其余（冒号/胶囊/线框）用冒号 ----
        val showSep = style == 0
        if (homeBigTimeSep.visibility != (if (showSep) View.VISIBLE else View.GONE))
            homeBigTimeSep.visibility = if (showSep) View.VISIBLE else View.GONE
        if (homeBigTimeColon.visibility != (if (showSep) View.GONE else View.VISIBLE))
            homeBigTimeColon.visibility = if (showSep) View.GONE else View.VISIBLE

        // ---- 行背景与配色：胶囊黑底白字 + 四周内衬；线框 2dp 描边圆角盒 +
        //      左右 16dp / 上下 4dp 内衬（同 inkOS Box Outline）；其余样式无底墨色 ----
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

        // ---- 字号：文本类变体按 dip 写入（不受「界面字号」fontScale 影响，
        //      时钟自有「时间字号」旋钮）；自绘 View 把 em 写进各自几何；
        //      竖线/横排小字 0.35em 下限 14sp（同 inkOS） ----
        if (homeBigTimeHour.textSize != timePx) {
            homeBigTimeHour.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, timeSp)
            homeBigTimeMin.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, timeSp)
            homeBigTimeColon.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, timeSp)
            homeBigTimeHourStack.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, timeSp)
            homeBigTimeMinStack.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, timeSp)
            homeVSplitHour.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, emSp)
            homeVSplitMin.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, emSp)
            homeHorizTime.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, emSp)
        }
        if (homeClockSeg.digitHeight != timePx) homeClockSeg.digitHeight = timePx
        if (homeClockFlip.sizePx != emPx) homeClockFlip.sizePx = emPx
        if (homeClockRound.sizePx != emPx) homeClockRound.sizePx = emPx
        if (homeClockAnalog.sizePx != emPx) homeClockAnalog.sizePx = emPx
        if (homeClockMatrix.sizePx != emPx) homeClockMatrix.sizePx = emPx
        val smallSp = (emSp * 0.35f).coerceAtLeast(14f)
        if (homeVSplitDate.textSize != smallSp * density) {
            homeVSplitDate.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, smallSp)
            homeVSplitBatt.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, smallSp)
            homeHorizDate.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, smallSp)
            homeHorizBatt.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, smallSp)
        }

        // ---- 信息行：图标主页顶部块自带日期电量而隐藏；纯文字主页里圆环/表盘纯时钟、
        //      竖线/横排/点阵自带日期电量时隐藏，其余显示在时钟下方 ----
        val infoGone = !textMode || style == 6 || style == 7 || style == 8 || style == 10 || style == 11
        if (homeInfoLine.visibility != (if (infoGone) View.GONE else View.VISIBLE))
            homeInfoLine.visibility = if (infoGone) View.GONE else View.VISIBLE

        // ---- WiFi/蓝牙快捷行：仅纯文字主页显示（图标主页顶部已有状态行） ----
        if (homeQuickRow.visibility != (if (textMode) View.VISIBLE else View.GONE))
            homeQuickRow.visibility = if (textMode) View.VISIBLE else View.GONE

        // ---- 位置：上边距 + 对齐 + 左右偏移。纯文字主页按用户设置（信息行/快捷行
        //      跟随时钟锚点，宽度一起居中/对齐）；图标主页回原版位（经典居中 8dp 上距、
        //      分栏左列 0 上距），不吃偏移设置 ----
        val clockTopPx = dp(config.clockTop.coerceIn(8, 256))
        val clockBottomPx = dp(config.clockBottom.coerceIn(0, 96))
        val clockXPx = dp(config.clockX.coerceIn(-resolvedTextPadding(), 96))
        val gravity: Int
        val mStart: Int
        val mEnd: Int
        if (textMode) {
            // 纯文字主页：容器整组锚定（左/中/右 + 页边距与偏移，见 areaLp 段），
            // 子元素在组内全部居中——日期行/快捷行因此居中于「时钟宽度」范围内
            gravity = Gravity.CENTER_HORIZONTAL
            mStart = 0
            mEnd = 0
        } else {
            gravity = if (config.homeTopStyle == 1) Gravity.START else Gravity.CENTER_HORIZONTAL
            mStart = 0
            mEnd = 0
        }
        val clockTopEffective = if (textMode) clockTopPx
                                else if (config.homeTopStyle == 1) 0 else dp(8)
        for (v in listOf(homeBigTimeRow, homeBigTimeStack, homeClockSeg, homeClockFlip,
                homeClockRound, homeClockVSplit, homeClockHorizontal, homeClockAnalog, homeClockMatrix)) {
            val lp = v.layoutParams as? LinearLayout.LayoutParams ?: continue
            if (lp.topMargin != clockTopEffective || lp.gravity != gravity ||
                lp.marginStart != mStart || lp.marginEnd != mEnd
            ) {
                lp.topMargin = clockTopEffective
                lp.gravity = gravity
                lp.marginStart = mStart
                lp.marginEnd = mEnd
                v.layoutParams = lp
            }
        }
        // 快捷行跟随时钟锚点（同 gravity/偏移，宽度随对齐一起居中），上距固定 10dp
        val lpQuick = homeQuickRow.layoutParams as? LinearLayout.LayoutParams
        if (lpQuick != null && (lpQuick.gravity != gravity ||
                lpQuick.marginStart != mStart || lpQuick.marginEnd != mEnd)) {
            lpQuick.gravity = gravity
            lpQuick.marginStart = mStart
            lpQuick.marginEnd = mEnd
            homeQuickRow.layoutParams = lpQuick
        }
        val lpInfo = homeInfoLine.layoutParams as? LinearLayout.LayoutParams
        if (lpInfo != null && (lpInfo.topMargin != clockBottomPx || lpInfo.gravity != gravity ||
                lpInfo.marginStart != mStart || lpInfo.marginEnd != mEnd)) {
            lpInfo.topMargin = clockBottomPx
            lpInfo.gravity = gravity
            lpInfo.marginStart = mStart
            lpInfo.marginEnd = mEnd
            homeInfoLine.layoutParams = lpInfo
        }

        // ---- 纯文字主页：时钟区整组锚定。容器 wrap_content，宽度=最宽子元素（通常
        //      是时钟），用容器 LP 的 gravity/边距对齐整组；页边距与左右偏移合入
        //      边距（居中档的偏移转为整组平移：marginStart=2X → 实际右移 X） ----
        val padPx = dp(resolvedTextPadding())
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

    /**
     * 纯文字应用列表：默认显示全部可见应用（固定优先 → 点击次数排序）；
     * 批量管理模式下显示全部应用（含已隐藏，带 ○/●/隐 标记，单击切换选中）。
     * 单击启动，长按弹操作菜单（固定 / 隐藏 / 应用信息 / 批量管理）。
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun populateTextList() {
        if (!::textList.isInitialized) return
        val manage = homeManage
        val sorted = if (manage) repository.loadAllApps()
                     else repository.sortHomeApps(repository.loadApps(), Int.MAX_VALUE)

        val sizeSp = resolvedTextFontSize()
        val spacingDp = resolvedTextLineSpacing()
        val hidden = repository.hiddenPackages()
        // 签名不含选中集合：选中切换用行内原位更新，不重建列表（墨水屏友好）
        val signature = "$manage|$sizeSp|$spacingDp|${config.listAlign}|" +
                sorted.joinToString(",") { it.activityInfo.packageName } + "|" +
                hidden.joinToString(",")
        if (signature == textListSignature && textList.childCount > 0) return
        textListSignature = signature

        textList.removeAllViews()

        val ink = color(R.color.ink)
        val sizeSpF = sizeSp.toFloat()
        val spacingPx = dp(spacingDp)
        val minTouchPx = dp(44)

        sorted.forEachIndexed { index, info ->
            val row = TextView(this).apply {
                includeFontPadding = false
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                minHeight = minTouchPx
                gravity = when (config.listAlign.coerceIn(0, 2)) {
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
                        gravity = when (config.listAlign.coerceIn(0, 2)) {
                            1 -> Gravity.CENTER
                            2 -> Gravity.CENTER_VERTICAL or Gravity.END
                            else -> Gravity.CENTER_VERTICAL or Gravity.START
                        }
                    }
                }
                if (manage) {
                    updateHomeManageRow(this, info, hidden)
                    setOnClickListener { toggleHomeManageSelect(info, this) }
                } else {
                    text = info.loadLabel(packageManager)
                    setOnClickListener { launchApp(info) }
                    setOnLongClickListener {
                        showActionSheet(info)
                        true
                    }
                }
            }
            textList.addView(row)
        }
        textListScroll.scrollTo(0, 0)
        applyFontTree(textList)
    }

    /** 批量管理模式下刷新单行：标记（● 选中 / ○ 未选 / 隐 已隐藏）全墨色，不淡化。 */
    private fun updateHomeManageRow(row: TextView, info: ResolveInfo, hidden: Set<String>) {
        val pkg = info.activityInfo.packageName
        val selected = manageSelected.contains(pkg)
        val isHidden = hidden.contains(pkg)
        val prefix = when {
            selected -> "● "
            isHidden -> "隐 "
            else -> "○ "
        }
        row.text = prefix + info.loadLabel(packageManager)
        // 黑白高对比：隐藏行不再灰淡，靠「隐」前缀区分
        row.setTextColor(color(R.color.ink))
        if (row.alpha != 1f) row.alpha = 1f
    }

    private fun toggleHomeManageSelect(info: ResolveInfo, row: TextView) {
        val pkg = info.activityInfo.packageName
        if (manageSelected.contains(pkg)) manageSelected.remove(pkg) else manageSelected.add(pkg)
        updateHomeManageRow(row, info, repository.hiddenPackages())
    }

    // ------------------------------------------------------------------ 壁纸

    /** 壁纸铺满整窗（含底部标签栏区域）：尺寸直接取屏幕，无需等视图布局。 */
    private fun applyWallpaper() {
        if (config.wallpaperPath.isBlank()) {
            wallpaperController.clear()
            return
        }
        val dm = resources.displayMetrics
        if (dm.widthPixels <= 0 || dm.heightPixels <= 0) return
        wallpaperController.apply(
            dm.widthPixels, dm.heightPixels,
            if (config.wallpaperDimOn) config.wallpaperDim else 0
        )
    }

    private fun pickWallpaper() {
        wallpaperController.pick()
    }

    private fun removeWallpaper() {
        wallpaperController.remove()
        config = config.copy(wallpaperPath = "")
        store.save(config)
        wallpaperController.clear()
        if (::settingsController.isInitialized) settingsController.updateValues(config)
        toast(getString(R.string.wallpaper_removed))
    }

    // ------------------------------------------------------------------ APP 字体

    /** 当前全局字体；未选或文件丢失返回 null（系统默认）。按路径+mtime+大小缓存。 */
    fun launcherTypeface(): Typeface? {
        val path = config.appFontPath
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
        val path = config.clockFontPath
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

    /**
     * 全局换字体：遍历视图树，把每个 TextView 换成所选字体；
     * 换回默认（null）时从 [originalTypefaces] 还原各自原始字面（含加粗变体）。
     */
    private fun applyFontTree(view: View) {
        val tf = launcherTypeface()
        if (view is TextView) {
            if (!originalTypefaces.containsKey(view)) originalTypefaces[view] = view.typeface
            view.typeface = tf ?: originalTypefaces[view] ?: Typeface.DEFAULT
            applyTextWeight(view)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) applyFontTree(view.getChildAt(i))
        }
    }

    /** 字体粗细：>0 时对 TextView 画笔启用描边加粗（FILL_AND_STROKE），0 = 原样。 */
    private fun applyTextWeight(view: TextView) {
        val p = view.paint
        val w = resolvedTextStrokePx()
        if (w > 0f) {
            p.style = android.graphics.Paint.Style.FILL_AND_STROKE
            p.strokeWidth = w
        } else {
            p.style = android.graphics.Paint.Style.FILL
            p.strokeWidth = 0f
        }
    }

    /** 字体粗细（0–3）→ 描边宽度 px：0 = 原样，其余逐级加粗（任意字体均生效）。 */
    private fun resolvedTextStrokePx(): Float {
        val density = resources.displayMetrics.density
        return when (config.textWeight.coerceIn(0, 3)) {
            1 -> 0.35f * density
            2 -> 0.8f * density
            3 -> 1.4f * density
            else -> 0f
        }
    }

    /** 对整个内容树应用一次字体（静态文本、设置行、底部栏等），随后重设时钟字体。 */
    private fun refreshAppFont() {
        applyFontTree(findViewById(android.R.id.content))
        applyClockFont()
    }

    /** 大时间专用字体：优先时钟字体，否则跟随全局；都没有时不动（保持原始/全局状态）。 */
    private fun applyClockFont() {
        if (!::homeBigTimeHour.isInitialized) return
        val tf = clockTypeface() ?: launcherTypeface()
        if (tf == null) return
        if (homeBigTimeHour.typeface !== tf) {
            homeBigTimeHour.typeface = tf
            homeBigTimeMin.typeface = tf
            homeBigTimeColon.typeface = tf
            homeBigTimeHourStack.typeface = tf
            homeBigTimeMinStack.typeface = tf
        }
        if (homeVSplitHour.typeface !== tf) {
            homeVSplitHour.typeface = tf
            homeVSplitMin.typeface = tf
            homeHorizTime.typeface = tf
        }
        homeClockFlip.setTypeface(tf)
        homeClockRound.setTypeface(tf)
    }

    /** 设置入口：扫描 /sdcard 需要存储权限（API 23+），拒绝时仍可「从文件选择」。 */
    private fun showFontPicker(target: FontTarget) {
        if (Build.VERSION.SDK_INT in 23..32 &&
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingFontTarget = target
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQ_READ_STORAGE)
            return
        }
        showFontDialog(target)
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

    /** 字体选择对话框：目录扫描结果逐行预览（滚动时懒加载），另支持从文件管理器选。 */
    private fun showFontDialog(target: FontTarget) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.window?.apply {
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setWindowAnimations(0)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        val density = resources.displayMetrics.density
        fun dpx(v: Int) = (v * density).toInt()

        // 目标差异：标题 / 默认项文案 / 默认点击行为 / 勾选基准
        val isClock = target == FontTarget.CLOCK
        val titleRes = if (isClock) R.string.settings_clock_font else R.string.settings_app_font
        val defaultLabelRes = if (isClock) R.string.clock_font_follow else R.string.app_font_default
        val currentFontName = if (isClock) config.clockFontName else config.appFontName

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(color(R.color.paper))
                setStroke(dpx(1), color(R.color.ink))
                cornerRadius = dpx(10).toFloat()
            }
            setPadding(dpx(24), dpx(24), dpx(24), dpx(16))
            layoutParams = ViewGroup.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.9).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(TextView(this).apply {
            text = getString(titleRes)
            textSize = 20f
            setTextColor(color(R.color.ink))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, dpx(6))
        })
        root.addView(TextView(this).apply {
            text = getString(R.string.app_font_scan_hint)
            textSize = 13f
            setTextColor(color(R.color.gray))
            setPadding(0, 0, 0, dpx(12))
        })

        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
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
            (resources.displayMetrics.heightPixels * 0.55f).toInt()
        )

        val pendingPreviews = mutableListOf<Pair<TextView, Any>>()
        // SAF 预览临时文件：每次打开对话框清一遍（content URI 需先复制落盘才能解码）
        cacheDir.listFiles { f -> f.name.startsWith("font_preview_") }?.forEach { it.delete() }

        fun divider() {
            list.addView(View(this).apply {
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
            val row = TextView(this).apply {
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
        addRow(getString(defaultLabelRes), currentFontName.isBlank(), null, null) {
            if (isClock) {
                onConfigChanged(config.copy(clockFontPath = "", clockFontName = ""), SettingsController.AFFECT_FONT)
            } else {
                onConfigChanged(config.copy(appFontPath = "", appFontName = ""), SettingsController.AFFECT_FONT)
            }
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
        addRow(getString(R.string.app_font_pick_folder), false, null, null) {
            pickFontFolder(target)
        }
        // 4. 从文件选择（单个文件）
        addRow(getString(R.string.app_font_pick_file), false, null, null) {
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

    /** 把字体拷进私有目录并立即生效；校验失败回滚并提示。 */
    private fun importFont(source: File, displayName: String, target: FontTarget) {
        val fontsDir = File(filesDir, "fonts").apply { mkdirs() }
        val ext = source.extension.lowercase().ifEmpty { "ttf" }
        val dest = File(fontsDir, if (target == FontTarget.CLOCK) "clock.$ext" else "launcher.$ext")
        val copied = runCatching {
            source.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
            dest.length() > 0
        }.getOrDefault(false)
        if (!copied) {
            dest.delete()
            toast(getString(R.string.app_font_invalid))
            return
        }
        val tf = runCatching { Typeface.createFromFile(dest) }.getOrNull()
        if (tf == null) {
            dest.delete()
            toast(getString(R.string.app_font_invalid))
            return
        }
        val newConfig = when (target) {
            FontTarget.CLOCK -> config.copy(clockFontPath = dest.absolutePath, clockFontName = displayName)
            FontTarget.GLOBAL -> config.copy(appFontPath = dest.absolutePath, appFontName = displayName)
        }
        onConfigChanged(newConfig, SettingsController.AFFECT_FONT)
        toast(getString(R.string.app_font_applied, displayName))
    }

    /** 从系统文件选择器挑单个字体（不需要存储权限）：
     *  优先 DocumentsUI 的 OPEN_DOCUMENT（框架自带、必然存在），失败再退
     *  GET_CONTENT / chooser，全打不开时明确提示，不再静默没反应。 */
    private fun pickFontFile(target: FontTarget) {
        pendingFontTarget = target
        val mimeTypes = arrayOf("font/ttf", "font/otf", "application/octet-stream", "application/x-font-ttf")
        val open = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        if (runCatching { startActivityForResult(open, REQ_PICK_FONT) }.isSuccess) return
        val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        }
        if (runCatching { startActivityForResult(getContent, REQ_PICK_FONT) }.isSuccess) return
        if (runCatching { startActivityForResult(Intent.createChooser(getContent, null), REQ_PICK_FONT) }.isSuccess) return
        toast(getString(R.string.font_picker_unavailable))
    }

    /** 让用户自选字体文件夹（SAF 树权限，跨重启保留）：选完保存配置并重开字体对话框。 */
    private fun pickFontFolder(target: FontTarget) {
        pendingFontTarget = target
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        if (runCatching { startActivityForResult(intent, REQ_PICK_FONT_FOLDER) }.isSuccess) return
        if (runCatching { startActivityForResult(Intent.createChooser(intent, null), REQ_PICK_FONT_FOLDER) }.isSuccess) return
        toast(getString(R.string.font_folder_pick_failed))
    }

    /** 自选文件夹里的一个字体（SAF 文档 URI + 展示名）。 */
    private data class FolderFont(val uri: Uri, val name: String)

    /** 扫描用户自选的字体文件夹（DocumentsContract 列子文档，按扩展名过滤；
     *  Android 11+ 分区存储下这是拿到 /sdcard 字体的唯一可靠通路）。 */
    private fun scanFolderFonts(): List<FolderFont> {
        val uriStr = config.fontFolderUri
        if (uriStr.isBlank()) return emptyList()
        return runCatching {
            val tree = Uri.parse(uriStr)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree)
            )
            val result = mutableListOf<FolderFont>()
            contentResolver.query(
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
        val tmp = File(cacheDir, "font_preview_${uri.hashCode()}.ttf")
        return runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            }
            Typeface.createFromFile(tmp)
        }.getOrNull()
    }

    /** 从 SAF 文档导入字体：流复制进私有字体目录，校验后立即生效。 */
    private fun importFontFromUri(uri: Uri, displayName: String, target: FontTarget) {
        val fontsDir = File(filesDir, "fonts").apply { mkdirs() }
        val name = displayName.substringBeforeLast('.')
        val ext = displayName.substringAfterLast('.', "ttf").lowercase().takeIf { it in FONT_EXTENSIONS } ?: "ttf"
        val dest = File(fontsDir, if (target == FontTarget.CLOCK) "clock.$ext" else "launcher.$ext")
        val copied = runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
            } != null && dest.length() > 0
        }.getOrDefault(false)
        if (!copied) {
            dest.delete()
            toast(getString(R.string.app_font_invalid))
            return
        }
        val tf = runCatching { Typeface.createFromFile(dest) }.getOrNull()
        if (tf == null) {
            dest.delete()
            toast(getString(R.string.app_font_invalid))
            return
        }
        val newConfig = when (target) {
            FontTarget.CLOCK -> config.copy(clockFontPath = dest.absolutePath, clockFontName = name)
            FontTarget.GLOBAL -> config.copy(appFontPath = dest.absolutePath, appFontName = name)
        }
        onConfigChanged(newConfig, SettingsController.AFFECT_FONT)
        toast(getString(R.string.app_font_applied, name))
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()

    // 切换顶部风格：GONE 的那一套不参与 measure/layout，剩下的那套自然顶到顶部。
    private fun applyHomeTopStyle() {
        if (!::homeTopSplit.isInitialized) return
        val split = config.homeTopStyle == 1
        homeTopClassic.visibility = if (split) View.GONE else View.VISIBLE
        homeTopSplit.visibility = if (split) View.VISIBLE else View.GONE

        // 分割线上方留白 = 顶部块上方留白，顶部块就在「屏幕顶 → 分割线」之间居中，
        // 与块高无关，所以时间字号改了也不用重算。
        val lp = homeTopDivider.layoutParams as? LinearLayout.LayoutParams ?: return
        val gap = resources.getDimensionPixelSize(
            if (split) R.dimen.home_top_gap_split else R.dimen.home_top_gap_classic
        )
        if (lp.topMargin != gap) {
            lp.topMargin = gap
            homeTopDivider.layoutParams = lp
        }
    }

    private fun refreshApps() {
        ensureAppsPage()
        appsLoaded = true
        // 管理模式或「隐藏应用在应用页可见」开启时展示全部应用；
        // 后者下已隐藏应用由 EInkLauncherView 按既有规则置灰可点。
        appsAll = if (manageMode || config.drawerShowsHidden) repository.loadAllApps()
                  else repository.loadApps()
        StartupTracer.stage("refreshApps-load")
        gridApps.labelStrokePx = resolvedTextStrokePx()
        gridApps.labelTypeface = launcherTypeface()
        val (columns, rows) = resolvedAppGrid()
        currentAppGrid = columns to rows
        gridApps.configure(columns, rows)
        appsPageCount =
            if (appsAll.isEmpty()) 0
            else ceil(appsAll.size.toDouble() / (columns * rows)).toInt()
        showAppsPage(0)
        StartupTracer.stage("refreshApps-showPage")
    }

    private fun showAppsPage(index: Int) {
        val (columns, rows) = resolvedAppGrid()
        val pageSize = columns * rows
        val clamped = index.coerceIn(0, (appsPageCount - 1).coerceAtLeast(0))
        appsPageIndex = clamped
        val start = clamped * pageSize
        val end = minOf(start + pageSize, appsAll.size)
        val page: MutableList<ResolveInfo?> = appsAll.subList(start, end).toMutableList()
        while (page.size < pageSize) page.add(null)

        gridApps.setApps(
            pageApps = page,
            selection = manageSelected,
            manageMode = manageMode,
            showDividers = config.showAppDividers,
            hidden = repository.hiddenPackages()
        )
        val pageText = if (appsPageCount > 0) "${clamped + 1}/$appsPageCount" else "0/0"
        if (textPage.text != pageText) textPage.text = pageText
    }

    // ------------------------------------------------------------------ Tabs

    private fun setupTabs() {
        tabHome.setOnClickListener { onHomeClick() }
        tabApps.setOnClickListener { selectPage(PAGE_APPS, animate = true) }
        tabSettings.setOnClickListener { selectPage(PAGE_SETTINGS, animate = true) }
    }

    private fun onHomeClick() {
        if (recentOverlay.visibility == View.VISIBLE) {
            hideRecentTasks()
            lastHomeClickTime = 0L
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastHomeClickTime in 1..799) {
            lastHomeClickTime = 0L
            showRecentTasks()
        } else {
            lastHomeClickTime = now
            selectPage(PAGE_HOME, animate = true)
        }
    }

    private fun selectPage(page: Int, animate: Boolean = false) {
        if (page == currentPage) return

        currentPage = page

        applyImmersiveBars()

        if (page == PAGE_APPS) {
            ensureAppsPage()
            if (!appsLoaded) refreshApps()
        }
        if (page == PAGE_SETTINGS) {
            ensureSettingsPage()
        }

        pageHome.visibility = if (page == PAGE_HOME) View.VISIBLE else View.GONE
        if (::pageApps.isInitialized) {
            pageApps.visibility = if (page == PAGE_APPS) View.VISIBLE else View.GONE
        }
        if (::pageSettings.isInitialized) {
            pageSettings.visibility = if (page == PAGE_SETTINGS) View.VISIBLE else View.GONE
        }

        tabHomeLabel.setTextColor(color(if (page == PAGE_HOME) R.color.ink else R.color.gray))
        tabAppsLabel.setTextColor(color(if (page == PAGE_APPS) R.color.ink else R.color.gray))
        tabSettingsLabel.setTextColor(color(if (page == PAGE_SETTINGS) R.color.ink else R.color.gray))

        dotHome.visibility = if (page == PAGE_HOME) View.VISIBLE else View.INVISIBLE
        dotApps.visibility = if (page == PAGE_APPS) View.VISIBLE else View.INVISIBLE
        dotSettings.visibility = if (page == PAGE_SETTINGS) View.VISIBLE else View.INVISIBLE

        if (::manageToggle.isInitialized) {
            manageToggle.visibility = if (page == PAGE_APPS) View.VISIBLE else View.GONE
        }
    }

    // ------------------------------------------------------------------ Lock

    private fun setupLock() {
        btnLock.setOnClickListener { lockScreen() }
    }

    // ------------------------------------------------------------------ Manage mode

    private fun setupManage() {
        manageDone.setOnClickListener { exitManageMode() }
        manageHide.setOnClickListener {
            if (manageSelected.isEmpty()) {
                toast("请先选择应用")
            } else {
                repository.batchSetHidden(manageSelected, true)
                toast("已隐藏 ${manageSelected.size} 个应用")
                manageSelected.clear()
                refreshManagedSurfaces()
            }
        }
        manageShow.setOnClickListener {
            if (manageSelected.isEmpty()) {
                toast("请先选择应用")
            } else {
                repository.batchSetHidden(manageSelected, false)
                toast("已显示 ${manageSelected.size} 个应用")
                manageSelected.clear()
                refreshManagedSurfaces()
            }
        }
        manageUninstall.setOnClickListener {
            if (manageSelected.isEmpty()) {
                toast("请先选择应用")
            } else {
                uninstallQueue.clear()
                uninstallQueue.addAll(manageSelected)
                manageSelected.clear()
                startNextUninstall()
            }
        }
    }

    /** 批量操作后同时刷新两个可能受影响的面（应用页 + 主页），
     *  带签名去重，没变的面不会重绘。 */
    private fun refreshManagedSurfaces() {
        if (::gridApps.isInitialized) refreshApps()
        refreshHome()
    }

    private fun enterManageMode() {
        manageMode = true
        homeManage = false
        manageSelected.clear()
        manageToggle.text = getString(R.string.manage_done)
        bottomBar.visibility = View.GONE
        manageBar.visibility = View.VISIBLE
        refreshApps()
    }

    /** 纯文字主页的批量管理：从长按菜单进入，长按的那项默认选中。 */
    private fun enterHomeManageMode(preselect: ResolveInfo?) {
        manageMode = true
        homeManage = true
        manageSelected.clear()
        preselect?.let { manageSelected.add(it.activityInfo.packageName) }
        bottomBar.visibility = View.GONE
        manageBar.visibility = View.VISIBLE
        populateTextList()
    }

    private fun exitManageMode() {
        manageMode = false
        homeManage = false
        manageSelected.clear()
        if (::manageToggle.isInitialized) manageToggle.text = getString(R.string.manage)
        bottomBar.visibility = View.VISIBLE
        manageBar.visibility = View.GONE
        refreshManagedSurfaces()
    }

    private fun toggleManageSelect(info: ResolveInfo) {
        val pkg = info.activityInfo.packageName
        if (manageSelected.contains(pkg)) manageSelected.remove(pkg) else manageSelected.add(pkg)
        gridApps.updateSelection(manageSelected)
    }

    private fun startNextUninstall() {
        if (uninstallQueue.isEmpty()) {
            exitManageMode()
            refreshManagedSurfaces()
            toast("卸载流程结束")
            return
        }
        val pkg = uninstallQueue.removeAt(0)
        runCatching {
            startActivityForResult(
                Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")),
                REQ_UNINSTALL
            )
        }.onFailure { e ->
            Log.w(TAG, "start uninstall failed for $pkg: ${e.message}")
            toast("无法卸载 $pkg：${e.message}")
            startNextUninstall()
        }
    }

    // ------------------------------------------------------------------ Action sheet

    private fun setupActionSheet() {
        findViewById<View>(R.id.actionDim).setOnClickListener { hideActionSheet() }
        findViewById<View>(R.id.actionClose).setOnClickListener { hideActionSheet() }
        actionPin.setOnClickListener {
            selectedApp?.let { togglePin(it) }
            hideActionSheet()
        }
        actionHide.setOnClickListener {
            selectedApp?.let { hideApp(it) }
            hideActionSheet()
        }
        actionBatch.setOnClickListener {
            val preselect = selectedApp
            hideActionSheet()
            enterHomeManageMode(preselect)
        }
        actionAppInfo.setOnClickListener {
            selectedApp?.let { showAppInfo(it) }
            hideActionSheet()
        }
    }

    private fun showActionSheet(info: ResolveInfo) {
        selectedApp = info
        actionTitle.text = info.loadLabel(packageManager)
        actionPin.text = getString(
            if (repository.isPinned(info.activityInfo.packageName)) R.string.action_unpin
            else R.string.action_pin
        )
        actionHide.text = getString(R.string.action_hide)
        // 批量管理只对纯文字主页有意义（网格主页用应用页的管理入口）；
        // 且必须当前就在主页，应用页长按不触发跨页进入
        actionBatch.text = getString(R.string.action_batch)
        actionBatch.visibility =
            if (currentPage == PAGE_HOME && config.homeStyle == 1) View.VISIBLE else View.GONE
        actionAppInfo.text = getString(R.string.action_app_info)
        actionOverlay.visibility = View.VISIBLE
    }

    private fun hideActionSheet() {
        actionOverlay.visibility = View.GONE
        selectedApp = null
    }

    // ------------------------------------------------------------------ Recent tasks

    private fun lockScreen() {
        deviceManagerLock()
    }

    private fun deviceManagerLock() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, LockAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            runCatching { dpm.lockNow() }
        } else {
            runCatching {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                    .putExtra(
                        DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                        getString(R.string.lock_admin_description)
                    )
                startActivity(intent)
            }
        }
    }

    private fun setupRecentOverlay() {
        findViewById<View>(R.id.recentDim).setOnClickListener { hideRecentTasks() }
        recentClear.setOnClickListener {
            val appsToKill = currentRecentApps()
            store.markRecentCleared()
            previousApp = null
            populateRecentGrid()
            killBackgroundApps(appsToKill)
        }
        recentCancel.setOnClickListener {
            hideRecentTasks()
            if (config.recentCancelBackToApp) {
                previousApp?.let { launchApp(it) }
            }
        }

        recentGrid.setOnItemInteractionListener(object : EInkLauncherView.OnItemInteractionListener {
            override fun onItemClick(info: ResolveInfo) {
                hideRecentTasks()
                launchApp(info)
            }

            @SuppressLint("SetTextI18n")
            override fun onItemLongClick(info: ResolveInfo) {
                val pkg = info.activityInfo.packageName
                if (pkg == packageName) return
                val label = info.loadLabel(packageManager).toString()

                val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                runCatching {
                    am.killBackgroundProcesses(pkg)
                    toast("已请求清理: $label")
                    removeAppFromRecentUI(pkg)
                }
            }
        })
        recentGrid.setOnPageChangeListener(null)
    }

    private fun removeAppFromRecentUI(pkg: String) {
        // 【修复 1】如果被杀掉的应用刚好是“上一应用”，立刻置空，防止取消时诈尸
        if (previousApp?.activityInfo?.packageName == pkg) {
            previousApp = null
        }

        store.markAppCleared(pkg)
        val remaining = currentRecentApps()
        if (remaining.isEmpty()) {
            hideRecentTasks()
        } else {
            populateRecentGrid()
        }
    }

    // ------------------------------------------------------------------ Info overlay

    private fun setupSelectOverlay() {
        findViewById<View>(R.id.selectDim).setOnClickListener { hideSelectOverlay() }
        findViewById<View>(R.id.selectCancel).setOnClickListener { hideSelectOverlay() }
    }

    private fun showSelectOverlay(
        title: String,
        options: List<String>,
        selectedIndex: Int,
        onPicked: (Int) -> Unit
    ) {
        selectTitle.text = title
        selectCallback = onPicked
        selectList.removeAllViews()

        options.forEachIndexed { index, label ->
            val row = TextView(this).apply {
                text = if (index == selectedIndex) "● $label" else "○ $label"
                textSize = 16f
                setTextColor(color(if (index == selectedIndex) R.color.ink else R.color.gray))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(24), 0, dp(24), 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(52)
                )
                setOnClickListener {
                    hideSelectOverlay()
                    onPicked(index)
                }
            }
            selectList.addView(row)

            if (index < options.size - 1) {
                selectList.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(1)
                    )
                    setBackgroundColor(color(R.color.hairline))
                })
            }
        }

        selectOverlay.visibility = View.VISIBLE
        applyFontTree(selectList)
    }

    private fun hideSelectOverlay() {
        selectOverlay.visibility = View.GONE
        selectCallback = null
    }

    // ------------------------------------------------------------------ WiFi transfer

    private fun setupWifiTransfer() {
        textTime.setOnClickListener { startWifiTransfer() }
        textTimeSplit.setOnClickListener { startWifiTransfer() }
        homeBigTimeRow.setOnClickListener { startWifiTransfer() }
        homeBigTimeStack.setOnClickListener { startWifiTransfer() }
        homeClockSeg.setOnClickListener { startWifiTransfer() }
        homeClockFlip.setOnClickListener { startWifiTransfer() }
        homeClockRound.setOnClickListener { startWifiTransfer() }
        homeClockVSplit.setOnClickListener { startWifiTransfer() }
        homeClockHorizontal.setOnClickListener { startWifiTransfer() }
        homeClockAnalog.setOnClickListener { startWifiTransfer() }
        homeClockMatrix.setOnClickListener { startWifiTransfer() }
        findViewById<View>(R.id.wifiClose).setOnClickListener { closeWifiTransfer() }
    }

    private fun startWifiTransfer() {
        if (wifiOverlay.visibility == View.VISIBLE) return

        if (Build.VERSION.SDK_INT >= 23 &&
            Build.VERSION.SDK_INT <= 28 &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQ_WRITE_STORAGE)
            return
        }
        openWifiTransferOverlay()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_UNINSTALL) {
            startNextUninstall()
            return
        }
        if (requestCode == REQ_PICK_FONT) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) {
                val name = queryDisplayName(uri)?.substringBeforeLast('.') ?: "自定义字体"
                val tmp = File(cacheDir, "font_pick.tmp")
                val ok = runCatching {
                    contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output, 64 * 1024) }
                    } != null && tmp.length() > 0
                }.getOrDefault(false)
                val valid = ok && runCatching { Typeface.createFromFile(tmp) }.getOrNull() != null
                if (valid) {
                    importFont(tmp, name, pendingFontTarget)
                } else {
                    toast(getString(R.string.app_font_invalid))
                }
                tmp.delete()
            }
            return
        }
        if (requestCode == REQ_PICK_FONT_FOLDER) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) {
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                config = config.copy(fontFolderUri = uri.toString())
                store.save(config)
                if (::settingsController.isInitialized) settingsController.updateValues(config)
                if (scanFolderFonts().isEmpty()) toast(getString(R.string.font_folder_empty))
                showFontDialog(pendingFontTarget)
            }
            return
        }
        if (requestCode == WallpaperController.REQ_PICK_WALLPAPER) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) {
                val file = wallpaperController.importPicked(uri)
                if (file != null && file.exists()) {
                    config = config.copy(wallpaperPath = file.absolutePath)
                    store.save(config)
                    applyWallpaper()
                    if (::settingsController.isInitialized) settingsController.updateValues(config)
                    toast(getString(R.string.wallpaper_applied))
                } else {
                    toast(getString(R.string.wallpaper_pick_failed))
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_READ_STORAGE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                showFontDialog(pendingFontTarget)
            } else {
                // 没有目录扫描权限，仍可走文件选择器
                toast(getString(R.string.app_font_need_permission))
                showFontDialog(pendingFontTarget)
            }
            return
        }
        if (requestCode != REQ_WRITE_STORAGE) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            openWifiTransferOverlay()
        } else {
            toast("需要存储权限才能保存上传的文件")
        }
    }

    private fun openWifiTransferOverlay() {
        val ip = getWifiIpAddress()
        if (ip == null) {
            toast(getString(R.string.wifi_transfer_no_wifi))
            return
        }

        val dir = File(Environment.getExternalStorageDirectory(), "WIFI_transfer")
        val server = WifiTransferServer(WIFI_TRANSFER_PORT, dir)
        if (!server.start()) {
            toast(getString(R.string.wifi_transfer_start_failed))
            return
        }

        wifiServer?.stop()
        wifiServer = server

        val url = "http://$ip:$WIFI_TRANSFER_PORT"
        wifiUrlText.text = url
        wifiHintText.text = getString(R.string.wifi_transfer_url_hint, url) + "\n" +
                getString(R.string.wifi_transfer_folder_hint)
        wifiQr.setImageBitmap(QrCodes.generate(url, dp(300)))
        wifiOverlay.visibility = View.VISIBLE
    }

    private fun closeWifiTransfer() {
        wifiServer?.stop()
        wifiServer = null
        wifiOverlay.visibility = View.GONE
    }

    private fun getWifiIpAddress(): String? {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    val addresses = interfaces.nextElement().inetAddresses
                    while (addresses.hasMoreElements()) {
                        val addr = addresses.nextElement()
                        if (addr is Inet4Address && !addr.isLoopbackAddress && addr.isSiteLocalAddress) {
                            return addr.hostAddress
                        }
                    }
                }
            }
            null
        }.getOrNull()
    }

    private fun setupInfoOverlay() {
        infoClose.setOnClickListener { hideInfo() }
    }

    private fun showInfo(title: String, text: String) {
        infoTitle.text = title
        infoText.text = text
        // 原作者赞赏二维码仅随「关于」页显示；帮助页隐藏
        val isAbout = title == getString(R.string.settings_about)
        aboutDonationBlock.visibility = if (isAbout) View.VISIBLE else View.GONE
        if (isAbout) ensureAboutDonationQr()
        infoOverlay.visibility = View.VISIBLE
    }

    /** 原作者赞赏二维码（wxp 收款码）：首次打开关于页时生成一次。 */
    private fun ensureAboutDonationQr() {
        if (aboutDonationQrReady) return
        aboutDonationQrReady = true
        aboutDonationQr.setImageBitmap(QrCodes.generate(DONATION_WECHAT_URI, dp(240)))
    }

    private fun hideInfo() {
        infoOverlay.visibility = View.GONE
    }

    private fun showRecentTasks() {
        if (actionOverlay.visibility == View.VISIBLE) hideActionSheet()
        if (manageMode) exitManageMode()
        recentOverlay.visibility = View.VISIBLE
        previousApp = loadRecentApps().firstOrNull()
        populateRecentGrid()
    }

    @SuppressLint("SetTextI18n")
    private fun populateRecentGrid() {
        if (Build.VERSION.SDK_INT < 21) {
            recentMessage.text = "最近任务需要 Android 5.0 及以上"
            recentMessage.setOnClickListener(null)
            recentMessage.visibility = View.VISIBLE
            recentGrid.visibility = View.GONE
            return
        }

        if (!usageAccessGranted()) {
            recentMessage.text = "需要开启「使用情况访问」权限才能显示后台应用，点此去开启"
            recentMessage.setOnClickListener {
                runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            }
            recentMessage.visibility = View.VISIBLE
            recentGrid.visibility = View.GONE
            return
        }

        val apps = currentRecentApps()

        if (apps.isEmpty()) {
            recentMessage.text =
                if (config.recentWindowMs > 0L) "暂无近期使用" else "暂无最近任务"
            recentMessage.setOnClickListener(null)
            recentMessage.visibility = View.VISIBLE
            recentGrid.visibility = View.GONE
            return
        }

        recentMessage.visibility = View.GONE
        recentGrid.visibility = View.VISIBLE
        // 最近任务是一个底部浮层：列数按屏幕宽度自适应（手机 4 列，宽屏最多 5 列），
        // 行数由应用数量决定，行高按图标大小与文字行高推算。
        val density = resources.displayMetrics.density
        val columns = GridSizing.recentColumnCap(displayWidthDp())

        // 行高先按单格估算，再用实际格宽算出图标大小，最后按图标大小定行高。
        val estimatedRowPx = dp(60).coerceAtLeast(dp(48))
        val cellWidthPx = (resources.displayMetrics.widthPixels - dp(72)).coerceAtLeast(dp(120)) / columns
        val iconPx = recentGrid.iconPxFor(cellWidthPx, estimatedRowPx, columns, 1)
        var rows = (apps.size + columns - 1) / columns
        rows = rows.coerceIn(1, GridSizing.recentRowCap(displayHeightDp() * 0.4f))
        recentGrid.configure(columns, rows)
        recentGrid.labelStrokePx = resolvedTextStrokePx()
        recentGrid.labelTypeface = launcherTypeface()

        val rowHeight = GridSizing.rowHeightDp(iconPx, density)
        recentGrid.layoutParams = recentGrid.layoutParams.apply {
            height = dp(rows * rowHeight)
        }

        recentGrid.setApps(
            pageApps = apps,
            selection = emptySet(),
            manageMode = false,
            showDividers = true,
            hidden = emptySet()
        )
    }

    private fun currentRecentApps(): List<ResolveInfo> {
        val windowMs = config.recentWindowMs
        return if (windowMs > 0L) {
            loadRecentApps(System.currentTimeMillis() - windowMs)
        } else {
            loadRecentApps()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun killBackgroundApps(apps: List<ResolveInfo>) {
        if (apps.isEmpty()) return

        if (Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.KILL_BACKGROUND_PROCESSES) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "KILL_BACKGROUND_PROCESSES not granted")
            toast("缺少后台清理权限")
            return
        }
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var killed = 0
        for (info in apps) {
            val pkg = info.activityInfo.packageName
            if (pkg == packageName) continue
            runCatching {
                am.killBackgroundProcesses(pkg)
                killed++
            }.onFailure { e ->
                Log.w(TAG, "killBackgroundProcesses failed for $pkg: ${e.message}")
            }
        }
        if (killed > 0) {
            Log.i(TAG, "killBackgroundProcesses requested for $killed packages")
            toast("已清理 $killed 个后台应用")
        }
    }

    private fun loadRecentApps(since: Long = 0L): List<ResolveInfo> {
        if (Build.VERSION.SDK_INT < 21) return emptyList()
        return RecentTasks.load(
            context = this,
            selfPackage = packageName,
            since = since,
            clearedAt = store.recentClearedAt(),
            appClearedAt = { store.appClearedAt(it) }
        )
    }

    private fun usageAccessGranted(): Boolean {
        if (Build.VERSION.SDK_INT < 21) return false
        return RecentTasks.hasUsageAccess(this)
    }

    private fun hideRecentTasks() {
        recentOverlay.visibility = View.GONE
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun togglePin(info: ResolveInfo) {
        val pkg = info.activityInfo.packageName
        val pinned = repository.isPinned(pkg)
        repository.setPinned(pkg, !pinned)
        toast(if (!pinned) "已固定到主页" else "已取消固定")
        refreshHome()
    }

    private fun hideApp(info: ResolveInfo) {
        repository.setHidden(info.activityInfo.packageName, true)
        toast("已隐藏")
        refreshHome()
        refreshApps()
    }

    private fun showAppInfo(info: ResolveInfo) {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${info.activityInfo.packageName}")
                }
            )
        }
    }

    // ------------------------------------------------------------------ Common

    private fun launchApp(info: ResolveInfo) {
        repository.recordUsage(info.activityInfo.packageName)
        repository.launch(info)
    }

    private fun registerTimeReceiver() {
        val filter = IntentFilter(Intent.ACTION_TIME_TICK).apply {
            // 电量变化也刷新：信息行里显示百分比
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timeReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(timeReceiver, filter)
        }
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(packageReceiver, filter)
        }
    }

    private fun updateClock() {
        if (!::textTime.isInitialized) return
        val now = Date()
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        // 日期统一格式：X月X日，星期X（如 11月22日，星期日）
        val date = SimpleDateFormat("M月d日，EEEE", Locale.CHINA).format(now)
        textTime.text = time
        textDate.text = date
        textTimeSplit.text = time
        textDateSplit.text = date
        infoTime.text = time

        // 时钟区（图标/纯文字主页共用）：大时间（12 种样式同步更新）+ 信息行 11月22日，星期日 | 76%
        if (::homeBigTimeHour.isInitialized) {
            val hh = SimpleDateFormat("HH", Locale.getDefault()).format(now)
            val mm = SimpleDateFormat("mm", Locale.getDefault()).format(now)
            homeBigTimeHour.text = hh
            homeBigTimeMin.text = mm
            if (::homeBigTimeHourStack.isInitialized) {
                homeBigTimeHourStack.text = hh
                homeBigTimeMinStack.text = mm
            }
            if (::homeClockSeg.isInitialized) homeClockSeg.setTime(hh + mm)
            val level = batteryPercent()
            // 信息行改为纯日期（电量已由快捷行电池项承担，不重复显示），
            // 居中于时钟宽度范围内（见 applyClockStyleAndAlign 的容器锚定）
            if (homeInfoLine.text.toString() != date) homeInfoLine.text = date
            // ---- inkOS 移植样式：翻页 / 圆环 / 竖线 / 横排 / 表盘 / 点阵 ----
            // 电量文本不带 ⚡ 前缀（按用户要求移除）；充电指示保留在电池图标内的闪电
            val batt = if (level >= 0) "$level%" else ""
            if (::homeClockFlip.isInitialized) homeClockFlip.setTime(hh, mm)
            if (::homeClockRound.isInitialized) homeClockRound.setTime(hh, mm)
            if (::homeVSplitHour.isInitialized) {
                homeVSplitHour.text = hh
                homeVSplitMin.text = mm
                homeVSplitDate.text = date
                homeVSplitBatt.text = batt
            }
            if (::homeHorizTime.isInitialized) {
                homeHorizTime.text = "$hh:$mm"
                homeHorizDate.text = date
                homeHorizBatt.text = batt
            }
            if (::homeClockAnalog.isInitialized) homeClockAnalog.setTime(hh, mm)
            if (::homeClockMatrix.isInitialized) {
                homeClockMatrix.setTime(hh, mm)
                homeClockMatrix.setBattery(level)
            }
        }
    }

    /** 最近一次成功读到的电量（-1 = 从未读到）：本机 ROM 的 sticky 广播偶发取不到，缓存兜底。 */
    private var lastBatteryPercent = -1

    /** 电池百分比，读 sticky 广播，无开销；偶发读不到时回退上次成功值（仅冷启动首读可能为空）。 */
    private fun batteryPercent(): Int {
        val fresh = runCatching {
            val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) level * 100 / scale else -1
        }.getOrDefault(-1)
        if (fresh >= 0) lastBatteryPercent = fresh
        return if (fresh >= 0) fresh else lastBatteryPercent
    }

    @Suppress("DEPRECATION")
    private fun color(res: Int): Int = resources.getColor(res)

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    @SuppressLint("GestureBackNavigation")
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                when {
                    infoOverlay.visibility == View.VISIBLE -> hideInfo()
                    selectOverlay.visibility == View.VISIBLE -> hideSelectOverlay()
                    wifiOverlay.visibility == View.VISIBLE -> closeWifiTransfer()
                    recentOverlay.visibility == View.VISIBLE -> hideRecentTasks()
                    actionOverlay.visibility == View.VISIBLE -> hideActionSheet()
                    manageMode -> exitManageMode()
                    ::pageSettings.isInitialized && pageSettings.visibility == View.VISIBLE -> selectPage(PAGE_HOME, animate = true)
                    ::pageApps.isInitialized && pageApps.visibility == View.VISIBLE -> selectPage(PAGE_HOME, animate = true)
                    else -> Unit
                }
                true
            }
            else -> super.onKeyUp(keyCode, event)
        }
    }

    companion object {
        private const val TAG = "LauncherActivity"
        private const val PAGE_HOME = 0
        private const val PAGE_APPS = 1
        private const val PAGE_SETTINGS = 2
        private const val WIFI_TRANSFER_PORT = 25025
        // 原作者（@Silentint）赞赏收款码，仅用于关于页二维码展示
        private const val DONATION_WECHAT_URI =
            "wxp://f2f0KzDXCZc0SriqXOoY8fWKxxyFbxz9xhImNUL1VgMLLaQ"
        private const val REQ_WRITE_STORAGE = 7
        private const val REQ_UNINSTALL = 8
        private const val REQ_PICK_FONT = 22
        private const val REQ_PICK_FONT_FOLDER = 23
        private const val REQ_READ_STORAGE = 9

        // 字体扫描目录：墨水屏字体管理器的常规位置 + 下载目录
        private val FONT_SCAN_DIRS = arrayOf("/sdcard/Fonts", "/sdcard/Download", "/sdcard/Downloads")
        private val FONT_EXTENSIONS = setOf("ttf", "otf", "ttc")

        // 纯文字模式参数的可调范围（与 SettingsController 中同套镜像）
        private const val FONT_MIN_SP = 12
        private const val FONT_MAX_SP = 56
        private const val SPACING_MIN_DP = 4
        private const val SPACING_MAX_DP = 64
        private const val PADDING_MIN_DP = 8
        private const val PADDING_MAX_DP = 192
        private const val TIME_MIN_SP = 12
        private const val TIME_MAX_SP = 200
    }
}