# 知乎 11.10.0 (41012) 兼容性审查报告

- 审查对象：知了模块 31 个 Hook（libxposed API 102）
- 审查方式：6 个并行子代理静态审查（javap 反汇编 + 整包常量池/字符串扫描，167486 个类）＋ 主代理真机实测（小米 2109119BC / Android 14 / LSPosed 2.2.0 / api=102）
- 证据 JAR：`targetapp/jars/11.10.0 41012.jar`
- 审查日期：2026-09-24

## 一、结论汇总

| 状态 | 数量 | Hook |
|---|---|---|
| ✅ 正常 | 15 | LaunchAd、FeedAd(主链)、AnswerListAd、CommentAd(回退)、AnswerAd、ShareAd、FollowButton、HotBanner、MineHybridView、ColorMode、NavRes、ExternLink、ThirdPartyLogin、Cleaner、ZhihuPreference |
| ⚠️ 可疑（有静默风险） | 7 | CustomFilter、Tag、Article、StatusBar、NextAnswer、RedDot、SearchAd |
| ❌ 已失效（静默/报错） | 7 | Horizontal(左右划)、FeedTopHotBanner、HeadZoneBanner、VIPBanner、FullScreen、LiveButton、AutoRefresh（处置见 §六） |

> 注：上表按"是否有功能级失效"划分；RedDot 实为 8/12 路径仍有效，AutoRefresh 为 2 处失效（含 1 处 P0）。

## 二、已失效项（必须修）

### 1. 左右划切换回答（Horizontal）— 功能下线（已删除）
- 真机 LSPosed 日志抓到 `IllegalArgumentException / ClassCastException`（Integer → List）：`ActionSheetLayout` 的回调列表字段由 `z` 改为 `J`，而 `z` 被复用为 `private int z;`，强转抛异常并被适配层吞掉。
- 按字段类型重定位后仍不稳定；真机回答页已是 `ShortContainerHostActivity` + 纵向 `ViewPager2`，额外接管 `RecyclerView.onTouchEvent` 的手势实现也只做到单向可用。
- 新版知乎回答直接呈现在连续信息流中，翻页不再是核心交互，因此**整条功能删除**：`hooks/Horizontal.java`、设置项 `switch_horizontal` / `seekbar_sensitivity`、相关图标与 `Helper.sensitivity` 全部移除，设置页原「左右划」分类改为「回答」（仅保留「移除下一个回答按钮」）。

### 2. 隐藏推荐页置顶热门（FeedTopHotBanner）
- `deserialize` 已上移到父类 `com.zhihu.android.autojackson.BaseObjectStdDeserializer`；适配层 `hookAllMethods` 只扫本类声明方法 ⇒ 装 0 个 hook，完全静默。
- 修复：从父类按签名解析该方法 + 回调内 `thisObject.getClass()` 守卫（否则会波及全站反序列化）。

### 3. 隐藏热榜顶部置顶（HeadZoneBanner）
- `RankFeedList` 新增 `headZones`，宿主 fragment 优先消费 `headZones`，只清 `head_zone` 无效。
- 修复：同时置空 `headZones`；保留 `isInstance` 守卫。

### 4. 隐藏会员卡片（VIPBanner）
- `premium.view.VipEntranceView` 方法改名 `initView` → `init(Context)`，init 抛 NoSuchMethodException → **整条 hook（含 MoreVipData.isLegal）都不安装**。
- 修复：改解析 `init(Context)`；删已消失的 NewMoreFragment/resetStyle 分支；补 MoreVipData 的 null 守卫。

### 5. 禁止进入全屏（FullScreen）
- 旧匿名类 `clearscreen.d$c` 已不存在（匿名类序号在 11.10.0 大面积作废）。
- 修复：改钩具名 `enterClearScreen()`（`mix.mixshort.clearscreen.ClearScreenToolBarView` 与 `answer.module.mixshort.clearscreen.AnswerClearScreenToolBarView`），只拦 enter 不碰 exit。

### 6. 移除直播按钮（LiveButton）
- `FeedsTabsFragment.mEntranceManger` 字段已消失，且无同形态替代（该 fragment 所有自定义字段类型均无"首参 FrameLayout"构造器）。
- 修复：找不到稳定替代目标则显式报"不可用"，避免用户误以为功能仍在。

### 7. 禁止自动刷新（AutoRefresh）— 已按真机目标重写
- 静态审计（market 渠道包 JAR）给出的目标在真机上不存在：FeedAutoRefreshManager、com.zhihu.android.feed.delegate.m 运行期 ClassNotFoundException。
- 真机（小米渠道包）实际结构：BaseTabChildFragment.a(boolean)（= 返回顶部或刷新）、FeedFragment.a|b(boolean, delegate)（真实刷新入口）、feed.util.j（= 定时刷新管理器）。
- 旧实现的副作用：returnTopAndRefresh 解析成 BaseTabChildFragment.b(boolean)（另一个方法，被 no-op）；refreshSucceed 复位 hook 解析失败 ⇒ 第 2 次起 postRefreshSucceed 被永久吞掉。
- 新实现（真机日志已确认）：在 (boolean, refreshType) 入口按刷新来源名拦截 —— TabClick（再点「首页」tab）与 AutoRefreshBackground/DetailPage/OtherPage/SearchPage；Pull、RefreshButton 等主动刷新不受影响。实测：点「首页」只回顶部不再刷新，下拉刷新照常。

