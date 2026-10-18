package com.shatyuka.zhiliao

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shatyuka.zhiliao.ui.Accent
import com.shatyuka.zhiliao.ui.ActionText
import com.shatyuka.zhiliao.ui.AppSurface
import com.shatyuka.zhiliao.ui.CardParagraph
import com.shatyuka.zhiliao.ui.IconBadge
import com.shatyuka.zhiliao.ui.RowDivider
import com.shatyuka.zhiliao.ui.SectionHeader
import com.shatyuka.zhiliao.ui.SettingCard
import com.shatyuka.zhiliao.ui.SmoothRoundedCornerShape
import com.shatyuka.zhiliao.ui.ValueWithArrow
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.extra.SuperDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** 设置项的形态：开关行、可编辑文本行。 */
enum class ItemKind { Switch, Text }

/** 设置项定义：key、标题、说明、图标（Lucide 资源），与原 preferences_zhihu.xml 一一对应。 */
data class PrefItem(
    val key: String,
    val title: String,
    val summary: String? = null,
    val icon: Int,
    val defaultValue: Boolean = false,
    val kind: ItemKind = ItemKind.Switch,
    /** 上级开关的 key：上级关掉时这一行跟着置灰。 */
    val parent: String? = null,
)

data class PrefGroup(
    val key: String,
    val title: String,
    val accent: Color,
    val items: List<PrefItem>,
)

object Catalog {
    // 原版 preferences_zhihu.xml 的 defaultValue：广告组全部默认开（去广告是模块本职），
    // 其余全部默认关（过滤/隐藏类都是主动选择）。默认值必须与原版一致，
    // 否则首次运行就会把用户没开过的功能全部写进配置（曾因此清空整个推荐页）。
    // 注意：object 自上而下初始化，defaultOff 必须先于 groups 声明（item() 会引用它）。
    private val defaultOff = setOf(
        "switch_video", "switch_removearticle", "switch_pin", "switch_marketcard",
        "switch_club", "switch_goods", "switch_related", "switch_searchwords",
        "switch_externlink", "switch_externlinkex", "switch_colormode", "switch_tag",
        "switch_statusbar", "switch_fullscreen", "switch_thirdpartylogin",
        "switch_autorefresh", "switch_antitracking",
        "switch_reddot", "switch_vipbanner", "switch_hotbanner", "switch_article",
        "switch_feedtophot", "switch_minehybrid",
        "switch_homenav", "switch_findnav", "switch_panelnav", "switch_messagenav",
        "switch_profilenav", "switch_navres",
        "switch_nextanswer",
        "switch_webview_debug", "switch_watermark", "switch_imagesource", "switch_subscribe",
        "switch_autoclean", "switch_silenceclean",
        "switch_hidetoast", "switch_hooktrace",
    )

