package com.shatyuka.zhiliao.hooks;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.TargetResolver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public class CommentAd implements IHook {
    static Class<?> CommentListAd;
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
            CommentListAd = classLoader.loadClass("com.zhihu.android.adbase.model.CommentListAd");
        }

        if (Helper.MorphAdHelper != null) {
            resolveCommentAdParam = TargetResolver.findMethod(Helper.MorphAdHelper, false, 0,
                    method -> Modifier.isStatic(method.getModifiers())
                            && method.getReturnType() == boolean.class
                            && Arrays.equals(method.getParameterTypes(),
                            new Class<?>[]{Context.class, CommentListAd, Boolean.class}));
        }

        if (resolveCommentAdParam == null) {
            Class<?> holder = classLoader.loadClass("com.zhihu.android.comment.holder.CommentDynamicAdViewHolderV70");
            bindCommentAd = TargetResolver.requireMethod(holder, false, 0,
                    "onBindData(CommentListAd)", method -> method.getReturnType() == void.class
                            && Arrays.equals(method.getParameterTypes(), new Class<?>[]{CommentListAd}));
            Class<?> viewHolder = classLoader.loadClass("androidx.recyclerview.widget.RecyclerView$ViewHolder");
            itemView = viewHolder.getField("itemView");
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
        if (bindCommentAd != null) {
            XposedBridge.hookMethod(bindCommentAd, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws IllegalAccessException {
                    if (!isEnabled()) {
                        return;
                    }
                    View view = (View) itemView.get(param.thisObject);
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
