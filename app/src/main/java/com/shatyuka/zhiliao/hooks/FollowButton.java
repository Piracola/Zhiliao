package com.shatyuka.zhiliao.hooks;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;

public class FollowButton implements IHook {
    static Class<?> followWithAvatarView;
    static Class<?> bottomReactionView;
    static Class<?> followPeopleButton;
    static Class<?> zHAuthorInfoView;

    static Field followWithAvatarViewField;
    static Field followPeopleButtonField;

    @Override
    public String getName() {
        return "去关注按钮";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        try {
            followWithAvatarView = classLoader.loadClass("com.zhihu.android.unify_interactive.view.follow.FollowWithAvatarView");
            bottomReactionView = classLoader.loadClass("com.zhihu.android.mixshortcontainer.function.mixup.view.BottomReactionView");

            followPeopleButton = classLoader.loadClass("com.zhihu.android.unify_interactive.view.follow.FollowPeopleButton");
            zHAuthorInfoView = classLoader.loadClass("com.zhihu.android.mixshortcontainer.function.mixup.author.ZHAuthorInfoView");

            followWithAvatarViewField = Helper.findFieldByType(bottomReactionView, followWithAvatarView);
            followPeopleButtonField = Helper.findFieldByType(zHAuthorInfoView, followPeopleButton);
        } catch (ClassNotFoundException ignore) {
        }
    }

    @Override
    public void hook() throws Throwable {
        if (followWithAvatarViewField != null) {
            XposedBridge.hookAllMethods(bottomReactionView, "setData", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_subscribe", false)) {
                        FrameLayout followWithAvatarViewInstance = (FrameLayout) followWithAvatarViewField.get(param.thisObject);
                        if (followWithAvatarViewInstance != null) {
                            followWithAvatarViewInstance.setVisibility(View.GONE);
                        }
                    }
                }
            });
        }

        if (followPeopleButtonField != null) {
            XposedBridge.hookAllMethods(zHAuthorInfoView, "setData", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_subscribe", false)) {
                        ViewGroup followPeopleButtonFieldInstance = (ViewGroup) followPeopleButtonField.get(param.thisObject);
                        if (followPeopleButtonFieldInstance != null) {
                            followPeopleButtonFieldInstance.setVisibility(View.GONE);
                        }
                    }
                }
            });
        }
    }
}
