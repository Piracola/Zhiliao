package com.shatyuka.zhiliao.hooks;

import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AutoRefresh implements IHook {
    /**
     * 刷新来源名。11.10.0 的刷新入口 refresh(boolean, refreshType) 会用 refreshType 的一个
     * 无参 String 方法说明来源：Pull / TabClick / RefreshButton / AutoRefreshBackground ...
     */
    private static final String TAB_CLICK = "TabClick";
    private static final String AUTO_REFRESH_PREFIX = "AutoRefresh";

    static Method returnTopOrRefresh;
    static Method tryRefresh;
    static Method[] refreshEntries = new Method[0];
    /** 与 refreshEntries 一一对应：不同重载的 refreshType 类型可能不同，来源名方法必须按入口配对 */
    static Method[] refreshEntryTypeNames = new Method[0];

    private final Set<String> blockedTypes = ConcurrentHashMap.newKeySet();

    @Override
    public String getName() {
        return "禁止自动刷新";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        Class<?> baseTabChildFragment = loadClass(classLoader, "com.zhihu.android.app.feed.ui.fragment.BaseTabChildFragment");
        if (baseTabChildFragment != null) {
            findReturnTopOrRefresh(baseTabChildFragment);
        }

        Class<?> feedAutoRefreshManager = loadClass(classLoader, "com.zhihu.android.app.feed.util.FeedAutoRefreshManager");
        if (feedAutoRefreshManager != null) {
            findTryRefreshMethod(feedAutoRefreshManager);
        } else {
            // 部分渠道包把类名也混淆了，按方法签名在整个包里找
            Helper.findClass(classLoader, "com.zhihu.android.app.feed.util.", 0, 1, this::findTryRefreshMethod);
        }

        Class<?> feedFragment = loadClass(classLoader, "com.zhihu.android.app.feed.ui2.feed.FeedFragment");
        if (feedFragment != null) {
            findRefreshEntries(feedFragment);
        }

        XposedBridge.log("[Zhiliao][自检] 禁止自动刷新 目标 返回顶部=" + (returnTopOrRefresh != null)
                + " 定时刷新=" + (tryRefresh != null)
                + " 刷新入口=" + refreshEntries.length
                + (refreshEntryTypeNames.length == 0 ? "" : " 来源名=" + refreshEntryTypeNames[0].getName()));
    }

    @Override
    public void hook() throws Throwable {
        if (!Helper.prefs.getBoolean("switch_mainswitch", false) || !Helper.prefs.getBoolean("switch_autorefresh", false)) {
            return;
        }
        if (returnTopOrRefresh != null) {
            XposedBridge.hookMethod(returnTopOrRefresh, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    // 返回 1 表示「已在顶部」，调用方不会再刷新
                    if (param.args[0] instanceof Boolean && (boolean) param.args[0]) {
                        param.setResult(1);
                    }
                }
            });
        }
        if (tryRefresh != null) {
            XposedBridge.hookMethod(tryRefresh, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(null);
                }
            });
        }
        for (int i = 0; i < refreshEntries.length; i++) {
            final Method typeName = refreshEntryTypeNames[i];
            XposedBridge.hookMethod(refreshEntries[i], new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length != 2 || param.args[1] == null) {
                        return;
                    }
                    String type = (String) typeName.invoke(param.args[1]);
                    if (type == null || !isAutoRefresh(type)) {
                        return;
                    }
                    if (blockedTypes.add(type)) {
                        XposedBridge.log("[Zhiliao][禁止自动刷新] 已拦截 " + type);
                    }
                    param.setResult(null);
                }
            });
        }
    }

    private static boolean isAutoRefresh(String type) {
        return TAB_CLICK.equals(type) || type.startsWith(AUTO_REFRESH_PREFIX);
    }

    private static Class<?> loadClass(ClassLoader classLoader, String name) {
        try {
            return classLoader.loadClass(name);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    /** 返回顶部或刷新：public int xxx(boolean)，老版本叫 returnTopOrRefresh，新版本被混淆成 a */
    private static void findReturnTopOrRefresh(Class<?> clazz) {
        for (String name : new String[]{"returnTopOrRefresh", "a"}) {
            try {
                Method method = clazz.getMethod(name, boolean.class);
                if (method.getReturnType() == int.class) {
                    returnTopOrRefresh = method;
                    return;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }
        for (Method method : clazz.getMethods()) {
            if (method.getReturnType() == int.class && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == boolean.class) {
                returnTopOrRefresh = method;
                return;
            }
        }
    }

    /** 刷新入口：public void xxx(boolean, refreshType)，refreshType 是带来源名的抽象基类 */
    private static void findRefreshEntries(Class<?> feedFragment) {
        List<Method> entries = new ArrayList<>();
        List<Method> typeNames = new ArrayList<>();
        for (Method method : feedFragment.getDeclaredMethods()) {
            if (method.getReturnType() != void.class || method.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            if (types[0] != boolean.class || types[1].isInterface() || types[1] == Object.class) {
                continue;
            }
            Method name = findTypeName(types[1]);
            if (name == null) {
                continue;
            }
            entries.add(method);
            typeNames.add(name);
        }
        refreshEntries = entries.toArray(new Method[0]);
        refreshEntryTypeNames = typeNames.toArray(new Method[0]);
    }

    private static Method findTypeName(Class<?> refreshType) {
        for (Method method : refreshType.getMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType() == String.class
                    && Modifier.isPublic(method.getModifiers()) && !"toString".equals(method.getName())) {
                return method;
            }
        }
        return null;
    }

    /** 定时刷新：void(long, int, 回调接口, 抽象的参数类) */
    private boolean findTryRefreshMethod(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getReturnType() == void.class && method.getParameterCount() == 4 &&
                    method.getParameterTypes()[0] == long.class &&
                    method.getParameterTypes()[1] == int.class &&
                    (method.getParameterTypes()[2].getModifiers() & Modifier.INTERFACE) != 0 &&
                    (method.getParameterTypes()[3].getModifiers() & Modifier.ABSTRACT) != 0
            ) {
                method.setAccessible(true);
                tryRefresh = method;
                return true;
            }
        }
        return false;
    }
}