    // 图标沿用原知乎内设置页那套 Lucide 资源（tools/update-icons.mjs 生成的 ic_*.xml）。
    val groups = listOf(
        PrefGroup("ads", "广告", Accent.Orange, listOf(
            item("switch_launchad", "去启动页广告", "去除启动页全屏广告", R.drawable.ic_ad_units),
            item("switch_feedad", "去信息流广告", "去除信息流内夹杂的广告", R.drawable.ic_table_rows),
            item("switch_answerlistad", "去回答列表广告", "去除回答列表内的广告", R.drawable.ic_format_list),
            item("switch_commentad", "去评论广告", "去除评论页面顶部的广告", R.drawable.ic_comment),
            item("switch_sharead", "去分享广告", "去除分享页面顶部的广告", R.drawable.ic_share),
            item("switch_answerad", "去回答底部广告", "去除回答底部广告", R.drawable.ic_notes),
            item("switch_searchad", "去搜索广告", "去除「大家都在搜」与推广", R.drawable.ic_search),
        )),
        PrefGroup("misc", "其他", Accent.Green, listOf(
            item("switch_video", "过滤视频", "去除推荐列表内的视频", R.drawable.ic_play_circle),
            item("switch_removearticle", "过滤文章", "去除推荐列表内的文章", R.drawable.ic_article),
            item("switch_pin", "过滤想法", "去除推荐列表内的想法", R.drawable.ic_emoji_objects),
            item("switch_marketcard", "去信息流会员推荐", "去除信息流里的会员推荐", R.drawable.ic_vip),
            item("switch_club", "去回答圈子", "去除回答底部相关圈子", R.drawable.ic_group),
            item("switch_goods", "去商品推荐", "去除回答底部商品推荐", R.drawable.ic_local_mall),
            item("switch_related", "去相关搜索", "去除回答底部相关搜索", R.drawable.ic_search_off),
            item("switch_searchwords", "去关键字搜索", "去除页内关键字搜索", R.drawable.ic_plagiarism),
            item("switch_externlink", "直接打开外部链接", "无需再次确认", R.drawable.ic_link),
            item("switch_externlinkex", "直接打开外部链接Ex", "使用浏览器打开外部链接", R.drawable.ic_link),
            item("switch_colormode", "禁止切换色彩模式", "使用系统当前色彩模式显示大图", R.drawable.ic_color),
            item("switch_tag", "显示卡片类别", "在推荐卡片标题前显示类别标签", R.drawable.ic_label),
            item("switch_statusbar", "状态栏沉浸", "沉浸式状态栏", R.drawable.ic_fullscreen),
            item("switch_fullscreen", "禁止进入全屏模式", "看看时间", R.drawable.ic_fullscreen_exit),
            item("switch_thirdpartylogin", "第三方登录", "解锁全部第三方登录方式", R.drawable.ic_login),
            item("switch_autorefresh", "禁止自动刷新", "禁止首页推荐自动刷新", R.drawable.ic_refresh),
            item("switch_antitracking", "反追踪", "阻断埋点上报", R.drawable.ic_block),
            item("switch_antitracking_link", "反链接参数追踪", "链接自动去掉追踪参数", R.drawable.ic_unlink, parent = "switch_antitracking"),
            item("switch_antitracking_qr", "反图片链接追踪", "二维码直达原文", R.drawable.ic_qr_code, parent = "switch_antitracking"),
        )),
        PrefGroup("ui", "界面净化", Accent.Purple, listOf(
            item("switch_reddot", "不显示小红点", "强迫症福音", R.drawable.ic_mark_chat_unread),
            item("switch_vipbanner", "隐藏会员卡片", "隐藏「我的」页面会员卡片", R.drawable.ic_vip_banner),
            item("switch_hotbanner", "隐藏热点通知", "隐藏顶部弹窗通知", R.drawable.ic_whatshot),
            item("switch_article", "精简文章页面", "隐藏相关推荐", R.drawable.ic_article),
            item("switch_feedtophot", "隐藏置顶热门", "隐藏推荐页顶部热门推荐", R.drawable.ic_whatshot),
            item("switch_minehybrid", "隐藏混合卡片", "隐藏「我的」底部混合卡片", R.drawable.ic_viewcard),
        )),
        PrefGroup("nav", "导航栏", Accent.Indigo, listOf(
            item("switch_homenav", "隐藏首页", "隐藏导航栏首页按钮", R.drawable.ic_rss_feed),
            item("switch_findnav", "隐藏看山", "隐藏导航栏看山按钮", R.drawable.ic_cross_star),
            item("switch_panelnav", "隐藏发布", "隐藏导航栏发布按钮", R.drawable.ic_add_circle),
            item("switch_messagenav", "隐藏消息", "隐藏导航栏消息按钮", R.drawable.ic_notifications_off),
            item("switch_profilenav", "隐藏我的", "隐藏导航栏我的按钮", R.drawable.ic_person),
            item("switch_navres", "禁用活动主题", "禁止导航栏使用活动主题", R.drawable.ic_event),
        )),
        PrefGroup("answer", "回答", Accent.Pink, listOf(
            item("switch_nextanswer", "移除下一个回答按钮", "多余的按钮", R.drawable.ic_circle_down),
        )),
        PrefGroup("webview", "WebView", Accent.Cyan, listOf(
            item("switch_webview_debug", "开启WebView调试", "chrome://inspect", R.drawable.ic_code),
            item("switch_watermark", "移除水印", "页面/视频/容器水印；像素级水印无法移除", R.drawable.ic_layers),
            item("switch_imagesource", "图片换源去水印", "实验性：仅对服务端另发原图的图片水印有效", R.drawable.ic_image),
            item("switch_subscribe", "去除回答页面关注头像", "防止误触", R.drawable.ic_person_add_alt),
            text("edit_js", "自定义脚本", "向知乎网页注入 JavaScript，留空则不注入", R.drawable.ic_javascript),
        )),
        PrefGroup("cleaner", "临时文件", Accent.Amber, listOf(
            item("switch_autoclean", "自动清理临时文件", "自动释放空间", R.drawable.ic_auto_delete),
            item("switch_silenceclean", "静默清理", "沉默是金", R.drawable.ic_notifications_off),
        )),
        PrefGroup("filter", "自定义过滤", Accent.Blue, listOf(
            text("edit_title", "标题过滤", "支持正则表达式，留空为忽略", R.drawable.ic_label),
            text("edit_author", "作者过滤", "支持正则表达式，留空为忽略", R.drawable.ic_person),
            text("edit_content", "内容过滤", "支持正则表达式，留空为忽略", R.drawable.ic_notes),
        )),
        PrefGroup("debug", "调试", Accent.Grey, listOf(
            item("switch_hidetoast", "隐藏功能加载失败提示", "无视风险，继续运行", R.drawable.ic_tooltip),
            item("switch_hooktrace", "记录 Hook 触发日志", "排障用：启动两分钟后列出没被触发过的目标", R.drawable.ic_scroll_text),
        )),
    )

