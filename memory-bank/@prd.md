# LocalADB - 本机无线 ADB Shell

## 核心功能
- Bundled ADB 二进制文件（ARM64 Android）
- 通过 Android 无线调试（Wireless Debugging）连接本机 ADB server
- 终端界面：输入命令 → 执行 → 显示输出
- 无需电脑，手机独立运行

## 技术方案
- 语言：Kotlin
- UI：Jetpack Compose 终端风格界面
- ADB binary：从官方 Android platform-tools 提取 adb 二进制
- 连接方式：Wireless Debugging 的配对模式（pairing）
- 最低 Android 版本：API 26 (Android 8.0)，目标 API 34

## 界面设计
- 顶部：连接状态栏（未连接/配对中/已连接）
- 中部：终端输出区域（scrollable，monospace 字体）
- 底部：命令输入框 + 执行按钮
- 菜单：设置（清除配对信息）、关于

## 连接流程
1. 用户开启"无线调试"→ 选择"配对设备"
2. App 显示配对码输入框
3. 用户输入 IP:Port 和配对码
4. App 通过内置 ADB 执行 `adb pair ip:port paircode`
5. 连接成功后执行 `adb connect ip:port`
6. 保持连接，执行用户输入的命令 `adb shell <cmd>`

## 包名
com.sting.localadb