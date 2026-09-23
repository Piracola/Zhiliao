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
        try {
            CommentListAd = classLoader.loadClass("com.zhihu.android.api.model.CommentListAd");
        } catch (ClassNotFoundException e) {
            try {
                CommentListAd = classLoader.loadClass("com.zhihu.android.adbase.model.CommentListAd");
            } catch (ClassNotFoundException ignored) {
            }
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
                holder = classLoader.loadClass("com.zhihu.android.comment.holder.CommentDynamicAdViewHolderV70");
                ViewHolder = classLoader.loadClass("androidx.recyclerview.widget.RecyclerView$ViewHolder");
                itemView = ViewHolder.getField("itemView");
            } catch (ClassNotFoundException | NoSuchFieldException ignored) {
                ViewHolder = null;
                itemView = null;
                return;
            }
            bindCommentAd = findBindMethod(holder);
        }
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