    /** 总开关单独一组：它不受自己管辖，由免责声明（EULA）约束。 */
    val mainSwitch = PrefItem(
        key = "switch_mainswitch",
        title = "启用知了",
        summary = "知了功能总开关",
        icon = R.drawable.ic_toggle_on,
        defaultValue = true,
        kind = ItemKind.Switch,
    )

    val restartToastKeys = setOf(
        "switch_navres", "switch_tag", "switch_thirdpartylogin", "switch_autorefresh",
        "switch_reddot", "switch_vipbanner", "switch_homenav", "switch_messagenav",
        "switch_profilenav", "switch_panelnav", "switch_findnav", "switch_article",
        "switch_nextanswer", "switch_autoclean", "switch_feedtophot", "switch_minehybrid",
        "switch_imagesource", "switch_antitracking", "switch_antitracking_link",
        "switch_antitracking_qr",
    )

    val switchItems: List<PrefItem> =
        listOf(mainSwitch) + groups.flatMap { it.items }.filter { it.kind == ItemKind.Switch }

    val textItems: List<PrefItem> = groups.flatMap { it.items }.filter { it.kind == ItemKind.Text }

    fun textItem(key: String): PrefItem? = textItems.firstOrNull { it.key == key }

    private fun item(key: String, title: String, summary: String, icon: Int, parent: String? = null) =
        PrefItem(key, title, summary, icon, defaultValue = key !in defaultOff, parent = parent)

    private fun text(key: String, title: String, summary: String, icon: Int) =
        PrefItem(key, title, summary, icon, kind = ItemKind.Text)
}

object HookConstants {
    const val hookPackage = "com.zhihu.android"
    const val ACTION_RESTART_ZHIHU = "com.shatyuka.zhiliao.action.RESTART_ZHIHU"
    const val ACTION_CLEAN_CACHE = "com.shatyuka.zhiliao.action.CLEAN_CACHE"
    const val ACTION_STATUS = "com.shatyuka.zhiliao.action.STATUS"
    const val RESTART_PERMISSION = "com.shatyuka.zhiliao.permission.RESTART_ZHIHU"
}

/** 行内边距：左 16 对齐 MIUI 列表，右 12 给开关/箭头留呼吸。 */
private val RowMargin = PaddingValues(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)

