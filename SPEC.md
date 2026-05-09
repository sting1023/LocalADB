# ADB Terminal - 独立 ADB 命令执行器

## 项目信息
- **包名**: com.sting.adbterminal
- **GitHub**: sting1023/ADBTerminal
- **描述**: 在手机上直接执行 ADB 命令的终端工具

## 功能需求
1. 命令输入框（多行文本）
2. 执行按钮
3. 输出显示区域（支持滚动）
4. 清空输出按钮
5. 清空输入按钮
6. 命令执行在 app 进程内（Runtime.getRuntime().exec()）
7. 不需要 root，不需要 root 权限
8. 权限：INTERNET（用于调试连接）、WAKE_LOCK

## 技术栈
- Kotlin + Jetpack Compose
- Material Design 3
- Android Gradle Plugin 8.2.2
- minSdk 26, targetSdk 34

## 权限
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

## 界面
- 顶部：标题栏 "ADB Terminal"
- 中部：输出显示区域（可滚动，深色背景，浅色文字）
- 底部：输入框 + 执行按钮 + 清空按钮
- 布局：纵向排列，输出区占主要空间

## 命令执行
- 使用 `Runtime.getRuntime().exec(command)` 在 app 进程内执行
- 输出通过 `BufferedReader` 读取
- 在协程内执行，不阻塞 UI
