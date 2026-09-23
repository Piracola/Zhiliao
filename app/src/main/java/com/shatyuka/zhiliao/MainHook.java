package com.shatyuka.zhiliao;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.widget.Toast;

import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import com.shatyuka.zhiliao.xposed.XposedHelpers;

import java.io.File;

import io.github.libxposed.api.XposedModule;

public class MainHook extends XposedModule {
    static String modulePath;
    static String processName;

    private native void initNative();

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    private void tryLoadNative(boolean showLog) {
        if (modulePath == null) {
            if (showLog)
                XposedBridge.log("[Zhiliao] 未获取到模块路径，无法加载native模块");
            return;
        }
        String path = modulePath.substring(0, modulePath.lastIndexOf('/'));
        String[] libs = {
                path + "/lib/arm64/libzhiliao.so",
                path + "/lib/arm/libzhiliao.so",
                modulePath + "!/lib/arm64-v8a/libzhiliao.so",
                modulePath + "!/lib/armeabi-v7a/libzhiliao.so"
        };

        for (String lib : libs) {
            try {
                System.load(lib);
                initNative();
                return;
            } catch (Throwable ignored) {
            }
        }

        if (showLog)
            XposedBridge.log("[Zhiliao] 知了native模块加载失败");
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        XposedBridge.attach(this);
        processName = param.getProcessName();

        ApplicationInfo moduleInfo = getModuleApplicationInfo();
        if (moduleInfo != null) {
            modulePath = moduleInfo.sourceDir != null ? moduleInfo.sourceDir : moduleInfo.publicSourceDir;
        }
        if (modulePath != null) {
            try {
                Helper.modRes = Helper.getModuleRes(modulePath);
            } catch (Throwable e) {
                XposedBridge.log(e);
            }
        }
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (!Helper.hookPackage.equals(param.getPackageName()))
            return;

        boolean isMainProcess = Helper.hookPackage.equals(processName);
        ClassLoader classLoader = param.getDefaultClassLoader();
        Context systemContext = (Context) XposedHelpers.callMethod(XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.app.ActivityThread", classLoader), "currentActivityThread"), "getSystemContext");
        if (!Helper.checkSignature(systemContext)) {
            tryLoadNative(isMainProcess);
            Helper.officialZhihu = false;
        }

        if (!isMainProcess)
            return;

        try {
            XposedBridge.hookAllConstructors(classLoader.loadClass("com.tencent.tinker.loader.app.TinkerApplication"), new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    param.args[0] = 0;
                }
            });
        } catch (ClassNotFoundException ignored) {
        }

        XposedHelpers.findAndHookMethod(android.app.Instrumentation.class, "callApplicationOnCreate", Application.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args[0] instanceof Application) {
                    Helper.context = ((Application) param.args[0]).getApplicationContext();

                    if (!Helper.init(classLoader))
                        Helper.toast("知了初始化失败，可能不支持当前版本知乎: " + Helper.packageInfo.versionName, Toast.LENGTH_SHORT);
                    else {
                        Hooks.init(classLoader);
                        if (!Helper.prefs.getBoolean("switch_mainswitch", false))
                            Helper.toast("知了加载成功，请到设置页面开启功能。", Toast.LENGTH_LONG);
                    }
                }
            }
        });

        XposedHelpers.findAndHookMethod(File.class, "exists", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                File file = (File) param.thisObject;
                if (file.getName().equals(".allowXposed")) {
                    param.setResult(true);
                }
            }
        });
    }
}
