package com.shatyuka.zhiliao;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import com.shatyuka.zhiliao.hooks.AnswerAd;
import com.shatyuka.zhiliao.hooks.AnswerListAd;
import com.shatyuka.zhiliao.hooks.AntiTracking;
import com.shatyuka.zhiliao.hooks.Article;
import com.shatyuka.zhiliao.hooks.AutoRefresh;
import com.shatyuka.zhiliao.hooks.Cleaner;
import com.shatyuka.zhiliao.hooks.ColorMode;
import com.shatyuka.zhiliao.hooks.CommentAd;
import com.shatyuka.zhiliao.hooks.CustomFilter;
import com.shatyuka.zhiliao.hooks.ExternLink;
import com.shatyuka.zhiliao.hooks.FeedAd;
import com.shatyuka.zhiliao.hooks.FeedTopHotBanner;
import com.shatyuka.zhiliao.hooks.FollowButton;
import com.shatyuka.zhiliao.hooks.FullScreen;
import com.shatyuka.zhiliao.hooks.HeadZoneBanner;
import com.shatyuka.zhiliao.hooks.HotBanner;
import com.shatyuka.zhiliao.hooks.IHook;
import com.shatyuka.zhiliao.hooks.LaunchAd;
import com.shatyuka.zhiliao.hooks.MineHybridView;
import com.shatyuka.zhiliao.hooks.NavButton;
import com.shatyuka.zhiliao.hooks.NavRes;
import com.shatyuka.zhiliao.hooks.NextAnswer;
import com.shatyuka.zhiliao.hooks.RedDot;
import com.shatyuka.zhiliao.hooks.SearchAd;
import com.shatyuka.zhiliao.hooks.ShareAd;
import com.shatyuka.zhiliao.hooks.StatusBar;
import com.shatyuka.zhiliao.hooks.Tag;
import com.shatyuka.zhiliao.hooks.ThirdPartyLogin;
import com.shatyuka.zhiliao.hooks.VIPBanner;
import com.shatyuka.zhiliao.hooks.ImageSource;
import com.shatyuka.zhiliao.hooks.Watermark;
import com.shatyuka.zhiliao.hooks.WebView;
import com.shatyuka.zhiliao.xposed.XposedBridge;

public class Hooks {
    /** 失败 → 设置项 的账本，见 HookStatus。 */
    static final HookStatus status = new HookStatus();

    static final IHook[] hooks = {
            new LaunchAd(),
            new CustomFilter(),
            new FeedAd(),
            new AnswerListAd(),
            new CommentAd(),
            new AnswerAd(),
            new ShareAd(),
            new NextAnswer(),
            new RedDot(),
            new ExternLink(),
            new VIPBanner(),
            new NavButton(),
            new HotBanner(),
            new ColorMode(),
            new Article(),
            new Tag(),
            new SearchAd(),
            new StatusBar(),
            new ThirdPartyLogin(),
            new NavRes(),
            new WebView(),
            new Cleaner(),
            new FeedTopHotBanner(),
            new HeadZoneBanner(),
            new MineHybridView(),
            new FollowButton(),
            new FullScreen(),
            new AutoRefresh(),
            new ImageSource(),
            new Watermark(),
            new AntiTracking(),
    };

    public static void init(final ClassLoader classLoader) {
        final boolean verboseSelfCheck = Helper.prefs.getBoolean("switch_hooktrace", false);
        status.reset();
        StringBuilder failed = new StringBuilder();
        int ok = 0;
        for (IHook hook : hooks) {
            int before = XposedBridge.getHookCount();
            // 每条 Hook 归到自己功能名下，运行期"命中"日志才看得出是谁拦的
            XposedBridge.setCurrentFeature(hook.getName());
            try {
                hook.init(classLoader);
            } catch (Throwable e) {
                reportFailure(hook, "目标定位", e);
                failed.append(hook.getName()).append("(定位) ");
                status.markUnsupported(hook.getName());
                continue;
            }
            try {
                hook.hook();
                ok++;
            } catch (Throwable e) {
                reportFailure(hook, "Hook安装", e);
                failed.append(hook.getName()).append("(安装) ");
                status.markUnsupported(hook.getName());
            }
            XposedBridge.log("[Zhiliao][自检] 安装 " + hook.getName() + " 句柄=+" + (XposedBridge.getHookCount() - before));
        }
        XposedBridge.setCurrentFeature(null);
        okCount = ok;
        failedFeatures = failed.toString().trim();
        XposedBridge.log("[Zhiliao][自检] " + statusLine()
                + " Hook句柄=" + XposedBridge.getHookCount()
                + (failed.length() == 0 ? " 全部可用" : " 失败=" + failed));
        if (verboseSelfCheck) {
            scheduleSelfCheckSummary();
        }
    }

    /** 一行自检结论，日志与 App 回执共用。 */
    public static String statusLine() {
        return "知乎 " + Helper.packageInfo.versionName + "/" + Helper.versionCode
                + " 初始化成功=" + okCount + "/" + hooks.length
                + (failedFeatures.isEmpty() ? "" : " 失败=" + failedFeatures);
    }

    public static int okCount() {
        return okCount;
    }

    public static int totalCount() {
        return hooks.length;
    }

    /** 当前知乎版本上装不起来的功能，它们负责的设置项（逗号分隔），供设置 App 置灰。 */
    public static String failedKeysLine() {
        return status.failedKeysLine();
    }

    private static volatile int okCount;
    private static volatile String failedFeatures = "";

    /** 装完两分钟后回头看：哪些目标一次都没进过——"签名对、语义变了"就露在这里。 */
    private static void scheduleSelfCheckSummary() {
        try {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                XposedBridge.log("[Zhiliao][自检] 运行 2 分钟回顾：" + SelfCheck.summary());
            }, SELF_CHECK_DELAY_MS);
        } catch (Throwable e) {
            XposedBridge.log("[Zhiliao][自检] 延迟回顾不可用: " + e);
        }
    }

    private static final long SELF_CHECK_DELAY_MS = 120_000L;

    private static void reportFailure(IHook hook, String phase, Throwable error) {
        if (!Helper.prefs.getBoolean("switch_hidetoast", false)) {
            Helper.toast(hook.getName() + "功能加载失败，可能不支持当前版本知乎: "
                    + Helper.packageInfo.versionName, Toast.LENGTH_LONG);
        }
        XposedBridge.log("[Zhiliao][兼容性][" + Helper.packageInfo.versionName + "/"
                + Helper.versionCode + "][" + hook.getName() + "][" + phase + "] " + error);
        XposedBridge.log(error);
    }
}

