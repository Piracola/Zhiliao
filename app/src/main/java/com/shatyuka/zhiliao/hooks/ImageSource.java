package com.shatyuka.zhiliao.hooks;

import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 图片换源：端内有两条路径会主动挑服务端下发的水印变体——
 * 回答/文章正文默认取 ZRichImageBean.urls（水印变体），想法/动态取 PinContent.watermarkUrl。
 * 这里把展示 URL 换成无水印原图（originalUrls / originalUrl），只在原图存在时才替换，缺失即回退。
 * 仅对"服务端另发原图"的图片水印有效；像素级烘焙水印与真·盲水印无效，故默认关闭。
 */
public class ImageSource implements IHook {
    static Method getImageUrl;
    static Field ZRichImageBean_originalUrls;

    static Class<?> pinContentDeserializer;
    static Method pinContentDeserialize;
    static Field PinContent_isWatermark;
    static Field PinContent_watermarkUrl;

    @Override
    public String getName() {
        return "图片换源去水印";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        Class<?> zrichBean = loadClassOrNull(classLoader, "com.zhihu.android.zrichCore.model.bean.ZRichImageBean");
        Class<?> zrichExt = loadClassOrNull(classLoader, "com.zhihu.android.zrichCore.model.bean.ZRichImageBeanExtKt");
        if (zrichBean != null && zrichExt != null) {
            ZRichImageBean_originalUrls = listField(zrichBean, "originalUrls");
            if (ZRichImageBean_originalUrls != null) {
                for (Method method : zrichExt.getDeclaredMethods()) {
                    if (method.getReturnType() == String.class && method.getParameterCount() == 1
                            && method.getParameterTypes()[0] == zrichBean && "getImageUrl".equals(method.getName())) {
                        method.setAccessible(true);
                        getImageUrl = method;
                        break;
                    }
                }
            }
        }

        pinContentDeserializer = loadClassOrNull(classLoader, "com.zhihu.android.api.model.PinContentAutoJacksonDeserializer");
        if (pinContentDeserializer != null) {
            Class<?> pinContent = loadClassOrNull(classLoader, "com.zhihu.android.api.model.PinContent");
            if (pinContent != null) {
                PinContent_isWatermark = fieldOfType(pinContent, "isWatermark", boolean.class);
                PinContent_watermarkUrl = fieldOfType(pinContent, "watermarkUrl", String.class);
            }
            // deserialize 声明在父类 BaseObjectStdDeserializer 上，本类只有构造器与 processMember
            if (pinContentDeserializer.getSuperclass() != null) {
                for (Method method : pinContentDeserializer.getSuperclass().getDeclaredMethods()) {
                    if ("deserialize".equals(method.getName()) && method.getParameterCount() == 2) {
                        method.setAccessible(true);
                        pinContentDeserialize = method;
                        break;
                    }
                }
            }
        }

        if (getImageUrl == null && pinContentDeserialize == null)
            throw new ClassNotFoundException("ZRichImageBeanExtKt.getImageUrl / PinContentAutoJacksonDeserializer");
    }

    @Override
    public void hook() throws Throwable {
        if (!Helper.prefs.getBoolean("switch_mainswitch", false) || !Helper.prefs.getBoolean("switch_imagesource", false))
            return;

        if (getImageUrl != null) {
            XposedBridge.hookMethod(getImageUrl, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args == null || param.args.length == 0 || param.args[0] == null)
                        return;
                    String original = firstUrl(ZRichImageBean_originalUrls.get(param.args[0]));
                    // original_urls 缺失时保持原逻辑，避免图裂
                    if (original != null)
                        param.setResult(original);
                }
            });
        }

        if (pinContentDeserialize != null) {
            XposedBridge.hookMethod(pinContentDeserialize, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    // 父类方法被所有 AutoJackson 反序列化复用，必须限定目标类型
                    if (param.thisObject == null || param.thisObject.getClass() != pinContentDeserializer)
                        return;
                    Object content = param.getResult();
                    if (content == null)
                        return;
                    try {
                        if (PinContent_isWatermark != null)
                            PinContent_isWatermark.setBoolean(content, false);
                        if (PinContent_watermarkUrl != null)
                            PinContent_watermarkUrl.set(content, null);
                    } catch (Throwable ignored) {
                    }
                }
            });
        }
    }

    static String firstUrl(Object value) {
        if (!(value instanceof List))
            return null;
        for (Object item : (List<?>) value) {
            if (item instanceof String && !((String) item).isEmpty())
                return (String) item;
        }
        return null;
    }

    static Field listField(Class<?> clazz, String name) {
        for (Field field : clazz.getDeclaredFields()) {
            if (List.class.isAssignableFrom(field.getType()) && field.getName().equals(name)) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    static Field fieldOfType(Class<?> clazz, String name, Class<?> type) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getType() == type && field.getName().equals(name)) {
                field.setAccessible(true);
                return field;
            }
        }
        return null;
    }

    static Class<?> loadClassOrNull(ClassLoader classLoader, String name) {
        try {
            return classLoader.loadClass(name);
        } catch (Throwable e) {
            return null;
        }
    }
}
