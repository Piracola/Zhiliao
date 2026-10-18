# 知乎 11.10.0 (41012) 分享链路追踪审计

- 审查对象：分享/传播链路里的用户追踪点（对应知了「反追踪」功能）
- 审查方式：droidasc 全局字符串/方法引用检索 + 关键类反编译通读（`targetapp/futureve-app-market(zhihuwap64)-release-11.10.0(41012)-arm64-v8a-bangcle.apk`，167486 个类）
- 证据 JAR：`targetapp/jars/11.10.0 41012.jar`（market 渠道）、`targetapp/jars/xiaomi 11.10.0 41012.jar`
- 审查日期：2026-10-16

## 一、结论

分享链路上共找到六类追踪行为，前三类是纯客户端行为（可从客户端掐断），第四类依赖服务端（只能去掉入口），后两类是埋点基础设施。知了的应对（`hooks/AntiTracking.java`）：埋点上报归 `switch_antitracking` 管，链接参数归 `switch_antitracking_link`，二维码归 `switch_antitracking_qr`，后两项默认跟随总开关打开。

| # | 追踪行为 | 客户端证据 | 应对 |
|---|---|---|---|
| 1 | 分享链接拼 utm 参数 | `sharecore.v.o.b/c/d` 往 URL 追加 `utm_medium=social`、`utm_content=select_word/longpic` 等参数 | 拦下后改为剥离全部 `utm_*` 与 `share_code` |
| 2 | 分享面板点击上报 | 每个按钮 onClick 调 `x.a.c(按钮名, getZaData())` → `Za.za3Log` | `getZaData()` 返回空 ZABean，按钮名取不到 token/contentType |
| 3 | 分享回执上报 | `v.o.g/h` 从回跳链接取 `share_code`/`utm_psn` 回传 `/community-share/unify/share/report`（POST） | `v.o.h`、`u.d.a/b`、`SharePortalHandlerImpl.handleReport` 全部置空 |
| 4 | 图片分享二维码 | `u.c.d` 请求 `https://api.zhihu.com/image_share/qrcode`，扫码即可关联分享者与被分享者 | 服务端会把内容缩成 `s.zhihu.com` 短链，改参数无效（见 §八）；改为拦图片控件、本地编码二维码 |
| 5 | 打开他人分享链接时伪造页面浏览 | `RouterPortalLegacyInterceptor.recordVirtualSharePage`：URL 带 `utm_oi`（分享者用户 ID）→ `Za.pageShow` 伪造 PV（事件 5862，`fakeurl://fictitious_share`） | `Za.pageShow` 全部置空 |
| 6 | 全站埋点基础设施 | `Za` 是埋点门面，所有事件经 `LogRecorderImpl.record` 入库、`ZaNewUploadManager` 批量上传 | `record` 与 `ZaLogHandlerImpl.inQueue`（第二道闸）置空，埋点不落库 |

## 二、关键细节

