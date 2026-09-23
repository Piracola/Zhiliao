package com.shatyuka.zhiliao.hooks;

import android.content.Context;
import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.TargetResolver;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import com.shatyuka.zhiliao.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class FeedAd implements IHook {
    static Class<?> BasePagingFragment;
    static Class<?> FeedAdvert;
    static Class<?> ListAd;
    static Class<?> Advert;
    static Class<?> Ad;

    static Field FeedList_data;

    static Method resolveFeedAdParam;

    @Override
    public String getName() {
        return "去信息流广告";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        Helper.requireTarget(Helper.MorphAdHelper, "com.zhihu.android.morph.ad.utils.MorphAdHelper");
        BasePagingFragment = classLoader.loadClass("com.zhihu.android.app.ui.fragment.paging.BasePagingFragment");
        try {
            FeedAdvert = classLoader.loadClass("com.zhihu.android.api.model.FeedAdvert");
            ListAd = classLoader.loadClass("com.zhihu.android.api.model.ListAd");
            Advert = classLoader.loadClass("com.zhihu.android.api.model.Advert");
            Ad = classLoader.loadClass("com.zhihu.android.api.model.Ad");
        } catch (ClassNotFoundException e) {
            FeedAdvert = classLoader.loadClass("com.zhihu.android.adbase.model.FeedAdvert");
            ListAd = classLoader.loadClass("com.zhihu.android.adbase.model.ListAd");
            Advert = classLoader.loadClass("com.zhihu.android.adbase.model.Advert");
            Ad = classLoader.loadClass("com.zhihu.android.adbase.model.Ad");
        }

        FeedList_data = classLoader.loadClass("com.zhihu.android.api.model.FeedList").getField("data");
        resolveFeedAdParam = TargetResolver.findMethod(Helper.MorphAdHelper, false, 0,
                method -> method.getReturnType() == boolean.class
                        && Arrays.equals(method.getParameterTypes(),
                        new Class<?>[]{Context.class, FeedAdvert, boolean.class, Boolean.class}));
    }

    @Override
    public void hook() throws Throwable {
        XposedBridge.hookAllMethods(BasePagingFragment, "postRefreshSucceed", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_feedad", true)) {
                    if (param.args[0] == null || !FeedList_data.getDeclaringClass().isInstance(param.args[0]))
                        return;
                    Object data = FeedList_data.get(param.args[0]);
                    if (!(data instanceof List))
                        return;
                    List<?> list = (List<?>) data;
                    if (list.isEmpty())
                        return;
                    for (int i = list.size() - 1; i >= 0; i--) {
                        if (isFeedAdvert(list.get(i))) {
                            list.remove(i);
                        }
                    }
                }
            }
        });
        XposedHelpers.findAndHookMethod(BasePagingFragment, "insertDataRangeToList", int.class, List.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_feedad", true)) {
                    if (!(param.args[1] instanceof List))
                        return;
                    List<?> list = (List<?>) param.args[1];
                    if (list.isEmpty())
                        return;
                    for (int i = list.size() - 1; i >= 0; i--) {
                        if (isFeedAdvert(list.get(i))) {
                            list.remove(i);
                        }
                    }
                }
            }
        });
        if (resolveFeedAdParam != null) {
            XposedBridge.hookMethod(resolveFeedAdParam, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_feedad", true)) {
                        param.setResult(false);
                    }
                }
            });
        }
        try {
            XposedHelpers.findAndHookMethod(Helper.MorphAdHelper, "resolve", Context.class, ListAd, Boolean.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_feedad", true)) {
                        param.setResult(false);
                    }
                }
            });
        } catch (NoSuchMethodError ignore) {
        }
        XposedHelpers.findAndHookMethod(Advert, "isSlidingWindow", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_feedad", true)) {
                    param.setResult(false);
                }
            }
        });
        XposedHelpers.findAndHookMethod(Ad, "isFloatAdCard", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_feedad", true)) {
                    param.setResult(false);
                }
            }
        });
    }

    private static boolean isFeedAdvert(Object item) {
        return item != null && FeedAdvert != null && FeedAdvert.isInstance(item);
    }
}
