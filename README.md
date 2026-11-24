# SilentPaper

> 白纸黑字 · 1px 灰线 · 无卡片 · 无打扰 —— 一个纯 Android 极简桌面。

SilentPaper 是一个单 Activity、无 AndroidX 依赖的极简启动器：白色纸面、黑色文字、1px 灰色发丝线，没有卡片、阴影和多余装饰。它源自 [SilentInt/YotaPaper](https://github.com/SilentInt/YotaPaper)，移除了全部 Yota 特化能力，回归一个可在普通 Android 设备上使用的纯净桌面。

## 功能

### 主页
- 自适应网格，应用按「固定优先 → 点击次数」自动排序
- 单击图标打开应用；长按弹出操作菜单：固定 / 取消固定、隐藏、应用信息
- 双击底部「主页」标签打开最近任务
- 顶部两套风格（设置 → 网格与图标主页）：
  - **经典居中**：日期、时间、状态行居中
  - **左右分栏**：时间日期靠左，电量 / 蓝牙 / WiFi 竖排右对齐
- 状态图标两套风格：**线条 / 实心**（电池、WiFi、蓝牙）
- 自绘电池图标，按墨屏像素对齐，四格 / 连续填充两种画法；充电时框内绘制闪电
- 12 种时钟样式（分栏 / 冒号 / 堆叠 / 数码管 / 胶囊 / 翻页 / 圆环 / 竖线 / 横排 / 线框 / 表盘 / 点阵），图标主页与纯文字主页共用
- **纯文字主页**：无图标应用名列表 + 大号时钟与信息行；字号 / 行距 / 页边距 / 时钟与列表对齐 / 边距均可调，支持批量管理与底部标语
- **壁纸**：从相册选图，自动灰度化并按比例混入纸色；铺满整窗，底部标签栏透明
- **字体**：APP 字体 / 时钟字体独立设置，支持系统文件选择器与 SAF 文件夹（Android 11+），另有字体粗细、界面字号、时钟周边字号
- 日期行、状态行与界面字号可独立缩放，互不干扰

### 应用抽屉
- 自适应网格，左右或上下滑动翻页
- 左上角「管理」进入批量模式：隐藏 / 显示 / 卸载
- 可选「隐藏应用在应用页可见」：隐藏应用仍在应用页显示，带 ● 标记
- 应用安装、卸载、更新后自动刷新

### 最近任务
- 双击「主页」标签或双击 HOME 打开，网格展示
- 时间范围：关 / 5 分钟 / 30 分钟 / 1 小时 / 6 小时 / 1 天
- 一键清空；取消行为可选「回到上一应用 / 回到桌面」
- 依赖系统使用情况统计，仅 Android 5.0（API 21）及以上可用

### 其他
- 第三方图标包（appfilter 映射）与自动线条图标
- WiFi / 蓝牙状态显示；Android 10+ WiFi 点击直达系统设置，旧版本保留原地开关；长按进系统设置
- WiFi 传书：25025 端口网页上传，文件保存到 `WIFI_transfer`
- 锁屏：通过设备管理器实现
- 设置项分组：网格与图标主页 / 纯文字主页 / 时钟 / 字体 / 图标 / 壁纸 / 最近任务
- 赞赏：关于页二维码与 50 次 HOME 提醒（可在设置 → 关于关闭）

## 与原版 YotaPaper 的差异

已移除：

| 模块 | 内容 |
|---|---|
| Xposed | 模块入口、`MainHook` / `EpdPageTurnHook` / `SwipeTracker` |
| Root | libsu Root Shell、强制停止、Root 清理后台 |
| Yota EPD | 背屏 SDK、刷新模式、翻页 / 开屏 / 息屏动画、EPD 参数管理与 Provider 同步 |
| 控制中心 | 下拉手势服务、浮层面板、通知 / 媒体读取、飞行模式 / 电源等快捷开关 |

同时把应用标识从 `com.yota.launcher` 改为 `com.silent.paper`，版本重置为 1.0。

## 安卓版本需求

| 项目 | 值 |
|---|---|
| 最低支持 | **Android 4.2（API 17，minSdk 17）** |
| 目标 / 编译 SDK | Android 16（API 36） |
| 最近任务 | Android 5.0（API 21）及以上 |

## 构建

### 环境
- JDK 17+
- Android SDK 36（`platforms/android-36`、`build-tools/36.0.0`）
- 自带 Gradle Wrapper（Gradle 9.1.0）

### WSL / Linux / macOS

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleDebug
```

或在项目根目录创建 `local.properties`（已被 gitignore）：

```properties
sdk.dir=/home/user/Android/Sdk
```

### Windows

```bat
set ANDROID_HOME=C:\tools\android-sdk
gradlew.bat assembleDebug
```

一键脚本：

```bat
scripts\build.bat
scripts\deploy.bat
```

或 WSL / Linux：

```bash
scripts/build.sh
scripts/deploy.sh
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

## 安装与权限

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell appops set com.silent.paper GET_USAGE_STATS allow
adb shell am start -n com.silent.paper/.LauncherActivity
```

| 权限 | 用途 |
|---|---|
| `PACKAGE_USAGE_STATS` | 最近任务（需在系统设置或 adb 授予） |
| `QUERY_ALL_PACKAGES` | 枚举已安装应用 |
| `REQUEST_DELETE_PACKAGES` | 管理模式卸载 |
| `ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE` | WiFi 状态与开关 |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` / `BLUETOOTH_CONNECT` | 蓝牙状态与开关（32+ 需要运行时授权） |
| `ACCESS_NETWORK_STATE` / `INTERNET` | WiFi 传书 |
| `KILL_BACKGROUND_PROCESSES` | 最近任务清理 |
| `WRITE_EXTERNAL_STORAGE`（≤28） | WiFi 传书写入文件 |
| `READ_EXTERNAL_STORAGE`（≤32） | 扫描字体目录（Android 11+ 可改用 SAF 文件夹） |

## 目录

```
app/src/main/java/com/silent/paper/
├── LauncherActivity.kt        主界面：三页切换、网格、最近任务、WiFi 传书、锁屏
├── LockAdminReceiver.kt       设备管理器锁屏
├── QrCodes.kt                 ZXing 二维码封装
├── WifiTransferServer.kt      WiFi 传书 HTTP 服务
├── data/
│   ├── Models.kt              配置模型
│   ├── LauncherConfigStore.kt 设置持久化
│   ├── AppRepository.kt       应用列表、隐藏/固定、点击次数
│   ├── RecentTasks.kt         最近任务（API 21+ 隔离实现）
│   └── grid/GridSizing.kt     自适应行列与图标尺寸计算
└── ui/
    ├── EInkLauncherView.kt    网格 ViewGroup
    ├── BatteryIconView.kt     线条 / 实心电池图标（含充电闪电）
    ├── IconLoader.kt          系统图标 / 图标包 / 线条图标
    ├── LineIconRenderer.kt    线条图标生成
    ├── SettingsController.kt  设置页
    ├── StatusBarController.kt 电量 / WiFi / 蓝牙状态与快捷行
    ├── ClockAreaController.kt 12 种时钟样式、落位、对齐与字号
    ├── TextHomeController.kt  纯文字主页列表与版式
    ├── WallpaperController.kt 壁纸导入、灰度与纸色淡化
    ├── FontController.kt      字体选择、导入与全局应用
    ├── InkClockViews.kt       翻页 / 圆环 / 表盘 / 点阵时钟
    ├── SevenSegmentClockView.kt 七段数码管时钟
    └── ChipTextView.kt        壁纸上的纸色圆角底片文字
```

## 许可证与来源

- 本项目源自 [SilentInt/YotaPaper](https://github.com/SilentInt/YotaPaper)，遵循 **GNU AGPL-3.0**，全文见 [LICENSE](LICENSE)。
- 与原版一样，衍生版本也必须以 AGPL-3.0 发布，并保留本说明。
- 时钟样式的几何参数参考 [gezimos/inkOS](https://github.com/gezimos/inkOS)（GPLv3），按几何参数重绘移植。
- ZXing Core 为 Apache-2.0；Kotlin 标准库为 Apache-2.0。详见 [docs/THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md)。
