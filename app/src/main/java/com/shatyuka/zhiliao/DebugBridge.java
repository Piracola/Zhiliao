package com.shatyuka.zhiliao;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Process;

import com.shatyuka.zhiliao.xposed.XposedBridge;

/**
 * 调试入口：在知乎进程里挂一个广播接收器，用 adb 命令直接改开关，不用点界面。
 *
 * <pre>
 * adb shell am broadcast -a com.shatyuka.zhiliao.DEBUG --es cmd list
 * adb shell am broadcast -a com.shatyuka.zhiliao.DEBUG --es cmd set --es key switch_video --es value true
 * adb shell am broadcast -a com.shatyuka.zhiliao.DEBUG --es cmd restart
 * </pre>
 *
 * 覆盖值写进知乎本地的一份小配置（zhiliao_debug），读取时优先于远端偏好——
 * 不碰用户真正的设置，clear 一下就能回到原样。
 *
 * 接收器要求发送方持有 android.permission.DUMP：shell 和 root 天然有，
 * 普通 App 没有，所以这扇门只对 adb 开。
 */
public final class DebugBridge {
    public static final String ACTION = "com.shatyuka.zhiliao.DEBUG";

    private static SharedPreferences overrides;
    private static volatile java.util.Map<String, Object> snapshot = java.util.Collections.emptyMap();

    private DebugBridge() {
    }

    public static void install(Context context) {
        try {
            overrides = context.getSharedPreferences("zhiliao_debug", Context.MODE_PRIVATE);
            reloadSnapshot();
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context receiverContext, Intent intent) {
                    handle(receiverContext, intent);
                }
            };
            IntentFilter filter = new IntentFilter(ACTION);
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, "android.permission.DUMP", null,
                        Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter, "android.permission.DUMP", null);
            }
            XposedBridge.log("[Zhiliao][调试] 命令行开关已就绪：" + ACTION);
        } catch (Throwable e) {
            XposedBridge.log("[Zhiliao][调试] 命令行开关不可用: " + e);
        }
    }

    /** 调试覆盖值；没有则返回 null。读的是内存快照，热路径不做任何分配。 */
    public static Object override(String key) {
        return key == null ? null : snapshot.get(key);
    }

    /** 全部覆盖值（只读视图）。 */
    public static java.util.Map<String, Object> overrides() {
        return java.util.Collections.unmodifiableMap(snapshot);
    }

    private static void reloadSnapshot() {
        SharedPreferences current = overrides;
        if (current == null) {
            snapshot = java.util.Collections.emptyMap();
            return;
        }
        snapshot = new java.util.HashMap<>(current.getAll());
    }

    private static void handle(Context context, Intent intent) {
        String cmd = intent.getStringExtra("cmd");
        try {
            if ("set".equals(cmd)) {
                String key = intent.getStringExtra("key");
                if (key == null) {
                    XposedBridge.log("[Zhiliao][调试] set 缺少 key");
                    return;
                }
                String raw = intent.hasExtra("value") ? String.valueOf(intent.getExtras().get("value")) : null;
                if (raw == null) {
                    XposedBridge.log("[Zhiliao][调试] set 缺少 value");
                    return;
                }
                Object value = coerce(raw);
                SharedPreferences.Editor editor = overrides.edit();
                if (value instanceof Boolean) {
                    editor.putBoolean(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    editor.putInt(key, (Integer) value);
                } else {
                    editor.putString(key, (String) value);
                }
                editor.apply();
                reloadSnapshot();
                XposedBridge.log("[Zhiliao][调试] set " + key + " = " + raw + "（已生效于下一次读取）");
            } else if ("get".equals(cmd)) {
                String key = intent.getStringExtra("key");
                XposedBridge.log("[Zhiliao][调试] get " + key + " = " + Helper.prefs.getAll().get(key));
            } else if ("list".equals(cmd)) {
                java.util.Map<String, Object> all = snapshot;
                XposedBridge.log("[Zhiliao][调试] 覆盖项 " + all.size() + " 条:");
                for (java.util.Map.Entry<String, Object> entry : all.entrySet()) {
                    XposedBridge.log("[Zhiliao][调试]   " + entry.getKey() + " = " + entry.getValue());
                }
            } else if ("clear".equals(cmd)) {
                overrides.edit().clear().apply();
                reloadSnapshot();
                XposedBridge.log("[Zhiliao][调试] 覆盖项已清空");
            } else if ("restart".equals(cmd)) {
                XposedBridge.log("[Zhiliao][调试] 重启知乎进程");
                Process.killProcess(Process.myPid());
            } else if ("unsupported".equals(cmd)) {
                // 预演"当前版本不支持"的界面：把某个功能记进置灰账本，设置页刷新后对应开关会变灰
                String feature = intent.getStringExtra("feature");
                if (feature == null || "clear".equals(feature)) {
                    Hooks.status.reset();
                    XposedBridge.log("[Zhiliao][调试] 置灰账本已重置");
                } else {
                    Hooks.status.markUnsupported(feature);
                    XposedBridge.log("[Zhiliao][调试] 标记不支持：" + feature + " → " + Hooks.status.failedKeysLine());
                }
            } else if ("dex".equals(cmd)) {
                // 探针：验证 DexKit 兜底能不能在这台机器上跑起来，以及条件松紧是否合适
                String anchor = intent.getStringExtra("str");
                int max = intent.getIntExtra("max", 3);
                if (anchor == null || anchor.isEmpty()) {
                    XposedBridge.log("[Zhiliao][调试] dex 探测：" + DexKitFallback.probe());
                    return;
                }
                java.util.List<String> hits = DexKitFallback.findClasses(anchor, max);
                XposedBridge.log("[Zhiliao][调试] dex 查询 " + anchor + " 上限=" + max
                        + " 命中=" + hits.size() + " 状态=" + DexKitFallback.status());
                for (String name : hits) {
                    XposedBridge.log("[Zhiliao][调试]   " + name);
                }
            } else {
                XposedBridge.log("[Zhiliao][调试] 未知命令 " + cmd + "（可用 set/get/list/clear/restart/unsupported/dex）");
            }
        } catch (Throwable e) {
            XposedBridge.log("[Zhiliao][调试] " + cmd + " 失败: " + e);
        }
    }

    private static Object coerce(String raw) {
        if ("true".equalsIgnoreCase(raw)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return Boolean.FALSE;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return raw;
        }
    }
}