## 三、可疑项（静默风险，建议加固）

| Hook | 风险 | 建议 |
|---|---|---|
| CustomFilter | 新增 Author/RichText/TextImage/CoverContent 等嵌套元素；多处 NPE/越界被吞 | filter() 判 null type；补 extra/sourceLine/elements 守卫；作者行补 Author 分支 |
| Tag | `cardView.getChildAt()` 直接强转，结构一变即 CCE | instanceof 守卫；Card.extra 判空 |
| Article | `t == "article"` 引用比较恒假 | 改 `"article".equals()` |
| StatusBar | `n0$a` 字段改名 j/k，按名取绑错对象 → 主题回调每次抛 IAE | `Helper.findFieldByType` 精确定位 |
| NextAnswer | 定位到无关字段 `L`（真按钮字段是 `y`）；回调误用 `param.args[0]` | 按字段类型定位并置 GONE；修回调 |
| RedDot | 4 处目标消失（onUnReadCountLoaded 等），8/12 仍有效 | 移除死目标；`P1` 补 setAccessible |
| SearchAd | `SearchTopTabsItemList` 类消失；`SearchRecommendQuery.content` 改为 `commercialData` | 清空 `commercialData`；死分支降级不 throw |

## 四、真机实测（主代理）

| 项目 | 方法 | 结果 |
|---|---|---|
| 模块加载 | `/proc/<pid>/fd` 持有模块 APK、`libzhiliao.so` 已映射 | ✅ 注入成功 |
| 设置页注入 | `zhihu://app/settings` 打开知乎设置 | ✅ 「知了 / 当前版本 26.02.03」正常显示 |
| 启动页广告 | 冷启动连续 10 次采样找"跳过/广告"标记 | ✅ 直接进 MainActivity，无广告痕迹 |
| 推荐流 | 滚动 6 屏 dump 检查"广告/推广/视频/播放"标记 | ✅ 均无命中 |
| 直播入口 | 首页 dump 检查"直播" | ✅ 无（switch_livebutton 生效） |
| 底部导航 id | 真机插桩记录 `getTab` 实参 | **实测 id = home / find / panel / message / profile**；旧 market/video/friend 已不存在 |
| 回答页翻页机制 | `javap -c PagerView.buildVP2()` | 纵向 ViewPager2（`setOrientation(1)`）；VP1/VP2 双实现由 AB 常量决定 |

## 五、可复用的经验（供后续版本适配）

1. **字段按名取是最大风险源**：11.10.0 出现"旧字段名被复用为其它类型"（ActionSheetLayout.z、n0$a 的 b→j/k）。一律用 `Helper.findFieldByType` 或类型/泛型校验。
2. **匿名类序号（`$c`、`$1`）在 11.10.0 大面积作废**：优先钩具名 public 方法。
3. **方法上移到父类**会让 `hookAllMethods`（只扫本类）静默装 0 个 hook —— 关键 hook 应显式解析父类方法或断言非空。
4. **运行期回调异常被静默吞掉**：只能靠 LSPosed 日志发现，关键 hook 建议显式打点。
5. **知乎字符串混淆**（"G…"/H.d）＝ 原地 XOR 固定 16 字节循环密钥 `09 e3 b5 7a df 50 cb 49 86 6e f0 28 92 85 a3 b7`，可用于核实常量与属性名。
6. **云端下发的 id 无法静态证实**（如底部导航 item id）：静态"全包扫描无命中"不能证明失效，必须真机验证。
7. **同一 versionCode 的不同渠道包混淆名完全不同**（market 包 vs 小米包）：审计 JAR 只代表那一份构建。Hook 目标要按结构/签名解析，运行期常量（如刷新来源名 Pull / TabClick）比类名、方法名稳定得多，改动后一律用真机日志验证。

## 六、修复进展（截至 2026-09-24）

- **静态修复**：6 个并行子代理按文件互斥分区完成；`gradlew testDebugUnitTest assembleRelease` 通过（单测 0 失败）。
- **功能下线**：LiveButton（直播入口已不存在）、Horizontal（左右划，见 §二.1）整条删除，并同步清理设置项、图标资源与 `Helper.sensitivity`。
- **设置页重排**：删除已消失的会员/视频/关注导航项与「左右划」分类；底部导航精简适配真机 `home / find / panel / message / profile` 五个 id；保留「禁用活动主题」「隐藏导航栏突起」。
- **真机回归**（小米 2109119BC / Android 14 / LSPosed 2.2.0）：模块注入正常、知了设置页可打开、冷启动无开屏广告、推荐流无广告与视频标记、底部导航隐藏生效、LSPosed 模块日志无新增异常。
- **遗留风险**：`targetapp` 审计 JAR 与真机安装的 base.apk 不是同一构建（183,316,018 vs 171,081,818 字节），混淆名不同 —— 一切以真机实测为准。
- **AutoRefresh 二次修复（2026-09-24）**：从真机拉取 base.apk → dex2jar 得到 targetapp/jars/xiaomi 11.10.0 41012.jar，用它 + 真机 hook 追踪（打印调用栈）定位真实刷新链路：
  BottomNavView.c → MainActivity.onTabReselected → MainDelegation.c → CommonReturnTopDelegate 的 onTabReselected 监听 → FeedFragment.a(boolean, TabClick)，再按刷新来源名拦截。
  真机日志：`[Zhiliao][禁止自动刷新] 已拦截 TabClick`；单测对两份 JAR 均通过。
