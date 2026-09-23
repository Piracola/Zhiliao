package com.shatyuka.zhiliao.hooks;

import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import java.lang.reflect.Method;

public class FeedTopHotBanner implements IHook {
    static Class<?> feedTopHotAutoJacksonDeserializer;
    static Method deserialize;

    @Override
    public String getName() {
        return "隐藏推荐页置顶热门";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        try {
            feedTopHotAutoJacksonDeserializer = classLoader.loadClass("com.zhihu.android.api.model.FeedTopHotAutoJacksonDeserializer");
        } catch (ClassNotFoundException ignore) {
        }
        if (feedTopHotAutoJacksonDeserializer == null) {
            return;
        }

        // deserialize 已上移到父类 BaseObjectStdDeserializer，本类只声明构造器与 processMember
        for (Method method : feedTopHotAutoJacksonDeserializer.getSuperclass().getDeclaredMethods()) {
            if (method.getName().equals("deserialize") && method.getParameterTypes().length == 2) {
                method.setAccessible(true);
                deserialize = method;
                break;
            }
        }
        if (deserialize == null) {
            throw new NoSuchMethodException("com.zhihu.android.autojackson.BaseObjectStdDeserializer.deserialize");
        }
    }

    @Override
    public void hook() throws Throwable {
        if (deserialize == null) {
            return;
        }
        XposedBridge.hookMethod(deserialize, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                // 父类方法被所有 AutoJackson 反序列化复用，必须限定目标类型，否则会清空全站数据
                if (param.thisObject == null || param.thisObject.getClass() != feedTopHotAutoJacksonDeserializer) {
                    return;
                }
                if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_feedtophot", false)) {
                    param.setResult(null);
                }
            }
        });
    }
}
