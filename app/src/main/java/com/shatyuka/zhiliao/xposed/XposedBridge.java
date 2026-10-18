package com.shatyuka.zhiliao.xposed;

import android.util.Log;

import com.shatyuka.zhiliao.SelfCheck;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;

/**
 * Entry point for hook installation on top of libxposed API 102.
 * {@link #attach} must be called with the module instance before any hook is installed.
 */
public final class XposedBridge {
    private static final String TAG = "Zhiliao";

    private static XposedInterface framework;

    private static final java.util.concurrent.atomic.AtomicInteger hookCount = new java.util.concurrent.atomic.AtomicInteger();

    /** 当前正在安装 Hook 的功能名；安装时被截取进 lambda，运行期不再依赖它。 */
    private static volatile String currentFeature;

    private XposedBridge() {
    }

    public static void attach(XposedInterface xposedInterface) {
        framework = xposedInterface;
    }

    public static int getHookCount() {
        return hookCount.get();
    }

    /** 由 Hooks 依次设置，让每条 Hook 归到自己功能名下，日志才读得懂。 */
    public static void setCurrentFeature(String feature) {
        currentFeature = feature;
    }

    public static void log(String text) {
        if (framework != null)
            framework.log(Log.INFO, TAG, text);
        else
            System.err.println("[" + TAG + "] " + text);
    }

    public static void log(Throwable throwable) {
        log(Log.getStackTraceString(throwable));
    }

    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        if (!(hookMethod instanceof Method) && !(hookMethod instanceof Constructor<?>))
            throw new IllegalArgumentException("Only methods and constructors can be hooked: " + hookMethod);
        if (framework == null)
            throw new IllegalStateException("XposedBridge is not attached to the framework");
        final String feature = currentFeature;
        final String target = signatureOf(hookMethod);
        SelfCheck.installed(feature, target);
        XposedInterface.HookHandle handle = framework.hook((Executable) hookMethod)
                .intercept(chain -> intercept(callback, chain, feature, target));
        hookCount.incrementAndGet();
        return new XC_MethodHook.Unhook(hookMethod, handle);
    }

    private static String signatureOf(Member member) {
        return member.getDeclaringClass().getName() + "#" + member.getName();
    }

    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {
        Set<XC_MethodHook.Unhook> unhooks = new LinkedHashSet<>();
        for (Method method : hookClass.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                makeAccessible(method);
                unhooks.add(hookMethod(method, callback));
            }
        }
        return unhooks;
    }

    public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> hookClass, XC_MethodHook callback) {
        Set<XC_MethodHook.Unhook> unhooks = new LinkedHashSet<>();
        for (Constructor<?> constructor : hookClass.getDeclaredConstructors()) {
            makeAccessible(constructor);
            unhooks.add(hookMethod(constructor, callback));
        }
        return unhooks;
    }

    static void makeAccessible(Executable executable) {
        try {
            executable.setAccessible(true);
        } catch (Throwable ignored) {
        }
    }

    private static Object intercept(XC_MethodHook callback, XposedInterface.Chain chain,
                                    String feature, String target) throws Throwable {
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam(
                chain.getExecutable(), chain.getThisObject(), chain.getArgs().toArray());
        if (SelfCheck.fire(feature, target)) {
            // 每个目标只记第一条：这是"确实拦到了"的证据，也是"装了却没进这里"的对照
            log("[自检][命中] " + (feature == null ? "" : feature + " ") + target);
        }

        try {
            callback.beforeHookedMethod(param);
        } catch (Throwable t) {
            log(t);
        }

        if (!param.isReturnEarly()) {
            try {
                Object result = chain.proceed(param.args);
                // A constructor has no return value, its chain result is the instance being created.
                param.setResult(chain.getExecutable() instanceof Constructor<?> ? null : result);
            } catch (Throwable t) {
                param.setThrowable(t);
            }
        }

        try {
            callback.afterHookedMethod(param);
        } catch (Throwable t) {
            log(t);
        }

        return param.getResultOrThrowable();
    }
}
