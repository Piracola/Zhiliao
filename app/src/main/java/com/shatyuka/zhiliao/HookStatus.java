package com.shatyuka.zhiliao;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Hook 失败 → 设置项 的映射账本。
 *
 * 某个功能在当前知乎版本上定位不到目标时，它驱动的那些开关就该在设置页里置灰；
 * 否则用户会对着一个永远不生效的开关反复尝试。这份映射是纯数据，不碰 Android API，
 * 所以能在 JVM 单测里直接把"哪条 Hook 该灰哪些键"钉死。
 */
public final class HookStatus {

    /** 功能名 → 它负责的设置项（第一个通常是主开关）。 */
    private static final Map<String, String[]> FEATURE_KEYS = new LinkedHashMap<>();

    static {
        FEATURE_KEYS.put("去启动页广告", new String[]{"switch_launchad"});
        FEATURE_KEYS.put("自定义过滤", new String[]{"switch_video", "switch_removearticle", "switch_pin", "switch_marketcard"});
        FEATURE_KEYS.put("去信息流广告", new String[]{"switch_feedad"});
        FEATURE_KEYS.put("去回答列表广告", new String[]{"switch_answerlistad"});
        FEATURE_KEYS.put("去评论广告", new String[]{"switch_commentad"});
        FEATURE_KEYS.put("去回答底部广告", new String[]{"switch_answerad"});
        FEATURE_KEYS.put("去分享广告", new String[]{"switch_sharead"});
        FEATURE_KEYS.put("移除下一个回答按钮", new String[]{"switch_nextanswer"});
        FEATURE_KEYS.put("不显示小红点", new String[]{"switch_reddot"});
        FEATURE_KEYS.put("直接打开外部链接", new String[]{"switch_externlink", "switch_externlinkex"});
        FEATURE_KEYS.put("隐藏会员卡片", new String[]{"switch_vipbanner"});
        FEATURE_KEYS.put("隐藏导航栏按钮", new String[]{"switch_homenav", "switch_findnav", "switch_messagenav", "switch_profilenav", "switch_panelnav"});
        FEATURE_KEYS.put("隐藏热点通知", new String[]{"switch_hotbanner"});
        FEATURE_KEYS.put("禁止切换色彩模式", new String[]{"switch_colormode"});
        FEATURE_KEYS.put("精简文章页面", new String[]{"switch_article"});
        FEATURE_KEYS.put("显示卡片类别", new String[]{"switch_tag"});
        FEATURE_KEYS.put("去搜索广告", new String[]{"switch_searchad"});
        FEATURE_KEYS.put("状态栏沉浸", new String[]{"switch_statusbar"});
        FEATURE_KEYS.put("第三方登录", new String[]{"switch_thirdpartylogin"});
        FEATURE_KEYS.put("导航栏样式", new String[]{"switch_navres"});
        FEATURE_KEYS.put("WebView修改", new String[]{"switch_webview_debug"});
        FEATURE_KEYS.put("临时文件清理", new String[]{"switch_autoclean", "switch_silenceclean"});
        FEATURE_KEYS.put("隐藏推荐页置顶热门", new String[]{"switch_feedtophot"});
        FEATURE_KEYS.put("隐藏「我的」底部混合卡片", new String[]{"switch_minehybrid"});
        FEATURE_KEYS.put("去关注按钮", new String[]{"switch_subscribe"});
        FEATURE_KEYS.put("禁止进入全屏模式", new String[]{"switch_fullscreen"});
        FEATURE_KEYS.put("禁止自动刷新", new String[]{"switch_autorefresh"});
        FEATURE_KEYS.put("图片换源去水印", new String[]{"switch_imagesource"});
        FEATURE_KEYS.put("移除水印", new String[]{"switch_watermark"});
        FEATURE_KEYS.put("反追踪", new String[]{
                "switch_antitracking", "switch_antitracking_link", "switch_antitracking_qr"});
    }

    /** 只有总开关、没有独立开关的功能：失败时无处可灰，也不该在单测里要求它有映射。 */
    private static final Set<String> NO_SWITCH_FEATURES = new LinkedHashSet<>(Arrays.asList("隐藏热榜顶部置顶"));

    private final Set<String> failedKeys = new LinkedHashSet<>();

    public static boolean hasSwitch(String feature) {
        return FEATURE_KEYS.containsKey(feature);
    }

    public static boolean hasNoSwitch(String feature) {
        return NO_SWITCH_FEATURES.contains(feature);
    }

    public void reset() {
        failedKeys.clear();
    }

    /** 记下这条功能挂了，它管的开关都要灰。未知功能（或没有独立开关的）静默跳过。 */
    public void markUnsupported(String feature) {
        String[] keys = FEATURE_KEYS.get(feature);
        if (keys != null) {
            failedKeys.addAll(Arrays.asList(keys));
        }
    }

    /** 逗号分隔的键列表，塞进广播 extras 传给设置 App。 */
    public String failedKeysLine() {
        return String.join(",", failedKeys);
    }

    public int size() {
        return failedKeys.size();
    }
}