class MainActivity : ComponentActivity() {
    private var preferences: SharedPreferences? = null
    private var hookValues by mutableStateOf<Map<String, Boolean>>(emptyMap())
    private var textValues by mutableStateOf<Map<String, String>>(emptyMap())
    private var editingKey by mutableStateOf<String?>(null)
    private var editingValue by mutableStateOf("")
    private var eulaAccepted by mutableStateOf(false)
    private var selfCheckStatus by mutableStateOf<String?>(null)
    /** 当前知乎版本上 Hook 装不起来的设置项：对应开关置灰，别让用户对着无效开关发呆 */
    private var unsupportedKeys by mutableStateOf<Set<String>>(emptySet())
    private var gotStatus = false
    private var versionClicks = 0
    /** 上次问询知乎拿到的原始回执，导出诊断时用 */
    private var lastZhihu = ""
    private var lastFingerprint = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ModulePreferences.connect(this) { remote ->
            runOnUiThread {
                preferences = remote
                reloadValues()
            }
        }
        setContent {
            // 设置行默认是 17sp 标题 + 14sp 说明，配上 30dp 的图标显得头重脚轻；
            // 标题 16sp、说明 12sp，两行加起来和图标更成比例。
            val textStyles = remember {
                val base = defaultTextStyles()
                base.copy(
                    headline1 = base.headline1.copy(fontSize = 16.sp),
                    body2 = base.body2.copy(fontSize = 12.sp),
                )
            }
            MiuixTheme(
                colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
                textStyles = textStyles,
            ) {
                SettingsScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 每次回到这个页面都顺手问一句：用户可能刚重启过知乎，自检结果该跟着变
        queryStatus()
    }

    private fun reloadValues() {
        val prefs = preferences ?: return
        hookValues = Catalog.switchItems.associate { it.key to prefs.getBoolean(it.key, it.defaultValue) }
        textValues = Catalog.textItems.associate { it.key to prefs.getString(it.key, "").orEmpty() }
        eulaAccepted = prefs.getBoolean("accept_eula", false)
    }

    private val lastToggleAt = HashMap<String, Long>()

    private fun putBoolean(key: String, value: Boolean) {
        // 开关和整行都会响应同一次点击，两次写入在 250ms 内连发会把值翻回来（看起来"点了没反应"）。
        // 这里做一次去抖：同一行 250ms 内只认第一次写入。
        val now = android.os.SystemClock.uptimeMillis()
        val last = lastToggleAt[key] ?: 0L
        if (now - last < 250L) return
        lastToggleAt[key] = now

        preferences?.edit()?.putBoolean(key, value)?.apply()
        hookValues = hookValues + (key to value)
        if (key in Catalog.restartToastKeys) {
            Toast.makeText(this, "重启知乎生效", Toast.LENGTH_SHORT).show()
        }
    }

    private fun putText(key: String, raw: String) {
        // 正则做长度封顶 + 编译校验，防御灾难性回溯；脚本按 32KB 封顶
        if (key == "edit_js") {
            val clipped = raw.take(32 * 1024)
            preferences?.edit()?.putString(key, clipped)?.apply()
            textValues = textValues + (key to clipped)
            return
        }
        val clipped = raw.take(512)
        val valid = clipped.isEmpty() || try {
            Pattern.compile(clipped); true
        } catch (_: PatternSyntaxException) {
            false
        }
        preferences?.edit()?.putString(key, clipped)?.apply()
        textValues = textValues + (key to clipped)
        if (!valid) Toast.makeText(this, "正则无效，已停用该过滤（编译失败）", Toast.LENGTH_LONG).show()
    }

    /** 当前配置后端（remote=远端偏好 / local=本地回落），排障用。 */
    private fun backendName(): String = ModulePreferences.activeBackend()

    // ---------------------------------------------------------------- UI

    @Composable
    private fun SettingsScreen() {
        val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
        val mainOn = hookValues[Catalog.mainSwitch.key] ?: Catalog.mainSwitch.defaultValue
        val gate = eulaAccepted && mainOn

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = AppSurface.page(),
            topBar = {
                TopAppBar(
                    title = "知了",
                    largeTitle = "知了",
                    color = AppSurface.page(),
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 28.dp,
                ),
            ) {
                if (!eulaAccepted) {
                    item(key = "eula") { EulaSection() }
                }
                item(key = "main") { MainSwitchSection(gate) }
                items(Catalog.groups, key = { it.key }) { group ->
                    GroupSection(group, gate)
                }
                item(key = "clean") { CleanSection() }
                item(key = "about") { AboutSection() }
                item(key = "footer") { Footer() }
            }
            editingKey?.let { key -> EditDialog(key) }
        }
    }

