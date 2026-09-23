package com.shatyuka.zhiliao.hooks;

import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import java.lang.reflect.Method;

public class FullScreen implements IHook {
    static Method ClearScreenToolBarView_enterClearScreen;
    static Method UnifyClearScreenToolBarView_enterClearScreen;
    static Method MixClearScreenToolBarView_enterClearScreen;
    static Method AnswerClearScreenToolBarView_enterClearScreen;

    @Override
    public String getName() {
        return "禁止进入全屏模式";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        // 11.10.0: 匿名类 ...clearscreen.d$c 已作废，改钩具名 public 方法 enterClearScreen()
        // 同一语义的 ToolBarView 在多个包各有一套实现，全部拦截（只拦 enter，不碰 exitClearScreen）
        ClearScreenToolBarView_enterClearScreen = findEnterClearScreen(classLoader,
                "com.zhihu.android.feature.short_container_feature.ui.widget.toolbar.clearscreen.ClearScreenToolBarView");
        UnifyClearScreenToolBarView_enterClearScreen = findEnterClearScreen(classLoader,
                "com.zhihu.android.feature.short_container_feature.ui.widget.toolbar.clearscreen.UnifyClearScreenToolBarView");
        MixClearScreenToolBarView_enterClearScreen = findEnterClearScreen(classLoader,
                "com.zhihu.android.mix.mixshort.clearscreen.ClearScreenToolBarView");
        AnswerClearScreenToolBarView_enterClearScreen = findEnterClearScreen(classLoader,
                "com.zhihu.android.answer.module.mixshort.clearscreen.AnswerClearScreenToolBarView");
        if (ClearScreenToolBarView_enterClearScreen == null && UnifyClearScreenToolBarView_enterClearScreen == null
                && MixClearScreenToolBarView_enterClearScreen == null && AnswerClearScreenToolBarView_enterClearScreen == null)
            throw new ClassNotFoundException("com.zhihu.android.*.clearscreen.ClearScreenToolBarView.enterClearScreen");
    }

    @Override
    public void hook() throws Throwable {
        XC_MethodHook blockEnterClearScreen = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_fullscreen", false))
                    param.setResult(null);
            }
        };
        Method[] targets = {
                ClearScreenToolBarView_enterClearScreen,
                UnifyClearScreenToolBarView_enterClearScreen,
                MixClearScreenToolBarView_enterClearScreen,
                AnswerClearScreenToolBarView_enterClearScreen
        };
        for (Method target : targets) {
            if (target != null)
                XposedBridge.hookMethod(target, blockEnterClearScreen);
        }
    }

    private static Method findEnterClearScreen(ClassLoader classLoader, String className) {
        try {
            return classLoader.loadClass(className).getDeclaredMethod("enterClearScreen");
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            return null;
        }
    }
}