1. **utm 参数的拼装不是常量拼接**：`utm_source=share` 这类完整字符串在包里搜不到，因为知乎用 `Uri.Builder.appendQueryParameter` 逐段拼。定位靠 `findrefs method appendQueryParameter` 过滤 sharecore 包，再反编译调用者。
2. **参数名是 XOR 混淆字符串**（`H.d("G…")`），解出后确认：`v.o.b` 加 `utm_content=select_word`（长按选字分享，`CardShareLayout` 调用）、`v.o.c` 加 `utm_medium=social&utm_content=…`（图片装饰分享 `ImageDecorShare*Fragment` 调用）、`v.o.d` 加 `utm_medium=social&utm_content=longpic`（长图分享 `LongImageShareFragment`/`WebRenderShareFragment`/`WebOnlyShareFragment` 调用）。
3. **记录「分享页面-更多」里点了哪个 App 的，不止一条路**：`app.share.s0.h`（分享面板数据）把 `l/q/k/m` 字段和底栏项交给 ShareCore；系统分享执行前后各有一次上报（点击时 `x.a.c`，成功回执走 `WXEntryActivity` → `v.o.h` → `/community-share/unify/share/report`）。分享时间、目标 App、内容 token 三样都在上报体里。
4. **自建分享页本身没有独立的"点了哪个 App"上报面**：用户能看到的那一页是 `sharecore` 的 ShareFragment/ShareSheet，其点击上报就是上表的 #2。所谓"自建页面记录分享时间"实际由 #2（点击时）+ #3（目标 App 回跳时）两段拼出来。
5. **反调试上报面**：知乎自带的 `shit_kk_report`（`RouterPortalLegacyInterceptor.beforeProcess`）是云端开关控制的额外上报，总闸（#6）兜住。
6. **第二道闸的必要性**：`PanelZaRecorderImpl` 等旁路最终都汇入 `za3LogInternal`，但入库漏斗只有 `LogRecorderImpl.record` → `ZaLogHandlerImpl.inQueue` → `ZaEnqueueTask` 两层。两层都拦，未来版本即使上层改结构也有兜底。
7. **定位策略**：全部按类名 + 结构签名定位（如「静态、入参全 String、返回 String」找拼参方法，「入参 String/HashMap、返回 Observable」找上报方法），不依赖混淆方法名，跨渠道包可用。
8. **二维码是服务端生成的，不是本地渲染**：`CardShareLayout.e()` 的落点是 `setImageURI(u.d.f(...))`——把 `https://api.zhihu.com/image_share/qrcode?content=…&size=…&fill_color=…&back_color=…` 当网络图加载（Fresco）；连前景色/背景色都作为参数发给服务端，本地画图没有理由传颜色。成就卡（MilestoneFragment）同路。包里虽带 zxing，但 `QRCodeWriter`/`Encoder` 在知乎自有代码零引用（是其他 SDK 的传递依赖）。

## 三、拦不干净的（如实记录）

- **二维码内容**：图片分享的二维码图由服务端 `image_share/qrcode` 生成（见 §二.8）。反追踪开启时客户端拦下 `u.d.f` 的返回 URL，把 content 剥参后用 zxing 本地重绘同款配色的二维码（data URI 回填），二维码请求不再出门；生成失败则退回远程 URL（content 已剥参）。成就卡同路。
- **服务端烘焙的水印与盲水印**：同 `docs/watermark-audit-11.10.0.md` 的结论，不重复。
- **用户主动访问短链**：别人发给你的 `zhihu.com/s/xxx` 或 `zhihu://` 带参链接，落地页本身的 PV 属于正常页面访问，服务端可见；反追踪拦的是"客户端替你额外说的"那部分（伪造 PV、点击上报、回执）。
- **账号体系本身**：登录态下的行为归因不依赖以上任何一条，那是另一个层面的问题。

## 四、验证

- 单测：`ZHILIAO_TEST_DIR=targetapp/jars` 下 28 个 Hook 兼容性测试全部通过，AntiTracking 在两份审计 JAR 上均定位成功（无 skip）。
- 真机（待验证）：开 `反追踪` → 重启知乎 → 分享一篇文章到微信/复制链接，检查剪贴板里的链接不再带 `utm_*`；LSPosed 日志应出现 `[Zhiliao][自检] 安装 反追踪 句柄=+N`。

## 五、11.11.0 小米渠道包上的类名漂移（2026-10-18 真机核对）

真机（11.11.0/41106，从小米渠道包拉下来的 base.apk，183,637,096 B）上自检输出 `反追踪 埋点=true 拼参=0 上报=0 二维码=无`：
上表里按审计 JAR 写死的类名在这个包里全都不存在。核对方式是直接在被装包的 dex 里找类描述符：

