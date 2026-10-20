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
    /** ad_card 模块的视图入口：void 方法 + (回调接口, int 卡片类型)。 */
    static Method adCardView;

    @Override
    public String getName() {
        return "去信息流广告";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        Helper.requireTarget(Helper.MorphAdHelper, "com.zhihu.android.morph.ad.utils.MorphAdHelper");
        BasePagingFragment = classLoader.loadClass("com.zhihu.android.app.ui.fragment.paging.BasePagingFragment");
        // 广告模型的包名渠道间不同（api.model / adbase.model），由候选名单给顺序，
        // 命中一个就按同包补齐另外三个——同一份渠道包里这四个类总在同一个包
        FeedAdvert = TargetResolver.findClassByQualifiedCandidates(classLoader, "feedAd.feedAdvert",
                FeedAd::looksLikeAdModel);
        if (FeedAdvert != null) {
            String pkg = FeedAdvert.getPackage().getName();
            ListAd = classLoader.loadClass(pkg + ".ListAd");
            Advert = classLoader.loadClass(pkg + ".Advert");
            Ad = classLoader.loadClass(pkg + ".Ad");
        } else {
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
        }

        FeedList_data = classLoader.loadClass("com.zhihu.android.api.model.FeedList").getField("data");
        resolveFeedAdParam = TargetResolver.findMethod(Helper.MorphAdHelper, false, 0,
                method -> method.getReturnType() == boolean.class
                        && Arrays.equals(method.getParameterTypes(),
                        new Class<?>[]{Context.class, FeedAdvert, boolean.class, Boolean.class}));

        // 推荐页顶部那条横贯整屏的焦点广告栏不来自信息流数据：feed 找 ad_card 模块要视图，
        // 拿回来自己塞在列表最前面。所以按条目删广告删不到它，只能在"要视图"这一步拦。
        // 入口是接口方法 getAdCardView(回调, int cardType)，实现类里同名；参数类型才是稳的锚。
        adCardView = null;
        Throwable adCardError = null;
        try {
            Class<?> adCardImpl = classLoader.loadClass("com.zhihu.android.ad_card.AdCardViewInterfaceImpl");
            for (Method method : adCardImpl.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (method.getReturnType() != void.class || types.length != 2
                        || types[1] != int.class || !types[0].isInterface()) {
                    continue;
                }
                if ("getAdCardView".equals(method.getName())) {
                    adCardView = method;
                    break;
                }
                if (adCardView == null) {
                    adCardView = method;
                }
            }
        } catch (Throwable e) {
            adCardError = e;
        }
        if (adCardView == null) {
            XposedBridge.log("[Zhiliao][去信息流广告] 没有找到 ad_card 的 getAdCardView(回调,int)，顶部广告栏会漏"
                    + (adCardError == null ? "" : "：" + adCardError));
        }
    }

    /** 广告模型的结构特征：公开、非抽象、类名对得上。 */
    private static boolean looksLikeAdModel(Class<?> clazz) {
        return "FeedAdvert".equals(clazz.getSimpleName())
                && java.lang.reflect.Modifier.isPublic(clazz.getModifiers())
                && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers());
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
        if (adCardView != null) {
            XposedBridge.hookMethod(adCardView, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!Helper.prefs.getBoolean("switch_mainswitch", false) || !Helper.prefs.getBoolean("switch_feedad", true)) {
                        return;
                    }
                    // 只拦焦点图那一种（FOCUS_VIEW = 1）：话题红包卡走 getAdCardView(String,int) 另一个重载，
                    // 将来这个重载若也支持别的类型，别顺手一起误伤。
                    if (!(param.args[1] instanceof Integer) || (Integer) param.args[1] != 1) {
                        return;
                    }
                    // 直接不给视图：feed 拿到 null 就跳过插入（setResult 之后回调也不会被调用）
                    param.setResult(null);
                }
            });
        }
    }

    private static boolean isFeedAdvert(Object item) {
        return item != null && FeedAdvert != null && FeedAdvert.isInstance(item);
    }
}
