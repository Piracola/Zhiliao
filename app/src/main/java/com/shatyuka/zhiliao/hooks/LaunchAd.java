package com.shatyuka.zhiliao.hooks;

import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.TargetResolver;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public class LaunchAd implements IHook {
    static Class<?> AdNetworkManager;

    static Method isShowLaunchAd;
    static Method requestLaunchAd;

    @Override
    public String getName() {
        return "去启动页广告";
    }

    @Override
    public void init(final ClassLoader classLoader) throws Throwable {
        AdNetworkManager = Helper.findClass(classLoader, "com.zhihu.android.sdk.launchad.",
                (Class<?> clazz) -> {
                    for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                        if (field.getType().getName().startsWith("okhttp3.")) {
                            return true;
                        }
                    }
                    return false;
                });
        if (AdNetworkManager == null) {
            throw new ClassNotFoundException("com.zhihu.android.sdk.launchad.AdNetworkManager");
        }
        Class<?> LaunchAdInterface = classLoader.loadClass("com.zhihu.android.ad.LaunchAdInterface");
        Class<?> LaunchAdHelper = Helper.findClass(classLoader, "com.zhihu.android.app.util.", 0, 32,
                (Class<?> clazz) -> {
                    return LaunchAdInterface.isAssignableFrom(clazz)
                            && clazz != LaunchAdInterface;
                });
        if (LaunchAdHelper == null) {
            throw new ClassNotFoundException("com.zhihu.android.app.util.LaunchAdHelper implements LaunchAdInterface");
        }
        isShowLaunchAd = LaunchAdHelper.getMethod("isShowLaunchAd");
        requestLaunchAd = TargetResolver.requireMethod(AdNetworkManager, false, 0,
                "requestLaunchAd(int,long,long,String)", method ->
                        method.getReturnType() == String.class
                                && java.util.Arrays.equals(method.getParameterTypes(), new Class<?>[]{int.class, long.class, long.class, String.class}));
    }

    @Override
    public void hook() throws Throwable {
        XposedBridge.hookMethod(isShowLaunchAd, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_launchad", true))
                    param.setResult(false);
            }
        });
        XposedBridge.hookMethod(requestLaunchAd, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_launchad", true)) {
                    param.setResult("");
                }
            }
        });
    }
}