| 审计时的类 | 11.11.0 小米包的实际情况 |
| --- | --- |
| `com.zhihu.android.library.sharecore.u.d`（二维码 URL 拼装） | 不存在；结构等价类是 `com.zhihu.android.library.sharecore.i.d`，方法名 `a`（`String f(String content, int size, int fill, int back)` 的等价物） |
| `com.zhihu.android.library.sharecore.v.o`（utm 拼参） | 不存在，且 utm 键名在这个包里是加密字符串（`H.d(...)`），运行期才还原，**没法再用 "utm_medium" 字符串当锚点** |
| `com.zhihu.android.library.sharecore.AbsSharable`、`sharecore.card.CardShareLayout` | 存在（名字保留） |

所以 `AntiTracking` 在名字定位失败后走过 DexKit 兜底（`DexKitFallback`）：11.11.0 真机能定位到二维码构建器 `com.zhihu.android.library.sharecore.i.d#a`，
但实测那个方法并不参与卡片二维码的生成（见 5.2），这条兜底已随旧方案一起删掉；`DexKitFallback` 现在只保留 `findMethodsBySignature`，
给数据层（缓存写入）用。utm 拼参那一支仍是死的（键名加密），复制链接的清洗改由文本级拦截兜住（见 §七）。

### 5.1 不能把二维码换成 data URI（2026-10-18 真机踩坑）

拿到目标之后试过"本地 zxing 出图 + `data:image/png;base64,…` 回填"的老方案，真机上直接崩：

```
FATAL EXCEPTION: main
  at com.zhihu.android.app.util.cy.c(ImageUrlUtils.java:716)
  at com.zhihu.android.app.util.cy.b(ImageUrlUtils.java:585)
  at com.zhihu.android.picasa.impl.ZHDraweeStrategyImpl.convertUri(ZHDraweeStrategyImpl.kt:60)
  at com.zhihu.android.base.widget.ZHDraweeView.setImageURI(ZHDraweeView.java:817)
  at com.zhihu.android.library.sharecore.card.CardShareLayout.a(CardShareLayout.kt:241)
  at com.zhihu.android.library.sharecore.card.CardShareFragment.a(CardShareFragment.kt:217)
```

`setImageURI` 之前会过一遍 `ImageUrlUtils` 做 CDN 规范化，`data:` 这种 scheme 直接抛异常：卡片渲染中断、二维码留白框，
**连"切换分享样式"也会崩**（每次换模板都要重新 setImageURI）。

### 5.2 改 URL 里 content 参数的做法也弃用了（2026-10-18 真机复测）

第二版改成"拦 `sharecore.i.d#a` 的返回值，只把 content 洗一遍"。真机复测：二维码扫出来还是旧链接，
日志里也从来没出现过 `二维码…` 那一行——说明**那个方法压根没被调用**，卡片上的二维码不是从这个拼装器出来的。

结论：按"某个类/方法"去改二维码这条路，只要猜错一次就全盘落空，不值得再押。现在换成图片网络层：
二维码图片无论由谁拼 URL，发请求前都要经过 OkHttp 的 `HttpUrl`，在那里把 `image_share/qrcode` 的 content 换成干净链接
（`installImageUrlCleaners`），图片仍旧由服务端生成，知乎的渲染管线一点不碰，也不需要认任何混淆名。

## 六、长图分享的二维码不归客户端管（2026-10-18）

「长按 → 分享 → 生成长图」走的是 `com.zhihu.android.library.sharecore.activity.WebRenderShareActivity`：
它在 WebView 里加载 `https://www.zhihu.com/appview/picture-share/answer/<answerId>`（URL 由 `AbsSharable.getShareLongImgUrl` 用资源串
`https://www.zhihu.com/appview/picture-share/%s/%d` 拼出），截图后当图片分享。这条链路里右下角二维码是**网页自己画的**
（页面是 amadeus 的 JS 应用，数据在 `<script id="js-initialData">`），客户端既不发 `image_share/qrcode` 请求，也没有 `u.d.f` 之类的调用点。
所以上表第 4 条"客户端拦 QR 构建"只覆盖卡片分享（`CardShareLayout → sharecore.i.d.a`），长图那张二维码要改得落到 WebView 层（拦截页面请求或替换页面），
属于另一条战线；真机这次复现时页面停在「似乎出了点问题…」，尚未取得那张二维码的实际内容。

