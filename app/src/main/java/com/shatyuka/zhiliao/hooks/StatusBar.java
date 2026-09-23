package com.shatyuka.zhiliao.hooks;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.View;
import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import com.shatyuka.zhiliao.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class StatusBar implements IHook {
    static Class<?> CombinedDrawable;
    static Class<?> StatusBarDrawable;
    static Class<?> ThemeChangedEvent;

    static Method setColor;

    static Field CombinedDrawable_statusBarDrawable;

    @Override
    public String getName() {
        return "状态栏沉浸";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        Helper.requireTarget(Helper.AnswerPagerFragment, "com.zhihu.android.answer.module.pager.AnswerPagerFragment");
        Helper.findClass(classLoader, "com.zhihu.android.base.util.", 0, 2,
                (Class<?> StatusBarUtil) -> {
                    String className = StatusBarUtil.getName();
                    Class<?> combinedDrawable = classLoader.loadClass(className + "$a");
                    Class<?> statusBarDrawable = classLoader.loadClass(className + "$b");
                    if (!LayerDrawable.class.isAssignableFrom(combinedDrawable) || !Drawable.class.isAssignableFrom(statusBarDrawable))
                        return false;
                    // n0$a 里 k(=n0$b) 才是 StatusBarDrawable，j 是 origin Drawable，按字段名取会绑错接收者
                    if (Helper.findFieldByType(combinedDrawable, statusBarDrawable) == null)
                        return false;
                    CombinedDrawable = combinedDrawable;
                    StatusBarDrawable = statusBarDrawable;
                    return true;
                });
        if (CombinedDrawable == null)
            throw new ClassNotFoundException("com.zhihu.android.base.util.StatusBarUtil$CombinedDrawable");

        ThemeChangedEvent = classLoader.loadClass("com.zhihu.android.app.event.ThemeChangedEvent");

        setColor = StatusBarDrawable.getMethod("a", int.class);

        CombinedDrawable_statusBarDrawable = Helper.findFieldByType(CombinedDrawable, StatusBarDrawable);
        if (CombinedDrawable_statusBarDrawable == null)
            throw new NoSuchFieldException(CombinedDrawable.getName() + ".statusBarDrawable");
    }

    @Override
    public void hook() throws Throwable {
        XposedHelpers.findAndHookConstructor(StatusBarDrawable, int.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_statusbar", false))
                    param.args[0] = getStatusbarColor();
            }
        });
        XposedBridge.hookMethod(setColor, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_statusbar", false))
                    param.args[0] = getStatusbarColor();
            }
        });
        XposedHelpers.findAndHookMethod(Helper.AnswerPagerFragment, "onViewCreated", View.class, Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_statusbar", false))
                    if (param.args[0] instanceof View)
                        ((View) param.args[0]).setBackgroundColor(getStatusbarColor());
            }
        });

        XposedHelpers.findAndHookConstructor(ThemeChangedEvent, int.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_statusbar", false)) {
                    if (Helper.settingsView != null) {
                        Object background = ((View) Helper.settingsView).getBackground();
                        if (CombinedDrawable.isInstance(background)) {
                            Object statusBarDrawable = CombinedDrawable_statusBarDrawable.get(background);
                            if (StatusBarDrawable.isInstance(statusBarDrawable))
                                setColor.invoke(statusBarDrawable, 0);
                        }
                    }
                }
            }
        });
    }

    static int getStatusbarColor() {
        if (Helper.getDarkMode())
            return 0xFF121212;
        else
            return 0xFFFFFFFF;
    }
}
