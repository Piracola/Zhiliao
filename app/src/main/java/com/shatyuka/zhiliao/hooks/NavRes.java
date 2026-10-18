package com.shatyuka.zhiliao.hooks;

import android.view.View;
import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.TargetResolver;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XC_MethodReplacement;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import com.shatyuka.zhiliao.xposed.XposedHelpers;
import java.lang.reflect.Field;

public class NavRes implements IHook {
    static Class<?> BottomNav;
    static Class<?> FeedConfigManager;


    @Override
    public String getName() {
        return "导航栏样式";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        // 混淆名随渠道包变（a/b/e 都见过）：顺序交给候选名单，结构验证兜底
        BottomNav = TargetResolver.findClassByQualifiedCandidates(classLoader, "navRes.bottomNav",
                NavRes::looksLikeBottomNav);
        if (BottomNav == null) {
            BottomNav = loadLegacyBottomNav(classLoader);
        }
        FeedConfigManager = Helper.findClass(classLoader, "com.zhihu.android.", 0, 2,
                (Class<?> FeedConfigManager) -> {
                    Class<?>[] types = FeedConfigManager.getConstructors()[0].getParameterTypes();
                    return types.length == 6 && types[0] == String.class && types[1] == String.class && types[2] == long.class && types[3] == long.class && types[4] == types[5];
                });
    }

    private static boolean looksLikeBottomNav(Class<?> clazz) {
        try {
            return clazz.getDeclaredField("a").getType() == boolean.class;
        } catch (Throwable e) {
            return false;
        }
    }

    private static Class<?> loadLegacyBottomNav(ClassLoader classLoader) throws Throwable {
        Throwable last = null;
        for (String name : new String[]{"com.zhihu.android.bottomnav.a",
                "com.zhihu.android.bottomnav.b", "com.zhihu.android.bottomnav.e"}) {
            try {
                Class<?> clazz = classLoader.loadClass(name);
                if (looksLikeBottomNav(clazz)) {
                    return clazz;
                }
            } catch (Throwable e) {
                last = e;
            }
        }
        throw last != null ? last : new ClassNotFoundException("com.zhihu.android.bottomnav.BottomNav");
    }

    @Override
    public void hook() throws Throwable {
        if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_navres", false)) {
            XposedHelpers.findAndHookMethod(BottomNav, "b", XC_MethodReplacement.returnConstant(null));
            if (FeedConfigManager != null) {
                XposedBridge.hookAllConstructors(FeedConfigManager, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        param.args[3] = 0;
                    }
                });
            }
        }

    }
}
