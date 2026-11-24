# 静纸（JingZhi）v1.9

> 基于 SilentPaper 的独立共存修改版 —— 把极简墨水屏桌面带到普通 Android 设备（含掌阅 Neo 3 Ultra / Android 14）。

静纸是 [SilentInt/SilentPaper v1.0](https://github.com/SilentInt/SilentPaper/releases/tag/v1.0) 的下游衍生版，使用独立包名 `com.momocat.silentpaper`（应用名「静纸」），可与原版 SilentPaper 在同一台设备上共存。本仓库的 `jingzhi-v1.9` 分支承载 v1.9 全部改造。

> 本项目遵循上游的 **GNU AGPL-3.0** 许可，完整保留原作者署名与本说明。

## v1.9 亮点

- **修复 Android 11+ 启动崩溃**：状态栏纸色设置移到 `setContentView` 之后，解决 API 30+ 的 DecorView NPE。
- **时钟样式两主页共用**：12 种时钟样式在图标主页 / 纯文字主页复用，图标主页原位原尺寸、改动即时生效。
- **时钟周边字号 / 界面字号**两档独立旋钮，与时间字号三档互不干扰。
- **时钟下快捷行**：电池 / WiFi / 蓝牙快捷行，随线条 / 实心风格，点击直达对应设置。
- **纯文字主页沉浸式**：可隐藏状态栏与导航栏。
- **壁纸铺满整窗 + 底部标签栏透明 + 纸色底片衬底**，深色壁纸下文字仍可读。
- **修复 Android 11+ 字体无法选择**：新增 SAF「从文件夹选择字体」入口，持久授权跨重启。
- 充电闪电指示、批量管理点击区收窄、隐藏标记改实心圆、WiFi 图标直达设置等共 16 项迭代。

完整逐项说明见 [静纸-v1.9-说明.md](静纸-v1.9-说明.md)，版本历史见 [CHANGELOG.md](CHANGELOG.md)。

## 目录

| 路径 | 说明 |
|---|---|
| `app/src/main/java/com/silent/paper/` | 应用源码（单 Activity，无 AndroidX） |
| `app/src/main/res/` | 布局 / 可绘制 / 资源 |
| `docs/` | 架构、用户指南、第三方声明 |
| `素材/` | v1.9 真机验证截图（7 张） |
| `静纸-1.9-release.apk` | 已签名安装包（debug 证书，versionCode 10） |
| `静纸-v1.9-说明.md` | v1.9 交付说明（逐项改动与实机验证记录） |
| `CHANGELOG.md` | v1.1–v1.9 更新日志 |
| `scripts/` | 构建 / 部署辅助脚本 |

## 构建

环境：JDK 17+、Android SDK 36（platforms/android-36、build-tools/36.0.0）、自带 Gradle Wrapper。

```bash
export ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

Windows 可用 `scripts\build.bat`。打包 release 使用 debug 测试证书便于直接安装；如需正式签名请自行替换 `app/build.gradle.kts` 中的 signingConfig。

## 安装

```bash
adb install -r 静纸-1.9-release.apk
```

应用为桌面启动器，安装后在系统桌面选择器中将其设为默认桌面即可。配置、壁纸、字体原地保留升级。

## 来源与许可

- 原版：[SilentInt/SilentPaper v1.0](https://github.com/SilentInt/SilentPaper)，源自 [SilentInt/YotaPaper](https://github.com/SilentInt/YotaPaper)，遵循 **GNU AGPL-3.0**。
- 时钟样式几何参数参考 [gezimos/inkOS](https://github.com/gezimos/inkOS)（GPLv3）。
- 第三方组件：ZXing Core 3.5.1、Kotlin 标准库（Apache-2.0），详见 [docs/THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md)。
- 许可证全文见 [LICENSE](LICENSE)，署名见 [NOTICE](NOTICE)。

衍生版本须继续以 AGPL-3.0 发布并保留署名。

修改者：momo_cat · 包名 `com.momocat.silentpaper`。
