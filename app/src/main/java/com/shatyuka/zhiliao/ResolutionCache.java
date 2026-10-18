package com.shatyuka.zhiliao;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 解析结果缓存（路线图第二阶段）。
 *
 * 缓存的是"上次哪个候选命中了"，下次冷启动先试它，省掉前面的空转；
 * 名单本身永远完整走一遍验证，缓存只改顺序、不跳过验证，也不改变"全落空才算失败"。
 *
 * 失效条件靠指纹：知乎 versionCode + 候选名单 revision + APK 的 size^mtime 任一变化，
 * 旧缓存整份作废——不需要任何清理逻辑，也不需要版本迁移代码。
 *
 * 存储是可插拔的：真机接 SharedPreferences（写在知乎自己的数据目录），
 * JVM 单测用内存表，默认空实现保证单测里不会碰盘。
 */
public final class ResolutionCache {
    public interface Store {
        String get(String key);

        void put(String key, String value);

        void clear();
    }

    private static final Store NO_OP = new Store() {
        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public void put(String key, String value) {
        }

        @Override
        public void clear() {
        }
    };

    private static volatile Store store = NO_OP;
    private static volatile String fingerprint = "";

    private ResolutionCache() {
    }

    /**
     * 接入持久化存储。指纹与上次不同则清空旧缓存。
     * 返回是否沿用（true=同一份知乎版本+名单，缓存有效）。
     */
    public static synchronized boolean attach(Store newStore, String newFingerprint) {
        store = newStore == null ? NO_OP : newStore;
        String previous = store.get(KEY_FINGERPRINT);
        boolean reusable = newFingerprint != null && newFingerprint.equals(previous);
        if (!reusable) {
            store.clear();
            store.put(KEY_FINGERPRINT, newFingerprint);
        }
        fingerprint = newFingerprint == null ? "" : newFingerprint;
        return reusable;
    }

    /** 把缓存命中的候选挪到最前面；没有缓存则原样返回。 */
    public static String[] prioritize(String registryKey, String[] candidates) {
        String cached = store.get(registryKey);
        if (cached == null || candidates == null || candidates.length < 2) {
            return candidates;
        }
        if (!cached.equals(candidates[0]) && contains(candidates, cached)) {
            String[] ordered = new String[candidates.length];
            ordered[0] = cached;
            int index = 1;
            for (String candidate : candidates) {
                if (!cached.equals(candidate)) {
                    ordered[index++] = candidate;
                }
            }
            return ordered;
        }
        return candidates;
    }

    /** 记录某个 key 这次命中的候选。 */
    public static void remember(String registryKey, String candidate) {
        if (registryKey == null || candidate == null) {
            return;
        }
        store.put(registryKey, candidate);
    }

    /** 当前缓存里记着的候选（单测与排障用）。 */
    public static String peek(String registryKey) {
        return store.get(registryKey);
    }

    public static String fingerprint() {
        return fingerprint;
    }

    private static boolean contains(String[] candidates, String value) {
        Set<String> set = new LinkedHashSet<>();
        for (String candidate : candidates) {
            set.add(candidate);
        }
        return set.contains(value);
    }

    /** 单测：换回空实现并清空指纹。 */
    static void reset() {
        store = NO_OP;
        fingerprint = "";
    }

    private static final String KEY_FINGERPRINT = "fingerprint";
}
