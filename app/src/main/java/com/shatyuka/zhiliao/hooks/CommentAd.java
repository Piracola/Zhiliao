package com.shatyuka.zhiliao.hooks;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.TargetResolver;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class CommentAd implements IHook {
    static Class<?> CommentListAd;
    static Class<?> ViewHolder;
    static Method resolveCommentAdParam;
    static Method bindCommentAd;
    static Field itemView;

    @Override
    public String getName() {
        return "去评论广告";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        CommentListAd = TargetResolver.findClassByQualifiedCandidates(
                classLoader, "commentAd.model", CommentAd::looksLikeCommentListAd);
        if (CommentListAd == null) {
            CommentListAd = loadLegacyModel(classLoader);
        }
        if (CommentListAd == null)
            return;

        if (Helper.MorphAdHelper != null) {
            resolveCommentAdParam = TargetResolver.findMethod(Helper.MorphAdHelper, false, 0,
                    method -> Modifier.isStatic(method.getModifiers())
                            && method.getReturnType() == boolean.class
                            && Arrays.equals(method.getParameterTypes(),
                            new Class<?>[]{Context.class, CommentListAd, Boolean.class}));
        }

        if (resolveCommentAdParam == null) {
            Class<?> holder;
            try {
                ViewHolder = classLoader.loadClass("androidx.recyclerview.widget.RecyclerView$ViewHolder");
                itemView = ViewHolder.getField("itemView");
                holder = TargetResolver.findClassByQualifiedCandidates(
                        classLoader, "commentAd.viewHolder", clazz -> looksLikeAdViewHolder(clazz, ViewHolder));
                if (holder == null) {
                    holder = classLoader.loadClass("com.zhihu.android.comment.holder.CommentDynamicAdViewHolderV70");
                }
            } catch (ClassNotFoundException | NoSuchFieldException ignored) {
                ViewHolder = null;
                itemView = null;
                return;
            }
            bindCommentAd = findBindMethod(holder);
        }
    }

    /** 广告模型的形状：公开、具体类。名字对不上时，结构上也只有这些可依。 */
    private static boolean looksLikeCommentListAd(Class<?> clazz) {
        try {
            int modifiers = clazz.getModifiers();
            return Modifier.isPublic(modifiers) && !Modifier.isAbstract(modifiers);
        } catch (Throwable e) {
            return false;
        }
    }

    /** 广告 ViewHolder 的形状：是 RecyclerView.ViewHolder 子类，且有一个吃 CommentListAd 的静态 void 方法。 */
    private static boolean looksLikeAdViewHolder(Class<?> clazz, Class<?> viewHolderBase) {
        try {
            if (clazz == viewHolderBase || !viewHolderBase.isAssignableFrom(clazz)) {
                return false;
            }
            for (Method method : clazz.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) && method.getReturnType() == void.class
                        && !method.isBridge() && !method.isSynthetic()
                        && Arrays.equals(method.getParameterTypes(), new Class<?>[]{CommentListAd})) {
                    return true;
                }
            }
            return false;
        } catch (Throwable e) {
            return false;
        }
    }

    private static Class<?> loadLegacyModel(ClassLoader classLoader) {
        for (String name : new String[]{"com.zhihu.android.api.model.CommentListAd",
                "com.zhihu.android.adbase.model.CommentListAd"}) {
            try {
                return classLoader.loadClass(name);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Method findBindMethod(Class<?> holder) {
        Method result = null;
        for (Method method : holder.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) || method.getReturnType() != void.class
                    || method.isBridge() || method.isSynthetic()
                    || !Arrays.equals(method.getParameterTypes(), new Class<?>[]{CommentListAd}))
                continue;
            if (result != null)
                return null;
            result = method;
        }
        if (result != null)
            result.setAccessible(true);
        return result;
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
}
