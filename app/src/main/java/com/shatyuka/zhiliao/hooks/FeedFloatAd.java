package com.shatyuka.zhiliao.hooks;

import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 去浮层广告：首屏浮层、二屏浮层、搜索浮层、视频浮层、首页彩蛋，知乎把它们统称「特形广告」。
 *
 * 数据只拉一次 app_float_layer，再按 float_layer / second_float_layer / feed_egg /
 * search_middle_page 四个字段各存一份（ad.special.b 包里的单例配置）。展示前每个 presenter
 * 都要先问同一个继承来的 initData(String)「这个类型有没有数据」——没有数据就什么都不画，
 * 所以这里不碰布局、不碰图片，直接让它一律回答"没有"。
 *
 * 父类的类名在渠道包里是混淆的，presenter 名却是公开的，所以顺着 presenter 找继承来的公开方法；
 * 父类里还有个同签名的私有 isImageCached(String)，按签名瞎猜会挑错，必须认名字。
 *
 * 推荐页那个彩蛋（helper.AdFeedEggManager）走另一条线：自己往容器里塞视图，全是无参方法，
 * 靠"返回 void 且没有参数"认出来，全部拦掉等于这个彩蛋再也不会显形。
 */
public class FeedFloatAd implements IHook {

    /** 已知的特形广告 presenter，渠道包里名字都保留着。 */
    private static final String[] PRESENTERS = {
            "com.zhihu.android.ad.special.AdEgg",
            "com.zhihu.android.ad.special.AdFloat",
            "com.zhihu.android.ad.special.AdFloatSecond",
            "com.zhihu.android.ad.special.AdFloatVideo",
            "com.zhihu.android.ad.special.AdSearchFloatVideo",
    };

    static final List<Method> initDataMethods = new ArrayList<>();
    static final List<Method> eggHides = new ArrayList<>();

    @Override
    public String getName() {
        return "去浮层广告";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        initDataMethods.clear();
        eggHides.clear();
        for (String name : PRESENTERS) {
            Class<?> presenter;
            try {
                presenter = classLoader.loadClass(name);
            } catch (Throwable ignore) {
                continue;
            }
            Method method;
            try {
                method = presenter.getMethod("initData", String.class);
            } catch (Throwable ignore) {
                continue;
            }
            if (!initDataMethods.contains(method)) {
                initDataMethods.add(method);
            }
        }
        if (initDataMethods.isEmpty()) {
            XposedBridge.log("[Zhiliao][去浮层广告] 没有找到特形广告的 initData(String)");
        }

        try {
            Class<?> eggManager = classLoader.loadClass("com.zhihu.android.app.feed.ui.fragment.helper.AdFeedEggManager");
            for (Method method : eggManager.getDeclaredMethods()) {
                if (method.getReturnType() == void.class && method.getParameterTypes().length == 0) {
                    eggHides.add(method);
                }
            }
        } catch (Throwable e) {
            XposedBridge.log("[Zhiliao][去浮层广告] 没有推荐页彩蛋管理器: " + e);
        }

        if (initDataMethods.isEmpty() && eggHides.isEmpty()) {
            throw new NoSuchMethodException("浮层广告的两个入口都没找到");
        }
    }

    /** 单测用：挂到的必须是 initData，被同签名的 isImageCached 顶掉就说明解析退化了。 */
    public static String initDataName() {
        return initDataMethods.isEmpty() ? null : initDataMethods.get(0).getName();
    }

    @Override
    public void hook() throws Throwable {
        for (Method method : initDataMethods) {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_feedfloatad", true)) {
                        param.setResult(false);
                    }
                }
            });
        }
        for (Method method : eggHides) {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_feedfloatad", true)) {
                        param.setResult(null);
                    }
                }
            });
        }
    }
}
