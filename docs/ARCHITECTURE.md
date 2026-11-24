# 架构与源码结构

## 总览

SilentPaper 是单 Activity 应用，不依赖 AndroidX / RecyclerView / ViewPager / ConstraintLayout / Coroutines。

- 语言：Kotlin
- UI：纯 XML 布局 + 自定义 `EInkLauncherView` 网格视图
- 持久化：`SharedPreferences`
- 图标：系统图标 + appfilter 图标包 + 自绘线条图标（`Canvas`）
- 最低版本：API 17（Android 4.2）

## 包结构

```
com.silent.paper
├── LauncherActivity.kt        主界面：页面切换、网格装配、最近任务、锁屏、WiFi 传书
├── LockAdminReceiver.kt       设备管理器锁屏
├── QrCodes.kt                 二维码生成（ZXing 封装）
├── WifiTransferServer.kt      WiFi 传书 HTTP 服务
├── data/
│   ├── Models.kt              LauncherConfig 配置模型与默认值
│   ├── LauncherConfigStore.kt 设置持久化（silent_paper_config）
│   ├── AppRepository.kt       应用列表、隐藏/固定、点击次数、label 缓存
│   ├── RecentTasks.kt         最近任务（API 21+ 隔离实现）
│   └── grid/GridSizing.kt     自适应行列与图标尺寸计算
└── ui/
    ├── EInkLauncherView.kt       网格视图（固定 ViewHolder 池，无动画）
    ├── BatteryIconView.kt        线条 / 实心电池图标（Canvas 绘制，含充电闪电）
    ├── IconLoader.kt             图标加载：系统图标 / 图标包 / 线条图标
    ├── LineIconRenderer.kt       根据原图标生成黑色线条图标
    ├── SettingsController.kt     设置页控制器（折叠分组、弹窗列表、步进器）
    ├── StatusBarController.kt    电量 / WiFi / 蓝牙实时状态与两套顶部风格
    ├── ClockAreaController.kt    12 种时钟样式、两主页落位、对齐与字号
    ├── TextHomeController.kt     纯文字主页列表、版式参数与标语
    ├── WallpaperController.kt    壁纸导入、灰度 + 纸色淡化渲染
    ├── FontController.kt         字体扫描 / SAF 选择、导入与全局应用
    ├── InkClockViews.kt          翻页 / 圆环 / 表盘 / 点阵自绘时钟
    ├── SevenSegmentClockView.kt  七段数码管时钟
    └── ChipTextView.kt           壁纸上的纸色圆角底片文字
```

### 功能控制器边界

`LauncherActivity` 保留页面切换、网格装配、最近任务、WiFi 传书、锁屏等
主流程；新增能力各自收敛到 `ui/` 下的控制器：

| 控制器 | 职责 | 对外接口 |
|---|---|---|
| `ClockAreaController` | 12 种时钟变体、快捷行、信息行；按主页形态重挂到顶部时间槽位或 pageHome | `bind` / `apply` / `update` / `applyTypeface` |
| `TextHomeController` | 应用名列表、页边距 / 行距 / 字号、标语、批量管理行状态 | `applyMetrics` / `populate` / `updateManageRow` |
| `WallpaperController` | 选图、私有目录落盘、cover 裁剪 + 灰度 + 纸色混合 | `pick` / `importPicked` / `apply` / `clear` |
| `FontController` | 字体扫描、SAF 文件夹、预览对话框、导入与视图树应用 | `showPicker` / `applyToTree` / `launcherTypeface` / `clockTypeface` |

控制器通过 lambda 读取当前 `LauncherConfig`，配置写回统一走
`LauncherActivity.onConfigChanged(config, affected)`，由 `AFFECT_*` 位掩码
决定刷新范围。

## 主界面结构

`LauncherActivity` 是唯一 Activity（`singleInstance`，MAIN/LAUNCHER/HOME/DEFAULT）。

| 页面 | 实现 |
|---|---|
| 主页 | `page_home.xml` + `gridHome` |
| 应用抽屉 | `page_apps.xml`（ViewStub 懒加载）+ `gridApps` |
| 设置 | `page_settings.xml`（ViewStub 懒加载）+ `SettingsController` |

