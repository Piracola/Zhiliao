package com.shatyuka.zhiliao.hooks;

import android.view.View;
import android.widget.ImageView;
import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XC_MethodReplacement;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 端内水印分布在多个子系统，这里只处理"客户端绘制"的部分：
 * 视频水印（VodWatermarkPlugin）与混合容器水印（tornado TDataWaterMark）的水印图都取自数据模型，
 * 把模型置空即从源头去掉；页面 WebView 的背景水印由 {@link WebView} 的 CSS 注入处理。
 * 服务端烘焙进图片字节的水印（watermark_src 换源问题）与真·盲水印不在可移除范围内。
 */
public class Watermark implements IHook {
    static Class<?> WatermarkInfo;
    static Class<?> TDataWaterMark;
    static Class<?> VodWatermarkPlugin;

    static Method WatermarkInfo_getLogoUrl;
    static Method WatermarkInfo_getNicknameUrl;
    static Method WatermarkInfo_getWatermarked;
    static Method TDataWaterMark_setLogoImage;
    static Method TDataWaterMark_setNickImage;
    static Method VodWatermarkPlugin_layoutWatermarkUI;

    @Override
    public String getName() {
        return "移除水印";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        // 这两个模型的成员名是 JSON 契约（logo / nick_name_url / logo_image / nick_image），
        // R8 不混淆它们，所以按名字定位在这里是安全的；任一缺失都只降级，不整条失败。
        WatermarkInfo = loadClassOrNull(classLoader, "com.zhihu.android.api.model.plugin.WatermarkInfo");
        if (WatermarkInfo != null) {
            WatermarkInfo_getLogoUrl = methodOrNull(WatermarkInfo, "getLogoUrl");
            WatermarkInfo_getNicknameUrl = methodOrNull(WatermarkInfo, "getNicknameUrl");
            WatermarkInfo_getWatermarked = methodOrNull(WatermarkInfo, "getWatermarked");
        }

        TDataWaterMark = loadClassOrNull(classLoader, "com.zhihu.android.tornado.data.TDataWaterMark");
        if (TDataWaterMark != null) {
            TDataWaterMark_setLogoImage = methodOrNull(TDataWaterMark, "setLogoImage", String.class);
            TDataWaterMark_setNickImage = methodOrNull(TDataWaterMark, "setNickImage", String.class);
        }

        VodWatermarkPlugin = loadClassOrNull(classLoader, "com.zhihu.android.media.plugin.VodWatermarkPlugin");
        if (VodWatermarkPlugin != null) {
            VodWatermarkPlugin_layoutWatermarkUI = methodOrNull(VodWatermarkPlugin, "layoutWatermarkUI",
                    int.class, int.class, int.class, int.class, int.class);
        }

        if (WatermarkInfo_getLogoUrl == null && TDataWaterMark_setNickImage == null && VodWatermarkPlugin_layoutWatermarkUI == null) {
            throw new ClassNotFoundException("com.zhihu.android.api.model.plugin.WatermarkInfo");
        }
    }

    @Override
    public void hook() throws Throwable {
        if (!Helper.prefs.getBoolean("switch_mainswitch", false) || !Helper.prefs.getBoolean("switch_watermark", false))
            return;

        if (WatermarkInfo_getLogoUrl != null)
            XposedBridge.hookMethod(WatermarkInfo_getLogoUrl, XC_MethodReplacement.returnConstant(null));
        if (WatermarkInfo_getNicknameUrl != null)
            XposedBridge.hookMethod(WatermarkInfo_getNicknameUrl, XC_MethodReplacement.returnConstant(null));
        if (WatermarkInfo_getWatermarked != null)
            XposedBridge.hookMethod(WatermarkInfo_getWatermarked, XC_MethodReplacement.returnConstant(false));

        // TDataWaterMark 可变，直接吞掉写入即可，无需依赖 getter 的实现细节
        if (TDataWaterMark_setLogoImage != null)
            XposedBridge.hookMethod(TDataWaterMark_setLogoImage, XC_MethodReplacement.returnConstant(null));
        if (TDataWaterMark_setNickImage != null)
            XposedBridge.hookMethod(TDataWaterMark_setNickImage, XC_MethodReplacement.returnConstant(null));

        if (VodWatermarkPlugin_layoutWatermarkUI != null) {
            XposedBridge.hookMethod(VodWatermarkPlugin_layoutWatermarkUI, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    hideWatermarkImages(param.thisObject);
                }
            });
        }
    }

    /**
     * 视图层兜底：数据层被服务端换字段绕过后，水印图仍可能被 inflate 出来。
     * 只隐藏 ImageView 子类（水印本身就是 ZHDraweeView），避免误伤同插件里的 rootView。
     */
    static void hideWatermarkImages(Object plugin) {
        for (Field field : plugin.getClass().getDeclaredFields()) {
            if (!ImageView.class.isAssignableFrom(field.getType()))
                continue;
            try {
                field.setAccessible(true);
                View view = (View) field.get(plugin);
                if (view != null && view.getVisibility() != View.GONE)
                    view.setVisibility(View.GONE);
            } catch (Throwable ignored) {
            }
        }
    }

    static Class<?> loadClassOrNull(ClassLoader classLoader, String name) {
        try {
            return classLoader.loadClass(name);
        } catch (Throwable e) {
            return null;
        }
    }

    static Method methodOrNull(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            Method method = clazz.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (Throwable e) {
            return null;
        }
    }
}
