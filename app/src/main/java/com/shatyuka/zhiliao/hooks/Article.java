package com.shatyuka.zhiliao.hooks;

import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.TargetResolver;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Article implements IHook {
    private static final String KEY_ADAPTER = "article.contentMixAdapter";

    /** 名单全落空时的结构化兜底：按历史上出现过的包名顺序试。 */
    private static final String[] LEGACY_ADAPTERS = {
            "com.zhihu.android.mix.a.a",
            "com.zhihu.android.mix.b.a",
            "com.zhihu.android.mix.adapter.a",
            "com.zhihu.android.mix.adapter.ContentMixAdapter",
    };

    static Class<?> ContentMixAdapter;
    static Class<?> ContentMixPagerFragment;

    static Method getItemCount;

    static Field ContentMixAdapter_fragment;
    static Field ContentMixPagerFragment_type;

    @Override
    public String getName() {
        return "精简文章页面";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        if (Helper.versionCode > 2614) {
            // 11.10.0 只剩 mix.adapter 包，mix.a.a / mix.b.a 已不存在
            ContentMixAdapter = TargetResolver.findClassByQualifiedCandidates(
                    classLoader, KEY_ADAPTER, Article::looksLikeContentMixAdapter);
            if (ContentMixAdapter != null) {
                getItemCount = ContentMixAdapter.getMethod("getItemCount");
                XposedBridge.log("[Zhiliao][自检] 精简文章页面 目标适配器=" + ContentMixAdapter.getName() + " 来源=候选名单");
            } else {
                // DexKit 那条兜底已明确弃用：这个适配器的 getItemCount 和 Fragment 字段都是继承来的，
                // dex 层面看不到（真机实测 0 命中），留着只会给人"有兜底"的错觉。实测记录见 ROADMAP 第三阶段。
                resolveLegacyAdapter(classLoader);
                XposedBridge.log("[Zhiliao][自检] 精简文章页面 目标适配器=" + ContentMixAdapter.getName() + " 来源=结构化兜底");
            }

            ContentMixPagerFragment = classLoader.loadClass("com.zhihu.android.mix.fragment.ContentMixPagerFragment");
            Class<?> Fragment = classLoader.loadClass("androidx.fragment.app.Fragment");
            ContentMixAdapter_fragment = Helper.findFieldByType(ContentMixAdapter, Fragment);
            if (ContentMixAdapter_fragment == null) {
                throw new NoSuchFieldException("fragment");
            }
            ContentMixAdapter_fragment.setAccessible(true);
            try {
                ContentMixPagerFragment_type = ContentMixPagerFragment.getField("c");
                if (ContentMixPagerFragment_type.getType() != String.class)
                    throw new NoSuchFieldException("type");
            } catch (NoSuchFieldException e) {
                ContentMixPagerFragment_type = ContentMixPagerFragment.getField("t");
                if (ContentMixPagerFragment_type.getType() != String.class)
                    throw new NoSuchFieldException("type");
            }
        }
    }

    /** 适配器的结构特征：无参 getItemCount() 返回 int，且持有一个 Fragment 字段。 */
    public static boolean looksLikeContentMixAdapter(Class<?> clazz) {
        try {
            Method method = clazz.getMethod("getItemCount");
            if (method.getReturnType() != int.class || method.getParameterCount() != 0) {
                return false;
            }
            Class<?> fragment = Class.forName("androidx.fragment.app.Fragment", false, clazz.getClassLoader());
            return Helper.findFieldByType(clazz, fragment) != null;
        } catch (Throwable e) {
            return false;
        }
    }

    private static void resolveLegacyAdapter(ClassLoader classLoader) throws Throwable {
        Throwable last = null;
        for (String name : LEGACY_ADAPTERS) {
            try {
                Class<?> clazz = classLoader.loadClass(name);
                Method method = clazz.getMethod("getItemCount");
                ContentMixAdapter = clazz;
                getItemCount = method;
                return;
            } catch (Throwable e) {
                last = e;
            }
        }
        throw last != null ? last : new ClassNotFoundException("ContentMixAdapter");
    }

    @Override
    public void hook() throws Throwable {
        if (Helper.versionCode > 2614) { // after 6.61.0
            if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_article", false)) {
                XposedBridge.hookMethod(getItemCount, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object thisObject = param.thisObject;
                        if (thisObject == null || thisObject.getClass() != ContentMixAdapter) {
                            return;
                        }
                        Object fragment = ContentMixAdapter_fragment.get(thisObject);
                        if (fragment == null) {
                            return;
                        }
                        Object type = ContentMixPagerFragment_type.get(fragment);
                        if ("article".equals(type)) {
                            param.setResult(1);
                        }
                    }
                });
            }
        }
    }
}



