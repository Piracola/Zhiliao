# 知乎 11.10.0 (41012) 暗水印去除能力审计报告

- 审计对象：知了模块 26.02.03（202602030，当时版本；现役 26.09.24）的设置项 `switch_watermark`「去除背景盲水印」
- 模块侧证据：`app/src/main/java/com/shatyuka/zhiliao/hooks/WebView.java`、`app/src/main/res/xml/preferences_zhihu.xml`
- App 侧证据：`targetapp/jars/11.10.0 41012.jar`（178,320 个类，其中 `com/zhihu/**` 128,482 个，逐个常量池扫描，含 `watermark`（忽略大小写）的类 134 个）＋ `targetapp/*.apk` 的 `assets/**`（706 项，含 `assets/tornado/*.json` 路由配置）
- 审计方式：静态审计（模块源码 + 整包常量池扫描 + assets 扫描）。真机实测**未执行**，验证方案见 §六
- 审计日期：2026-09-24

## 一、结论

`switch_watermark` 的全部实现是「向知乎 mercury WebView 注入一段 CSS，把水印元素的 `background-image` 置空」。
它**不足以去除知乎客户端内的所有暗水印**：端内至少有 6 类水印机制，模块仅对其中 1 类（WebView 内用 CSS background 贴的水印）有条件生效，且该类本身还有多项失效前提。

| # | 水印机制 | 端内证据（11.10.0） | 模块覆盖 |
|---|---|---|---|
| A | 混合页 HTML/CSS 平铺背景水印 | 无 APK 内证据（页面 CSS 由服务端下发，见 `WebView.java:13` 注释） | ⚠️ 有条件部分覆盖 |
| B | 视频原生水印视图 | `media/plugin/VodWatermarkPlugin`、`VodWatermarkPlugin$WatermarkParams`、`video/player2/t/a` → `https://api.zhihu.com/video/play/watermark`、`morph/extension/R$layout:layout_vod_watermark`、`R$id:iv_nickname_watermark / iv_zhihu_watermark / watermark_root` | ❌ |
| C | 混合容器 `watermarkImgPlugin`（card / fullscreen watermark block） | `assets/tornado/{zvideo_detail_99,ogv_99,edu_aicourse_detail_103,edu_aicourse_introduction_103,follow,recommend_page,short_container_42_4}.json`；数据类 `tornado/data/TDataWaterMark`；实现类 `plugin/basic/u/d`（native 插件，见 §四.3） | ❌ |
| D | 直播 / 短剧水印 | `morph/extension/R$layout:player_scaffold_live_watermark`、`R$styleable:ZUIWaterMarkView`、`videox/api/model/DramaWatermark`、`videox/a_rebuild/room/widget/StreamPreviewView` | ❌ |
| E | 服务端烘焙进图片像素的水印（"换源"问题，非"去水印"问题） | `api/model/Image` 同时有 `url / src / originalSrc` 与 `watermarkSrc(watermark_src) / privateWatermarkSrc(private_watermark_src) / watermark`；`zrichCore getImageWatermarkEnable / getImageUrlHasWaterMark`；`PinContent$WatermarkType`；`MomentPin$Content.isWatermark/watermarkUrl`；`NetworkMediaInfo.watermarkUrl`；`CardOriginalPinMiddle`；`topic/holder/MetaCommentFeedItemHolder` | ❌（CSS 物理上不可能生效） |
| F | 真·盲水印（不可见、像素/频域隐写） | 客户端不存在任何解码或去除代码 | ❌（客户端无解） |

## 二、模块当前实现（代码事实）

| 位置 | 内容 |
|---|---|
| `WebView.java:15-37` | 注入字符串 `script_hide_water_mark`：`.App,html,body{background-image:none!important}` + `[class*=watermark],[class*=Watermark],[id*=watermark],[id*=Watermark]{background-image:none!important;background:none!important}`；`<style id="zhiliaoHideWaterMark">` 追加到 `document.body`（无 body 时 head/documentElement），`DOMContentLoaded` 兜底 + `MutationObserver({childList,subtree,attributes,attributeFilter:['class','style']})` 反复应用 |
| `WebView.java:111-138` | 仅在 `WebViewClientWrapper.onPageStarted / onPageCommitVisible / onPageFinished` 回调中 `evaluateJavascript` 注入 |
| `WebView.java:141-152` | `injectScript` 只处理 `instanceof android.webkit.WebView` 的实参；注入异常 `catch (Throwable ignored)` 静默吞掉，失败无日志、无提示 |
| `WebView.java:98-100` | 开关打开时强制 `AnswerAppView.canPreRender()` 返回 false（否则预渲染缓存页会丢弃注入） |
| `preferences_zhihu.xml:279-284` | `switch_watermark` 默认 `false`，依赖 `switch_mainswitch`；标题「去除背景盲水印」，summary「禁止开盒」 |
| 全模块引用面 | `switch_watermark` 仅被 `WebView.java` 与设置页图标使用（无任何原生数据层改动） |

