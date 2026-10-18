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

- 现役 26.10.18；真机已验证：知乎 11.11.0/41106 小米渠道包与官网包，31/31 Hook 可用、Lucide 图标正常。
- 禁止自动刷新按刷新来源名分流：冷启动 `AppStart` 放行整条链路（缓存渲染成上次那一屏），随后在 `postRefreshSucceed` 丢弃网络结果，并在数据层拦截这次网络结果写回推荐缓存——两步一起才叫冻结（只丢 UI 结果的话，磁盘缓存仍会前进一格，下次冷启动就被带跑）。点「首页」`TabClick` 直接拦截；下拉 `Pull` 放行并解除冻结。冷启动不能直接拦截——拦掉刷新等于连缓存渲染一起拦掉，首页整片空白（真机已验证）。
- 缓存写入按结构定位：`FeedLocalDataSource` 的 `void(FeedList)` + 缓存回调接口 + 无参清缓存 + String 缓存键四个特征，候选名单（官网 11.11 `q0.x`、小米 `b.b`、market 11.10 `o0.x`）优先，落空时 DexKit 按精确签名扫 `com.zhihu.android.app.feed` 兜底；绝不写死渠道包的混淆名。未知刷新来源用 `refreshType` 自己的 boolean 判定，避免新来源被当成主动刷新放行。
- `docs/compat-audit-11.10.0.md`、`docs/watermark-audit-11.10.0.md` 是审计快照：现役结论看 compat-audit §六，水印结论目前只有静态证据、真机未验证。
- 反追踪的分享链路在 11.11.0 小米渠道包上类名整体漂移（`sharecore.u.d`→`sharecore.i.d` 等）。二维码**只能本地生成**：服务端 `image_share/qrcode` 并不按 `content` 原样编码，而是缩成 `http://s.zhihu.com/2_xxxx` 短链（用 OpenCV 解码同一链接的带参/不带参两张图确认，两次短链还不一样），所以改 `content`、改请求、改 URL 一律等于白改。现做法在 `ZHDraweeView#setImageURI(String)`（该方法定义在父类 Fresco `SimpleDraweeView` 上，按继承链找）拦下 `image_share` 地址，用 zxing 按干净链接本地编码一张，经 `getHierarchy().setImage(...)` 塞进控件并 `setResult(null)`，不联网也不经过 `ImageUrlUtils`。二维码**不能**换成本地 data URI：`setImageURI` 前会过 `ImageUrlUtils`，`data:` 直接抛异常，卡片留白框、切样式闪退（真机已复现）。
- 分享链接去参改走**文本级**：拦 `ClipboardManager#setPrimaryClip` 与 `Intent#putExtra/putExtras` 里的 `EXTRA_TEXT`，用正则认知乎链接再剥 `utm_*`/`share_code`，完全不依赖混淆名（真机已验证：复制链接粘出来是干净地址）。单测 `ShareLinkTest`。长图分享的二维码由 `WebRenderShareActivity` 里的网页自己画，不归客户端 hook 管，详见 tracking-audit §五～§七。
