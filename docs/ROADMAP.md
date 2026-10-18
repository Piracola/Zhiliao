# 知了动态适配路线图

对齐 daxiaamu fork 的思路，结合本仓库现状排的完整计划。
每一步做完都能独立发一版；前一步是后一步的地基。
影子模式与众包事实层经讨论暂不排入。

> 进度（2026-10-17）：
> 第〇阶段 1–5 已完成并出包（`Zhiliao_26.09.24.apk`，编译 + 41 条单测通过）：独立 miuix 设置 App、
> 远端偏好通道（Hook 侧只读，读穿透回落到旧文件）、知乎内设置页已删除、`edit_js` 等旧设置项全部保留。
> **界面视觉细节暂停调优**，改为交给用户按清单验收，不在自动化流程里继续动样式。
> 第一阶段 6–7 起步：内置名单到 revision 2（方法名 + 混合同名类全名），
> 「禁止自动刷新」「精简文章页面」已接到按名单解析（真机日志：`目标适配器=com.zhihu.android.mix.adapter.a 来源=候选名单`）。
> 第二阶段第 11 项完成：每个 Hook 首次命中打一条 `[自检][命中] <功能> <类#方法>`；
> 打开 `switch_hooktrace` 时，启动两分钟后另输出"装了但一次没触发"的清单。
> 真机基线：知乎 11.10.0/41012 小米渠道包，`Hook句柄=85 初始化成功=31/31 全部可用`。
>
> 2026-10-17 追加：修掉一次"设置全关但首页空白"。根因是配置通道的读穿透——App 按远端默认值显示全关，
> Hook 却回落到知乎里那份 53 项的旧设置（过滤视频/过滤文章/禁止自动刷新都开着），界面和实际行为打架。
> 现在旧文件不再参与读取，双侧只认远端；首次检测到旧配置时提示一次。同时加了命令行调试口
> （`DebugBridge`，`am broadcast -a com.shatyuka.zhiliao.DEBUG`），调开关不必再点界面。
>
> 2026-10-17 晚：名单 revision 4，新增 `navRes.bottomNav`（导航栏样式，a/b/e 三种混淆名）
> 与 `searchAd.converters`（搜索广告的 Retrofit 转换器，一串同构类要全挂，`TargetResolver.findClassesByQualifiedCandidates`）。
> 设置页开关"点了没反应"的根因也定位了：开关本体和整行各响应一次点击，250ms 内连写两次把值翻回来，
> 现在 `putBoolean` 做了去抖。真机实测：`navRes.bottomNav → com.zhihu.android.bottomnav.b`，31/31 可用。
>
> 第二阶段第 12 项（状态通道）完成：设置页「关于 → 自检」发有序广播问知乎，Hook 侧用 `setResultExtras`
> 回一行 `知乎 11.10.0/41012 初始化成功=31/31`；顺带把 App→知乎的接收器从 NOT_EXPORTED 改成 EXPORTED
> （否则跨应用广播根本收不到，重启/清理其实一直是哑的）。
>
> 第二阶段第 12 项的下半截（自动降级）也完成：`HookStatus` 记「功能 → 设置项」映射，Hook 失败时随回执
> 发 `failedKeys`，设置页把对应开关置灰、副标题改成"当前知乎版本暂不支持"。真机预演过正反两遍
> （`cmd unsupported` 标记 → 去搜索广告变灰；`clear` 复位 → 恢复正常）。单测 52 条。
>
> 2026-10-18 审阅外部反馈的五条问题后的处置：`AutoRefresh` 的 DexKit 兜底移出主线程——名单和解析缓存
> 照旧在 `init()` 里做，扫 dex 放后台线程、命中后补装 Hook，结果写进 `ResolutionCache`；自检的记账 key
> 改在装 Hook 时拼一次，命中路径不再拼字符串；`CommentAd` 的模型验证器从"公开且非抽象"收紧成
> "FeedAdvert 的具体子类"，绑定方法改收实例方法（11.10 的 `V70#J1` 是实例方法，旧代码只收 static，
> 结果是装了但一次都不会进来）；`StatusBar` 删掉方法体为空的 `ThemeChangedEvent` 构造钩子。
> "老配置检测读错目录"那条不成立：旧文件本来就该从知乎私有目录读（见 DEVELOPMENT.md），未改。
> 单测 88 条通过；另外用 11.11.0/41106 官方包反转出的审计 JAR 做了静态确认（模型仍继承 `FeedAdvert`，
> 绑定仍是实例方法 `V70#J1`），真机行为待跑。
>
> 同日晚：修掉云端 Android CI 自迁移以来一直红的原因——CI 没有 11.x 的审计 JAR，`HookTest` 落到仓库里
> 上游遗留的 6–8.x 老 JAR 上，新增的 AutoRefresh 断言和 ExternLink 目标自然对不上。现在 `HookTest`
> 只认 `ZHILIAO_TEST_DIR` / `targetapp/jars`，没准备就整类跳过；`app/test` 那批 2019 年的老 JAR 一并删掉。
> 兼容性解析留给每日巡检（用真实最新包）。

