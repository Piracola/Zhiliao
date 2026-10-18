package com.shatyuka.zhiliao;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.webkit.WebViewClient;
import android.widget.Toast;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Helper {
    public static Class<?> MorphAdHelper;
    public static Class<?> AnswerPagerFragment;
    public static Class<?> IZhihuWebView;
    public static Class<?> WebViewClientWrapper;

    public static Field DataUnique_type;

    public static Pattern regex_title;
    public static Pattern regex_author;
    public static Pattern regex_content;

    public static float scale;

    @SuppressLint("StaticFieldLeak")
    public static Context context;
    public static SharedPreferences prefs;
    public static Resources modRes;
    public static PackageInfo packageInfo;
    public static int versionCode;

    public static boolean officialZhihu = true;

    /** 渠道：Play 包有 split APK（splitSourceDirs 非空），国内包为整体 APK。混淆名差异主要由此产生。 */
    public static String channel = "domestic";

    public final static String hookPackage = "com.zhihu.android";
    public final static String REMOTE_PREFS_GROUP = "zhiliao_preferences";
    public final static String ACTION_RESTART_ZHIHU = "com.shatyuka.zhiliao.action.RESTART_ZHIHU";
    public final static String ACTION_CLEAN_CACHE = "com.shatyuka.zhiliao.action.CLEAN_CACHE";
    /** App 发起的自检问询；知乎侧以有序广播回执的形式把状态塞回 result extras。 */
    public final static String ACTION_STATUS = "com.shatyuka.zhiliao.action.STATUS";
    public final static String RESTART_PERMISSION = "com.shatyuka.zhiliao.permission.RESTART_ZHIHU";
    private final static byte[] signature = new byte[]{(byte) 0xB6, (byte) 0xF9, (byte) 0x97, (byte) 0xE3, (byte) 0x82, 0x7B, (byte) 0xE1, 0x1A, (byte) 0xF2, (byte) 0xFA, 0x4A, 0x15, 0x3F, (byte) 0xEA, 0x3F, (byte) 0xE6, 0x27, 0x68, 0x66, 0x02};

    /** @noinspection RedundantSuppression*/
    @SuppressWarnings("deprecation")
    static boolean init(ClassLoader classLoader, SharedPreferences remotePreferences) {
        try {
            // 远端偏好是框架侧给的，服务没就绪时会拿到 null。这时候不能带病往下走：
            // withDebugOverrides 的包装类会把 NPE 推迟到第一次读配置，最后整条初始化白跑，
            // 用户只看到「初始化失败」。直接退回只读调试覆盖的空偏好，功能照旧按默认值工作。
            if (remotePreferences == null) {
                XposedBridge.log("[Zhiliao][自检] 远端偏好不可用，本次退回默认配置（开关读写都不生效）");
                // 空文件即可：没人往里写，读到的一律是各 Hook 自己的默认值。
                remotePreferences = context.getSharedPreferences("zhiliao_unavailable", Context.MODE_PRIVATE);
            }
            // 候选名单（第一阶段）：内置 asset 永远兜底
            CandidateRegistry.initialize(modRes != null ? modRes.getAssets() : null);
            // 云端配置（第五阶段）：App 侧验签通过后写进远端偏好，只有比内置新才采纳。
            // Hook 侧不重做验签——这份值只有本模块 App（同签名）能写，写进来的已经是校验过的 payload。
            applyCloudCompatibility(remotePreferences);

            initSharedClasses(classLoader);

            // 配置通道：LSPosed 远端偏好（模块 App 写、Hook 侧只读）。
            // 旧版设置文件不再参与读取——只提示一次，让用户在新设置页重新确认。
            detectLegacyPreferences();
            // 命令行调试口：adb 发广播改开关，覆盖值优先于远端偏好
            DebugBridge.install(context);
            prefs = withDebugOverrides(remotePreferences);

            packageInfo = context.getPackageManager().getPackageInfo("com.zhihu.android", 0);
            versionCode = packageInfo.versionCode;
            channel = packageInfo.applicationInfo != null && packageInfo.applicationInfo.splitSourceDirs != null
                    ? "play" : "domestic";

            attachResolutionCache();

            regex_title = compileRegex(prefs.getString("edit_title", ""));
            regex_author = compileRegex(prefs.getString("edit_author", ""));
            regex_content = compileRegex(prefs.getString("edit_content", ""));

            scale = context.getResources().getDisplayMetrics().density;

            return true;
        } catch (Exception e) {
            XposedBridge.log("[Zhiliao] " + e);
            XposedBridge.log(e);
            return false;
        }
    }

    /** 读取顺序：命令行覆盖 > 远端偏好。Hook 侧不写配置，写入直接转给远端（远端本身也是只读的）。 */
    private static SharedPreferences withDebugOverrides(SharedPreferences remote) {
        return new SharedPreferences() {
            @Override
            public java.util.Map<String, ?> getAll() {
                java.util.Map<String, Object> merged = new java.util.HashMap<>(remote.getAll());
                merged.putAll(DebugBridge.overrides());
                return merged;
            }

            @Override
            public String getString(String key, String defValue) {
                Object value = DebugBridge.override(key);
                return value instanceof String ? (String) value : remote.getString(key, defValue);
            }

            @Override
            public java.util.Set<String> getStringSet(String key, java.util.Set<String> defValues) {
                Object value = DebugBridge.override(key);
                if (value instanceof java.util.Set) {
                    @SuppressWarnings("unchecked")
                    java.util.Set<String> casted = (java.util.Set<String>) value;
                    return casted;
                }
                return remote.getStringSet(key, defValues);
            }

            @Override
            public int getInt(String key, int defValue) {
                Object value = DebugBridge.override(key);
                return value instanceof Integer ? (Integer) value : remote.getInt(key, defValue);
            }

            @Override
            public long getLong(String key, long defValue) {
                Object value = DebugBridge.override(key);
                return value instanceof Long ? (Long) value : remote.getLong(key, defValue);
            }

            @Override
            public float getFloat(String key, float defValue) {
                Object value = DebugBridge.override(key);
                return value instanceof Float ? (Float) value : remote.getFloat(key, defValue);
            }

            @Override
            public boolean getBoolean(String key, boolean defValue) {
                Object value = DebugBridge.override(key);
                return value instanceof Boolean ? (Boolean) value : remote.getBoolean(key, defValue);
            }

            @Override
            public boolean contains(String key) {
                return DebugBridge.override(key) != null || remote.contains(key);
            }

            @Override
            public Editor edit() {
                return remote.edit();
            }

            @Override
            public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
                remote.registerOnSharedPreferenceChangeListener(listener);
            }

            @Override
            public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
                remote.unregisterOnSharedPreferenceChangeListener(listener);
            }
        };
    }

    /** 云端兼容性配置：payload 与 revision 由设置 App 验签通过后写入远端偏好，只有比内置新才采纳。 */
    private static void applyCloudCompatibility(SharedPreferences remote) {
        try {
            if (remote == null) {
                return;
            }
            String payload = remote.getString("cloud_compatibility", null);
            int revision = remote.getInt("cloud_revision", 0);
            int builtin = CandidateRegistry.getRevision();
            if (payload == null || payload.isEmpty() || !CompatibilityEnvelope.isNewer(builtin, revision)) {
                return;
            }
            if (CandidateRegistry.install(payload)) {
                XposedBridge.log("[Zhiliao][云端] 采纳兼容性配置 revision=" + CandidateRegistry.getRevision()
                        + "（内置 " + builtin + "）");
            }
        } catch (Throwable e) {
            XposedBridge.log("[Zhiliao][云端] 配置采纳失败，继续用内置名单: " + e);
        }
    }

    /**
     * 旧版设置只做检测，不再默默生效。
     *
     * 老版本的开关写在知乎自己的数据目录（zhiliao_preferences.xml），Hook 侧是唯一读得到它的地方；
     * 现在两侧只认远端偏好，第一次发现旧文件时提示一次，请用户在新设置页重新确认。
     *
     * 之前是"远端缺键就回落旧文件"，结果是 App 按默认值显示全关、Hook 按旧文件里的开关干活
     * （旧配置里开着的过滤视频/过滤文章/禁止自动刷新就够让首页空着），
     * 用户看到的是一个"设置全关但首页空白"的知乎。
     */
    private static void detectLegacyPreferences() {
        try {
            SharedPreferences legacy = context.getSharedPreferences("zhiliao_preferences", Context.MODE_PRIVATE);
            int count = legacy.getAll().size();
            if (count == 0) {
                return;
            }
            SharedPreferences local = context.getSharedPreferences("zhiliao_local", Context.MODE_PRIVATE);
            if (local.getBoolean("legacy_notice_shown", false)) {
                return;
            }
            local.edit().putBoolean("legacy_notice_shown", true).apply();
            XposedBridge.log("[Zhiliao][自检] 发现旧版设置 " + count + " 项，读穿透已停用，以新设置页为准");
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                    android.widget.Toast.makeText(context,
                            "知了：检测到旧版设置 " + count + " 项，开关以新设置页为准，请重新确认",
                            android.widget.Toast.LENGTH_LONG).show());
        } catch (Throwable e) {
            XposedBridge.log("[Zhiliao] 旧版设置检测失败: " + e);
        }
    }

    public static void initSharedClasses(ClassLoader classLoader) throws Exception {
        MorphAdHelper = loadClassOrNull(classLoader, "com.zhihu.android.morph.ad.utils.MorphAdHelper");
        AnswerPagerFragment = loadClassOrNull(classLoader, "com.zhihu.android.answer.module.pager.AnswerPagerFragment");
        try {
            IZhihuWebView = classLoader.loadClass("com.zhihu.android.app.mercury.api.IZhihuWebView");
        } catch (ReflectiveOperationException | LinkageError ignore) {
            try {
                IZhihuWebView = classLoader.loadClass("com.zhihu.android.app.search.ui.widget.SearchResultLayout").getDeclaredField("c").getType();
            } catch (ReflectiveOperationException | LinkageError ignored) {
                IZhihuWebView = null;
            }
        }
        WebViewClientWrapper = findClass(classLoader, "com.zhihu.android.app.mercury.web.", 0, 3,
                (Class<?> clazz) -> clazz.getSuperclass() == WebViewClient.class);
        try {
            Class<?> dataUnique = classLoader.loadClass("com.zhihu.android.api.model.template.DataUnique");
            DataUnique_type = dataUnique.getField("type");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            DataUnique_type = null;
        }
    }

    private static Class<?> loadClassOrNull(ClassLoader classLoader, String name) {
        try {
            return classLoader.loadClass(name);
        } catch (ClassNotFoundException | LinkageError ignored) {
            return null;
        }
    }

    public static <T> T requireTarget(T target, String description) throws ClassNotFoundException {
        if (target == null) {
            throw new ClassNotFoundException(description);
        }
        return target;
    }

    /** 自定义过滤的正则上限：超过这个长度直接判无效，不交给 Pattern 去赌。 */
    public static final int MAX_REGEX_LENGTH = 512;

    public static Pattern compileRegex(String regex) {
        if (regex == null || regex.isEmpty()) {
            return null;
        }
        if (regex.length() > MAX_REGEX_LENGTH) {
            XposedBridge.log("[Zhiliao][自定义过滤] 正则长度 " + regex.length() + " 超过上限 " + MAX_REGEX_LENGTH + "，本条已停用");
            return null;
        }
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException ignore) {
            return null;
        }
    }

    @SuppressWarnings({"JavaReflectionMemberAccess", "deprecation"})
    @SuppressLint("DiscouragedPrivateApi")
    static Resources getModuleRes(String path) throws Throwable {
        AssetManager assetManager = AssetManager.class.newInstance();
        AssetManager.class.getDeclaredMethod("addAssetPath", String.class).invoke(assetManager, path);
        return new Resources(assetManager, null, null);
    }

    public static boolean getDarkMode() {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void doRestart(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(context.getPackageName());
            if (launchIntent != null) {
                Intent restartIntent = Intent.makeRestartActivityTask(launchIntent.getComponent());
                restartIntent.setPackage(context.getPackageName());
                context.startActivity(restartIntent);
                System.exit(0);
            }
        } catch (Exception ignored) {
        }
    }

    public static boolean deleteDirectory(File dirFile) {
        if (!dirFile.exists() || !dirFile.isDirectory()) {
            return false;
        }
        boolean result;
        File[] files = dirFile.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    result = new File(file.getAbsolutePath()).delete();
                    if (!result) return false;
                } else if (file.isDirectory()) {
                    result = deleteDirectory(file.getAbsolutePath());
                    if (!result) return false;
                }
            }
        }
        return dirFile.delete();
    }

    public static boolean deleteDirectory(String filePath) {
        return deleteDirectory(new File(filePath));
    }

    public static void toast(CharSequence text, int duration) {
        Toast toast = Toast.makeText(context, "", duration);
        toast.setText("知了：" + text);
        toast.show();
    }

    public interface IClassCheck {
        boolean check(Class<?> clazz) throws Exception;
    }

    public static Class<?> findClass(ClassLoader classLoader, String beginWith, IClassCheck check) {
        return findClass(classLoader, beginWith, 0, 1, check);
    }

    public static Class<?> findClass(ClassLoader classLoader, String beginWith, int cycleStart, int cycleRound, IClassCheck check) {
        return TargetResolver.findObfuscatedClass(classLoader, beginWith, cycleStart, cycleRound, check::check);
    }

    public static Method getMethodByParameterTypes(Class<?> clazz, Class<?>... parameterTypes) {
        if (clazz == null) return null;
        for (Method method : clazz.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (Arrays.equals(types, parameterTypes)) {
                return method;
            }
        }
        return null;
    }

    public static Method getMethodByParameterTypes(Class<?> clazz, int skip, Class<?>... parameterTypes) {
        if (clazz == null) return null;
        int count = 0;
        Method[] methods = clazz.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (Method method : methods) {
            Class<?>[] types = method.getParameterTypes();
            if (Arrays.equals(types, parameterTypes)) {
                if (count < skip) {
                    count++;
                    continue;
                }
                return method;
            }
        }
        return null;
    }

    public static Field findFieldByType(Class<?> clazz, Class<?> type) {
        Optional<Field> fieldOptional = Arrays.stream(clazz.getDeclaredFields())
                .filter(f -> f.getType() == type).findFirst();
        if (!fieldOptional.isPresent())
            return null;

        Field field = fieldOptional.get();
        field.setAccessible(true);
        return field;
    }

    /** @noinspection RedundantSuppression*/
    @SuppressWarnings("deprecation")
    private static Signature[] getSignatures(Context context) throws PackageManager.NameNotFoundException {
        PackageManager pm = context.getPackageManager();
        Signature[] sig;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            android.content.pm.SigningInfo sign = pm.getPackageInfo(hookPackage, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo;
            sig = sign.hasMultipleSigners() ? sign.getApkContentsSigners() : sign.getSigningCertificateHistory();
        } else {
            sig = pm.getPackageInfo(hookPackage, PackageManager.GET_SIGNATURES).signatures;
        }
        return sig;
    }

    /**
     * 解析缓存接盘：缓存写在知乎自己的数据目录（Hook 侧唯一的可写位置，远端偏好只读）。
     * 指纹 = 知乎 versionCode + 名单 revision + APK 的 size^mtime，任一变化整份作废。
     */
    private static void attachResolutionCache() {
        try {
            String fingerprint = versionCode + ":" + CandidateRegistry.getRevision() + ":" + apkStamp();
            boolean reused = ResolutionCache.attach(
                    new PrefsResolutionStore(context.getSharedPreferences("zhiliao_resolution", Context.MODE_PRIVATE)),
                    fingerprint);
            XposedBridge.log("[Zhiliao][自检] 解析缓存 指纹=" + fingerprint + " 沿用上次=" + reused);
        } catch (Throwable e) {
            XposedBridge.log("[Zhiliao] 解析缓存不可用: " + e);
        }
    }

    private static long apkStamp() {
        try {
            File apk = new File(packageInfo.applicationInfo.sourceDir);
            return apk.length() ^ apk.lastModified();
        } catch (Throwable e) {
            return 0L;
        }
    }

    private static final class PrefsResolutionStore implements ResolutionCache.Store {
        private final SharedPreferences target;

        PrefsResolutionStore(SharedPreferences target) {
            this.target = target;
        }

        @Override
        public String get(String key) {
            return target.getString(key, null);
        }

        @Override
        public void put(String key, String value) {
            target.edit().putString(key, value).apply();
        }

        @Override
        public void clear() {
            target.edit().clear().apply();
        }
    }

    public static boolean checkSignature(Context context) {
        try {
            Signature[] sig = getSignatures(context);
            MessageDigest md = MessageDigest.getInstance("SHA1");
            for (Signature s : sig) {
                byte[] dig = md.digest(s.toByteArray());
                if (Arrays.equals(dig, signature)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