即：这是**移除 DOM 元素背景图**的能力，不是「去水印」；设置项中的「盲水印」命名与实际能力不符。

## 三、A 类（唯一覆盖项）的生效前提与失效条件

1. **选择器面窄**：只匹配 `watermark` / `Watermark` 两种拼写。知乎自身命名多用驼峰 `waterMark`（见 `assets/Video_Detail_UIConfig.json`、`assets/Ogv_UIConfig.json` 的 `"view_name": "waterMarkView"`，以及 `tornado/q0/b`、`tornado/x0/c0`），**不命中**；`wm`、`water-mark`、`Mask`、`bg-tile` 等同样不命中。
2. **只能命中根元素或含关键词的元素**：平铺背景若挂在内层容器（类名不含关键词）而非 `.App/html/body`，则存活。
3. **`!important` + 特异性对抗**：站点若用更高特异性（如 `.App .xxx`）或内联 `style="…!important"`，注入规则会输。
4. **非背景手段无效**：水印若是 `<img>`、`<canvas>`、伪元素 `::after` 内容，`background:none` 完全无效。
5. **仅主 frame**：`evaluateJavascript` 只作用于主 frame，跨域 iframe 内的水印不受影响；`<style>` 位于 light DOM，**不穿透 shadow root**。
6. **生命周期与容器**：只在 `WebViewClientWrapper` 三个回调触发；其它容器创建的 WebView（第三方页面、帮助中心、编辑器 hybrid 等）不在覆盖范围。
7. **静默失效**：注入失败被 `catch` 吞掉；页面结构变化导致的失效只能靠真机观察。

## 四、目标端存在的其它水印机制（模块无法覆盖的原因）

### 1. 视频 VOD 水印（B）— 原生 ImageView 覆盖层

`media/plugin/VodWatermarkPlugin` 常量池证据：字段 `nicknameWatermarkView` / `zhihuWatermarkView`（类型均为 `com/zhihu/android/base/widget/ZHDraweeView`）、`rootView`、`watermarkInfo(Lcom/zhihu/android/api/model/plugin/WatermarkInfo;)`、`watermarkParams`；方法 `getWatermarkInfo`、`updateWatermarkUrl(String,String,Z)`、`updateWatermarkLocation(Rect)`、`layoutWatermarkUI`；水印图来自 `video/player2/t/a` 的 `https://api.zhihu.com/video/play/watermark`。
→ 由原生 Fresco 视图绘制，与 WebView DOM 无关。

### 2. 混合容器 `watermarkImgPlugin`（C）— 原生插件视图

- 路由配置：`assets/tornado/zvideo_detail_99.json` 等文件里 `"block_id":"card_watermark_block" / "fullscreen_watermark_block"` → `"plugins":["watermarkImgPlugin"]`，插件数据 `"data":{"type":"TDataWaterMark"}`。
- 数据类 `tornado/data/TDataWaterMark`：字段 `logoImage(getLogoImage/setLogoImage)`、`nickImage(getNickImage)`，对应的 JSON 键为 `logo_image` / `nick_image`。
- 实现类 `plugin/basic/u/d`：经 `com/zhihu/android/plugin/basic/m` 实现 tornado 插件接口（`api/interfaces/tornado/u`、`tornado/o`、`api/model/tornado/TPluginConfigConversion`、`getPluginImplType`），用 `LayoutInflater` + `tplugin_center` 布局资源 inflate，`ZHDraweeView/SimpleDraweeView` 走 Drawee pipeline 加载 `getNickImage()`。
- 常量池扫描：`watermarkImgPlugin` 字面量在 `com/zhihu/**` 中**零命中** → 该插件按服务端配置经插件中心注册派发，不存在可直接 hook 的字符串 id。
→ 同样是原生视图，CSS 注入无效。

### 3. 服务端烘焙的图片水印（E）— 无法用 CSS 去除

