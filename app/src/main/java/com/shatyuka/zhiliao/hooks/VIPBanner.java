package com.shatyuka.zhiliao.hooks;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.R;
import com.shatyuka.zhiliao.xposed.XC_MethodReplacement;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import java.lang.reflect.Method;

public class VIPBanner implements IHook {
    static Class<?> VipEntranceView;
    static Class<?> MoreVipData;

    static Method initView;
    static Method initView_new;
    static Method setData;
    static Method onClick;

    @Override
    public String getName() {
        return "隐藏会员卡片";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        // 11.10.0: 旧类 more.more.widget.VipEntranceView、NewMoreFragment、resetStyle 均已不存在
        // 回退类 premium.view.VipEntranceView 的入口是 public init(Context)，initView(Context) 只是其私有实现
        VipEntranceView = classLoader.loadClass("com.zhihu.android.premium.view.VipEntranceView");
        initView_new = VipEntranceView.getDeclaredMethod("init", Context.class);
        try {
            initView = VipEntranceView.getDeclaredMethod("initView", Context.class);
            initView.setAccessible(true);
        } catch (NoSuchMethodException ignored) {
            initView = null;
        }
        try {
            onClick = VipEntranceView.getDeclaredMethod("onClick", View.class);
        } catch (NoSuchMethodException ignored) {
            onClick = null;
        }
        try {
            MoreVipData = classLoader.loadClass("com.zhihu.android.api.MoreVipData");
            setData = Helper.getMethodByParameterTypes(VipEntranceView, MoreVipData, String.class, boolean.class);
        } catch (ClassNotFoundException ignored) {
            MoreVipData = null;
        }
    }

    @Override
    public void hook() throws Throwable {
        if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_vipbanner", false)) {
            XC_MethodReplacement replaceVipEntranceLayout = new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) {
                    if (param.args[0] instanceof Context && param.thisObject instanceof ViewGroup) {
                        XmlResourceParser layout_vipentranceview_new = Helper.modRes.getLayout(R.layout.layout_vipentranceview_new);
                        LayoutInflater.from((Context) param.args[0]).inflate(layout_vipentranceview_new, (ViewGroup) param.thisObject);
                    }
                    return null;
                }
            };
            XposedBridge.hookMethod(initView_new, replaceVipEntranceLayout);
            if (initView != null)
                XposedBridge.hookMethod(initView, replaceVipEntranceLayout);
            if (setData != null)
                XposedBridge.hookMethod(setData, XC_MethodReplacement.returnConstant(null));
            if (onClick != null)
                XposedBridge.hookMethod(onClick, XC_MethodReplacement.returnConstant(null));
            // 会员卡片已迁到「我的」页，MineTabFragment 仍消费 isLegal
            if (MoreVipData != null)
                XposedBridge.hookAllMethods(MoreVipData, "isLegal", XC_MethodReplacement.returnConstant(Boolean.FALSE));
        }
    }
}
