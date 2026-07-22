# 知乎版本适配与开发

## 本机环境

- JDK 17
- Android SDK 36、Build Tools 36
- NDK 28.2.13676358
- CMake 3.22.1、Ninja
- JADX 1.5.5
- dex2jar 2.4（大型 APK 的 Java 内存上限建议设为 8GB）
- ADB 和安装了 LSPosed 的 arm64 测试设备

项目使用 `JAVA_HOME` 和 `ANDROID_HOME`。本机 SDK 路径写在被 Git 忽略的 `local.properties` 中。

## 新版适配流程

把合法取得的知乎 APK 放进 `targetapp`，然后执行：

```powershell
.\tools\prepare-target.ps1 -ApkPath '.\targetapp\知乎11.1.0.apk' -RunTests
```

脚本会自动读取版本名和 versionCode，生成 `targetapp/jars/<版本名> <versionCode>.jar`，并生成可搜索的 JADX 源码。只想生成测试 JAR 时增加 `-SkipDecompile`。

也可以单独测试已经准备好的所有目标版本：

```powershell
$env:ZHILIAO_TEST_DIR = (Resolve-Path '.\targetapp\jars').Path
.\gradlew.bat testDebugUnitTest --rerun-tasks
```

## Hook 目标选择原则

新增或修复 Hook 时按以下顺序定位目标：

1. 优先使用没有混淆的稳定接口、模型类和 Android 回调。
2. 类名会变化时，用接口、父类、字段类型和方法签名组合识别。
3. 方法名会变化时，使用 `TargetResolver` 按参数、返回值、修饰符和稳定顺序解析。
4. 只有无法枚举目标包时才增加候选类名；不要继续堆叠版本号判断。
5. 如果目标跨包移动或没有可枚举名称，再引入 DexKit，用字符串和调用关系查找。`TargetResolver` 应继续作为统一入口，避免业务 Hook 直接依赖 DexKit。

每个 Hook 的 `init()` 必须完成全部目标解析，`hook()` 只负责安装 Hook。这样 JVM 兼容性测试可以在没有 LSPosed 的情况下发现大多数版本变化。

## 真机验证

单元测试不能执行 LSPosed 的 native Hook，因此发布前仍需在 Android 14 或更高版本的 arm64 设备验证：

1. 安装构建出的模块 APK 和目标知乎版本。
2. 在 LSPosed 中只勾选知乎，强制停止后重新启动知乎。
3. 逐项检查启动广告、信息流、回答、评论、搜索、分享、设置页和 WebView。
4. 失败时保存 LSPosed 模块日志和 `adb logcat`。日志中的 `[兼容性][版本][功能][阶段]` 可区分目标定位失败与 Hook 安装失败。