## 第〇阶段：独立设置 App（miuix）

对标 fork，把设置从知乎里搬出来，做成独立的模块 App 界面。
这是唯一一个"搬进数据"之前就要做的阶段，因为它改变了配置的读写通道，
越晚做，迁移面越大。

1. **引入 Kotlin + Compose 构建能力**。业务 Hook 代码保持 Java 不动；新增的界面层用 Kotlin + Compose（这是 miuix 的前提，属于用户界面层的例外，记入 DEVELOPMENT.md）。build.gradle 增加 kotlin-android、org.jetbrains.kotlin.plugin.compose 插件、compose-bom、activity-compose，以及 miuix：
   `top.yukonga.miuix.kmp:miuix-android:0.8.8`（Maven Central，已核实为最新版）。
   注意 R8 full mode + Compose 需要 keep 规则；libxposed service 依赖 `io.github.libxposed:service:102.0.0`。
2. **配置通道切换到远端偏好（本阶段枢纽）**。
   - Hook 侧：`MainHook` 里把 `Helper.prefs` 从 `context.getSharedPreferences("zhiliao_preferences", …)` 换成 `getRemotePreferences("zhiliao_preferences")`（Modern API 自带，fork 同款接法）。
   - App 侧：`XposedServiceHelper` 绑定服务后拿同名远端偏好读写；沿用 fork 的做法：绑定时自动把 scope 收敛到只有 `com.zhihu.android`，缺了就 `requestScope`。
   - **存量配置迁移**：老用户的配置现在存在知乎的数据目录里（知乎进程内的 zhiliao_preferences.xml），只有 Hook 侧读得到。方案：Hook 侧首次运行时把旧文件内容逐键写进远端偏好，写完打标记。若 Hook 侧远端偏好不可写（实现时验证），兜底是保留一版只读的知乎内设置页专门做迁移，下个版本再删。
3. **独立界面（miuix 风格）**。
   - MainActivity：Launcher + `de.robv.android.xposed.category.MODULE_SETTINGS` 双入口（fork 同款 manifest 写法，后者让 LSPosed 管理页直接跳转）。
   - 页面结构对应现有 preferences_zhihu.xml 的分组：免责声明（EULA 流程原样搬过来）、通用、广告、其他、界面净化、导航栏、回答、自定义过滤、WebView、临时文件、调试、关于。
   - **图标方案（用户明确要求）**：沿用上一轮更新的 Lucide 图标（`res/drawable/ic_*.xml`，与原设置页一一对应），放在每个选项文字前方；图标外面加一层**圆角矩形背景，使用 G2 连续曲率圆角**（iOS/MIUI 灵动风格，不是 Compose 默认 RoundedCornerShape 的 G1 圆弧）。G2 形状用三次贝塞尔逼近超椭圆自绘 Shape 实现（约百行 Kotlin，不引第三方）。背景色随深浅色模式取系统色板的次级容器色。
   - 交互原样保留：正则三项的编辑弹窗与 ✓/✗ 状态图标、清理临时文件、版本/作者彩蛋、外链各项、导航栏等开关的重启提醒、switch_navres 删除 bottom_nav 目录的副作用。
   - 新增 App 侧能力：重启知乎按钮（广播方案：App 发自定义 action 广播，权限为 signature 级自定义权限；Hook 在知乎进程注册接收器，收到后走 Helper.doRestart。fork 同款，已核实其实现）。
4. **移除知乎内设置页**。
   - 删除 `ZhihuPreference` hook（Hooks 表 31→30），删除 `R.xml.settings`、`R.xml.preferences_zhihu` 及 settings_res_id 那套 inflate 替换逻辑。
   - 连带处理两处依赖：
     a. `StatusBar.java` 引用了 `Helper.settingsView`（取知乎内设置页背景做取色），设置页删除后该引用永远为 null，需要改成独立取色来源或删除相关分支；
     b. `MainHook` 里"请到设置页面开启功能"的 toast 改为引导打开知了 App。
   - README 功能列表、DEVELOPMENT.md、compat-audit 的 31/31 表述同步改。
5. **单测同步**。HookTest 是按 Hooks 表遍历的，删掉 ZhihuPreference 后自动收敛；补一条配置通道的说明（远端偏好在 JVM 里没有实现，单测里 Helper.prefs 的注入口保持不变即可）。

## 第一阶段：把定位从代码搬进数据（全在本地，风险最小）