    @Composable
    private fun EulaSection() {
        SectionHeader("免责声明")
        SettingCard {
            Column(modifier = Modifier.padding(16.dp)) {
                CardParagraph(
                    "本软件仅用于技术交流，请勿用于商业及非法用途，如产生法律纠纷与作者本人无关。\n\n" +
                        "本软件不会收集、上传您的个人信息，如有个人信息泄露与作者本人无关。",
                )
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        putBoolean("accept_eula", true)
                        // 界面把总开关显示成「已开启」，但每个 Hook 读 switch_mainswitch 的缺省值都是 false。
                        // 首次同意时补一个真值，否则新用户看到的开关是开的，功能却一个都不生效。
                        if (preferences?.contains("switch_mainswitch") != true) {
                            preferences?.edit()?.putBoolean("switch_mainswitch", true)?.apply()
                            hookValues = hookValues + ("switch_mainswitch" to true)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    BasicText(
                        text = "我已知晓并同意",
                        style = TextStyle(
                            fontSize = 15.sp,
                            color = MiuixTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }
    }

    @Composable
    private fun MainSwitchSection(gate: Boolean) {
        SectionHeader("通用")
        SettingCard {
            SwitchRow(Catalog.mainSwitch, Accent.Blue, enabled = eulaAccepted)
        }
        if (eulaAccepted && !gate) {
            Spacer(Modifier.height(8.dp))
            SettingCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    CardParagraph("总开关已关闭，下面的功能都不会生效。")
                }
            }
        }
    }

    @Composable
    private fun GroupSection(group: PrefGroup, gate: Boolean) {
        SectionHeader(group.title)
        SettingCard {
            group.items.forEachIndexed { index, item ->
                if (index > 0) RowDivider()
                // 子项跟着父开关走：总开关关掉时它不该还能拨。
                val parentOn = item.parent?.let { hookValues[it] == true } ?: true
                val enabled = gate && parentOn
                when (item.kind) {
                    ItemKind.Switch -> SwitchRow(item, group.accent, enabled)
                    ItemKind.Text -> TextRow(item, group.accent, enabled)
                }
            }
        }
    }

    @Composable
    private fun SwitchRow(item: PrefItem, accent: Color, enabled: Boolean) {
        val unsupported = item.key in unsupportedKeys
        val active = enabled && !unsupported
        val checked = !unsupported && (hookValues[item.key] ?: item.defaultValue)
        val toggle: (() -> Unit)? = if (active) ({ putBoolean(item.key, !checked) }) else null
        BasicComponent(
            title = item.title,
            summary = if (unsupported) "当前知乎版本暂不支持" else item.summary,
            enabled = active,
            startAction = { IconBadge(iconRes = item.icon, accent = accent, enabled = active) },
            endActions = {
                // 开关自己响应点击；整行也能点。同一次手势会触发两次写入，由 putBoolean 去抖兜住。
                Switch(
                    checked = checked,
                    onCheckedChange = { putBoolean(item.key, it) },
                    enabled = active,
                )
            },
            onClick = toggle,
            insideMargin = RowMargin,
        )
    }

    @Composable
    private fun TextRow(item: PrefItem, accent: Color, enabled: Boolean) {
        val value = textValues[item.key].orEmpty()
        val open: (() -> Unit)? = if (enabled) ({
            editingKey = item.key
            editingValue = value
        }) else null
        BasicComponent(
            title = item.title,
            summary = item.summary,
            enabled = enabled,
            startAction = { IconBadge(iconRes = item.icon, accent = accent, enabled = enabled) },
            endActions = { ValueWithArrow(text = previewValue(item.key, value), enabled = enabled) },
            onClick = open,
            insideMargin = RowMargin,
        )
    }

    @Composable
    private fun CleanSection() {
        Spacer(Modifier.height(8.dp))
        SettingCard {
            BasicComponent(
                title = "清理临时文件",
                summary = "清理知乎内的广告缓存和日志文件",
                startAction = { IconBadge(iconRes = R.drawable.ic_delete, accent = Accent.Amber) },
                endActions = {
                    ActionText(text = "清理", onClick = { cleanCache() })
                },
                insideMargin = RowMargin,
            )
        }
    }

    @Composable
    private fun AboutSection() {
        SectionHeader("关于")
        SettingCard {
            LinkRow("版本", versionString(), R.drawable.ic_info, Accent.Blue, arrow = false) {}
            RowDivider()
            LinkRow("作者", "Shatyuka", R.drawable.ic_person, Accent.Blue, arrow = false) { easterEgg() }
            RowDivider()
            LinkRow("帮助", "常见问题", R.drawable.ic_help, Accent.Blue) {
                open("https://github.com/shatyuka/Zhiliao/wiki")
            }
            RowDivider()
            LinkRow("TG频道", "订阅更新", R.drawable.ic_rss_feed, Accent.Blue) {
                open("https://t.me/zhiliao")
            }
            RowDivider()
            LinkRow("TG群", "找作者对线", R.drawable.ic_telegram, Accent.Blue) {
                open("https://t.me/joinchat/OibCWxbdCMkJ2fG8J1DpQQ")
            }
            RowDivider()
            LinkRow("源代码", "点个 Star 吧", R.drawable.ic_github, Accent.Blue) {
                open("https://github.com/shatyuka/Zhiliao")
            }
            RowDivider()
            LinkRow("捐赠", "快揭不开锅了", R.drawable.ic_monetization, Accent.Blue) {
                open("https://github.com/shatyuka/Zhiliao/wiki/Donate")
            }
            RowDivider()
            BasicComponent(
                title = "自检",
                summary = selfCheckStatus ?: "点右侧刷新，向知乎查询当前状态",
                startAction = { IconBadge(iconRes = R.drawable.ic_info, accent = Accent.Blue) },
                endActions = {
                    ActionText(text = "刷新", onClick = { queryStatus() })
                },
                onClick = { queryStatus() },
                insideMargin = RowMargin,
            )
            RowDivider()
            BasicComponent(
                title = "导出诊断",
                summary = "把这份报告发给开发者，只导出、不上传",
                startAction = { IconBadge(iconRes = R.drawable.ic_info, accent = Accent.Blue) },
                endActions = {
                    ActionText(text = "导出", onClick = { exportDiagnostics() })
                },
                onClick = { exportDiagnostics() },
                insideMargin = RowMargin,
            )
            RowDivider()
            BasicComponent(
                title = "重启知乎",
                summary = "应用新设置",
                startAction = { IconBadge(iconRes = R.drawable.ic_restart, accent = Accent.Blue) },
                endActions = {
                    ActionText(text = "重启", onClick = { restartZhihu() })
                },
                insideMargin = RowMargin,
            )
        }
    }

    @Composable
    private fun LinkRow(
        title: String,
        value: String,
        icon: Int,
        accent: Color,
        arrow: Boolean = true,
        onClick: () -> Unit,
    ) {
        BasicComponent(
            title = title,
            startAction = { IconBadge(iconRes = icon, accent = accent) },
            endActions = {
                if (arrow) {
                    ValueWithArrow(text = value)
                } else {
                    BasicText(
                        text = value,
                        style = TextStyle(
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        ),
                    )
                }
            },
            onClick = onClick,
            insideMargin = RowMargin,
        )
    }

    @Composable
    private fun Footer() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(
                text = "配置通道：" + if (backendName() == "remote") "远端偏好" else "本地回落",
                style = TextStyle(fontSize = 11.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary),
            )
        }
    }

