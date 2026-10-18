package com.shatyuka.zhiliao;

import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.ClassDataList;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.MethodDataList;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.shatyuka.zhiliao.xposed.XposedBridge;

/**
 * DexKit 兜底：候选名单和结构特征都没找到目标时，才轮到它直接扫 dex。
 *
 * 规则从严，宁可找不到也不猜：
 * - 必须限定搜索包名（只允许 com.zhihu.android 下），绝不整包乱扫；
 * - 必须至少给一个运行期字符串作为锚点（usingStrings），纯结构查询一律拒绝；
 * - 结果数超过上限直接判失败（命中一堆说明条件太松，这时候用哪个都是赌博）。
 *
 * 扫描完立刻 close，不让它挂在知乎的整个生命周期里。
 */
public final class DexKitFallback {

    /** 一次查询最多接受几个候选，超过就认为条件太松。 */
    private static final int MAX_RESULTS = 8;
    /** 精确签名查询允许多一点候选（调用方还要按类结构再筛），但仍有上限。 */
    private static final int MAX_SIGNATURE_CANDIDATES = 64;
    private static final String[] ALLOWED_PACKAGE_PREFIX = {"com.zhihu.android"};

    private static boolean loadAttempted = false;
    private static boolean loaded = false;
    private static String loadError = "";

    private DexKitFallback() {
    }

    /**
     * 把 dexkit 的 .so 从模块 APK 里加载进来。DexKit 2.x 不自动加载原生库，
     * 而模块跑在知乎进程里，知乎自己的 nativeLibraryDirectories 里没有它，只能按绝对路径 load。
     */
    private static synchronized boolean ensureLoaded() {
        if (loadAttempted) {
            return loaded;
        }
        loadAttempted = true;
        String modulePath = MainHook.modulePath;
        if (modulePath == null) {
            loadError = "拿不到模块路径";
            return false;
        }
        String dir = modulePath.substring(0, modulePath.lastIndexOf('/'));
        String[] candidates = {
                modulePath + "!/lib/arm64-v8a/libdexkit.so",
                modulePath + "!/lib/armeabi-v7a/libdexkit.so",
                dir + "/lib/arm64/libdexkit.so",
                dir + "/lib/arm/libdexkit.so",
        };
        for (String path : candidates) {
            try {
                System.load(path);
                loaded = true;
                XposedBridge.log("[Zhiliao][兜底] DexKit 原生库已加载：" + path);
                return true;
            } catch (Throwable ignored) {
            }
        }
        loadError = "libdexkit.so 加载失败";
        XposedBridge.log("[Zhiliao][兜底] " + loadError + "，本条兜底路径不可用");
        return false;
    }