`com/zhihu/android/api/model/Image` 字段集合：`url`、`src`、`originalSrc(original_src)`、`hash`、`token`、`format`、`width`、`height`、`watermark`、`watermarkSrc(watermark_src)`、`privateWatermarkSrc(private_watermark_src)`。
同族证据：`picture/upload/model/UploadedImage`（`watermarkSrc`、`privateWatermarkSrc`、`watermarkHash`）、`km_editor/ability/AnswerExtraAbility$l`、`next_editor/plugins/ImagePlugin`、`MomentPin$Content`、`NetworkMediaInfo`、`zrichCore/e/c`（`getImageUrlHasWaterMark`）、`zrichCore/d/b` 与 `zrich/ZRichCoreImpl`（`getImageWatermarkEnable`）。
→ 水印已烧进图片字节流，属于「服务端下发的是哪张图」的问题：CSS/DOM 层无论怎么做都无效，只能改取 `originalSrc/src`（且是否被 CDN 二次加水印由服务端决定）。

### 4. 直播 / 短剧（D）

`morph/extension/R$layout:player_scaffold_live_watermark`、`R$styleable:ZUIWaterMarkView`（`zuiWater_mark_align/degree/dx/dy/paddingX…`）、`videox/api/model/DramaWatermark`（`Integer,Integer,Boolean,String,Float,Float` 构造）、`videox/a_rebuild/room/widget/StreamPreviewView`。
→ 第三方直播 SDK（baijiayun / hpplay / meishe / bytertc，见 jar 中 `com/baijiayun`、`com/hpplay`、`com/meishe`、`com/ss/bytertc` 的 `*Watermark*` 类）与端内短剧播放器各自绘制的原生水印。

### 5. 真·盲水印（F）

不可见水印按定义存在于像素/频域，客户端既无解码代码也无「图层」可删。模块名「盲水印」缺少支撑：从可实现性看，客户端唯一手段是重新编码/破坏或获取无水印原图。

## 五、整改建议（分层，按优先级）

> 目标从「去水印」改为「阻断水印下发 + 隐藏水印视图」两条线；E/F 属换源与不可解，需在文案上如实说明。

- **P0｜数据层置空（一次覆盖 B/C/D 的昵称与 logo 图）**
  - `com.zhihu.android.api.model.plugin.WatermarkInfo`：`logoUrl`、`nicknameUrl` 置空，`watermarked=false`（JSON 键 `logo`、`nick_name_url`）。
  - `com.zhihu.android.tornado.data.TDataWaterMark`：`logoImage`、`nickImage` 置空（JSON 键 `logo_image`、`nick_image`）。
  - 可在反序列化（AutoJackson deserializer，如 `WatermarkInfo`/`TDataWaterMark` 相关 `*AutoJacksonDeserializer`）或 getter 层实现。
- **P0｜播放器层拦截**
  - `com.zhihu.android.media.plugin.VodWatermarkPlugin#updateWatermarkUrl(String,String,boolean)`、`updateWatermarkLocation(Rect)`、`layoutWatermarkUI`：不执行或把 `rootView` 置 `GONE`。
  - 备选：拦 `com.zhihu.android.video.player2.t.a` 的 `https://api.zhihu.com/video/play/watermark` 响应（返回空 `WatermarkInfo`，即 P0 第一条的下游效果）。
- **P1｜混合容器插件**
  - `com.zhihu.android.plugin.basic.u.d`（`onCreateView/onViewCreated/getContentView`）返回空视图或 `GONE`；注意该类为插件中心派发，做 `instanceof`/配置类型守卫，避免影响其它 `TPluginConfigConversion` 插件。
- **P1｜图片换源（针对 E，属"换源"而非"去水印"）**
  - 反序列化后按 `watermarkSrc/privateWatermarkSrc` → `originalSrc/src` 回填：`api/model/Image`、`MomentPin$Content`、`NetworkMediaInfo`、`CardOriginalPinMiddle`、`MetaCommentFeedItemHolder`、`RepinOriginView`。
  - `zrichCore getImageWatermarkEnable` 返回 false、`getImageUrlHasWaterMark` 返回 false（需确认调用方语义，避免误用未授权原图）。
- **P2｜文案与设置项**
  - `switch_watermark` 标题改为「移除页面背景水印」之类，去掉「盲水印」「禁止开盒」的过度承诺；在 Wiki/README 里显式声明 F 类不支持。
- **P2｜A 类加固（成本低、收益明确）**
  - 选择器：真机确认 DOM 后收窄为精确选择器；当前规则漏掉 `waterMark` 等驼峰大小写组合，可补齐大小写变体，但不要加入 `mark` 之类宽泛关键词（误伤面大）。
  - 注入失败改为 `XposedBridge.log` 打点，避免静默失效（与 `docs/compat-audit-11.10.0.md` §五.4 的结论一致）。

## 六、真机验证方案（本次未执行，待补）

