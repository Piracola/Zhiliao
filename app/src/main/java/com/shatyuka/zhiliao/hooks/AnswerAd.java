package com.shatyuka.zhiliao.hooks;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.TargetResolver;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class AnswerAd implements IHook {
    static Method shouldInterceptRequest;

    @Override
    public String getName() {
        return "去回答底部广告";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        Helper.requireTarget(Helper.IZhihuWebView, "com.zhihu.android.app.mercury.api.IZhihuWebView");
        // 真正要挂的是所有 AppView 子类，AnswerAppView 只是拿到这个父类的入口；
        // 入口类本身也会改名，所以先过名单，落空再用老名字
        Class<?> AnswerAppView = TargetResolver.findClassByQualifiedCandidates(classLoader, "answerAd.appView",
                clazz -> clazz.getSuperclass() != null && clazz.getSuperclass() != Object.class);
        if (AnswerAppView == null) {
            AnswerAppView = classLoader.loadClass("com.zhihu.android.answer.module.content.appview.AnswerAppView");
        }
        Class<?> AppView = AnswerAppView.getSuperclass();
        if (AppView == null)
            throw new ClassNotFoundException("com.zhihu.android.appview.AppView");
        Helper.findClass(classLoader, AppView.getName() + "$", 0, 32,
                (Class<?> ZhihuWebViewClient) -> {
                    shouldInterceptRequest = TargetResolver.findMethod(ZhihuWebViewClient, false, 0,
                            method -> method.getReturnType() == WebResourceResponse.class
                                    && Arrays.equals(method.getParameterTypes(),
                                    new Class<?>[]{Helper.IZhihuWebView, WebResourceRequest.class}));
                    return shouldInterceptRequest != null;
                });
        if (shouldInterceptRequest == null)
            throw new NoSuchMethodException("com.zhihu.android.appview.AppView$ZhihuWebViewClient(IZhihuWebView, WebResourceRequest): WebResourceResponse");
    }

    @Override
    public void hook() throws Throwable {
        XposedBridge.hookMethod(shouldInterceptRequest, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!Helper.prefs.getBoolean("switch_mainswitch", false))
                    return;
                WebResourceRequest request = (WebResourceRequest) param.args[1];
                if (request == null || request.getUrl() == null)
                    return;
                List<String> segments = request.getUrl().getPathSegments();
                if (segments.size() > 2 && request.getMethod().equals("GET")
                        && ((Helper.prefs.getBoolean("switch_answerad", true) && (segments.get(segments.size() - 1).equals("recommendations")
                        || (segments.get(2).equals("brand") && segments.get(segments.size() - 1).equals("card")))
                        || segments.get(segments.size() - 2).equals("hotmodule"))
                        || (Helper.prefs.getBoolean("switch_club", false) && segments.get(segments.size() - 1).equals("bind_club"))
                        || (Helper.prefs.getBoolean("switch_goods", false) && segments.get(segments.size() - 2).equals("goods"))
                        || (Helper.prefs.getBoolean("switch_article", false) && segments.get(segments.size() - 1).equals("recommendation"))
                        || (Helper.prefs.getBoolean("switch_related", false) && segments.get(segments.size() - 3).equals("related_queries")))
                        || (Helper.prefs.getBoolean("switch_searchwords", false) && segments.get(segments.size() - 1).equals("entity_word"))) {
                    WebResourceResponse response = new WebResourceResponse("application/json", "UTF-8", new ByteArrayInputStream("{}".getBytes()));
                    response.setStatusCodeAndReasonPhrase(200, "OK");
                    param.setResult(response);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (!Helper.prefs.getBoolean("switch_mainswitch", false))
                    return;
                WebResourceRequest request = (WebResourceRequest) param.args[1];
                if (request == null || request.getUrl() == null)
                    return;
                List<String> segments = request.getUrl().getPathSegments();
                if (segments.size() > 2 && request.getMethod().equals("GET")) {
                    if (Helper.prefs.getBoolean("switch_searchwords", false) && segments.get(0).equals("appview") && segments.get(segments.size() - 2).equals("answer")) {
                        WebResourceResponse response = (WebResourceResponse) param.getResult();
                        if (response == null || response.getData() == null)
                            return;
                        try {
                            java.io.InputStream in = response.getData();
                            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                            byte[] chunk = new byte[8192];
                            int read;
                            while ((read = in.read(chunk)) != -1) {
                                buffer.write(chunk, 0, read);
                            }
                            String content = new String(buffer.toByteArray());
                            content = content.replace("\"searchWords\"", "\"searchWords_bak\"");
                            content = content.replace("\"search_words\"", "\"search_words_bak\"");
                            response.setData(new ByteArrayInputStream(content.getBytes()));
                            param.setResult(response);
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        });
    }
}
