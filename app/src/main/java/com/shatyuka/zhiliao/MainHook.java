package com.shatyuka.zhiliao;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

                    if (!Helper.init(classLoader, getRemotePreferences(Helper.REMOTE_PREFS_GROUP)))
                        Helper.toast("知了初始化失败，可能不支持当前版本知乎: " + Helper.packageInfo.versionName, Toast.LENGTH_SHORT);
                    else {
                        Hooks.init(classLoader);
                        registerModuleBroadcasts(classLoader);
                        if (!Helper.prefs.getBoolean("switch_mainswitch", false))
                            Helper.toast("知了加载成功，请打开知了App开启功能。", Toast.LENGTH_LONG);
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

    /** 模块 App → 知乎进程的单向指令通道：重启知乎、清理缓存。需模块持有对应签名权限。 */
    private void registerModuleBroadcasts(ClassLoader classLoader) {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Helper.ACTION_RESTART_ZHIHU);
            filter.addAction(Helper.ACTION_CLEAN_CACHE);
            filter.addAction(Helper.ACTION_STATUS);
            Context context = Helper.context;
            // Android 13+（targetSdk 33+）注册非系统广播必须声明 exported 与否。
            // 必须 EXPORTED 才收得到模块 App 发来的跨应用广播；安全性由 RESTART_PERMISSION 保证，
            // 只有同签名、持有该权限的发送方才能进来。
            int flags = Context.RECEIVER_EXPORTED;
            context.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    String action = intent != null ? intent.getAction() : null;
                    if (Helper.ACTION_RESTART_ZHIHU.equals(action)) {
                        Helper.doRestart(ctx);
                    } else if (Helper.ACTION_CLEAN_CACHE.equals(action)) {
                        long size = com.shatyuka.zhiliao.hooks.Cleaner.doClean();
                        XposedBridge.log("[Zhiliao][临时文件清理] App触发，清理 " + size + " 字节");
                    } else if (Helper.ACTION_STATUS.equals(action)) {
                        // 有序广播回执：自检结果塞回 result extras，App 侧在 resultReceiver 里取
                        android.os.Bundle extras = new android.os.Bundle();
                        extras.putString("status", Hooks.statusLine());
                        extras.putInt("ok", Hooks.okCount());
                        extras.putInt("total", Hooks.totalCount());
                        extras.putString("failedKeys", Hooks.failedKeysLine());
                        extras.putString("zhihu", Helper.packageInfo.versionName + "/" + Helper.versionCode);
                        extras.putString("fingerprint", ResolutionCache.fingerprint());
                        setResultExtras(extras);
                        XposedBridge.log("[Zhiliao][自检] 已应答 App 问询：" + Hooks.statusLine());
                    }
                }
            }, filter, Helper.RESTART_PERMISSION, null, flags);
        } catch (Throwable e) {
            XposedBridge.log("[Zhiliao] 注册模块广播失败: " + e);
        }
    }
}