    /**
     * 按"包名 + 运行期字符串"找类。找不到、超界、原生库没起来，一律返回空列表。
     *
     * @param usingStrings 至少一个运行期字符串；为空则拒绝执行
     * @param maxResults   期望的最大命中数，超过即放弃（上限被 MAX_RESULTS 再压一道）
     */
    public static List<String> findClasses(String usingStrings, int maxResults) {
        if (usingStrings == null || usingStrings.trim().isEmpty()) {
            XposedBridge.log("[Zhiliao][兜底] 拒绝无字符串锚点的查询");
            return new ArrayList<>();
        }
        if (!ensureLoaded()) {
            return new ArrayList<>();
        }
        int cap = Math.min(maxResults <= 0 ? 1 : maxResults, MAX_RESULTS);
        DexKitBridge bridge = null;
        try {
            // 扫的是宿主（知乎）的 APK，不是模块自己的 APK——扫错了自然一个都命中不了
            String hostApk = Helper.context.getApplicationInfo().sourceDir;
            bridge = DexKitBridge.Companion.create(hostApk);
            FindClass query = FindClass.create().searchPackages(ALLOWED_PACKAGE_PREFIX);
            query.matcher(ClassMatcher.create().usingStrings(usingStrings));
            ClassDataList list = bridge.findClass(query);
            Set<String> names = new LinkedHashSet<>();
            for (ClassData data : list) {
                names.add(data.getName());
                if (names.size() > cap) {
                    XposedBridge.log("[Zhiliao][兜底] 命中超过 " + cap + " 个，条件太松，放弃：" + usingStrings);
                    return new ArrayList<>();
                }
            }
            return new ArrayList<>(names);
        } catch (Throwable e) {
            XposedBridge.log("[Zhiliao][兜底] 查询失败 " + usingStrings + ": " + e);
            return new ArrayList<>();
        } finally {
            if (bridge != null) {
                try {
                    bridge.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * 按"精确签名"找方法。这里破例不要求字符串锚点：调用方已经把范围收窄到
     * "参数恰好是某个模型类、返回 void、限定在知乎自己的包"，结果还要再过一遍结构验证、
     * 并且必须唯一命中。用在那种类名每个渠道都不同、又没有共享明文字符串可用的数据层目标上
     * （FeedLocalDataSource 的缓存写入就是这一类：官网包的字符串是加密的，小米包是明文）。
     *
     * @param maxCandidates 还原成 Method 之前最多接受多少个候选项；超过直接放弃
     */
    public static List<Method> findMethodsBySignature(String[] packages, Class<?>[] paramTypes,
                                                      Class<?> returnType, int maxCandidates,
                                                      ClassLoader classLoader) {
        if (paramTypes == null || paramTypes.length == 0 || returnType == null) {
            XposedBridge.log("[Zhiliao][兜底] 拒绝空签名查询");
            return new ArrayList<>();
        }
        if (!ensureLoaded()) {
            return new ArrayList<>();
        }
        int cap = Math.min(maxCandidates <= 0 ? 1 : maxCandidates, MAX_SIGNATURE_CANDIDATES);
        DexKitBridge bridge = null;
        List<Method> out = new ArrayList<>();
        try {
            bridge = DexKitBridge.Companion.create(Helper.context.getApplicationInfo().sourceDir);
            FindMethod query = FindMethod.create().searchPackages(
                    packages == null || packages.length == 0 ? ALLOWED_PACKAGE_PREFIX : packages);
            MethodMatcher matcher = new MethodMatcher();
            matcher.paramTypes(paramTypes).paramCount(paramTypes.length).returnType(returnType);
            query.matcher(matcher);
            MethodDataList list = bridge.findMethod(query);
            for (MethodData data : list) {
                Method method = resolve(data, classLoader);
                if (method == null) {
                    continue;
                }
                out.add(method);
                if (out.size() > cap) {
                    XposedBridge.log("[Zhiliao][兜底] 签名查询命中超过 " + cap + " 个，条件太松，放弃");
                    return new ArrayList<>();
                }
            }
            return out;
        } catch (Throwable e) {
            XposedBridge.log("[Zhiliao][兜底] 签名查询失败: " + e);
            return new ArrayList<>();
        } finally {
            if (bridge != null) {
                try {
                    bridge.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 把 dex 里查到的方法还原成运行期 Method；类还没装载就 loadClass，装不上就放弃。 */
    private static Method resolve(MethodData data, ClassLoader classLoader) {
        try {
            Class<?> clazz = Class.forName(data.getDeclaredClassName(), false, classLoader);
            List<String> paramNames = data.getParamTypeNames();
            Class<?>[] types = new Class<?>[paramNames.size()];
            for (int i = 0; i < types.length; i++) {
                types[i] = resolveType(paramNames.get(i), classLoader);
                if (types[i] == null) {
                    return null;
                }
            }
            Method method = clazz.getDeclaredMethod(data.getMethodName(), types);
            method.setAccessible(true);
            return method;
        } catch (Throwable e) {
            return null;
        }
    }

    private static Class<?> resolveType(String name, ClassLoader classLoader) {
        switch (name) {
            case "void": return void.class;
            case "boolean": return boolean.class;
            case "byte": return byte.class;
            case "char": return char.class;
            case "short": return short.class;
            case "int": return int.class;
            case "long": return long.class;
            case "float": return float.class;
            case "double": return double.class;
            default: break;
        }
        if (name.endsWith("[]")) {
            Class<?> component = resolveType(name.substring(0, name.length() - 2), classLoader);
            return component == null ? null : java.lang.reflect.Array.newInstance(component, 0).getClass();
        }
        try {
            return Class.forName(name, false, classLoader);
        } catch (Throwable e) {
            return null;
        }
    }

    /** 诊断用：确认 APK 到底有没有被解析、包名过滤能不能命中。 */
    public static String probe() {
        if (!ensureLoaded()) {
            return "原生库不可用：" + loadError;
        }
        DexKitBridge bridge = null;

        String base;

        try {
            bridge = DexKitBridge.Companion.create(Helper.context.getApplicationInfo().sourceDir);
            int dexNum = bridge.getDexNum();
            FindClass byPackage = FindClass.create().searchPackages(ALLOWED_PACKAGE_PREFIX);
            int total = bridge.findClass(byPackage).size();
            FindClass byName = FindClass.create();
            byName.matcher(ClassMatcher.create().className("com.zhihu.android.adbase.model.FeedAdvert"));
            int named = bridge.findClass(byName).size();
            FindClass bySuper = FindClass.create().searchPackages(ALLOWED_PACKAGE_PREFIX);
            bySuper.matcher(ClassMatcher.create().superClass("androidx.recyclerview.widget.RecyclerView$Adapter"));
            ClassDataList adapters = bridge.findClass(bySuper);
            FindClass byStrings = FindClass.create().searchPackages(ALLOWED_PACKAGE_PREFIX);
            byStrings.matcher(ClassMatcher.create().usingStrings("Pull"));
            int pulls = bridge.findClass(byStrings).size();
            base = "dex=" + dexNum + " 包内类=" + total + " 按全名命中=" + named
                    + " RecyclerView.Adapter子类=" + adapters.size() + " 含\"Pull\"=" + pulls;
        } catch (Throwable e) {
            return "探测失败：" + e;
        } finally {
            if (bridge != null) {
                try {
                    bridge.close();
                } catch (Throwable ignored) {
                }
            }
        }
        return base;
    }

    public static boolean available() {
        return loaded;
    }

    public static String status() {
        return loaded ? "可用" : (loadError.isEmpty() ? "未尝试" : loadError);
    }
}




