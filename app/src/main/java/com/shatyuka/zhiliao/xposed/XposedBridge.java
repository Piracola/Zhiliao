package com.shatyuka.zhiliao.xposed;

import android.util.Log;

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
    private static final Set<String> traced = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static volatile boolean traceEnabled;

    private XposedBridge() {
    }

    public static void attach(XposedInterface xposedInterface) {
        framework = xposedInterface;
    }

    /** Hooks successfully handed to the framework; read by the start-up self check. */
    public static int getHookCount() {
        return hookCount.get();
    }

    /**
     * Logs one line per hooked target the first time it is invoked.
     * Enabled by the "switch_hooktrace" debug setting; restart Zhihu after changing it.
     */
    public static void setTraceEnabled(boolean enabled) {
        traceEnabled = enabled;
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
        XposedInterface.HookHandle handle = framework.hook((Executable) hookMethod)
                .intercept(chain -> intercept(callback, chain));
        hookCount.incrementAndGet();
        return new XC_MethodHook.Unhook(hookMethod, handle);
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

    private static Object intercept(XC_MethodHook callback, XposedInterface.Chain chain) throws Throwable {
        XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam(
                chain.getExecutable(), chain.getThisObject(), chain.getArgs().toArray());
        if (traceEnabled) {
            String signature = chain.getExecutable().getDeclaringClass().getName() + "#" + chain.getExecutable().getName();
            if (traced.add(signature))
                log("[自检][触发] " + signature);
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
