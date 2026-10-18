package com.shatyuka.zhiliao.hooks;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XC_MethodReplacement;
import com.shatyuka.zhiliao.xposed.XposedBridge;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 分享链路的追踪点（11.10.0 静态审计，docs/tracking-audit-11.10.0.md），共六路：
 * 1. 分享链接拼参：sharecore.v.o 的三个静态拼参方法往 URL 追加 utm_medium=social、
 *    utm_content=select_word/longpic 等参数 —— 拦下后改为剥离全部 utm_* 与 share_code；
 * 2. 分享点击上报：分享面板每个按钮 onClick 都把按钮名 + getZaData() 交给埋点器；
 * 3. 分享回执：收到分享回跳后取 share_code/utm_psn 回传分享上报接口；
 * 4. 图片分享二维码：QR 码内容指向 api.zhihu.com 的 image_share/qrcode，服务端可把扫码与分享者关联；
 * 5. 打开他人分享链接：RouterPortalLegacyInterceptor 检测 utm_oi 后伪造一次虚拟分享页浏览；
 * 6. 总闸（双保险）：所有 Za 埋点经 LogRecorderImpl.record 入库、由 ZaNewUploadManager 批量上传，
 *    record 置空即全站埋点不再落库；ZaLogHandlerImpl.inQueue 是入库任务的直接入口，作为第二道闸。
 * 7. 分享卡二维码：图片来自 api.zhihu.com/image_share/qrcode?content=…，服务端会把 content
 *    缩成 s.zhihu.com 短链再编码（见 docs/tracking-audit-11.10.0.md §八），改参数无效；
 *    所以在 ZHDraweeView#setImageURI 处把图换成按干净链接本地编码的二维码（installLocalQrRenderer）。
 * 8. 分享链接兜底：拦剪贴板写入与分享 Intent 的文本，按正则认知乎链接再剥参数（installTextCleaners）。
 */
public class AntiTracking implements IHook {
    static Class<?> Za;
    static Class<?> LogRecorderImpl;
    static Class<?> ZaLogHandlerImpl;
    static Class<?> ShareCoreVO;
    static Class<?> AbsSharable;
    static Class<?> ShareCoreUD;
    static Class<?> SharePortalHandlerImpl;
    static Class<?> ZABean;
    static Class<?> RxObservable;

    static Method LogRecorderImpl_record;
    static Method ZaLogHandlerImpl_inQueue;
    static Method[] VO_utmAdders;
    static Method[] VO_reportSenders;
    static Method VO_receiveReport;
    static Method[] UD_reportSenders;
    static Method AbsSharable_getZaData;
    static Method SharePortalHandlerImpl_handleReport;
    static Method Observable_empty;

    /** 文本级净化的落点：剪贴板写入、分享 Intent 的文本 extra —— 都是系统类，不受混淆影响 */
    static Method Clipboard_setPrimaryClip;
    static Method[] Intent_textPutters = new Method[0];
    static Method Intent_putExtras;

    /** 二维码最终是画在图片控件上的，这里直接把本地生成的码塞进去，绕开服务端的短链 */
    static Class<?> ZHDraweeCls;
    static Method[] ZHDrawee_setImageURI = new Method[0];

    /** 分享文本里能认出来的知乎链接；不含空格与常见中英文标点，够用且不会贪婪吞掉后文 */
    static final Pattern ZHIHU_URL = Pattern.compile(
            "(?i)https?://(?:[a-z0-9-]+\\.)*zhihu\\.com/[^\\s\"'<>()（）【】\\u3000，。；！？、]*");

    /** 转发型短链：解出 target 参数，还原成真正的目标地址 */
    static final Pattern REDIRECT_URL = Pattern.compile(
            "(?i)^https?://(?:link|s)\\.zhihu\\.com/[^#]*?[?&]target=([^&#]+)");

    static final Set<String> TRACKING_PARAMS = new HashSet<>(Arrays.asList(
            "utm_source", "utm_medium", "utm_term", "utm_content", "utm_campaign", "utm_id",
            "utm_oi", "utm_psn", "utm_suid", "share_code", "share_from", "s_s_i", "s_r"));

    static final java.util.concurrent.atomic.AtomicInteger qrLogs =
            new java.util.concurrent.atomic.AtomicInteger();
    static final java.util.concurrent.atomic.AtomicInteger qrFailLogs =
            new java.util.concurrent.atomic.AtomicInteger();
    static final java.util.concurrent.atomic.AtomicInteger voLogs =
            new java.util.concurrent.atomic.AtomicInteger();

