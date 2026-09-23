package com.shatyuka.zhiliao.hooks;

import android.view.View;
import android.view.ViewGroup;
import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XC_MethodReplacement;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import com.shatyuka.zhiliao.xposed.XposedHelpers;
import java.lang.reflect.Method;

public class RedDot implements IHook {
    static Class<?> ViewModel;

    static Method BottomNavMenuItemView_setUnreadCount;
    static Method BottomNavMenuItemViewForIconOnly_setUnreadCount;
    static Method BaseBottomNavMenuItemView_setNavBadge;
    static Method NotiMsgModel_getUnreadCount;
    static Method IconWithDotAndCountView_setUnreadCount;
    static Method CountDotView_setUnreadCount;
    static Method BaseFeedFollowAvatarViewHolder_setUnreadTipVisibility;

    @Override
    public String getName() {
        return "不显示小红点";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        // 11.10.0: onUnReadCountLoaded、ZHMainTabLayout.d、NotiUnreadCountKt.hasUnread、RevisitView 均已消失，解析到才装
        try {
            Class<?> BottomNavMenuItemView = classLoader.loadClass("com.zhihu.android.bottomnav.core.BottomNavMenuItemView");
            BottomNavMenuItemView_setUnreadCount = Helper.getMethodByParameterTypes(BottomNavMenuItemView, int.class);
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class<?> BottomNavMenuItemViewForIconOnly = classLoader.loadClass("com.zhihu.android.bottomnav.core.BottomNavMenuItemViewForIconOnly");
            BottomNavMenuItemViewForIconOnly_setUnreadCount = Helper.getMethodByParameterTypes(BottomNavMenuItemViewForIconOnly, int.class);
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class<?> BaseBottomNavMenuItemView = classLoader.loadClass("com.zhihu.android.bottomnav.core.BaseBottomNavMenuItemView");
            Class<?> NavBadge = classLoader.loadClass("com.zhihu.android.bottomnav.api.model.NavBadge");
            BaseBottomNavMenuItemView_setNavBadge = Helper.getMethodByParameterTypes(BaseBottomNavMenuItemView, NavBadge);
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class<?> NotiMsgModel = classLoader.loadClass("com.zhihu.android.notification.model.viewmodel.NotiMsgModel");
            NotiMsgModel_getUnreadCount = NotiMsgModel.getDeclaredMethod("getUnreadCount");
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
        }
        try {
            Class<?> IconWithDotAndCountView = classLoader.loadClass("com.zhihu.android.community_base.view.icon.IconWithDotAndCountView");
            IconWithDotAndCountView_setUnreadCount = Helper.getMethodByParameterTypes(IconWithDotAndCountView, int.class, boolean.class, int.class);
        } catch (ClassNotFoundException ignored) {
        }
        try {
            Class<?> CountDotView = classLoader.loadClass("com.zhihu.android.notification.widget.CountDotView");
            CountDotView_setUnreadCount = Helper.getMethodByParameterTypes(CountDotView, int.class, boolean.class);
        } catch (ClassNotFoundException ignored) {
        }
        for (String name : new String[]{
                "com.zhihu.android.recentlyviewed.ui.viewholder.BaseFeedFollowAvatarViewHolder",
                "com.zhihu.android.moments.viewholders.BaseFeedFollowAvatarViewHolder"}) {
            try {
                Class<?> clazz = classLoader.loadClass(name);
                Method method = Helper.getMethodByParameterTypes(clazz, View.class, boolean.class);
                if (method != null) {
                    // P1 是包级私有方法，XposedBridge.hookMethod 不会自动 setAccessible
                    method.setAccessible(true);
                    BaseFeedFollowAvatarViewHolder_setUnreadTipVisibility = method;
                    break;
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
        try {
            ViewModel = classLoader.loadClass("com.zhihu.android.app.feed.ui.fragment.help.tabhelp.model.ViewModel");
        } catch (ClassNotFoundException ignored) {
        }

        if (BottomNavMenuItemView_setUnreadCount == null && BottomNavMenuItemViewForIconOnly_setUnreadCount == null
                && BaseBottomNavMenuItemView_setNavBadge == null && BaseFeedFollowAvatarViewHolder_setUnreadTipVisibility == null
                && NotiMsgModel_getUnreadCount == null && IconWithDotAndCountView_setUnreadCount == null
                && CountDotView_setUnreadCount == null && ViewModel == null)
            throw new ClassNotFoundException("小红点目标全部消失");
    }

    @Override
    public void hook() throws Throwable {
        if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_reddot", false)) {
            if (BottomNavMenuItemView_setUnreadCount != null)
                XposedBridge.hookMethod(BottomNavMenuItemView_setUnreadCount, XC_MethodReplacement.returnConstant(null));
            if (BottomNavMenuItemViewForIconOnly_setUnreadCount != null)
                XposedBridge.hookMethod(BottomNavMenuItemViewForIconOnly_setUnreadCount, XC_MethodReplacement.returnConstant(null));
            if (BaseBottomNavMenuItemView_setNavBadge != null)
                XposedBridge.hookMethod(BaseBottomNavMenuItemView_setNavBadge, XC_MethodReplacement.returnConstant(null));
            if (BaseFeedFollowAvatarViewHolder_setUnreadTipVisibility != null)
                XposedBridge.hookMethod(BaseFeedFollowAvatarViewHolder_setUnreadTipVisibility, XC_MethodReplacement.returnConstant(null));
            if (NotiMsgModel_getUnreadCount != null)
                XposedBridge.hookMethod(NotiMsgModel_getUnreadCount, XC_MethodReplacement.returnConstant(0));
            if (IconWithDotAndCountView_setUnreadCount != null)
                XposedBridge.hookMethod(IconWithDotAndCountView_setUnreadCount, XC_MethodReplacement.returnConstant(null));
            if (CountDotView_setUnreadCount != null)
                XposedBridge.hookMethod(CountDotView_setUnreadCount, new XC_MethodReplacement() {
                    @Override
                    protected Object replaceHookedMethod(MethodHookParam param) {
                        if (param.thisObject instanceof View)
                            ((View) param.thisObject).setVisibility(View.GONE);
                        return null;
                    }
                });
            if (ViewModel != null) {
                XposedHelpers.findAndHookConstructor(ViewModel, View.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args[0] instanceof ViewGroup) {
                            ViewGroup view = (ViewGroup) param.args[0];
                            if (view.getChildCount() == 2) { // red_parent
                                view.setVisibility(View.GONE);
                            }
                        }
                    }
                });
            }
        }
    }
}