1. 打开模块「开启 WebView 调试」（`switch_webview_debug`，`WebView.java:105-108`），`chrome://inspect` 连接知乎进程。
2. 回答页 / 文章页：检查水印元素的 tag 与 class——
   - 命中 `[class*=watermark]`/`[class*=Watermark]` 且 computed `background-image` 被清空 ⇒ A 类生效；
   - 元素是 `<img>`/`<canvas>`/类名为 `waterMark` ⇒ 属于 §三 的失效条件。
3. 视频页 / 直播页 / 短剧页：截图对比，确认 `VodWatermarkPlugin`、`ZUIWaterMarkView`、`DramaWatermark` 覆盖层是否仍在（预期仍在）。
4. 抓包核对：`api.zhihu.com/video/play/watermark` 是否被请求；图片 CDN URL 是否为 `watermark_src` 变体（用于区分「本地绘制」与「服务端烘焙」）。
5. 判定标准：注入后水印消失记 A 类覆盖；仍存在则按 §四 归因到 B–F。
6. 注意：`targetapp` 审计 JAR 与真机 base.apk 非同一构建（见 `docs/compat-audit-11.10.0.md` §六 遗留风险），一切以真机实测为准。

## 七、证据强度与限制

1. B–F 的类、字段、方法、URL、布局 id 均来自 11.10.0 审计 JAR 常量池，可直接核验；E 类的字段名即 JSON 键，可与抓包互证。
2. A 类的 DOM 类名与 CSS 由服务端下发，APK 内无对应 JS/CSS 资产（`assets/**` 只有 `tornado/*.json` 路由配置与编辑器离线包），**静态无法证实**，只能真机验证——这也是本报告把 A 列为"有条件覆盖"而非"已覆盖"的原因。
3. 134 个常量池命中类中，多数分布在编辑器/上传侧（`picture/upload`、`km_editor`、`next_editor`、`zvideo_publish`、`vessay/upload_to_pc`）与视频/直播播放侧——说明"水印"在端内是**多子系统概念**，不存在单一去除点。
4. 本文所有类名/成员名取自混淆后的审计 JAR，同一构建内可定位；跨版本适配需按 `docs/compat-audit-11.10.0.md` §五 的经验重新定位（字段按名取风险最大、匿名类序号易变、优先钩具名方法）。

## 附录 A：模块侧相关代码位置

| 文件 | 行 | 说明 |
|---|---|---|
| `app/src/main/java/com/shatyuka/zhiliao/hooks/WebView.java` | 15-37 | 去水印 CSS 与注入脚本 |
| 同上 | 98-100 | `canPreRender` 关闭（预渲染副作用） |
| 同上 | 111-138 | 三个 WebView 回调注入点 |
| 同上 | 141-152 | 注入实现与静默异常 |
| `app/src/main/res/xml/preferences_zhihu.xml` | 279-284 | 开关定义与文案 |
| `app/src/main/java/com/shatyuka/zhiliao/hooks/ZhihuPreference.java` | 481 | 设置项图标绑定 |

## 附录 B：关键类索引（11.10.0 审计 JAR）

- 数据/模型：`api/model/Image`、`api/model/plugin/WatermarkInfo`、`api/model/PinContent$WatermarkType`、`api/model/People(isEnableWatermark)`、`api/interfaces/WatermarkUserInterfaces(provideUserSource)`、`moments/model/MomentPin$Content`、`vessay/model/NetworkMediaInfo`、`picture/upload/model/{ImageMetaInfo,UploadedImage}`
- 视频/直播：`media/plugin/VodWatermarkPlugin*`、`media/scaffold/{ScaffoldPlugin,portrait/MediaPortraitFullScreenFragment}`、`video/player2/{t/a,utils/t,u/f/b/j/b}`、`videox/api/model/DramaWatermark`、`videox/a_rebuild/room/widget/StreamPreviewView`、`morph/extension/R$*`、`live_boot/R$styleable`
- 混合容器：`tornado/data/TDataWaterMark*`、`plugin/basic/u/d`、`tornado/{q0/b,x0/c0,l0}`
- 富文本/编辑器：`zrich/ZRichCoreImpl`、`zrichCore/{d/b,e/c,model/bean/ZRichImageBeanExtKt}`、`km_editor/ability/AnswerExtraAbility$l`、`next_editor/plugins/ImagePlugin`、`editor/EditorHybridView$EditorPlugin`
- assets：`assets/tornado/{zvideo_detail_99,ogv_99,edu_aicourse_detail_103,edu_aicourse_introduction_103,follow,recommend_page,short_container_42_4}.json`
