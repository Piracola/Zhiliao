package com.shatyuka.zhiliao.hooks;

import android.view.View;
import android.view.ViewGroup;
import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XC_MethodReplacement;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import com.shatyuka.zhiliao.xposed.XposedHelpers;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class NextAnswer implements IHook {
    static Class<?> NextContentAnimationView;
    static Class<?> NextContentAnimationView_short;
    static Class<?> MixShortContainerFragment;
    static Class<?> PopupMenuNextButton;
    static Class<?> NextContentAnimationView_feature;
    static Class<?> PopupMenuNextButton_feature;

    static Method initView;
    static Method initLayout;

    static Field nextButton;

    @Override
    public String getName() {
        return "移除下一个回答按钮";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        Helper.requireTarget(Helper.AnswerPagerFragment, "com.zhihu.android.answer.module.pager.AnswerPagerFragment");
        if (Helper.versionCode > 2614) {
            // 旧短容器(mixshortcontainer)与新版短容器(feature.short_container_feature)是两套平行实现,
            // 两边的按钮类互不继承, 所以两条路都要挂; 任一边缺失只降级, 不整条失败。
            try {
                NextContentAnimationView = classLoader.loadClass("com.zhihu.android.mix.widget.NextContentAnimationView");
                NextContentAnimationView_short = classLoader.loadClass("com.zhihu.android.mixshortcontainer.function.next.NextContentAnimationView");
                MixShortContainerFragment = classLoader.loadClass("com.zhihu.android.mixshortcontainer.MixShortContainerFragment");
                PopupMenuNextButton = classLoader.loadClass("com.zhihu.android.mixshortcontainer.function.next.PopupMenuNextButton");

                initView = Helper.getMethodByParameterTypes(MixShortContainerFragment, 0, View.class);
                initLayout = Helper.getMethodByParameterTypes(MixShortContainerFragment, 1, View.class);
                if (initView != null)
                    initView.setAccessible(true);
                if (initLayout != null)
                    initLayout.setAccessible(true);

                // 11.10.0: 按钮本体是字段 y(类型 PopupMenuNextButton)，旧的 t/f/e/M/L/d 名单只能命中无关的 L
                nextButton = Helper.findFieldByType(MixShortContainerFragment, PopupMenuNextButton);
            } catch (Throwable e) {
                XposedBridge.log("[Zhiliao] 移除下一个回答按钮: 旧短容器目标已缺失, 仅处理新短容器: " + e);
            }

            // 11.10.0 起回答流默认走新短容器模块; 该模块包名未混淆(真机 ShortContainerHostActivity 同包), 可直接按全名加载
            NextContentAnimationView_feature = loadClassOrNull(classLoader,
                    "com.zhihu.android.feature.short_container_feature.ui.widget.next.NextContentAnimationView");
            PopupMenuNextButton_feature = loadClassOrNull(classLoader,
                    "com.zhihu.android.feature.short_container_feature.ui.widget.next.PopupMenuNextButton");

            if (PopupMenuNextButton == null && PopupMenuNextButton_feature == null)
                throw new ClassNotFoundException("PopupMenuNextButton");
        }
    }

    @Override
    public void hook() throws Throwable {
        if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_nextanswer", false)) {
            XposedHelpers.findAndHookMethod(Helper.AnswerPagerFragment, "setupNextAnswerBtn", XC_MethodReplacement.returnConstant(null));
            if (nextButton != null) {
                if (initView != null) {
                    XposedBridge.hookMethod(initView, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            hideNextButton(param);
                        }
                    });
                }
                if (initLayout != null) {
                    XposedBridge.hookMethod(initLayout, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            hideNextButton(param);
                        }
                    });
                }
            }

            if (Helper.versionCode > 2614) {
                XposedHelpers.findAndHookMethod(ViewGroup.class, "addView", View.class, ViewGroup.LayoutParams.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (isNextButtonView(param.args[0]))
                            ((View) param.args[0]).setVisibility(View.GONE);
                    }
                });

                XposedHelpers.findAndHookMethod(View.class, "setVisibility", int.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isNextButtonView(param.thisObject))
                            param.args[0] = View.GONE;
                    }
                });
            }

            for (Class<?> buttonClass : new Class<?>[]{PopupMenuNextButton, PopupMenuNextButton_feature}) {
                if (buttonClass == null)
                    continue;
                XposedBridge.hookAllConstructors(buttonClass, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof View) {
                            View button = (View) param.thisObject;
                            button.setVisibility(View.GONE);
                            button.setScaleX(0);
                            button.setScaleY(0);
                        }
                    }
                });
            }
        }
    }

    static boolean isNextButtonView(Object object) {
        if (!(object instanceof View))
            return false;
        return (NextContentAnimationView != null && NextContentAnimationView.isInstance(object))
                || (NextContentAnimationView_short != null && NextContentAnimationView_short.isInstance(object))
                || (NextContentAnimationView_feature != null && NextContentAnimationView_feature.isInstance(object));
    }

    static Class<?> loadClassOrNull(ClassLoader classLoader, String name) {
        try {
            return classLoader.loadClass(name);
        } catch (Throwable e) {
            return null;
        }
    }

    // 置 null 会让宿主逻辑 NPE 或跳过后续处理，只隐藏按钮本体
    static void hideNextButton(XC_MethodHook.MethodHookParam param) {
        try {
            if (nextButton == null || !MixShortContainerFragment.isInstance(param.thisObject))
                return;
            Object button = nextButton.get(param.thisObject);
            if (button instanceof View)
                ((View) button).setVisibility(View.GONE);
        } catch (Throwable ignored) {
        }
    }
}