## 七、文本级净化：不认类名，只认链接长相（2026-10-18）

前六条都建立在"找得到那个类/方法"之上，渠道包一改名就全落空。所以分享链接这条补了一层与混淆无关的做法：
**不关心链接是谁拼出来的，只关心它最后出现在哪里**——系统写剪贴板、系统发 Intent，这两个口子是绕不过去的。

- 落点：`android.content.ClipboardManager#setPrimaryClip`（复制链接）、`android.content.Intent#putExtra(String,String|CharSequence)`
  与 `#putExtras(Bundle)` 里 key 为 `android.intent.extra.TEXT` 的那份文本（分享到微信/QQ/系统分享）。
- 匹配：`https?://(?:[a-z0-9-]+\.)*zhihu\.com/[^\s"'<>()（）【】、，。；！？]+`，逐条命中、逐条替换，其余文字原样保留
  （分享到微信的文本常是「标题 - 知乎 https://… 复制链接」这种夹心格式）。
- 净化：`link.zhihu.com` / `s.zhihu.com` 先解 `target` 还原真身，再剥掉 `utm_*`、`share_code`、`share_from`、`s_s_i`、`s_r`；
  没有命中追踪参数就原样返回，不动用户数据。
- 单测在 `app/src/test/java/com/shatyuka/zhiliao/hooks/ShareLinkTest.java`，11 条，纯字符串逻辑（不碰 `Uri`，JVM 里直接跑）。

真机（11.11.0/41106 小米包）验证：点「复制链接」后 LSPosed 日志出现
`[自检][命中] 反追踪 android.content.ClipboardManager#setPrimaryClip` 与 `[Zhiliao][反追踪] 剪贴板 已剥离追踪参数`，
把剪贴板粘进搜索框读到的内容是 `https://www.zhihu.com/question/2064366175723713389/answer/2086056659252668236`，`share_code` / `utm_psn` 均已消失。

## 八、二维码里的短链是服务端加的

`sharecore.i.d#a(String,int,int,int)` 本地只是拼 URL：

```
https://api.zhihu.com/image_share/qrcode?content=<分享地址>&size=…&fill_color=…&back_color=…
```

拼出来交给 `ZHDraweeView#setImageURI`，图片由服务端渲染。把两台参数不同的同一篇文章各请求一次（带 `share_code`/`utm_psn` 与不带），
下载后用 OpenCV 的 `QRCodeDetector` 解码：

| 请求的 content | 二维码里实际编码的内容 |
| --- | --- |
| `…/answer/2086056659252668236?share_code=11J19ZFeEtqjW&utm_psn=2095186356855124716` | `http://s.zhihu.com/2_1brRD0` |
| `…/answer/2086056659252668236` | `http://s.zhihu.com/2_2L2ZA1` |

结论：`content` 只影响服务端生成哪个短链，**二维码里永远不会是原始链接**。在客户端改 `content`、改请求 URL、
改 `sharecore.i.d#a` 的返回值，都只能换来另一个短链，短链本身去不掉。真要一个扫出来就是干净地址的码，只能本地编码。

现做法（`AntiTracking#installLocalQrRenderer`）：在 `ZHDraweeView#setImageURI(String)` 处拦下带 `image_share` 的地址，
读出 `content` 剥掉追踪参数，用 zxing 按干净链接画一张二维码，`getHierarchy().setImage(...)` 直接塞进控件并 `setResult(null)`
跳过原调用。好处是不发请求、不进 `ImageUrlUtils`（data URI 就是死在那里的）、也不依赖任何混淆名——
`setImageURI(String)` 是 Fresco `SimpleDraweeView` 的公开重载，顺着 `ZHDraweeView` 的继承链就能找到。