    @Composable
    private fun EditDialog(key: String) {
        val meta = Catalog.textItem(key)
        val isScript = key == "edit_js"
        SuperDialog(
            show = true,
            title = meta?.title ?: "编辑",
            summary = meta?.summary,
            renderInRootScaffold = false,
            onDismissRequest = { editingKey = null },
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(SmoothRoundedCornerShape(12.dp))
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    BasicTextField(
                        value = editingValue,
                        onValueChange = { editingValue = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = if (isScript) 160.dp else 72.dp),
                        textStyle = TextStyle(
                            fontSize = if (isScript) 12.sp else 14.sp,
                            lineHeight = if (isScript) 18.sp else 20.sp,
                            fontFamily = if (isScript) FontFamily.Monospace else FontFamily.Default,
                            color = MiuixTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
                    )
                    if (editingValue.isEmpty()) {
                        BasicText(
                            text = if (isScript) "// 例如 window.__zhiliao = 1" else "留空为忽略",
                            style = TextStyle(
                                fontSize = if (isScript) 12.sp else 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            ),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(
                    text = "保存",
                    onClick = {
                        putText(key, editingValue)
                        editingKey = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    text = "取消",
                    onClick = { editingKey = null },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    // ------------------------------------------------------------ 生命周期外的小工具

    private fun previewValue(key: String, value: String): String = when {
        value.isBlank() -> "未设置"
        key == "edit_js" -> "${value.length} 字符"
        value.length > 10 -> value.take(10) + "…"
        else -> value
    }

    private fun cleanCache() {
        // 清理目标在知乎的数据目录，模块 App 无权直接删，走广播让 Hook 侧执行
        sendBroadcast(Intent(HookConstants.ACTION_CLEAN_CACHE).setPackage(HookConstants.hookPackage))
        Toast.makeText(this, "已通知知乎端清理", Toast.LENGTH_SHORT).show()
    }

    /**
     * 诊断导出：拼一段纯文本写进 App 自己的外部目录，顺手复制到剪贴板。
     * 模块连 INTERNET 权限都没有，这份东西想上传也上不去。
     */
    private fun exportDiagnostics() {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())
        val report = DiagnosticReport.build(
            moduleVersion = versionString(),
            timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
            device = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            zhihu = lastZhihu,
            fingerprint = lastFingerprint,
            status = selfCheckStatus.orEmpty(),
            failures = unsupportedKeys.toList(),
            switches = hookValues,
            textLengths = textValues.mapValues { it.value.length },
        )
        try {
            val dir = File(getExternalFilesDir(null), "diagnostics").apply { mkdirs() }
            val file = File(dir, "zhiliao-$stamp.txt")
            file.writeText(report)
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("知了诊断报告", report))
            Toast.makeText(this, "已导出并复制：${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Throwable) {
            Toast.makeText(this, "导出失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun restartZhihu() {
        sendBroadcast(Intent(HookConstants.ACTION_RESTART_ZHIHU).setPackage(HookConstants.hookPackage))
        Toast.makeText(this, "已发送重启指令", Toast.LENGTH_SHORT).show()
    }

    /**
     * 自检状态通道：发有序广播问知乎一句，Hook 侧的接收器把结果塞进 result extras 带回来。
     * 走的是同一个签名权限，别人发不进来、也回不了这个结果。
     */
    private fun queryStatus() {
        val intent = Intent(HookConstants.ACTION_STATUS).setPackage(HookConstants.hookPackage)
        val reply = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, result: Intent?) {
                val extras = getResultExtras(false)
                if (extras == null) {
                    // 小米的后台冻结会把广播吃掉，这时别用"未响应"冲掉上一次的好结果
                    if (!gotStatus) selfCheckStatus = "知乎未响应（进程可能没在运行）"
                    return
                }
                gotStatus = true
                selfCheckStatus = extras.getString("status", "知乎未响应")
                lastZhihu = extras.getString("zhihu", "").orEmpty()
                // 装不起来的功能，把它管的开关灰掉
                unsupportedKeys = extras.getString("failedKeys", "")
                    .orEmpty()
                    .split(',')
                    .filter { it.isNotBlank() }
                    .toSet()
                lastFingerprint = extras.getString("fingerprint", "").orEmpty()
            }
        }
        try {
            // 这里不传 receiverPermission：那个参数约束的是"接收方要持有权限"。
            // 发送方要有权限，靠的是 Hook 侧注册接收器时要求的 RESTART_PERMISSION。
            sendOrderedBroadcast(intent, null, reply, null, 0, null, null)
        } catch (e: Throwable) {
            selfCheckStatus = "查询失败：${e.message}"
        }
    }

    private fun easterEgg() {
        versionClicks++
        if (versionClicks == 5) {
            Toast.makeText(this, "点我次数再多，更新也不会变快哦", Toast.LENGTH_SHORT).show()
            versionClicks = 0
        }
    }

    private fun versionString(): String = getString(R.string.app_version)

    private fun open(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}




