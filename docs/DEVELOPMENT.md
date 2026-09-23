# 知乎版本适配与开发

## 本机环境

- JDK 17
- Android SDK 36、Build Tools 36
- NDK 30.0.16248370
- CMake 3.22.1、Ninja
- JADX 1.5.5
- dex2jar 2.4（大型 APK 的 Java 内存上限建议设为 8GB）
- ADB 和安装了 LSPosed 的 arm64 测试设备

项目使用 `JAVA_HOME` 和 `ANDROID_HOME`。本机 SDK 路径写在被 Git 忽略的 `local.properties` 中。

## 模块结构

模块使用 [libxposed](https://github.com/libxposed/api) 的 Modern Xposed API 102，依赖 `io.github.libxposed:api:102.0.0`，需要实现 API 102 的框架（LSPosed 2.1.0 及以上）。

- 入口类 `com.shatyuka.zhiliao.MainHook` 继承 `io.github.libxposed.api.XposedModule`，由 `app/src/main/resources/META-INF/xposed/java_init.list` 声明，不再使用 `assets/xposed_init`。
- `META-INF/xposed/module.prop` 声明 `minApiVersion`、`targetApiVersion`、`staticScope`，`META-INF/xposed/scope.list` 声明作用域。这些文件必须进入 APK，所以 `packagingOptions` 只能排除具体的 `META-INF` 元数据文件，不能整体排除 `META-INF/**`。
- 模块名取 `android:label`，模块描述取 `android:description`，不再需要 `xposedmodule`、`xposedscope` 等 meta-data。
- `com.shatyuka.zhiliao.xposed` 是薄适配层：`XposedBridge`、`XC_MethodHook`、`XC_MethodReplacement`、`XposedHelpers` 保留 before/after 回调写法，内部转换成 API 102 的 `hook(Executable).intercept(Hooker)` 拦截器链。业务 Hook 沿用原有写法，新代码也可以直接使用链式 API。
- 框架不再提供 `XposedHelpers`，适配层只实现本项目用到的部分。Modern API 移除了资源 Hook，`NavRes`、`ZhihuPreference` 通过 Hook 普通 Java 方法实现。
- release 构建用 proguard 保持入口类名，并用 `-adaptresourcefilecontents META-INF/xposed/java_init.list` 让 R8 同步重命名后的类名。

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
6. 包内混淆名按 R8 的顺序 `a` … `z`、`a0` … `z0`、`a1` … 递增，`Helper.findClass` 的 `cycleRound` 就是枚举窗口，每轮 26 个名字。窗口不够时定位会直接失败：11.10.0 的 `WebViewClientWrapper` 已经排到 `com.zhihu.android.app.mercury.web.z0`，刚好落在 `cycleRound = 2` 之外，需要放宽到 3 轮。定位失败时先数清目标包里的类，再决定窗口大小。
7. 同一 `versionCode` 的不同渠道包混淆名可能完全不同（如 11.10.0 的 market 包与小米包）：`targetapp/jars` 里的审计 JAR 只代表那一份构建。定位一律按结构/签名，运行期常量（例如刷新来源名 `Pull` / `TabClick`）比类名和方法名稳定；改动后必须用真机日志确认。
8. 静态找不到真目标时，可以从真机 `base.apk` 重新生成审计 JAR，再配合 `[自检][触发]` 追踪与临时打印调用栈的 Hook 定位真实链路（AutoRefresh 就是这么定位到 `CommonReturnTopDelegate` 的）。

每个 Hook 的 `init()` 必须完成全部目标解析，`hook()` 只负责安装 Hook。这样 JVM 兼容性测试可以在没有 LSPosed 的情况下发现大多数版本变化。

## 真机验证

单元测试不能执行 LSPosed 的 native Hook，因此发布前仍需在 Android 14 或更高版本的 arm64 设备验证：

1. 安装构建出的模块 APK 和目标知乎版本。
2. 在 LSPosed 中只勾选知乎，强制停止后重新启动知乎。Modern API 不再 Hook 模块自身进程，模块设置页在知乎设置页里打开。
3. 逐项检查启动广告、信息流、回答、评论、搜索、分享、设置页和 WebView。
4. 失败时保存 LSPosed 模块日志和 `adb logcat`。日志中的 `[兼容性][版本][功能][阶段]` 可区分目标定位失败与 Hook 安装失败。
