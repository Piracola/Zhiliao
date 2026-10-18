package com.shatyuka.zhiliao.hooks;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import com.shatyuka.zhiliao.CandidateRegistry;
import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.TargetResolver;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class CommentAd implements IHook {
    private static final String FEATURE = "去评论广告";
    /** 广告卡片模型的基类：渠道间只换包名，顺序交给候选名单，名单不可用时退回两个已知包名。 */
    private static final String[] FALLBACK_AD_BASES = {
            "com.zhihu.android.api.model.FeedAdvert",
            "com.zhihu.android.adbase.model.FeedAdvert",
    };
    private static final String[] LEGACY_MODELS = {
            "com.zhihu.android.api.model.CommentListAd",
            "com.zhihu.android.adbase.model.CommentListAd",
    };
    private static final String[] LEGACY_HOLDERS = {
            "com.zhihu.android.comment.holder.CommentDynamicAdViewHolderV70",
    };

    static Class<?> CommentListAd;
    static Class<?> ViewHolder;
    static Method resolveCommentAdParam;
    static Method bindCommentAd;
    static Field itemView;

    @Override
    public String getName() {
        return FEATURE;
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        try {
            ViewHolder = classLoader.loadClass("androidx.recyclerview.widget.RecyclerView$ViewHolder");
            itemView = ViewHolder.getField("itemView");
        } catch (Throwable ignored) {
            ViewHolder = null;
            itemView = null;
        }

        CommentListAd = TargetResolver.findClassByQualifiedCandidates(
                classLoader, "commentAd.model", CommentAd::looksLikeCommentListAd);
        if (CommentListAd == null) {
            CommentListAd = loadLegacyModel(classLoader);
        }
        if (CommentListAd == null) {
            return;
        }

        if (Helper.MorphAdHelper != null) {
            resolveCommentAdParam = TargetResolver.findMethod(Helper.MorphAdHelper, false, 0,
                    method -> Modifier.isStatic(method.getModifiers())
                            && method.getReturnType() == boolean.class
                            && Arrays.equals(method.getParameterTypes(),
                            new Class<?>[]{Context.class, CommentListAd, Boolean.class}));
        }

        if (resolveCommentAdParam == null && ViewHolder != null) {
            Class<?> holder = TargetResolver.findClassByQualifiedCandidates(
                    classLoader, "commentAd.viewHolder", clazz -> looksLikeAdViewHolder(clazz, ViewHolder));
            if (holder == null) {
                holder = loadLegacyHolder(classLoader);
            }
            if (holder != null) {
                bindCommentAd = findBindMethod(holder);
            }
        }

        XposedBridge.log("[Zhiliao][自检] 去评论广告 模型=" + CommentListAd.getName()
                + " 参数拦截=" + (resolveCommentAdParam != null)
                + " 绑定=" + (bindCommentAd == null ? "无"
                : bindCommentAd.getDeclaringClass().getName() + "#" + bindCommentAd.getName()));
    }

    /**
     * 评论广告模型的形状：公开具体类，而且确实是广告卡片模型（FeedAdvert 的子类）。
     * 原来只验"公开且非抽象"，等于没验——名单里任何一个同名类都会被当成广告模型。
     */
    private static boolean looksLikeCommentListAd(Class<?> clazz) {
        return looksLikeAdCardModel(clazz, advertBaseNames());
    }

    /** 基类名单作为参数传入，单测才能用假类验证"只认 FeedAdvert 的子类"这条规则。 */
    static boolean looksLikeAdCardModel(Class<?> clazz, String[] baseNames) {
        try {
            int modifiers = clazz.getModifiers();
            if (!Modifier.isPublic(modifiers) || Modifier.isAbstract(modifiers)
                    || clazz.isInterface() || clazz.isEnum()) {
                return false;
            }
            ClassLoader classLoader = clazz.getClassLoader();
            for (String name : baseNames) {
                Class<?> base = loadOptional(classLoader, name);
                if (base != null && base != clazz && base.isAssignableFrom(clazz)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable e) {
            return false;
        }
    }

    private static String[] advertBaseNames() {
        String[] fromRegistry = CandidateRegistry.candidates("feedAd.feedAdvert");
        if (fromRegistry != null && fromRegistry.length > 0) {
            return fromRegistry;
        }
        return FALLBACK_AD_BASES;
    }

    /** 广告 ViewHolder：RecyclerView.ViewHolder 的具体子类，且有一个只吃该模型的 void 绑定方法。 */
    static boolean looksLikeAdViewHolder(Class<?> clazz, Class<?> viewHolderBase) {
        try {
            if (clazz == null || clazz == viewHolderBase || !viewHolderBase.isAssignableFrom(clazz)
                    || Modifier.isAbstract(clazz.getModifiers())) {
                return false;
            }
            return findBindMethod(clazz) != null;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 绑定方法：实例方法、返回 void、参数恰好是评论广告模型，并且唯一。
     *
     * 这里收实例方法是有原因的：知乎真正做绑定的是 V70 的 J1(CommentListAd)，它是实例方法，
     * hook 里又靠 thisObject 取 itemView。旧代码只收 static，两条要求互相矛盾，
     * 于是 11.10 的渠道包上这条 Hook 装了但一次都不会进来。
     */
    private static Method findBindMethod(Class<?> holder) {
        Method result = null;
        for (Method method : holder.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isAbstract(modifiers)
                    || method.getReturnType() != void.class
                    || method.isBridge() || method.isSynthetic()
                    || !Arrays.equals(method.getParameterTypes(), new Class<?>[]{CommentListAd})) {
                continue;
            }
            if (result != null) {
                return null;    // 不唯一就宁可不挂
            }
            result = method;
        }
        if (result != null) {
            result.setAccessible(true);
        }
        return result;
    }

    private static Class<?> loadLegacyModel(ClassLoader classLoader) {
        for (String name : LEGACY_MODELS) {
            Class<?> clazz = loadOptional(classLoader, name);
            if (clazz != null && looksLikeCommentListAd(clazz)) {
                return clazz;
            }
        }
        return null;
    }

    private static Class<?> loadLegacyHolder(ClassLoader classLoader) {
        for (String name : LEGACY_HOLDERS) {
            Class<?> clazz = loadOptional(classLoader, name);
            if (clazz != null && looksLikeAdViewHolder(clazz, ViewHolder)) {
                return clazz;
            }
        }
        return null;
    }

    private static Class<?> loadOptional(ClassLoader classLoader, String name) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (Throwable e) {
            return null;
        }
    }

    @Override
    public void hook() throws Throwable {
        if (resolveCommentAdParam != null) {
            XposedBridge.hookMethod(resolveCommentAdParam, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (isEnabled()) {
                        param.setResult(false);
                    }
                }
            });
        }
        if (bindCommentAd != null && ViewHolder != null && itemView != null) {
            XposedBridge.hookMethod(bindCommentAd, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws IllegalAccessException {
                    if (!isEnabled() || param.thisObject == null || !ViewHolder.isInstance(param.thisObject)) {
                        return;
                    }
                    Object value = itemView.get(param.thisObject);
                    if (!(value instanceof View)) {
                        return;
                    }
                    View view = (View) value;
                    view.setVisibility(View.GONE);
                    ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
                    if (layoutParams != null) {
                        layoutParams.height = 0;
                        view.setLayoutParams(layoutParams);
                    }
                    param.setResult(null);
                }
            });
        }
    }

    private static boolean isEnabled() {
        return Helper.prefs.getBoolean("switch_mainswitch", false)
                && Helper.prefs.getBoolean("switch_commentad", true);
    }

    /** 模型解析到了没有（单测断言用）。 */
    public static boolean hasModel() {
        return CommentListAd != null;
    }

    /** 有没有任何真实的拦截路径：参数解析或 ViewHolder 绑定，至少得有一条。 */
    public static boolean canIntercept() {
        return resolveCommentAdParam != null || bindCommentAd != null;
    }

    /** 有没有 ViewHolder 绑定这一条路径。 */
    public static boolean hasBindMethod() {
        return bindCommentAd != null;
    }

    /** 绑定方法必须是实例方法，hook 才能从 thisObject 拿到 itemView。 */
    public static boolean bindCarriesHolder() {
        return bindCommentAd != null && !Modifier.isStatic(bindCommentAd.getModifiers());
    }
}
