package com.shatyuka.zhiliao.hooks;

import android.view.View;
import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

public class NavButton implements IHook {
    static Class<?> BottomNavMenuView;
    static Class<?> IMenuItem;

    static Method getItemId;
    static Method getTab;

    static Field Tab_tabView;

    @Override
    public String getName() {
        return "隐藏导航栏按钮";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        BottomNavMenuView = classLoader.loadClass("com.zhihu.android.bottomnav.core.BottomNavMenuView");

        Class<?> tabLayoutTabClass = classLoader.loadClass("com.google.android.material.tabs.TabLayout$Tab");
        // e(r.g) 与 setMenuUiTestId(r.g) 参数相同，只有前者返回 Tab，按返回类型筛选
        getTab = Helper.requireTarget(Arrays.stream(BottomNavMenuView.getDeclaredMethods())
                .filter(method -> method.getReturnType() == tabLayoutTabClass && method.getParameterCount() == 1)
                .findFirst().orElse(null), "BottomNavMenuView.getTab");
        getTab.setAccessible(true);
        IMenuItem = getTab.getParameterTypes()[0];

        // 菜单项 id 的方法名在不同渠道包里不一致，按"无参且返回 String"结构定位（优先名字含 id 的）
        getItemId = Helper.requireTarget(Arrays.stream(IMenuItem.getDeclaredMethods())
                .filter(method -> method.getReturnType() == String.class && method.getParameterCount() == 0)
                .min(Comparator.comparingInt(method -> method.getName().toLowerCase().contains("id") ? 0 : 1))
                .orElse(null), "IMenuItem.getItemId");
        getItemId.setAccessible(true);

        Tab_tabView = Helper.requireTarget(tabLayoutTabClass.getField("view"), "TabLayout$Tab.view");
    }

    @Override
    public void hook() throws Throwable {
        if (Helper.prefs.getBoolean("switch_mainswitch", false) && (Helper.prefs.getBoolean("switch_homenav", false) || Helper.prefs.getBoolean("switch_findnav", false) || Helper.prefs.getBoolean("switch_panelnav", false) || Helper.prefs.getBoolean("switch_messagenav", false) || Helper.prefs.getBoolean("switch_profilenav", false))) {
            XposedBridge.hookMethod(getTab, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args == null || param.args.length == 0 || !IMenuItem.isInstance(param.args[0])) return;
                    if (!isHidden(getItemId.invoke(param.args[0]))) return;

                    Object tab = param.getResult();
                    if (tab == null || !Tab_tabView.getDeclaringClass().isInstance(tab)) return;

                    View tabView = (View) Tab_tabView.get(tab);
                    if (tabView != null) {
                        tabView.setVisibility(View.GONE);
                    } else if (param.thisObject instanceof View) {
                        // Tab 的 View 在 addTab 时才创建，此时先返回，稍后再隐藏
                        ((View) param.thisObject).post(() -> hideTab(tab));
                    }
                }
            });
        }
    }

    // 11.10.0 底栏实际下发 home/find/panel/message/profile
    private static boolean isHidden(Object itemId) {
        if ("home".equals(itemId)) return Helper.prefs.getBoolean("switch_homenav", false);
        if ("find".equals(itemId)) return Helper.prefs.getBoolean("switch_findnav", false);
        if ("panel".equals(itemId)) return Helper.prefs.getBoolean("switch_panelnav", false);
        if ("message".equals(itemId)) return Helper.prefs.getBoolean("switch_messagenav", false);
        if ("profile".equals(itemId)) return Helper.prefs.getBoolean("switch_profilenav", false);
        return false;
    }

    private static void hideTab(Object tab) {
        if (!Tab_tabView.getDeclaringClass().isInstance(tab)) return;
        try {
            Object tabView = Tab_tabView.get(tab);
            if (tabView instanceof View) {
                ((View) tabView).setVisibility(View.GONE);
            }
        } catch (IllegalAccessException | IllegalArgumentException ignored) {
        }
    }
}
