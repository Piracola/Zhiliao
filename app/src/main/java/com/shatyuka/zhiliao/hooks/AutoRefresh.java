package com.shatyuka.zhiliao.hooks;

import com.shatyuka.zhiliao.DexKitFallback;
import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.TargetResolver;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class AutoRefresh implements IHook {
    /**
     * 刷新来源名。11.10.0/11.11.0 的刷新入口 refresh(boolean, refreshType) 会用 refreshType 的
     * 一个无参 String 方法说明来源，已知取值：Pull / RefreshButton / Back / TabClick /
     * RefreshGuideButton（用户操作）、AppStart（冷启动自动刷新）、AutoRefreshBackground /
     * AutoRefreshDetailPage / AutoRefreshSearchPage / AutoRefreshOtherPage（回前台等）。
     *
     * 冷启动要分两步才算真的不刷新：
     * 1. 放行 AppStart 整条链路，让知乎把磁盘缓存渲染成上次那一屏；
     * 2. 在 postRefreshSucceed 丢掉随后的网络结果，同时掐掉这条网络结果写回本地缓存的那次写入。
     *
     * 第 2 步里"写回缓存"是以前一直漏掉的：请求本身在数据层执行，早于 postRefreshSucceed，
     * 于是屏幕看着没变，磁盘缓存却前进了一格，下次冷启动渲染的正是这一格。现在按
     * FeedLocalDataSource 的结构特征定位它的 void(FeedList) 写入方法，冷启动抑制期间直接跳过。
     *
     * 数据层的类名每个版本/渠道都不同（11.10.0 market 是 o0.x，小米是 b.b；
     * 11.11.0 官网是 q0.x，小米是 b.b），所以候选名单 + 结构校验 + DexKit 精确签名兜底，
     * 不写死任何一个名字。
     */
    private static final String TAB_CLICK = "TabClick";
    private static final String APP_START = "AppStart";
    private static final String AUTO_REFRESH_PREFIX = "AutoRefresh";

    /** 已知的用户主动刷新：放行并撤销冷启动抑制。 */
    private static final Set<String> USER_REFRESH = new java.util.HashSet<>(java.util.Arrays.asList(
            "Pull", "RefreshButton", "Back", "RefreshGuideButton"));

    static Method returnTopOrRefresh;
    static Method tryRefresh;
    static Method postRefreshSucceed;
    /** FeedLocalDataSource#void(FeedList)：网络结果写回推荐缓存 */
    static Method feedCacheWrite;
    static Class<?> FeedList;
    static Field FeedList_isCache;
    /** 刷新入口与其对应的来源名方法；不同重载的 refreshType 类型可能不同，两者按入口配对 */
    static RefreshEntry[] refreshEntries = new RefreshEntry[0];
    /** refreshType 的"是否用户主动刷新"判断（Pull/TabClick/Back/RefreshButton 返回 true） */
    static Method userRefreshMethod;

    private static final Set<String> loggedTypes = ConcurrentHashMap.newKeySet();
    /** 每个 feed fragment 一份：AppStart 放行后，用它判断缓存渲染完了没有 */
    private static final Map<Object, Suppression> suppressions = new WeakHashMap<>();

    /** 缓存写入被我们主动放行的一次性列表（首装没缓存时，把放行的那份写回磁盘）。 */
    private static final List<Object> persistAllowed = Collections.synchronizedList(new ArrayList<>(4));
    private static volatile boolean cacheBlockedLogged;
    private static volatile int cacheBlockedCount;

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
            findPostRefreshSucceed(feedFragment);
        }

        try {
            FeedList = classLoader.loadClass("com.zhihu.android.api.model.FeedList");
            FeedList_isCache = FeedList.getField("isCache");
        } catch (Throwable ignored) {
        }

        findFeedCacheWriter(classLoader);

        XposedBridge.log("[Zhiliao][自检] 禁止自动刷新 目标 返回顶部=" + (returnTopOrRefresh != null)
                + " 定时刷新=" + (tryRefresh != null)
                + " 刷新入口=" + refreshEntries.length
                + (refreshEntries.length == 0 ? "" : " 来源名=" + refreshEntries[0].typeName.getName())
                + " 用户刷新判定=" + (userRefreshMethod != null)
                + " 丢弃网络结果=" + canSuppressNetworkResult()
                + " 缓存冻结=" + (feedCacheWrite != null
                ? feedCacheWrite.getDeclaringClass().getName() + "#" + feedCacheWrite.getName() : "无"));
    }

    /**
     * 能不能在放行冷启动刷新之后把网络结果丢掉。没有这个回调就只能退回直接拦截，
     * 代价是首页整片空白——所以单测把它当断言。
     */
    public static boolean canSuppressNetworkResult() {
        return postRefreshSucceed != null;
    }

    /** 能不能连缓存写入一起冻结。找不到就退化为旧行为（屏幕不刷新，但缓存会前进一格）。 */
    public static boolean canFreezeCache() {
        return feedCacheWrite != null;
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
        if (postRefreshSucceed != null) {
            XposedBridge.hookMethod(postRefreshSucceed, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object list = param.args.length > 0 ? param.args[0] : null;
                    if (dropRefreshResult(param.thisObject, list)) {
                        param.setResult(null);
                    }
                }
            });
        }
        if (feedCacheWrite != null) {
            XposedBridge.hookMethod(feedCacheWrite, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object list = param.args.length > 0 ? param.args[0] : null;
                    if (cacheWriteAllowed(list)) {
                        if (isCacheList(list)) {
                            cacheRendered = true;
                        }
                        return;
                    }
                    param.setResult(null);
                    cacheBlockedCount++;
                    if (!cacheBlockedLogged) {
                        cacheBlockedLogged = true;
                        XposedBridge.log("[Zhiliao][禁止自动刷新] 已拦截网络结果写回缓存"
                                + "（" + feedCacheWrite.getDeclaringClass().getName() + "#"
                                + feedCacheWrite.getName() + "）");
                    }
                }
            });
        }
        for (RefreshEntry entry : refreshEntries) {
            XposedBridge.hookMethod(entry.entry, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length != 2 || param.args[1] == null) {
                        return;
                    }
                    String type = (String) entry.typeName.invoke(param.args[1]);
                    if (type == null) {
                        return;
                    }
                    boolean userRefresh = isUserRefresh(param.args[1], type);
                    if (APP_START.equals(type) && canSuppressNetworkResult()) {
                        allowColdStart(param.thisObject, type);
                        return;
                    }
                    if (APP_START.equals(type) || TAB_CLICK.equals(type)
                            || type.startsWith(AUTO_REFRESH_PREFIX) || !userRefresh) {
                        param.setResult(null);
                        if (loggedTypes.add(type)) {
                            XposedBridge.log("[Zhiliao][禁止自动刷新] 来源 " + type + " → 已拦截");
                        }
                        return;
                    }
                    if (loggedTypes.add(type)) {
                        XposedBridge.log("[Zhiliao][禁止自动刷新] 来源 " + type + " → 放行");
                    }
                    // 用户主动刷新（Pull / RefreshButton / Back / 刷新引导按钮）：撤销冷启动抑制，新内容照常进来
                    unfreeze(param.thisObject);
                }
            });
        }
    }

    /** 冷启动刷新放行前登记状态：缓存内容照渲染，之后的所有网络结果丢弃，直到用户自己刷新。 */
    private static void allowColdStart(Object fragment, String type) {
        synchronized (suppressions) {
            Suppression state = suppressions.get(fragment);
            if (state == null) {
                state = new Suppression();
                suppressions.put(fragment, state);
            }
            state.active = true;
            state.cacheSeen = false;
            state.dropped = 0;
            state.type = type;
        }
        cacheRendered = false;
        if (loggedTypes.add(type)) {
            XposedBridge.log("[Zhiliao][禁止自动刷新] 来源 " + type
                    + " → 放行（缓存照渲染，网络结果与缓存写入一起丢弃）");
        }
    }

    private static void unfreeze(Object fragment) {
        synchronized (suppressions) {
            suppressions.remove(fragment);
        }
        cacheRendered = false;
    }

    /**
     * 是否丢弃这次刷新结果。返回 false 表示放行：
     * 带 isCache 标记的缓存内容，或者抑制刚开始、还没渲染过任何内容（首装没有缓存时不能白屏）。
     * 抑制一旦生效就保持到用户主动刷新为止，网络结果晚到多久都进不来。
     */
    private static boolean dropRefreshResult(Object fragment, Object list) {
        synchronized (suppressions) {
            Suppression state = suppressions.get(fragment);
            if (state == null || !state.active) {
                return false;
            }
            if (isCacheList(list)) {
                state.cacheSeen = true;
                cacheRendered = true;
                XposedBridge.log("[Zhiliao][禁止自动刷新] 渲染 " + state.type + " 的缓存内容");
                return false;
            }
            if (!state.cacheSeen && !cacheRendered) {
                state.cacheSeen = true;
                XposedBridge.log("[Zhiliao][禁止自动刷新] 没有缓存可渲染，放行第一份结果并写回缓存");
                persistAllowed(list);
                return false;
            }
            state.dropped++;
            if (state.dropped <= 3) {
                XposedBridge.log("[Zhiliao][禁止自动刷新] 丢弃 " + state.type + " 的网络结果（第 "
                        + state.dropped + " 份）");
            }
            return true;
        }
    }

    /** 缓存写入是否放行：缓存内容本身照写，用户刷新后的新内容照写，只有抑制期间新拉回来的网络结果要拦。 */
    private static boolean cacheWriteAllowed(Object list) {
        if (list == null || FeedList == null || !FeedList.isInstance(list) || isCacheList(list)) {
            return true;
        }
        if (!hasActiveSuppression()) {
            return true;
        }
        synchronized (persistAllowed) {
            for (Object allowed : persistAllowed) {
                if (allowed == list) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 首装没有缓存时，放行的那份网络结果要主动写回缓存，否则下次冷启动没有内容可渲染。
     * 写入方法内部可能是异步的，用对象身份做一次性放行标记，不依赖线程。
     */
    private static void persistAllowed(Object list) {
        if (list == null || FeedList == null || !FeedList.isInstance(list) || feedCacheWrite == null) {
            return;
        }
        synchronized (persistAllowed) {
            if (persistAllowed.size() > 4) {
                persistAllowed.clear();
            }
            persistAllowed.add(list);
        }
        Object instance = cacheWriterInstance();
        if (instance == null) {
            return;
        }
        new Thread(() -> {
            if (!hasActiveSuppression()) {
                return;
            }
            try {
                feedCacheWrite.invoke(instance, list);
            } catch (Throwable e) {
                XposedBridge.log("[Zhiliao][禁止自动刷新] 写回缓存失败: " + e);
            }
        }, "ZhiliaoFeedCachePersist").start();
    }

    private static Object cacheWriterInstance() {
        try {
            java.lang.reflect.Constructor<?> constructor = feedCacheWrite.getDeclaringClass().getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Throwable e) {
            return null;
        }
    }

    private static volatile boolean cacheRendered;

    private static boolean hasActiveSuppression() {
        synchronized (suppressions) {
            for (Suppression state : suppressions.values()) {
                if (state.active) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 来源名已知时按名单走，未知来源用知乎自己的"是否用户触发"判断，避免新来源被当成主动刷新放行。 */
    private static boolean isUserRefresh(Object refreshType, String type) {
        if (USER_REFRESH.contains(type)) {
            return true;
        }
        if (TAB_CLICK.equals(type) || type.startsWith(AUTO_REFRESH_PREFIX) || APP_START.equals(type)) {
            return false;
        }
        Method method = userRefreshMethod;
        if (method != null) {
            try {
                return Boolean.TRUE.equals(method.invoke(refreshType));
            } catch (Throwable ignored) {
            }
        }
        return true;
    }

    private static boolean isCacheList(Object list) {
        if (list == null || FeedList == null || FeedList_isCache == null || !FeedList.isInstance(list)) {
            return false;
        }
        try {
            return FeedList_isCache.getBoolean(list);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Class<?> loadClass(ClassLoader classLoader, String name) {
        try {
            return classLoader.loadClass(name);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    /**
     * 定位 FeedLocalDataSource 的 void(FeedList) 写入方法。
     *
     * 先试候选名单（q0.x / b.b / o0.x 三个已知构建），再做结构校验：同一个类里要有
     * "接收缓存回调接口" 的方法、无参 void（清缓存）和 String 字段（缓存键）。
     * 名单落空时用 DexKit 按"参数恰好是 FeedList、返回 void、限定 feed 包"扫一遍，
     * 同样要过结构校验，并且必须唯一命中。
     */
    private static void findFeedCacheWriter(ClassLoader classLoader) {
        if (FeedList == null) {
            return;
        }
        try {
            Class<?> clazz = TargetResolver.findClassByQualifiedCandidates(classLoader,
                    "autoRefresh.feedCacheWriter", AutoRefresh::looksLikeLocalFeedStore);
            if (clazz != null) {
                feedCacheWrite = findVoidFeedListMethod(clazz);
            }
            if (feedCacheWrite != null) {
                return;
            }
            List<Method> candidates = DexKitFallback.findMethodsBySignature(
                    new String[]{"com.zhihu.android.app.feed"},
                    new Class<?>[]{FeedList}, void.class, 64, classLoader);
            Method unique = null;
            for (Method method : candidates) {
                if (!looksLikeLocalFeedStore(method.getDeclaringClass())) {
                    continue;
                }
                if (unique != null) {
                    XposedBridge.log("[Zhiliao][禁止自动刷新] 缓存写入候选不唯一，放弃冻结缓存");
                    return;
                }
                unique = method;
            }
            if (unique != null) {
                feedCacheWrite = unique;
                XposedBridge.log("[Zhiliao][禁止自动刷新] 缓存写入由 DexKit 兜底定位: "
                        + unique.getDeclaringClass().getName() + "#" + unique.getName());
            }
        } catch (Throwable e) {
            XposedBridge.log("[Zhiliao][禁止自动刷新] 缓存写入定位失败: " + e);
        }
    }

    private static Method findVoidFeedListMethod(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getReturnType() == void.class && method.getParameterCount() == 1
                    && method.getParameterTypes()[0] == FeedList) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    /**
     * FeedLocalDataSource 的结构指纹，刻意收得很窄：
     * - 一个 void(FeedList) 写入方法；
     * - 一个 void(回调接口) 读取方法，回调接口恰好有 onSuccess(Object) 和 onError(Throwable)；
     * - 一个无参 void（删除缓存）；
     * - 一个 String 字段（缓存键）。
     */
    private static boolean looksLikeLocalFeedStore(Class<?> clazz) {
        if (clazz == null || FeedList == null) {
            return false;
        }
        try {
            if (findVoidFeedListMethod(clazz) == null) {
                return false;
            }
            boolean callback = false;
            boolean noArg = false;
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getReturnType() != void.class) {
                    continue;
                }
                if (method.getParameterCount() == 0) {
                    noArg = true;
                } else if (method.getParameterCount() == 1 && method.getParameterTypes()[0].isInterface()
                        && looksLikeCacheCallback(method.getParameterTypes()[0])) {
                    callback = true;
                }
            }
            if (!callback || !noArg) {
                return false;
            }
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType() == String.class) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean looksLikeCacheCallback(Class<?> type) {
        Method[] methods = type.getMethods();
        if (methods.length != 2) {
            return false;
        }
        boolean success = false;
        boolean failure = false;
        for (Method method : methods) {
            if (method.getReturnType() != void.class || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> parameter = method.getParameterTypes()[0];
            if (parameter == Object.class) {
                success = true;
            } else if (parameter == Throwable.class) {
                failure = true;
            }
        }
        return success && failure;
    }

    /** 返回顶部或刷新：public int xxx(boolean)，老版本叫 returnTopOrRefresh，新版本被混淆成 a */
    private static void findReturnTopOrRefresh(Class<?> clazz) {
        // 名单优先（assets/candidates-v1.json，云端可追加），验证器兜底防误绑
        Method byName = TargetResolver.findMethodByCandidates(clazz, "autoRefresh.returnTopOrRefresh",
                true, method -> method.getReturnType() == int.class
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == boolean.class);
        if (byName != null) {
            returnTopOrRefresh = byName;
            return;
        }
        // 无名单（或名单落空）时的结构化解析：public int xxx(boolean)
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
        List<RefreshEntry> entries = new ArrayList<>();
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
            if (userRefreshMethod == null) {
                userRefreshMethod = findUserRefreshMethod(types[1]);
            }
            entries.add(new RefreshEntry(method, name));
        }
        refreshEntries = entries.toArray(new RefreshEntry[0]);
    }

    /** 刷新成功回调：void xxx(ZHObjectList)，缓存内容和网络内容都从这里进列表 */
    private static void findPostRefreshSucceed(Class<?> feedFragment) {
        Method byName = TargetResolver.findMethodByCandidates(feedFragment, "autoRefresh.postRefreshSucceed",
                false, method -> method.getReturnType() == void.class && method.getParameterCount() == 1);
        if (byName != null) {
            postRefreshSucceed = byName;
            return;
        }
        for (Method method : feedFragment.getDeclaredMethods()) {
            if (method.getReturnType() == void.class && method.getParameterCount() == 1
                    && "postRefreshSucceed".equals(method.getName())) {
                postRefreshSucceed = method;
                return;
            }
        }
    }

    static class RefreshEntry {
        final Method entry;
        final Method typeName;

        RefreshEntry(Method entry, Method typeName) {
            this.entry = entry;
            this.typeName = typeName;
        }
    }

    static class Suppression {
        boolean active;
        boolean cacheSeen;
        int dropped;
        String type;
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

    /** refreshType 的 public boolean xxx()：Pull/TabClick/Back/RefreshButton 为 true */
    private static Method findUserRefreshMethod(Class<?> refreshType) {
        for (Method method : refreshType.getMethods()) {
            if (method.getParameterCount() == 0 && method.getReturnType() == boolean.class
                    && Modifier.isPublic(method.getModifiers())
                    && method.getDeclaringClass() != Object.class) {
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