6. **建内置候选名单 JSON**（assets），每个 Hook 要找的类/方法/字段列多个候选、按优先顺序排。`AutoRefresh` 里手写的 `new String[]{"returnTopOrRefresh", "a"}` 搬进去当第一条。内置文件永远兜底，云端怎么折腾它都不动。
7. **TargetResolver 加"按名单解析"**：按顺序试候选，每个候选必须通过验证（类型对、非抽象、字段可取），第一个全过的才用，全失败才算失败——替换现在"谁先到谁赢"的 first-match 习惯。各 Hook 的解析逐步接到这条链上。
8. **渠道判定**：`packageInfo.applicationInfo.splitSourceDirs != null` → Play 包，否则国内包。只能分两类；国内各渠道（小米、华为……）之间的混淆差异靠第 7 步的多候选吸收，不做渠道分桶。
9. **单测读同一份 asset**：HookTest 用 targetapp/jars 的流程照旧，但候选名单来源与运行时同源（同一份 JSON）。

> 2026-10-17 完成：HookTest 启动时直接读 `app/src/main/assets/compatibility/candidates-v1.json` 并 `CandidateRegistry.install`，
> 日志会打 `候选名单已载入: … revision=4`；各 Hook 在审计 JAR 上真的走"来源=候选名单"这条路了（之前测试里 registry 一直是空的）。
> 另加一条 `candidateAssetIsSharedWithRuntime` 兜底断言，防止哪天载入失败却静悄悄退化成只测兜底路径。

## 第二阶段：让失败可见、让结果可复用

10. **解析结果缓存**：init 成功后写 SharedPreferences，key = 知乎 versionCode + 配置版本 + APK 指纹（length() XOR lastModified()）。任一变了缓存自然作废，无需清理逻辑。
11. **运行期自证**：每个 Hook 第一次真正命中时打一条日志（拦到了什么、目标在哪个类）。治"签名全对、语义已变"的静默失败。先不做 UI，日志可查即达标。
12. **自动降级**：init 失败的 Hook，独立设置 App 里对应开关自动置灰并标注"当前知乎版本暂不支持"。解析结果通过远端偏好的状态键暴露给 App 侧。

## 第三阶段：DexKit 兜底

> 2026-10-17 进度：13/14 的**管线**已通并真机验证——原生库按 `apk!/lib/…` 加载成功、扫的是知乎 APK
> （`dex=18 包内类=118757`）、`className`/`superClass`/`usingStrings` 三种匹配器都能出结果、
> 结果数上限与"扫完即 close"都生效、诊断命令 `cmd dex` 可随时探针。
> 还没完成的是**规则调准**：文章适配器不是 `RecyclerView.Adapter` 的直接子类（130 个直接子类里没有它），
> 按"有 getItemCount + Fragment 字段"的结构查询当前 0 命中，所以这条兜底现在等于安全空转，
> 落空时会退回原来的结构化链。下一步要么把字段类型放宽（子类/接口），要么换锚点（比如持有 Fragment 的类反向找）。

13. **引入 DexKit**（`org.luckypray:dexkit`，native 构建体系现成）。规则从严：限定 searchPackages、参数签名、运行期字符串（usingStrings），minCandidates/maxCandidates 卡死，超界拒装不猜。挂在第 7 步策略链最末端，名单找不到的才轮到它扫。
    > 2026-10-17 结论：**这条兜底明确弃用，DexKit 退为诊断工具**。真机实测：按"声明了 getItemCount"能捞到 154 个类、按"声明了 Fragment 字段"能捞到 440 个，两个条件合起来 0 个——因为文章适配器的这两个特性都是继承来的，dex 层面看不到。这类"身份藏在父类"的目标，结构扫描天然无解，留着只会给人"有兜底"的错觉。现在 `DexKitFallback` 只保留按字符串锚点的 `findClasses`、按精确签名查方法的 `findMethodsBySignature`（只授给 AutoRefresh 定位缓存写入，绝不拿来猜混淆名）与 `probe()`，后者通过 `am broadcast --es cmd dex` 手动调。
14. **扫描窗口收摊**：解析完在 finally 里 close DexKitBridge，不让它挂在知乎整个生命周期；扫描结果进第 10 步的缓存。

## 第四阶段：小而硬的补墙

15. **自定义正则加上限**：长度封顶（512 字符），预编译失败直接禁用该条过滤。灾难性回溯能把知乎进程整个拖死，这是当下就存在的真实风险。
    > 已完成：`Helper.compileRegex` 里加 `MAX_REGEX_LENGTH = 512`，超长记一条日志并返回 null（该条过滤停用）；
    > `RegexGuardTest` 覆盖超长/边界/语法错三种情况。注意这只挡长度，回溯本身仍靠用户写的正则质量。
