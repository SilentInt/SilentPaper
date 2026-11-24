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
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewStub
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.silent.paper.ui.ClockAreaController
import com.silent.paper.ui.FontController
import com.silent.paper.ui.SettingsController
import com.silent.paper.ui.TextHomeController
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
    /** 顶部日期原始字号（px），时钟周边字号恢复自动时还原。 */
    private var dateTextSizePx = 0f
    private var dateSplitTextSizePx = 0f

    // 纯文字主页 / 共用时钟区 / 字体（职责见对应 Controller）
    private lateinit var fontController: FontController
    private lateinit var textHome: TextHomeController
    private lateinit var clockArea: ClockAreaController

    // 壁纸
    private lateinit var wallpaperController: WallpaperController

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

    // Info overlay
    private lateinit var infoOverlay: View
    private lateinit var infoTitle: TextView
    private lateinit var infoText: TextView
    // 赞赏二维码（关于页 / 引导页 / 50 次 HOME 提醒共用）
    private lateinit var donationQr: ImageView
    private lateinit var guideDonationQr: ImageView
    private lateinit var donationPromptQr: ImageView
    private var donationQrReady = false

    // 50 次 HOME 赞赏提醒
    private lateinit var donationOverlay: View
    private lateinit var donationClose: TextView
    private var donationCloseEnabled = false

    // 关于页的提醒开关
    private lateinit var rowDonationPrompt: View
    private lateinit var settingsDonationPromptValue: TextView

    private val donationCloseRunnable = Runnable {
        donationCloseEnabled = true
        donationClose.text = getString(R.string.info_close)
        donationClose.setTextColor(color(R.color.ink))
        donationClose.setOnClickListener { donationOverlay.visibility = View.GONE }
    }
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

        window.decorView.post { ensureDonationQr() }
        StartupTracer.stage("postDonationQr")

        setupFeatureControllers()
        StartupTracer.stage("setupFeatureControllers")

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
        window.decorView.removeCallbacks(donationCloseRunnable)
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

        if (donationOverlay.visibility == View.VISIBLE) return

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

        maybeShowDonationPrompt()
    }

    private fun maybeShowDonationPrompt() {
        if (!store.isDonationPromptEnabled()) return
        val count = store.incrementHomePressCount()
        Log.d(TAG, "home press count=$count")
        if (count >= 50) {
            store.resetHomePressCount()
            showDonationPrompt()
        }
    }

    private fun showDonationPrompt() {
        ensureDonationQr()
        donationCloseEnabled = false
        donationClose.text = getString(R.string.donation_close_wait)
        donationClose.setTextColor(color(R.color.gray))
        donationClose.setOnClickListener(null)
        donationOverlay.visibility = View.VISIBLE
        window.decorView.postDelayed(donationCloseRunnable, 5000L)
    }

    override fun onResume() {
        StartupTracer.stage("onResume-enter")
        super.onResume()
        val newConfig = store.load()
        val configChanged = newConfig != config
        if (configChanged) {
            config = newConfig
            if (::settingsController.isInitialized) settingsController.updateValues(config)
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
        dateTextSizePx = textDate.textSize
        dateSplitTextSizePx = textDateSplit.textSize
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

        donationQr = findViewById(R.id.donationQr)
        guideDonationQr = findViewById(R.id.guideDonationQr)
        donationOverlay = findViewById(R.id.donationOverlay)
        donationPromptQr = findViewById(R.id.donationPromptQr)
        donationClose = findViewById(R.id.donationClose)
    }

    private fun ensureDonationQr() {
        if (donationQrReady) return
        Thread {
            val qr = QrCodes.generate(DONATION_WECHAT_URI, dp(240))
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                donationQr.setImageBitmap(qr)
                guideDonationQr.setImageBitmap(qr)
                donationPromptQr.setImageBitmap(qr)
                donationQrReady = true
            }
        }.start()
    }

    private fun setupFeatureControllers() {
        fontController = FontController(this, { config }, ::onConfigChanged)
        textHome = TextHomeController(
            this, { config }, repository, fontController,
            object : TextHomeController.Callbacks {
                override fun launchApp(info: ResolveInfo) = this@LauncherActivity.launchApp(info)
                override fun showActions(info: ResolveInfo) = showActionSheet(info)
                override fun toggleManageSelection(info: ResolveInfo, row: TextView) =
                    toggleHomeManageSelect(info, row)
            }
        )
        clockArea = ClockAreaController(
            this, pageHome, { config }, { textHome.resolvedPaddingDp() }
        )
        textHome.bind()
        clockArea.bind()
        clockArea.setOnClockClickListener { startWifiTransfer() }
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
        rowDonationPrompt = findViewById(R.id.rowDonationPrompt)
        settingsDonationPromptValue = findViewById(R.id.settingsDonationPromptValue)
        settingsInflated = true

        settingsController = SettingsController(
            this,
            { config },
            ::onConfigChanged,
            ::showSelectOverlay,
            ::pickWallpaper,
            ::removeWallpaper,
            { fontController.showPicker(FontController.Target.GLOBAL) },
            { fontController.showPicker(FontController.Target.CLOCK) }
        ).also {
            it.bind()
            it.setup(config)
        }

        findViewById<View>(R.id.rowHelp).setOnClickListener {
            rowDonationPrompt.visibility = View.GONE
            showInfo(getString(R.string.settings_help), getString(R.string.help_text))
        }
        findViewById<View>(R.id.rowAbout).setOnClickListener {
            rowDonationPrompt.visibility = View.VISIBLE
            updateDonationPromptValue()
            showInfo(getString(R.string.settings_about), getString(R.string.about_text))
        }
        rowDonationPrompt.setOnClickListener {
            store.setDonationPromptEnabled(!store.isDonationPromptEnabled())
            updateDonationPromptValue()
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
        if (::settingsController.isInitialized) settingsController.updateValues(config)

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
                textHome.populate(homeManage, repository.hiddenPackages(), manageSelected)
            } else {
                clockArea.apply()
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
        gridHome.labelStrokePx = fontController.strokePx()
        gridHome.labelTypeface = fontController.launcherTypeface()

        // 纯文字模式：顶部两套块 / 横线 / 网格全部隐藏，列表吃满剩余空间
        if (config.homeStyle == 1) {
            homeTopClassic.visibility = View.GONE
            homeTopSplit.visibility = View.GONE
            homeTopDivider.visibility = View.GONE
            gridHome.visibility = View.GONE
            textHome.show()
            clockArea.show()
            applyTextHomeMetrics()
            StartupTracer.stage("refreshHome-textMetrics")
            textHome.populate(homeManage, repository.hiddenPackages(), manageSelected)
            StartupTracer.stage("refreshHome-textList")
            fontController.applyToTree(pageHome)
            clockArea.applyTypeface(fontController.clockTypeface() ?: fontController.launcherTypeface())
            applyClockContextSize()
            applyImmersiveBars()
            return
        }

        textHome.hide()
        gridHome.visibility = View.VISIBLE
        // 时钟区与图标主页共用：顶部原生时间文字隐藏，12 种样式时钟接管显示
        clockArea.show()
        if (textTime.visibility != View.GONE) textTime.visibility = View.GONE
        if (textTimeSplit.visibility != View.GONE) textTimeSplit.visibility = View.GONE
        clockArea.apply()
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
        fontController.applyToTree(pageHome)
        clockArea.applyTypeface(fontController.clockTypeface() ?: fontController.launcherTypeface())
        applyImmersiveBars()
    }

    // ------------------------------------------------------------------ 纯文字主页 / 时钟

    /** 纯文字主页版式 + 共用时钟区（图标主页与纯文字主页共用）。 */
    private fun applyTextHomeMetrics() {
        textHome.applyMetrics()
        clockArea.apply()
        clockArea.applyInfoTextSize(textHome.infoTextSizeSp())
        clockArea.applyTypeface(fontController.clockTypeface() ?: fontController.launcherTypeface())
    }

    /** 时钟周边字号：日期行 + 时钟信息行 + 状态行（0 = 保持各自默认）。 */
    private fun applyClockContextSize() {
        val sp = config.clockContextSize.coerceIn(0, 32)
        if (sp <= 0) {
            // 自动：日期行回到 XML 字号；信息行由 applyTextHomeMetrics 恢复默认；
            // 状态行由 StatusBarController 还原各自记录的字号。
            val px = android.util.TypedValue.COMPLEX_UNIT_PX
            if (dateTextSizePx > 0f) textDate.setTextSize(px, dateTextSizePx)
            if (dateSplitTextSizePx > 0f) textDateSplit.setTextSize(px, dateSplitTextSizePx)
            clockArea.applyContextTextSize(0)
            if (::statusController.isInitialized) statusController.applyContextTextSize(0)
            return
        }
        val unit = android.util.TypedValue.COMPLEX_UNIT_DIP
        textDate.setTextSize(unit, sp.toFloat())
        textDateSplit.setTextSize(unit, sp.toFloat())
        clockArea.applyContextTextSize(sp)
        if (::statusController.isInitialized) statusController.applyContextTextSize(sp)
    }

    /** 纯文字主页沉浸式：开关开启 + 纯文字主页 + 主页页 → 隐藏系统状态栏与导航栏；
     *  其余场景恢复显示（API 30+ 走 WindowInsetsController，旧版本走 systemUiVisibility）。 */
    private fun applyImmersiveBars() {
        if (!::clockArea.isInitialized) return
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

    private fun toggleHomeManageSelect(info: ResolveInfo, row: TextView) {
        val pkg = info.activityInfo.packageName
        if (manageSelected.contains(pkg)) manageSelected.remove(pkg) else manageSelected.add(pkg)
        textHome.updateManageRow(row, info, repository.hiddenPackages(), manageSelected)
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

    // ------------------------------------------------------------------ 字体

    /** 对整个内容树应用一次字体，随后重设时钟字体。 */
    private fun refreshAppFont() {
        fontController.refreshAll()
        clockArea.applyTypeface(fontController.clockTypeface() ?: fontController.launcherTypeface())
    }

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
        gridApps.labelStrokePx = fontController.strokePx()
        gridApps.labelTypeface = fontController.launcherTypeface()
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
        textHome.populate(true, repository.hiddenPackages(), manageSelected)
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
        fontController.applyToTree(selectList)
    }

    private fun hideSelectOverlay() {
        selectOverlay.visibility = View.GONE
        selectCallback = null
    }

    // ------------------------------------------------------------------ WiFi transfer

    private fun setupWifiTransfer() {
        // 时钟区（12 种样式）的点击由 ClockAreaController 统一接线
        textTime.setOnClickListener { startWifiTransfer() }
        textTimeSplit.setOnClickListener { startWifiTransfer() }
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
        if (fontController.onActivityResult(requestCode, resultCode, data)) return
        if (requestCode == REQ_UNINSTALL) {
            startNextUninstall()
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
        if (fontController.onRequestPermissionsResult(requestCode, grantResults)) return
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
        infoOverlay.visibility = View.VISIBLE
    }

    private fun updateDonationPromptValue() {
        settingsDonationPromptValue.text =
            getString(if (store.isDonationPromptEnabled()) R.string.divider_on else R.string.divider_off)
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
        recentGrid.labelStrokePx = fontController.strokePx()
        recentGrid.labelTypeface = fontController.launcherTypeface()

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

        // 共用时钟区：12 种样式同步更新（信息行显示纯日期，电量由快捷行电池项承担）
        val hh = SimpleDateFormat("HH", Locale.getDefault()).format(now)
        val mm = SimpleDateFormat("mm", Locale.getDefault()).format(now)
        clockArea.update(hh, mm, date, batteryPercent())
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
    }
}