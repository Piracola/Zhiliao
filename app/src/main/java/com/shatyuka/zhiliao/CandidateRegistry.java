package com.shatyuka.zhiliao;

import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;

/**
 * 内置候选名单（assets/compatibility/candidates-v1.json）的加载与查询。
 *
 * 设计边界（对齐路线图第一阶段）：
 * - 名单只提供"候选符号的尝试顺序"，不做任何执行逻辑；每个候选必须通过调用方的
 *   验证器（类型/签名/可访问性）才算命中，第一个全过的才用——替换 first-match 习惯。
 * - 文件损坏、超限或格式不对时保持空表：Hook 走各自原有的结构化解析，行为与
 *   没有名单时一致（fail-closed，绝不让坏数据扩大权限）。
 * - 云端下发是第五阶段的事；本类先只读内置资产，接口按可替换来源设计。
 */
public final class CandidateRegistry {
    public static final int SCHEMA_VERSION = 1;
    static final String ASSET_PATH = "compatibility/candidates-v1.json";
    static final int MAX_BYTES = 256 * 1024;
    static final int MAX_KEYS = 256;
    static final int MAX_CANDIDATES = 64;
    static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_.]{1,127}");
    /**
     * 候选既可以是方法名/字段名，也可以是全限定类名（混淆后同一个类在不同渠道包里落在
     * 不同包，只有全名能表达），所以允许点号；长度封顶防止脏数据塞长串。
     */
    static final Pattern CANDIDATE = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$.]{0,199}");

    private static volatile Map<String, String[]> symbols = Collections.emptyMap();
    private static volatile int revision;

    private CandidateRegistry() {
    }

    /** 读取内置名单。失败时保留当前内容（首次即空表），返回是否加载成功。 */
    public static synchronized boolean initialize(@Nullable AssetManager assets) {
        if (assets == null) {
            return false;
        }
        String json;
        try (InputStream in = assets.open(ASSET_PATH)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_BYTES) {
                    return false;
                }
            }
            json = new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Throwable e) {
            return false;
        }
        return install(json);
    }

    /** 解析并整体换装；任何解析错误都拒绝生效（保持旧表），返回是否成功。 */
    static synchronized boolean install(String json) {
        try {
            Map<String, String[]> parsed = parse(json);
            symbols = parsed;
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    /** 供单测与未来的云端通道复用；解析失败抛 JSONException/IllegalArgumentException。 */
    public static Map<String, String[]> parse(String json) throws org.json.JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
            throw new IllegalArgumentException("schemaVersion 不支持");
        }
        int parsedRevision = root.optInt("revision", 0);
        if (parsedRevision <= 0) {
            throw new IllegalArgumentException("revision 非法");
        }
        JSONObject symbolRoot = root.optJSONObject("symbols");
        if (symbolRoot == null) {
            throw new IllegalArgumentException("缺少 symbols");
        }
        if (symbolRoot.length() > MAX_KEYS) {
            throw new IllegalArgumentException("symbols 超限");
        }
        Map<String, String[]> parsed = new HashMap<>();
        java.util.Iterator<String> keys = symbolRoot.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (!KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("key 非法: " + key);
            }
            JSONArray array = symbolRoot.optJSONArray(key);
            if (array == null || array.length() == 0 || array.length() > MAX_CANDIDATES) {
                throw new IllegalArgumentException("候选列表非法: " + key);
            }
            String[] candidates = new String[array.length()];
            for (int i = 0; i < array.length(); i++) {
                String candidate = array.optString(i, "");
                if (!CANDIDATE.matcher(candidate).matches()) {
                    throw new IllegalArgumentException("候选名非法: " + key);
                }
                candidates[i] = candidate;
            }
            parsed.put(key, candidates);
        }
        revision = parsedRevision;
        return Collections.unmodifiableMap(parsed);
    }

    /** 返回候选顺序的副本；未配置返回 null（调用方走原有解析）。 */
    @Nullable
    public static String[] candidates(String key) {
        String[] value = symbols.get(key);
        return value == null ? null : value.clone();
    }

    public static int getRevision() {
        return revision;
    }

    /** 单测复位。 */
    static synchronized void reset() {
        symbols = Collections.emptyMap();
        revision = 0;
    }
}
