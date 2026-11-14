# 更新日志

## [1.0] - 2026-11-14

SilentPaper 首个版本：基于 YotaPaper v0.7 的去 Yota 化极简桌面。

### 移除

- **Xposed**：模块入口、`MainHook`、`EpdPageTurnHook`、`SwipeTracker`、XposedBridge 编译桩。
- **Root**：libsu、Root Shell、强制停止、Root 清理后台、Root 授权补授。
- **Yota 刷新管理**：背屏 SDK、刷新模式、翻页 / 开屏 / 息屏动画、EPD 参数管理与系统 Provider 同步。
- **控制中心**：下拉手势服务、主页浮层与独立窗口、通知 / 媒体读取、飞行模式 / 电源 / 纯背屏等快捷项。
- 相关权限、清单组件、设置项、资源与文档。

### 变更

- 应用标识全量改名：`com.yota.launcher` → `com.silent.paper`，应用名 `SilentPaper`，版本 1.0。
- 合并上游 PR #5 的样式改动：
  - 主页顶部双风格：经典居中 / 左右分栏
  - 状态图标双风格：线条 / 实心
  - 自绘 `BatteryIconView`（墨水屏像素对齐）
  - 新版 WiFi / 蓝牙图标、竖向虚线、0.5dp 发丝线
  - 顶部分割线与网格对齐，并跟随「主页分割线」开关
- 锁屏统一走设备管理器；移除手动全刷入口。

### 兼容性

- 保持 `minSdk 17`（Android 4.2）。
- 移除 libsu（原先把最低 API 拉到 19）。
- WiFi 传书的 `Socket.use` 改为 `try/finally`，避免 API 19 的 `Closeable` 要求。
- 应用图标改为 `mipmap-*` PNG，4.2 可直接显示。
- Android 12+ 蓝牙开关在未授权时跳转系统设置；Android 13+ 注册广播使用 `RECEIVER_EXPORTED`。
- 通过 `lintDebug`，以 minSdk 17 为基准无 `NewApi` 错误。

### 保留

主页 / 应用抽屉、固定与隐藏、最近任务、图标包、自动线条图标、WiFi 传书、锁屏、赞赏、首启引导、设置页。