16. **不变量清单**：DEVELOPMENT.md 里给每条 Hook 记一行"怎么算对了"（如：广告对象是 FeedAdvert 实例、刷新来源名 Pull/TabClick），init 里已有类型校验的标注，缺的补上。类名每周都在变，不变量才是活得久的资产。

## 第五阶段：云端（前面全稳了再动）

17. **远端配置通道**：仓库一份 compatibility.json + manifest（SHA-256、revision 只增、多 CDN 链接）。App 拉取 → 验 SHA-256 → 验签（Ed25519 公钥硬编码在 APK，私钥只在维护者与 CI 手里）→ 字段白名单 → revision 比本地新 → 落地远端偏好。云端数据只能"选符号"，永远不能执行代码。feature 开关随配置下发（某版本某功能远程关闭）。

    > 2026-10-17 进度：**本地部分完成**。`CompatibilityEnvelope` 定义信封格式
    > `{"payload":<原样JSON字符串>,"sha256":<hex>,"signature":<base64>}`，校验顺序 =
    > 大小封顶（信封 512KB / payload 256KB）→ SHA-256 摘要 → ECDSA 签名 → schemaVersion / revision 合法 → 交给
    > `CandidateRegistry.parse` 复用同一套字段白名单。签名算法改用 **ECDSA P-256** 而不是 Ed25519——
    > 后者要 API 33+，本模块 minSdk 26。Hook 侧 `Helper.applyCloudCompatibility` 已能消费：
    > 只有 `cloud_revision` 比内置新才 `install`，失败一律退回内置名单。
    > 还没做的：App 侧的拉取与验签落库、公钥硬编码、多 CDN 与 CI 签名脚本。

18. **每日巡检 CI**：GitHub Actions cron 下载知乎官方 APK（校验包名 + 证书 SHA-256 + 版本号）→ prepare-target.ps1 出审计 JAR → gradlew testDebugUnitTest（需加 -PskipNativeBuild 属性）→ 失败自动开 Issue。

    > 2026-10-17 完成：`.github/workflows/compat-patrol.yml`（windows-latest，每天 UTC 20:00 / 北京 04:00，
    > 可手动触发）。配套 `tools/verify-zhihu-apk.ps1` 校验包名/版本/证书指纹，本机已用小米渠道包验证
    > 正例通过、错证书与低版本均正确拒绝；`-PskipNativeBuild` 属性已加进 `app/build.gradle`，
    > 实测能跳过整条 CMake/NDK 链只跑 JVM 单测（8 个测试类全绿）。
    > 失败时的 issue 去重逻辑写在 workflow 里：已有同名未关闭 issue 就不再开新的。
    > 待办：仓库变量 `ZHIHU_APK_URL` / `ZHIHU_CERT_SHA256` 需要维护者在 GitHub 上配置，
    > 没配时任务明确失败而不是静默跳过。
19. **（可选）诊断导出**：设置 App 里"导出诊断"——自检日志、知乎版本/渠道、各 Hook 解析结果打包成文本，用户手动贴出。只导出不自动上传；是将来众包层的地基，现阶段用于排障。
    > 2026-10-17 完成（无网络版）：设置页"自检"下面加了"导出诊断"，内容 = 模块版本、设备、知乎版本、缓存指纹、自检结果、失败项、全部开关真假、自定义文本框的**长度**（不记内容）。写进 App 自己的外部目录（`Android/data/com.shatyuka.zhiliao/files/diagnostics/`）并复制到剪贴板。模块至今**没有 INTERNET 权限**，所以"只导出不上传"不是承诺，是权限层面做不到。拼装逻辑抽成 `DiagnosticReport`，两条单测盯着"别把用户自定义文本原样写进报告"。状态广播顺带补了 `zhihu` 与 `fingerprint` 两个字段，报告才拿得到知乎版本与缓存指纹。

- 2026-10-17 候选名单升到第 6 版：新接 commentAd.model / commentAd.viewHolder / externLink.h5Event / externLink.openUrlMethod / externLink.h5Params（字段名也是候选列表，类型必须对得上）/ externLink.intentUtils / answerAd.appView。真机缓存里能看到各自认出来的结果，例如 externLink.h5Params = i、commentAd.model = com.zhihu.android.adbase.model.CommentListAd——小米包里 api.model 那条不存在，名单会自动往下走。
## 风险与边界（贯穿全程）

- Hook 永不向知乎调用链抛异常；单 Hook 失败只停掉自己（现有约定不变）。
- 云端规则再"对"，同 versionCode 不同渠道包的混淆差异永远存在——真机日志是唯一仲裁。
- 完全自动适配不存在：语义不变签名变、上游删功能、全新广告形态，三种情况必须人工判断。自动化的目标是把人工介入压缩到这三种。