    /** 被我们画过二维码的控件：卡片列表复用控件时要把背景擦掉，不然码会跟着列表跑。 */
    static final Set<View> qrPainted =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<View, Boolean>());

    @Override
    public String getName() {
        return "反追踪";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        Za = load(classLoader, "com.zhihu.android.za.Za");
        LogRecorderImpl = load(classLoader, "com.zhihu.android.za.model.LogRecorderImpl");
        ZaLogHandlerImpl = load(classLoader, "com.zhihu.android.za.model.loghandler.ZaLogHandlerImpl");
        ShareCoreVO = load(classLoader, "com.zhihu.android.library.sharecore.v.o");
        AbsSharable = load(classLoader, "com.zhihu.android.library.sharecore.AbsSharable");
        ShareCoreUD = load(classLoader, "com.zhihu.android.library.sharecore.u.d");
        SharePortalHandlerImpl = load(classLoader, "com.zhihu.android.library.sharecore.SharePortalHandlerImpl");
        ZABean = load(classLoader, "com.zhihu.android.library.sharecore.model.ZABean");
        RxObservable = load(classLoader, "io.reactivex.Observable");

        if (Za == null && ShareCoreVO == null)
            throw new ClassNotFoundException("com.zhihu.android.za.Za");

        if (RxObservable != null) {
            Observable_empty = RxObservable.getMethod("empty");
        }

        if (LogRecorderImpl != null) {
            LogRecorderImpl_record = findMethod(LogRecorderImpl, "record", 1, void.class);
        }

        // 第二道闸：入库任务（ZaEnqueueTask）的直接入口，防上层漏斗被绕过
        if (ZaLogHandlerImpl != null) {
            ZaLogHandlerImpl_inQueue = findMethod(ZaLogHandlerImpl, "inQueue", 1, void.class);
        }

        if (ShareCoreVO != null) {
            VO_utmAdders = findStringAppenders(ShareCoreVO);
            VO_reportSenders = findObservableSenders(ShareCoreVO);
            VO_receiveReport = findMethod(ShareCoreVO, "h", 1, void.class);
        }

        if (ShareCoreUD != null) {
            UD_reportSenders = findObservableSenders(ShareCoreUD);
        }

        if (AbsSharable != null && ZABean != null) {
            for (Method method : AbsSharable.getDeclaredMethods()) {
                if (method.getParameterCount() == 0 && method.getReturnType() == ZABean) {
                    method.setAccessible(true);
                    AbsSharable_getZaData = method;
                    break;
                }
            }
        }

        if (SharePortalHandlerImpl != null) {
            for (Method method : SharePortalHandlerImpl.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 2 && types[0] == Intent.class && types[1] == Uri.class
                        && method.getReturnType() == void.class) {
                    method.setAccessible(true);
                    SharePortalHandlerImpl_handleReport = method;
                    break;
                }
            }
        }

        // 文本级落点：系统类，名字不会随渠道包变。分享链接无论由谁拼出来，最后都要经过这里。
        Class<?> clipboard = load(classLoader, "android.content.ClipboardManager");
        if (clipboard != null) {
            Clipboard_setPrimaryClip = findMethod(clipboard, "setPrimaryClip", 1, void.class);
        }
        Class<?> intentCls = load(classLoader, "android.content.Intent");
        if (intentCls != null) {
            List<Method> putters = new ArrayList<>();
            for (Method method : intentCls.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"putExtra".equals(method.getName()) || types.length != 2 || types[0] != String.class) {
                    continue;
                }
                if (types[1] == String.class || types[1] == CharSequence.class) {
                    method.setAccessible(true);
                    putters.add(method);
                }
            }
            Intent_textPutters = putters.toArray(new Method[0]);
            // putExtras 有两份重载（Bundle 和 Intent），返回类型一样，必须按参数类型点明是 Bundle 那份。
            for (Method method : intentCls.getDeclaredMethods()) {
                if ("putExtras".equals(method.getName()) && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == Bundle.class) {
                    method.setAccessible(true);
                    Intent_putExtras = method;
                    break;
                }
            }
        }

        // 二维码图片控件：知乎用的是自己的 ZHDraweeView，二维码走 setImageURI(String)。
        // String 那个重载可能定义在父类（Fresco 的 SimpleDraweeView）上，所以要顺着继承链找。
        ZHDraweeCls = load(classLoader, "com.zhihu.android.base.widget.ZHDraweeView");
        if (ZHDraweeCls != null) {
            List<Method> list = new ArrayList<>();
            for (Class<?> clazz = ZHDraweeCls; clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
                for (Method method : clazz.getDeclaredMethods()) {
                    if (!"setImageURI".equals(method.getName()) || method.getParameterCount() != 1
                            || method.getParameterTypes()[0] != String.class) {
                        continue;
                    }
                    method.setAccessible(true);
                    list.add(method);
                }
            }
            ZHDrawee_setImageURI = list.toArray(new Method[0]);
        }

        // 可用性按「任何一项能干活」算：埋点、拼参、剪贴板、分享文本、本地二维码，
        // 只要有一项定位成功就继续装，不能因为分享相关类名漂移把还能用的功能一起废掉。
        boolean anyTarget = LogRecorderImpl_record != null
                || (VO_utmAdders != null && VO_utmAdders.length > 0)
                || AbsSharable_getZaData != null
                || Clipboard_setPrimaryClip != null
                || Intent_textPutters.length > 0
                || ZHDrawee_setImageURI.length > 0;
        if (!anyTarget)
            throw new NoSuchMethodException("AntiTracking: 无可用定位目标");

        XposedBridge.log("[Zhiliao][自检] 反追踪 埋点=" + (LogRecorderImpl_record != null)
                + " 拼参=" + (VO_utmAdders == null ? 0 : VO_utmAdders.length)
                + " 上报=" + (VO_reportSenders == null ? 0 : VO_reportSenders.length)
                + " 剪贴板=" + (Clipboard_setPrimaryClip != null)
                + " 分享文本=" + Intent_textPutters.length
                + " 本地二维码=" + ZHDrawee_setImageURI.length);
    }

    @Override
    public void hook() throws Throwable {
        if (!Helper.prefs.getBoolean("switch_mainswitch", false) || !Helper.prefs.getBoolean("switch_antitracking", false))
            return;

        if (LogRecorderImpl_record != null) {
            XposedBridge.hookMethod(LogRecorderImpl_record, XC_MethodReplacement.returnConstant(null));
        }

        if (ZaLogHandlerImpl_inQueue != null) {
            XposedBridge.hookMethod(ZaLogHandlerImpl_inQueue, XC_MethodReplacement.returnConstant(null));
        }

        if (Za != null) {
            XposedBridge.hookAllMethods(Za, "pageShow", XC_MethodReplacement.returnConstant(null));
        }

        // 两个子开关默认跟随总开关打开，用户可以单独关掉其中一项。
        boolean cleanLink = Helper.prefs.getBoolean("switch_antitracking_link", true);
        boolean cleanQr = Helper.prefs.getBoolean("switch_antitracking_qr", true);

        replaceWithEmptyObservable(VO_reportSenders);
        replaceWithEmptyObservable(UD_reportSenders);

        if (cleanLink) {
            if (VO_utmAdders != null) {
                for (Method method : VO_utmAdders) {
                    installParamStripper(method);
                }
            }
        }

        if (VO_receiveReport != null) {
            XposedBridge.hookMethod(VO_receiveReport, XC_MethodReplacement.returnConstant(null));
        }

        if (SharePortalHandlerImpl_handleReport != null) {
            XposedBridge.hookMethod(SharePortalHandlerImpl_handleReport, XC_MethodReplacement.returnConstant(null));
        }

        if (AbsSharable_getZaData != null && ZABean != null) {
            XposedBridge.hookMethod(AbsSharable_getZaData, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return ZABean.getDeclaredConstructor().newInstance();
                }
            });
        }

        // 文本级净化：不认类名、不认方法名，只认"这串文本里有一条带追踪参数的知乎链接"。
        // 剪贴板和分享 Intent 是系统 API，渠道包怎么改名都绕不开。
        if (cleanLink) {
            installTextCleaners();
        }

        if (cleanQr) {
            installLocalQrRenderer();
        }
    }

    /**
     * 服务端把 content 缩成 s.zhihu.com 短链再编码进二维码，所以在 URL 上怎么改都没用。
     * 这里等知乎把二维码地址交给图片控件时拦下来，本地编码一张干净的码直接塞进控件，
     * 既不发请求，也不经过知乎的图片地址处理（data: URI 那条路会崩）。
     */
    private static void installLocalQrRenderer() {
        for (Method method : ZHDrawee_setImageURI) {
            XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!(param.args[0] instanceof String)) {
                        return;
                    }
                    String url = (String) param.args[0];
                    if (!url.contains("image_share") || !url.contains("content=")) {
                        // 卡片列表会复用控件：上一张卡片画的码要擦掉，否则会跟着列表滚到别处。
                        Object view = param.thisObject;
                        if (view instanceof View && qrPainted.remove(view)) {
                            ((View) view).setBackground(null);
                        }
                        return;
                    }
                    try {
                        if (renderLocalQr(param, url)) {
                            param.setResult(null);
                        }
                    } catch (Throwable e) {
                        XposedBridge.log("[Zhiliao][反追踪] 本地二维码失败：" + e);
                    }
                }
            });
        }
    }

    private static boolean renderLocalQr(XC_MethodHook.MethodHookParam param, String url) throws Exception {
        Uri uri = Uri.parse(url);
        String content = uri.getQueryParameter("content");
        if (content == null || content.isEmpty()) {
            qrFail("地址里没有 content");
            return false;
        }
        Object view = param.thisObject;
        if (!(view instanceof View)) {
            qrFail("不是 View：" + (view == null ? "null" : view.getClass().getName()));
            return false;
        }
        String clean = cleanUrl(content);
        int size = parseInt(uri.getQueryParameter("size"), 138);
        int fill = parseColor(uri.getQueryParameter("fill_color"), Color.BLACK);
        int back = parseColor(uri.getQueryParameter("back_color"), Color.WHITE);
        float density = ((View) view).getResources().getDisplayMetrics().density;
        // 服务端给的 size 是 dp，换算成像素后再夹一层：二维码控件通常不到 200dp，
        // 万一哪天给了个离谱的值，也绝不能在这条线程上分配几百 MB。
        int px = Math.min(Math.max(Math.round(size * density), 256), 768);
        Bitmap bitmap = qrBitmap(clean, px, fill, back);
        if (bitmap == null) {
            return false;
        }
        Drawable drawable = new BitmapDrawable(((View) view).getResources(), bitmap);
        // 两条路一起走：hierarchy 上的方法名被 R8 改成 a/b/c/d，只能靠签名猜，
        // 猜错（设成了 placeholder 之类）时背景这层还能兜住，不会出现一片空白。
        Object hierarchy = invokeNoArg(view, "getHierarchy");
        boolean applied = hierarchy != null && applyDrawable(hierarchy, drawable);
        ((View) view).setBackground(drawable);
        qrPainted.add((View) view);
        ((View) view).invalidate();
        if (qrLogs.getAndIncrement() < 5) {
            XposedBridge.log("[Zhiliao][反追踪] 本地二维码：" + shortUrl(clean)
                    + "（" + (applied ? "hierarchy+背景" : "背景") + "）");
        }
        return true;
    }

    /**
     * Fresco 的 setImage/setPlaceholderImage 这些方法在 R8 之后全变成了 a/b/c/d，
     * 但签名骗不了人：一个 Drawable 参数、返回 void。这类方法全调一遍，
     * 谁是真的 setImage 都无所谓 —— 关键在于调用方还会额外铺一层背景，
     * 所以就算这里猜错（比如设成了 placeholder），码也不会消失。
     */
    private static boolean applyDrawable(Object hierarchy, Drawable drawable) {
        boolean applied = false;
        for (Method method : hierarchy.getClass().getMethods()) {
            if (method.getParameterCount() != 1 || method.getReturnType() != void.class) {
                continue;
            }
            if (!method.getParameterTypes()[0].isAssignableFrom(BitmapDrawable.class)) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(hierarchy, drawable);
                applied = true;
            } catch (Throwable ignored) {
            }
        }
        return applied;
    }

    private static void qrFail(String reason) {
        if (qrFailLogs.getAndIncrement() < 5) {
            XposedBridge.log("[Zhiliao][反追踪] 本地二维码失败：" + reason);
        }
    }

    private static Bitmap qrBitmap(String content, int px, int fill, int back) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);
        try {
            com.google.zxing.common.BitMatrix matrix =
                    new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, px, px, hints);
            int width = matrix.getWidth();
            int height = matrix.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pixels[y * width + x] = matrix.get(x, y) ? fill : back;
                }
            }
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (Throwable e) {
            XposedBridge.log("[Zhiliao][反追踪] 二维码编码失败：" + e);
            return null;
        }
    }

    /** 调无参方法（getHierarchy 这类本身就是 0 参，别拿 1 参的查找器去找）。 */
    private static Object invokeNoArg(Object target, String name) {
        for (Method candidate : target.getClass().getMethods()) {
            if (!name.equals(candidate.getName()) || candidate.getParameterCount() != 0) {
                continue;
            }
            try {
                candidate.setAccessible(true);
                return candidate.invoke(target);
            } catch (Throwable e) {
                qrFail("调 " + name + " 失败：" + e.getClass().getSimpleName());
                return null;
            }
        }
        return null;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int parseColor(String value, int fallback) {
        try {
            return value == null || value.isEmpty() ? fallback : Color.parseColor(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void installTextCleaners() {
        if (Clipboard_setPrimaryClip != null) {
            XposedBridge.hookMethod(Clipboard_setPrimaryClip, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!(param.args[0] instanceof ClipData)) {
                        return;
                    }
                    ClipData cleaned = cleanClip((ClipData) param.args[0]);
                    if (cleaned != null) {
                        param.args[0] = cleaned;
                        logTextClean("剪贴板");
                    }
                }
            });
        }

        for (Method putter : Intent_textPutters) {
            XposedBridge.hookMethod(putter, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!Intent.EXTRA_TEXT.equals(param.args[0]) || !(param.args[1] instanceof CharSequence)) {
                        return;
                    }
                    CharSequence original = (CharSequence) param.args[1];
                    String cleaned = cleanShareText(original.toString());
                    if (!cleaned.contentEquals(original)) {
                        param.args[1] = cleaned;
                        logTextClean("分享文本");
                    }
                }
            });
        }

        if (Intent_putExtras != null) {
            XposedBridge.hookMethod(Intent_putExtras, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (!(param.args[0] instanceof Bundle)) {
                        return;
                    }
                    Bundle bundle = (Bundle) param.args[0];
                    CharSequence text = bundle.getCharSequence(Intent.EXTRA_TEXT);
                    if (text == null) {
                        return;
                    }
                    String cleaned = cleanShareText(text.toString());
                    if (!cleaned.contentEquals(text)) {
                        bundle.putCharSequence(Intent.EXTRA_TEXT, cleaned);
                        logTextClean("分享文本");
                    }
                }
            });
        }
    }

    private static void logTextClean(String where) {
        if (voLogs.getAndIncrement() < 12) {
            XposedBridge.log("[Zhiliao][反追踪] " + where + " 已剥离追踪参数");
        }
    }

    /** 剪贴板里每条文本都过一遍；没有任何改动就返回 null，别去动原对象。 */
    static ClipData cleanClip(ClipData data) {
        boolean changed = false;
        List<CharSequence> texts = new ArrayList<>();
        for (int i = 0; i < data.getItemCount(); i++) {
            ClipData.Item item = data.getItemAt(i);
            // 图片、文件 URI、复制的 Intent 都不能重建（ClipData.Item 是只读的），
            // 一见这类条目就整个放手，宁可这条剪贴板不清洗。
            if (item.getUri() != null || item.getIntent() != null) {
                return null;
            }
            CharSequence text = item.getText();
            if (text == null) {
                texts.add(null);
                continue;
            }
            String cleaned = cleanShareText(text.toString());
            if (!cleaned.contentEquals(text)) {
                changed = true;
                texts.add(cleaned);
            } else {
                texts.add(text);
            }
        }
        if (!changed || texts.isEmpty()) {
            return null;
        }
        android.content.ClipDescription description = data.getDescription();
        CharSequence label = description == null ? null : description.getLabel();
        ClipData out = ClipData.newPlainText(label == null ? "" : label, texts.get(0));
        for (int i = 1; i < texts.size(); i++) {
            CharSequence text = texts.get(i);
            out.addItem(text == null ? new ClipData.Item("") : new ClipData.Item(text));
        }
        return out;
    }

    /**
     * 一段文本里可能夹着标题和链接（分享到微信常见"标题 - 知乎 https://…"），
     * 所以按正则逐条替换链接，其余原样保留。
     */
    static String cleanShareText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = ZHIHU_URL.matcher(text);
        StringBuilder sb = new StringBuilder();
        boolean changed = false;
        while (matcher.find()) {
            String url = matcher.group();
            String cleaned = cleanUrl(url);
            if (!cleaned.equals(url)) {
                changed = true;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(cleaned));
        }
        if (!changed) {
            return text;
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 一条 URL：先解转发短链，再剥追踪参数。纯字符串处理，方便单测里直接跑。 */
    static String cleanUrl(String url) {
        return stripTracking(unwrapRedirect(url));
    }

    /** link.zhihu.com / s.zhihu.com 这种转发跳板：把 target 解出来，换成真正的地址。 */
    static String unwrapRedirect(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = REDIRECT_URL.matcher(url);
        if (!matcher.find()) {
            return url;
        }
        try {
            String target = java.net.URLDecoder.decode(matcher.group(1), "UTF-8");
            return target.startsWith("http") ? target : url;
        } catch (Throwable ignored) {
            return url;
        }
    }

    static boolean isTrackingParam(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return TRACKING_PARAMS.contains(lower) || lower.startsWith("utm_");
    }

    static String stripTracking(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        int start = url.indexOf('?');
        if (start < 0 || start == url.length() - 1) {
            return url;
        }
        String head = url.substring(0, start);
        String rest = url.substring(start + 1);
        String fragment = "";
        int hash = rest.indexOf('#');
        if (hash >= 0) {
            fragment = rest.substring(hash);
            rest = rest.substring(0, hash);
        }
        StringBuilder kept = new StringBuilder();
        boolean dirty = false;
        for (String pair : rest.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = eq >= 0 ? pair.substring(0, eq) : pair;
            if (isTrackingParam(percentDecode(name))) {
                dirty = true;
                continue;
            }
            if (kept.length() > 0) {
                kept.append('&');
            }
            kept.append(pair);
        }
        if (!dirty) {
            return url;
        }
        return kept.length() == 0 ? head + fragment : head + "?" + kept + fragment;
    }

    private static String percentDecode(String value) {
        try {
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (Throwable ignored) {
            return value;
        }
    }

    /** 拼参方法：入参里的分享 URL 剥掉 utm_* 与 share_code 再返回。 */
    private static void installParamStripper(Method method) {
        XposedBridge.hookMethod(method, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                Object result = stripTracking((String) param.args[0]);
                if (voLogs.getAndIncrement() < 12) {
                    XposedBridge.log("[Zhiliao][反追踪] 拼参 " + param.method.getName()
                            + " 输入=" + param.args[0] + " 输出=" + result);
                }
                return result;
            }
        });
    }

    private static String shortUrl(String url) {
        if (url == null) {
            return "null";
        }
        return url.length() <= 200 ? url : url.substring(0, 200) + "…";
    }

    private static void replaceWithEmptyObservable(Method[] methods) {
        if (methods == null || Observable_empty == null) {
            return;
        }
        for (Method method : methods) {
            XposedBridge.hookMethod(method, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return Observable_empty.invoke(null);
                }
            });
        }
    }

    /**
     * v.o 的三个拼参方法都是「静态、入参全为 String、返回 String」，按这个结构找，
     * 不依赖 b/c/d 这类会被渠道包改掉的名字。
     */
    private static Method[] findStringAppenders(Class<?> clazz) {
        List<Method> out = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isStatic(method.getModifiers()) || method.isSynthetic()) {
                continue;
            }
            if (method.getReturnType() != String.class) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            // 只认单参：拼参方法的第一个参数就是要发出去的 URL，多参时返回值不一定是那个 URL，
            // 硬替换会把别的东西丢掉。
            if (types.length != 1 || types[0] != String.class) {
                continue;
            }
            method.setAccessible(true);
            out.add(method);
        }
        return out.toArray(new Method[0]);
    }

    /**
     * 上报方法的共同形态：入参一个 String 或 HashMap、返回 RxJava Observable。
     * 同类里的 0 参 Observable 方法是分享渠道配置拉取，不属于上报，不在此列。
     */
    private static Method[] findObservableSenders(Class<?> clazz) {
        if (RxObservable == null) {
            return null;
        }
        List<Method> out = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getReturnType() != RxObservable) {
                continue;
            }
            Class<?>[] types = method.getParameterTypes();
            if (types.length == 1 && (types[0] == String.class || types[0] == java.util.HashMap.class)) {
                method.setAccessible(true);
                out.add(method);
            }
        }
        return out.toArray(new Method[0]);
    }

    static Method findMethod(Class<?> clazz, String name, int paramCount, Class<?> returnType) {
        if (clazz == null) {
            return null;
        }
        for (Method method : clazz.getDeclaredMethods()) {
            if (name.equals(method.getName()) && method.getParameterCount() == paramCount
                    && (returnType == null || method.getReturnType() == returnType)) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    static Class<?> load(ClassLoader classLoader, String name) {
        try {
            return classLoader.loadClass(name);
        } catch (Throwable e) {
            return null;
        }
    }
}