浮层（全部为 `FrameLayout` 底部面板，非系统 Dialog）：

| 浮层 | 用途 |
|---|---|
| `actionOverlay` | 长按应用操作菜单 |
| `recentOverlay` | 最近任务 |
| `selectOverlay` | 设置项多选弹窗 |
| `infoOverlay` | 使用说明 / 关于 |
| `wifiOverlay` | WiFi 传书 |
| `donationOverlay` | 50 次 HOME 赞赏提醒 |
| `guideOverlay` | 首启引导 |

## 网格视图（EInkLauncherView）

- 固定大小的 ViewHolder 池（`columns × rows`），复用无重建。
- 空槽位：alpha=0、图标置空、角标 GONE。
- 滑动阈值：`min(width, height) / 8`；`dx < 0` 为下一页，否则上一页。
- 无任何动画；图标与文字大小由 `GridSizing` 按格子尺寸反算。

## 顶部状态行

`StatusBarController` 同时维护两套布局：

- **经典居中**：`homeTopClassic`，电池图标 + 百分比、WiFi、蓝牙横排。
- **左右分栏**：`homeTopSplit`，左侧时间/日期，右侧电池/蓝牙/WiFi 竖排，中间竖向虚线。

`BatteryIconView` 用整数像素坐标绘制，避免墨水屏抗锯齿把细线摊成灰线；`setBlockStyle` 切换线条/实心，`setPercent` 按档位（线条四格）或连续填充刷新。

## 最近任务

- 数据来源：`UsageStatsManager` 最近 7 天，按 `lastTimeUsed` 倒序，最多 12 个，跳过自身。
- 仅 API 21+ 可用；4.2 上显示「需要 Android 5.0 及以上」。
- 时间过滤：`recentWindowMs`（0=关，否则只显示 `now - window` 之后使用过的应用）。
- 清空：记录 `recent_cleared_at`，过滤该时间之前的使用记录；单个应用清理记录 `recent_cleared_<pkg>`。
- 清后台：系统 `killBackgroundProcesses`（非 Root）。
- 打开：双击「主页」标签（1..799ms）或双击 HOME；再次按 HOME 关闭。

## 配置模型（LauncherConfig）

```kotlin
columns = 0, rows = 0,          // 0 = 自动
homeColumns = 0, homeRows = 0,
showHomeDividers = true, showAppDividers = true,
homeTopStyle = 0,               // 0=经典居中, 1=左右分栏
homeTopIconStyle = 1,           // 0=线条, 1=实心
recentWindowMs = 30 * 60_000L,
recentCancelBackToApp = true,
iconPack = "", autoLineIcons = true,

homeStyle = 0,                  // 0=图标网格, 1=纯文字
textFontSize / textLineSpacing / textPadding / textTimeSize,
clockStyle = 0,                 // 0..11，见 ClockAreaController
clockTop / clockBottom / clockX, clockAlign, listAlign,
listTop / listBottom / listLeft / listRight,
homeSlogan / homeSloganOn / textHomeHideBars,
clockContextSize, uiFontScale,
appFontPath / clockFontPath / fontFolderUri / textWeight,
wallpaperPath / wallpaperDimOn / wallpaperDim,
drawerShowsHidden
```

## 兼容性设计

1. **无 AndroidX**：仅使用 `android.app.Activity`，APK 体积小。
2. **稳定排序**：应用列表先按 label 升序，再按「固定优先 → 点击次数」稳定排序。
3. **label 缓存**：所有 label 合并为单个 JSON 字符串持久化，避免冷启动逐进程 IPC。
4. **设置懒加载**：`pageSettings` 通过 `ViewStub` 首次进入设置页时才 inflate。
5. **墨水屏友好**：界面无动画、无卡片阴影；弹窗为静态底部面板。
6. **低版本**：应用图标使用 PNG；矢量图标由 AAPT 为 minSdk 17 生成 PNG；WiFi 传书不使用 API 19 的 `Closeable` 语义。
