package com.shatyuka.zhiliao;

import android.widget.Toast;
import com.shatyuka.zhiliao.hooks.AnswerAd;
import com.shatyuka.zhiliao.hooks.AnswerListAd;
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
import com.shatyuka.zhiliao.hooks.ZhihuPreference;
import com.shatyuka.zhiliao.xposed.XposedBridge;

public class Hooks {
    static final IHook[] hooks = {
            new ZhihuPreference(),
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
    };

    public static void init(final ClassLoader classLoader) {
        XposedBridge.setTraceEnabled(Helper.prefs.getBoolean("switch_hooktrace", false));
        StringBuilder failed = new StringBuilder();
        int ok = 0;
        for (IHook hook : hooks) {
            int before = XposedBridge.getHookCount();
            try {
                hook.init(classLoader);
            } catch (Throwable e) {
                reportFailure(hook, "目标定位", e);
                failed.append(hook.getName()).append("(定位) ");
                continue;
            }
            try {
                hook.hook();
                ok++;
            } catch (Throwable e) {
                reportFailure(hook, "Hook安装", e);
                failed.append(hook.getName()).append("(安装) ");
            }
            XposedBridge.log("[Zhiliao][自检] 安装 " + hook.getName() + " 句柄=+" + (XposedBridge.getHookCount() - before));
        }
        XposedBridge.log("[Zhiliao][自检] 知乎 " + Helper.packageInfo.versionName + "/" + Helper.versionCode
                + " Hook句柄=" + XposedBridge.getHookCount() + " 初始化成功=" + ok + "/" + hooks.length
                + (failed.length() == 0 ? " 全部可用" : " 失败=" + failed));
    }

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
