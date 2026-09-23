package com.shatyuka.zhiliao.hooks;

import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.util.Collections;

public class HeadZoneBanner implements IHook {
    static Class<?> feedsHotListFragment2;
    static Class<?> rankFeedList;

    static Field head_zone;
    static Field headZones;

    @Override
    public String getName() {
        return "隐藏热榜顶部置顶";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        rankFeedList = classLoader.loadClass("com.zhihu.android.api.model.RankFeedList");
        head_zone = rankFeedList.getDeclaredField("head_zone");
        head_zone.setAccessible(true);
        feedsHotListFragment2 = classLoader.loadClass("com.zhihu.android.app.feed.ui.fragment.FeedsHotListFragment2");

        // Gj(RankFeedList) 优先消费新字段 headZones，取不到时只降级清空 head_zone
        try {
            headZones = rankFeedList.getDeclaredField("headZones");
            headZones.setAccessible(true);
        } catch (NoSuchFieldException e) {
            headZones = null;
            XposedBridge.log("[Zhiliao] RankFeedList.headZones 不存在: " + e);
        }
    }

    @Override
    public void hook() throws Throwable {
        XposedBridge.hookAllMethods(feedsHotListFragment2, "postRefreshSucceed", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws IllegalAccessException {
                if (!Helper.prefs.getBoolean("switch_mainswitch", false) || param.args.length == 0 || !rankFeedList.isInstance(param.args[0])) {
                    return;
                }
                head_zone.set(param.args[0], null);
                if (headZones != null) {
                    headZones.set(param.args[0], Collections.emptyList());
                }
            }
        });
    }
}
