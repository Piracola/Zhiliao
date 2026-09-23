package com.shatyuka.zhiliao.hooks;

import android.graphics.Bitmap;
import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XC_MethodReplacement;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import com.shatyuka.zhiliao.xposed.XposedHelpers;
import java.lang.reflect.Method;

public class WebView implements IHook {
    /**
     * 水印由服务端页面 CSS 提供, 注入时 document 可能尚未就绪或即将被替换, 故立即执行 + DOMContentLoaded 兜底 + MutationObserver 重复应用
     */
    static final String script_hide_water_mark = "(function () {\n" +
            "    if (window.zhiliaoHideWaterMark) return;\n" +
            "    window.zhiliaoHideWaterMark = true;\n" +
            "    var css = '.App,html,body{background-image:none!important}' +\n" +
            "            '[class*=watermark],[class*=Watermark],[class*=waterMark],[class*=water-mark],[id*=watermark],[id*=Watermark],[id*=waterMark],[id*=water-mark]{background-image:none!important;background:none!important}';\n" +
            "    var apply = function () {\n" +
            "        var style = document.getElementById('zhiliaoHideWaterMark');\n" +
            "        if (!style) {\n" +
            "            style = document.createElement('style');\n" +
            "            style.id = 'zhiliaoHideWaterMark';\n" +
            "            style.textContent = css;\n" +
            "        }\n" +
            "        var parent = document.body || document.head || document.documentElement;\n" +
            "        if (parent && style.parentNode !== parent) parent.appendChild(style);\n" +
            "    };\n" +
            "    var observe = function () {\n" +
            "        if (!document.body) return;\n" +
            "        apply();\n" +
            "        new MutationObserver(apply).observe(document.body, {childList: true, subtree: true, attributes: true, attributeFilter: ['class', 'style']});\n" +
            "    };\n" +
            "    if (document.body) observe();\n" +
            "    else document.addEventListener('DOMContentLoaded', observe);\n" +
            "})();";

    /**
     * todo: 抽成文件
     */
    static final String script_hide_subscribe = "let toolbar = document.getElementsByClassName('Toolbar-functionButtons')[0];\n" +
            "if (toolbar) {\n" +
            "    let subscribe = toolbar.lastChild;\n" +
            "    if (subscribe && subscribe.children) {\n" +
            "        for (let i = 0; i < subscribe.children.length; i++) {\n" +
            "            if (subscribe.children[i].classList.contains('Avatar')) {\n" +
            "                let styleSubscribe = document.createElement('style');\n" +
            "                styleSubscribe.innerHTML = '.' + subscribe.className + '{display:none!important}';\n" +
            "                document.body.append(styleSubscribe);\n" +
            "                break\n" +
            "            }\n" +
            "        };\n" +
            "    }\n" +
            "}\n" +
            "let authorCard = document.getElementsByClassName('UserLine AuthorCard')[0];\n" +
            "if (authorCard) {\n" +
            "    let subscribe2 = authorCard.lastChild;\n" +
            "    if (subscribe2 && subscribe2.tagName === 'BUTTON') {\n" +
            "        let styleSubscribe = document.createElement('style');\n" +
            "        styleSubscribe.innerHTML = '.' + subscribe2.className + '{display:none!important}';\n" +
            "        document.body.append(styleSubscribe);\n" +
            "    }\n" +
            "}";

    static Class<?> answerAppView;
    static Method canPreRender;
    static Method onPageCommitVisible;

    @Override
    public String getName() {
        return "WebView修改";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        Helper.requireTarget(Helper.WebViewClientWrapper, "com.zhihu.android.app.mercury.web.WebViewClientWrapper");
        answerAppView = Helper.requireTarget(classLoader.loadClass("com.zhihu.android.answer.module.content.appview.AnswerAppView"), "com.zhihu.android.answer.module.content.appview.AnswerAppView");
        canPreRender = getDeclaredMethodOrNull(answerAppView, "canPreRender");
        if (canPreRender != null && canPreRender.getReturnType() != boolean.class)
            canPreRender = null;
        onPageCommitVisible = getDeclaredMethodOrNull(Helper.WebViewClientWrapper, "onPageCommitVisible", android.webkit.WebView.class, String.class);
    }

    static Method getDeclaredMethodOrNull(Class<?> clazz, String name, Class<?>... parameterTypes) {
        try {
            Method method = clazz.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    @Override
    public void hook() throws Throwable {
        // 预渲染直接用缓存页面, 注入的 js 会被丢弃, 去水印与去订阅都会失效
        if (canPreRender != null && (Helper.prefs.getBoolean("switch_watermark", false) || Helper.prefs.getBoolean("switch_subscribe", false))) {
            XposedBridge.hookMethod(canPreRender, XC_MethodReplacement.returnConstant(false));
        }

        XposedBridge.hookAllConstructors(android.webkit.WebView.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (Helper.prefs.getBoolean("switch_webview_debug", false)) {
                    XposedHelpers.callStaticMethod(android.webkit.WebView.class, "setWebContentsDebuggingEnabled", true);
                }
            }
        });

        XposedHelpers.findAndHookMethod(Helper.WebViewClientWrapper, "onPageStarted", android.webkit.WebView.class, String.class, Bitmap.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                injectScript(param.args[0]);
            }
        });

        if (onPageCommitVisible != null) {
            XposedBridge.hookMethod(onPageCommitVisible, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    injectScript(param.args[0]);
                }
            });
        }

        XposedHelpers.findAndHookMethod(Helper.WebViewClientWrapper, "onPageFinished", android.webkit.WebView.class, String.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                injectScript(param.args[0]);
                if (!(param.args[0] instanceof android.webkit.WebView))
                    return;
                String js = Helper.prefs.getString("edit_js", null);
                if (js != null) {
                    ((android.webkit.WebView) param.args[0]).evaluateJavascript(js, null);
                }
            }
        });
    }

    static void injectScript(Object webview) {
        if (!(webview instanceof android.webkit.WebView))
            return;
        String script = getScript(Helper.prefs.getBoolean("switch_watermark", false), Helper.prefs.getBoolean("switch_subscribe", false));
        if (script.isEmpty())
            return;
        try {
            ((android.webkit.WebView) webview).evaluateJavascript(script, null);
        } catch (Throwable e) {
            // 页面销毁等情况下注入失败, 由后续回调兜底, 但必须留痕, 否则水印去除会静默失效
            XposedBridge.log("[Zhiliao] 注入水印去除脚本失败: " + e);
        }
    }

    static String getScript(boolean hideWaterMark, boolean hideSubscribe) {
        StringBuilder sb = new StringBuilder();
        if (hideWaterMark) {
            // 水印脚本自带全局标记与 DOM 兜底, 可重复注入
            sb.append(script_hide_water_mark);
            sb.append("\n");
        }
        if (hideSubscribe) {
            sb.append("document.addEventListener('DOMContentLoaded',()=>{\n");
            sb.append("if (window.zhiliaoHideSubscribe) return;\n");
            sb.append("window.zhiliaoHideSubscribe = true;\n");
            sb.append(script_hide_subscribe);
            sb.append("\n});");
        }
        return sb.toString();
    }
}
