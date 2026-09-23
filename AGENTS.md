# AGENTS.md

知了（Zhiliao）：知乎 Android 客户端的 LSPosed 模块，去广告 + 界面净化。运行在知乎进程内，不是独立 App。

## 怎么跑

```powershell
# 兼容性单测（不需要设备）：遍历 targetapp/jars 里每个审计 JAR 跑每个 Hook 的 init()
$env:ZHILIAO_TEST_DIR = (Resolve-Path '.\targetapp\jars').Path; .\gradlew.bat testDebugUnitTest

# 从 APK 生成审计 JAR（可加 -RunTests）
.\tools\prepare-target.ps1 -ApkPath '.\targetapp\<知乎>.apk' -SkipDecompile

# 发布构建（R8 + native），产物 app/build/outputs/apk/release/Zhiliao_<版本>.apk
.\gradlew.bat assembleRelease
```

真机验证：`adb install -r <apk>` → `adb shell am force-stop com.zhihu.android` → 启动知乎 → 看 LSPosed 日志里的 `[Zhiliao][自检]` 行
（`Hook句柄=… 初始化成功=31/31 全部可用` 表示所有 Hook 装上；失败会打 `[兼容性][版本][功能][阶段]`）。
设备端日志：`/data/adb/lspd/log/modules_*.log`（LSPosed 2.x）。

## 技术栈

- Java（业务代码没有 Kotlin）；libxposed Modern API 102，薄适配层在 `app/src/main/java/com/shatyuka/zhiliao/xposed/`（保留旧 before/after 写法）。
- 入口 `MainHook`（`app/src/main/resources/META-INF/xposed/`）→ `Hooks` 依次 init/hook 每个 `IHook`。
- CMake native（Dobby 子模块）+ R8 full mode；版本号在 `gradle.properties` 的 `appVerName` / `appVerCode`。

## 约定（改 Hook 前必读）

- 每个 Hook 一个类：`init()` 只解析目标，`hook()` 只安装 Hook —— 单测靠这个约定在没有 LSPosed 的 JVM 里抓版本变化。
- 目标定位按结构/签名（`TargetResolver`、`Helper.findClass`、类型校验），不要堆版本号判断；方法名/类名会变，运行期常量（如刷新来源名 `Pull` / `TabClick`）更稳。
- **同一 versionCode 的不同渠道包混淆名不同**：`targetapp/jars` 里的审计 JAR 只代表那一份构建，改动必须真机验证。详见 `docs/DEVELOPMENT.md`。
- 改动用户可见行为时同步 `README.md` 的功能列表；设置项在 `app/src/main/res/xml/preferences_zhihu.xml` + `hooks/ZhihuPreference.java`（图标见 `tools/update-icons.mjs`，Lucide，ISC）。

## 当前状态

- 现役 26.09.24（已发 GitHub Release）；真机已验证：知乎 11.10.0/41012 小米渠道包，31/31 Hook 可用、禁止自动刷新按来源名拦截、Lucide 图标正常。
- `docs/compat-audit-11.10.0.md`、`docs/watermark-audit-11.10.0.md` 是审计快照：现役结论看 compat-audit §六，水印结论目前只有静态证据、真机未验证。
