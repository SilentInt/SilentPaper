# 第三方依赖与许可证

## 当前依赖清单

| 依赖 | 版本/来源 | 许可证 | 用途 | 是否随 APK 分发 |
|---|---|---|---|---|
| ZXing Core | 3.5.1（`libs/zxing-core-3.5.1.jar`） | Apache-2.0 | WiFi 传书二维码生成 | 是 |
| Kotlin 标准库 | Kotlin Gradle Plugin 2.2 | Apache-2.0 | 运行时标准库 | 是 |
| Android Gradle Plugin | 9.0.0 | Apache-2.0 | 构建工具 | 否 |
| Gradle | 9.1.0 | Apache-2.0 | 构建工具 | 否 |

## 已移除依赖

- **YotaDevices SDK Stub**（`yotadevice_sdk-full-stub.jar`）：随 Yota 刷新管理一并移除。
- **XposedBridgeApi Stub**：随 Xposed 集成一并移除。
- **libsu（com.github.topjohnwu.libsu）**：随 Root 能力一并移除。

## 源码许可

- SilentPaper 源码遵循 **GNU Affero General Public License v3.0 (AGPL-3.0)**，全文见项目根目录 [LICENSE](../LICENSE)。
- 本项目源自 [SilentInt/YotaPaper](https://github.com/SilentInt/YotaPaper)，原项目同样以 AGPL-3.0 发布。
- 任何修改、分发或通过网络提供服务，均需遵守 AGPL-3.0 的源码开放要求，并保留原创作者与本衍生说明。

## ZXing

```
Copyright (C) 2008 ZXing authors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
