package com.shatyuka.zhiliao;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 运行期自证：记录"装了哪些 Hook 目标"和"哪些真的被调用过"。
 *
 * 只做记账与文案拼装，不碰 Android、不打印日志（日志由调用方给），
 * 这样 JVM 单测里能直接验证去重与统计，不用起设备。
 *
 * 治的是"签名全对、语义已变"的静默失败：装了不等于拦到了，
 * 只有真正命中过才算数。
 */
public final class SelfCheck {
    private static final Set<String> installed = ConcurrentHashMap.newKeySet();
    private static final Set<String> fired = ConcurrentHashMap.newKeySet();

    private SelfCheck() {
    }

    /** 记录一个已安装的目标（Hook 安装成功时调用）。 */
    public static void installed(String feature, String target) {
        installed.add(key(feature, target));
    }

    /** 记录一次命中；首次返回 true，调用方据此只打一条日志。 */
    public static boolean fire(String feature, String target) {
        return fired.add(key(feature, target));
    }

    public static int installedCount() {
        return installed.size();
    }

    public static int firedCount() {
        return fired.size();
    }

    /** 已安装但从未触发的目标清单（按功能+目标排序）。 */
    public static List<String> neverFired() {
        Set<String> pending = new LinkedHashSet<>(installed);
        pending.removeAll(fired);
        List<String> result = new ArrayList<>(pending.size());
        for (String entry : pending) {
            result.add(entry.replace('|', ' '));
        }
        Collections.sort(result);
        return result;
    }

    /** 一行摘要，供延迟日志输出。 */
    public static String summary() {
        if (installed.isEmpty()) {
            return "尚未安装任何目标";
        }
        List<String> pending = neverFired();
        if (pending.isEmpty()) {
            return "已装 " + installed.size() + " 个目标，全部触发过";
        }
        return "已装 " + installed.size() + " 个目标，未触发 " + pending.size() + " 个：" + String.join("; ", pending);
    }

    /** 单测复位。 */
    static void reset() {
        installed.clear();
        fired.clear();
    }

    private static String key(String feature, String target) {
        return (feature == null || feature.isEmpty() ? "?" : feature) + "|" + target;
    }
}
