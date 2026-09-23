package com.shatyuka.zhiliao.hooks;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.XmlResourceParser;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.inputmethod.InputMethodManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;
import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.R;
import com.shatyuka.zhiliao.TargetResolver;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XC_MethodReplacement;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import com.shatyuka.zhiliao.xposed.XposedHelpers;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Random;
import org.xmlpull.v1.XmlPullParser;

@SuppressWarnings("deprecation")
public class ZhihuPreference implements IHook {
    final static String modulePackage = "com.shatyuka.zhiliao";

    private static Object preference_zhiliao;

    private static int version_click = 0;
    private static int author_click = 0;

    private static int settings_res_id = 0;
    private static int debug_res_id = 0;

    static Class<?> SettingsFragment;
    static Class<?> DebugFragment;
    static Class<?> Preference;
    static Class<?> SwitchPreference;
    static Class<?> OnPreferenceChangeListener;
    static Class<?> OnPreferenceClickListener;
    static Class<?> PreferenceFragmentCompat;
    static Class<?> PreferenceManager;
    static Class<?> PreferenceInflater;
    static Class<?> PageInfoType;
    static Class<?> ZHIntent;
    static Class<?> MainActivity;
    static Class<?> BasePreferenceFragment;
    static Class<?> PreferenceGroup;
    static Class<?> EditTextPreference;
    static Class<?> ListPreference;
    static Class<?> TooltipCompat;

    static Method findPreference;
    static Method setSummary;
    static Method setIcon;
    static Method setVisible;
    static Method getKey;
    static Method setChecked;
    static Method setOnPreferenceChangeListener;
    static Method setOnPreferenceClickListener;
    static Method setSharedPreferencesName;
    static Method getContext;
    static Method getText;
    static Method addPreferencesFromResource;
    static Method addPreferencesFromResourceAlt;
    static Method inflate;
    static Method setTooltipText;
    static Method startFragment;
    static Method onCreatePreferences;
    static Method onPreferenceChange;
    static Method inflateFromResource;
    static Method EditTextPreference_setText;

    static Field ListPreference_mEntries;
    static Field ListPreference_mEntryValues;

    static Constructor<?> ZHIntent_ctor;

    static String getLayoutResId_MethodName;
    static String getResourceId_MethodName;
    static String onCreate_MethodName;
    static String onPreferenceClick_MethodName;

    @Override
    public String getName() {
        return "设置页面";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        SettingsFragment = classLoader.loadClass("com.zhihu.android.app.ui.fragment.preference.SettingsFragment");
        DebugFragment = classLoader.loadClass("com.zhihu.android.app.ui.fragment.DebugFragment");
        Preference = classLoader.loadClass("androidx.preference.Preference");
        SwitchPreference = classLoader.loadClass("com.zhihu.android.app.ui.widget.SwitchPreference");
        try {
            OnPreferenceChangeListener = classLoader.loadClass("androidx.preference.Preference$d");
            OnPreferenceClickListener = classLoader.loadClass("androidx.preference.Preference$e");
        } catch (ClassNotFoundException e) {
            OnPreferenceChangeListener = classLoader.loadClass("androidx.preference.Preference$c");
            OnPreferenceClickListener = classLoader.loadClass("androidx.preference.Preference$d");
        }
        PreferenceFragmentCompat = classLoader.loadClass("androidx.preference.g");
        PreferenceManager = classLoader.loadClass("androidx.preference.j");
        PreferenceInflater = classLoader.loadClass("androidx.preference.i");
        PageInfoType = classLoader.loadClass("com.zhihu.android.data.analytics.PageInfoType");
        ZHIntent = classLoader.loadClass("com.zhihu.android.answer.entrance.AnswerPagerEntance").getMethod("buildIntent", long.class).getReturnType();
        MainActivity = classLoader.loadClass("com.zhihu.android.app.ui.activity.MainActivity");
        BasePreferenceFragment = classLoader.loadClass("com.zhihu.android.app.ui.fragment.BasePreferenceFragment");
        PreferenceGroup = classLoader.loadClass("androidx.preference.PreferenceGroup");
        EditTextPreference = classLoader.loadClass("androidx.preference.EditTextPreference");
        ListPreference = classLoader.loadClass("androidx.preference.ListPreference");
        TooltipCompat = classLoader.loadClass("androidx.appcompat.widget.TooltipCompat");

        findPreference = TargetResolver.requireMethod(BasePreferenceFragment.getSuperclass(), true, 0,
                "findPreference(CharSequence)", method ->
                        method.getParameterCount() == 1
                                && method.getParameterTypes()[0] == CharSequence.class
                                && Preference.isAssignableFrom(method.getReturnType()));

        // AndroidX public methods are renamed together by R8. Their signatures and relative order
        // stay stable across the supported versions, so resolve by structure instead of a name.
        setSummary = TargetResolver.requireMethod(Preference, false, 0,
                "setSummary(CharSequence)", method ->
                        Modifier.isPublic(method.getModifiers())
                                && method.getReturnType() == void.class
                                && Arrays.equals(method.getParameterTypes(), new Class<?>[]{CharSequence.class}));
        setIcon = TargetResolver.requireMethod(Preference, false, 0,
                "setIcon(Drawable)", method ->
                        Modifier.isPublic(method.getModifiers())
                                && method.getReturnType() == void.class
                                && Arrays.equals(method.getParameterTypes(), new Class<?>[]{Drawable.class}));
        setVisible = TargetResolver.requireMethod(Preference, false, 0,
                "setVisible(boolean)", method ->
                        Modifier.isPublic(method.getModifiers())
                                && Modifier.isFinal(method.getModifiers())
                                && method.getReturnType() == void.class
                                && Arrays.equals(method.getParameterTypes(), new Class<?>[]{boolean.class}));
        getKey = TargetResolver.requireMethod(Preference, false, 1,
                "getKey()", method ->
                        Modifier.isPublic(method.getModifiers())
                                && method.getReturnType() == String.class
                                && method.getParameterCount() == 0
                                && !"toString".equals(method.getName()));
        setChecked = TargetResolver.requireMethod(SwitchPreference.getSuperclass(), false, 0,
                "setChecked(boolean)", method ->
                        Modifier.isPublic(method.getModifiers())
                                && method.getReturnType() == void.class
                                && Arrays.equals(method.getParameterTypes(), new Class<?>[]{boolean.class}));
        getText = TargetResolver.requireMethod(EditTextPreference, false, 0,
                "getText()", method ->
                        Modifier.isPublic(method.getModifiers())
                                && method.getReturnType() == String.class
                                && method.getParameterCount() == 0);
        Method getResourceId = TargetResolver.requireMethod(Preference, false, 0,
                "getLayoutResource()", method ->
                        Modifier.isPublic(method.getModifiers())
                                && Modifier.isFinal(method.getModifiers())
                                && method.getReturnType() == int.class
                                && method.getParameterCount() == 0
                                && Character.isLowerCase(method.getName().charAt(0)));
        getResourceId_MethodName = getResourceId.getName();

        getLayoutResId_MethodName = Arrays.stream(BasePreferenceFragment.getDeclaredMethods())
                .filter(method -> Modifier.isAbstract(method.getModifiers())
                        && method.getReturnType() == int.class
                        && method.getParameterCount() == 0).findFirst().get().getName();

        onCreate_MethodName = Arrays.stream(BasePreferenceFragment.getDeclaredMethods())
                .filter(method -> Modifier.isAbstract(method.getModifiers())
                        && method.getReturnType() == void.class
                        && method.getParameterCount() == 0).findFirst().get().getName();

        setOnPreferenceChangeListener = Helper.getMethodByParameterTypes(Preference, OnPreferenceChangeListener);
        setOnPreferenceClickListener = Helper.getMethodByParameterTypes(Preference, OnPreferenceClickListener);
        try {
            setSharedPreferencesName = PreferenceManager.getMethod("a", String.class);
            addPreferencesFromResource = PreferenceFragmentCompat.getMethod("b", int.class);
            inflate = PreferenceInflater.getMethod("a", XmlPullParser.class, PreferenceGroup);
        } catch (NoSuchMethodException e) {
            PreferenceFragmentCompat = classLoader.loadClass("androidx.preference.f");
            PreferenceManager = classLoader.loadClass("androidx.preference.i");
            PreferenceInflater = classLoader.loadClass("androidx.preference.h");
            setSharedPreferencesName = Helper.getMethodByParameterTypes(PreferenceManager, String.class);
            try {
                addPreferencesFromResource = PreferenceFragmentCompat.getMethod("b", int.class);
            } catch (NoSuchMethodException e2) {
                addPreferencesFromResource = Helper.getMethodByParameterTypes(PreferenceFragmentCompat, int.class);
                addPreferencesFromResourceAlt = Helper.getMethodByParameterTypes(PreferenceFragmentCompat, 1, int.class);
            }
            inflate = Helper.getMethodByParameterTypes(PreferenceInflater, XmlPullParser.class, PreferenceGroup);
        }
        getContext = BasePreferenceFragment.getMethod("getContext");
        setTooltipText = TooltipCompat.getMethod("setTooltipText", View.class, CharSequence.class);
        startFragment = Helper.getMethodByParameterTypes(BasePreferenceFragment, ZHIntent);
        onCreatePreferences = Helper.requireTarget(Helper.getMethodByParameterTypes(BasePreferenceFragment, Bundle.class, String.class), "BasePreferenceFragment.onCreatePreferences");
        onPreferenceChange = Helper.requireTarget(Helper.getMethodByParameterTypes(DebugFragment, Preference, Object.class), "DebugFragment.onPreferenceChange");
        inflateFromResource = Helper.requireTarget(Helper.getMethodByParameterTypes(PreferenceInflater, int.class, PreferenceGroup), "PreferenceInflater.inflateFromResource");
        EditTextPreference_setText = Helper.requireTarget(Helper.getMethodByParameterTypes(EditTextPreference, String.class), "EditTextPreference.setText");
        try {
            ListPreference_mEntries = ListPreference.getDeclaredField("a");
            ListPreference_mEntryValues = ListPreference.getDeclaredField("b");
        } catch (NoSuchFieldException e) {
            ListPreference_mEntries = ListPreference.getDeclaredFields()[0];
            ListPreference_mEntryValues = ListPreference.getDeclaredFields()[1];
        }
        ListPreference_mEntries.setAccessible(true);
        ListPreference_mEntryValues.setAccessible(true);

        ZHIntent_ctor = ZHIntent.getConstructor(Class.class, Bundle.class, String.class, Array.newInstance(PageInfoType, 0).getClass());
        onPreferenceClick_MethodName = Helper.getMethodByParameterTypes(OnPreferenceClickListener, Preference).getName();
    }

    @Override
    public void hook() throws Throwable {
        XposedBridge.hookMethod(inflateFromResource, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XmlResourceParser parser;
                int id = (int) param.args[0];
                if (id == 7355608)
                    parser = Helper.modRes.getXml(R.xml.settings);
                else if (id == debug_res_id)
                    parser = Helper.modRes.getXml(R.xml.preferences_zhihu);
                else
                    return;
                try {
                    param.setResult(inflate.invoke(param.thisObject, parser, param.args[1]));
                } finally {
                    parser.close();
                }
            }
        });

        XposedHelpers.findAndHookMethod(SettingsFragment, getLayoutResId_MethodName, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                settings_res_id = (int) param.getResult();
            }
        });
        XposedHelpers.findAndHookMethod(DebugFragment, getLayoutResId_MethodName, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                debug_res_id = (int) param.getResult();
            }
        });

        XposedBridge.hookMethod(addPreferencesFromResource, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (((int) param.args[0]) == settings_res_id) {
                    addPreferencesFromResource.invoke(param.thisObject, 7355608);
                }
            }
        });
        if (addPreferencesFromResourceAlt != null) {
            XposedBridge.hookMethod(addPreferencesFromResourceAlt, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (((int) param.args[0]) == settings_res_id) {
                        addPreferencesFromResourceAlt.invoke(param.thisObject, 7355608);
                    }
                }
            });
        }

        XposedHelpers.findAndHookMethod(SettingsFragment, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object thisObject = param.thisObject;
                preference_zhiliao = findPreference.invoke(thisObject, "preference_id_zhiliao");
                setSummary.invoke(preference_zhiliao, "当前版本 " + Helper.modRes.getString(R.string.app_version));
                setOnPreferenceClickListener.invoke(preference_zhiliao, thisObject);
            }
        });

        XposedHelpers.findAndHookMethod(SettingsFragment, onPreferenceClick_MethodName, Preference, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.args[0] == preference_zhiliao) {
                    Object intent = ZHIntent_ctor.newInstance(DebugFragment, null, "SCREEN_NAME_NULL", Array.newInstance(PageInfoType, 0));
                    startFragment.invoke(param.thisObject, intent);
                    param.setResult(false);
                }
            }
        });
        XposedBridge.hookMethod(onCreatePreferences, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (param.thisObject.getClass() == DebugFragment) {
                    Field[] fields = PreferenceFragmentCompat.getDeclaredFields();
                    for (Field field : fields) {
                        if (field.getType() == PreferenceManager) {
                            field.setAccessible(true);
                            setSharedPreferencesName.invoke(field.get(param.thisObject), "zhiliao_preferences");
                            return;
                        }
                    }
                }
            }
        });
        XposedHelpers.findAndHookMethod(BasePreferenceFragment, "onViewCreated", View.class, Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (param.thisObject.getClass() == DebugFragment) {
                    Field[] fields = BasePreferenceFragment.getDeclaredFields();
                    for (Field field : fields) {
                        if (field.getType().getName().equals("com.zhihu.android.app.ui.widget.SystemBar")) {
                            field.setAccessible(true);
                            Object systemBar = field.get(param.thisObject);
                            ViewGroup toolbar = (ViewGroup) systemBar.getClass().getMethod("getToolbar").invoke(systemBar);
                            toolbar.getClass().getMethod("setTitle", CharSequence.class).invoke(toolbar, "知了");

                            ImageView restart = new ImageView(Helper.context);
                            final int _48dp = (int) (Helper.scale * 48 + 0.5);
                            restart.setLayoutParams(new ViewGroup.MarginLayoutParams(_48dp, _48dp));
                            restart.setImageDrawable(Helper.modRes.getDrawable(R.drawable.ic_restart));
                            restart.setScaleType(ImageView.ScaleType.CENTER);
                            restart.setOnClickListener(v -> Helper.doRestart(Helper.context));
                            restart.setColorFilter(Helper.getDarkMode() ? 0xffd3d3d3 : 0xff646464);
                            setTooltipText.invoke(null, restart, "重启知乎");
                            ViewGroup actionMenuView = (ViewGroup) (toolbar.getChildAt(0));
                            actionMenuView.addView(restart);
                            break;
                        }
                    }
                }
            }
        });
        XposedHelpers.findAndHookMethod(DebugFragment, onCreate_MethodName, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                Object thisObject = param.thisObject;
                Object preference_version = findPreference.invoke(thisObject, "preference_version");
                Object preference_author = findPreference.invoke(thisObject, "preference_author");
                Object preference_help = findPreference.invoke(thisObject, "preference_help");
                Object preference_channel = findPreference.invoke(thisObject, "preference_channel");
                Object preference_telegram = findPreference.invoke(thisObject, "preference_telegram");
                Object preference_sourcecode = findPreference.invoke(thisObject, "preference_sourcecode");
                Object preference_donate = findPreference.invoke(thisObject, "preference_donate");
                Object preference_status = findPreference.invoke(thisObject, "preference_status");
                Object switch_externlink = findPreference.invoke(thisObject, "switch_externlink");
                Object switch_externlinkex = findPreference.invoke(thisObject, "switch_externlinkex");
                Object switch_tag = findPreference.invoke(thisObject, "switch_tag");
                Object switch_thirdpartylogin = findPreference.invoke(thisObject, "switch_thirdpartylogin");
                Object switch_autorefresh = findPreference.invoke(thisObject, "switch_autorefresh");
                Object switch_reddot = findPreference.invoke(thisObject, "switch_reddot");
                Object switch_vipbanner = findPreference.invoke(thisObject, "switch_vipbanner");
                Object switch_homenav = findPreference.invoke(thisObject, "switch_homenav");
                Object switch_messagenav = findPreference.invoke(thisObject, "switch_messagenav");
                Object switch_profilenav = findPreference.invoke(thisObject, "switch_profilenav");
                Object switch_panelnav = findPreference.invoke(thisObject, "switch_panelnav");
                Object switch_findnav = findPreference.invoke(thisObject, "switch_findnav");
                Object switch_article = findPreference.invoke(thisObject, "switch_article");
                Object switch_navres = findPreference.invoke(thisObject, "switch_navres");
                Object switch_nextanswer = findPreference.invoke(thisObject, "switch_nextanswer");
                Object preference_clean = findPreference.invoke(thisObject, "preference_clean");
                Object switch_autoclean = findPreference.invoke(thisObject, "switch_autoclean");
                Object switch_feedtophot = findPreference.invoke(thisObject, "switch_feedtophot");
                Object switch_minehybrid = findPreference.invoke(thisObject, "switch_minehybrid");

                setOnPreferenceChangeListener.invoke(findPreference.invoke(thisObject, "accept_eula"), thisObject);
                setOnPreferenceClickListener.invoke(switch_externlink, thisObject);
                setOnPreferenceClickListener.invoke(switch_externlinkex, thisObject);
                setOnPreferenceClickListener.invoke(switch_tag, thisObject);
                setOnPreferenceClickListener.invoke(switch_thirdpartylogin, thisObject);
                setOnPreferenceClickListener.invoke(switch_autorefresh, thisObject);
                setOnPreferenceClickListener.invoke(switch_reddot, thisObject);
                setOnPreferenceClickListener.invoke(switch_vipbanner, thisObject);
                setOnPreferenceClickListener.invoke(switch_homenav, thisObject);
                setOnPreferenceClickListener.invoke(switch_messagenav, thisObject);
                setOnPreferenceClickListener.invoke(switch_profilenav, thisObject);
                setOnPreferenceClickListener.invoke(switch_panelnav, thisObject);
                setOnPreferenceClickListener.invoke(switch_findnav, thisObject);
                setOnPreferenceClickListener.invoke(switch_article, thisObject);
                setOnPreferenceClickListener.invoke(switch_navres, thisObject);
                setOnPreferenceClickListener.invoke(switch_nextanswer, thisObject);
                setOnPreferenceClickListener.invoke(findPreference.invoke(thisObject, "switch_imagesource"), thisObject);
                setOnPreferenceClickListener.invoke(preference_clean, thisObject);
                setOnPreferenceClickListener.invoke(switch_autoclean, thisObject);
                setOnPreferenceClickListener.invoke(preference_version, thisObject);
                setOnPreferenceClickListener.invoke(preference_author, thisObject);
                setOnPreferenceClickListener.invoke(preference_help, thisObject);
                setOnPreferenceClickListener.invoke(preference_channel, thisObject);
                setOnPreferenceClickListener.invoke(preference_telegram, thisObject);
                setOnPreferenceClickListener.invoke(preference_sourcecode, thisObject);
                setOnPreferenceClickListener.invoke(preference_donate, thisObject);
                setOnPreferenceClickListener.invoke(switch_feedtophot, thisObject);
                setOnPreferenceClickListener.invoke(switch_minehybrid, thisObject);

                String loaded_version = Helper.modRes.getString(R.string.app_version);
                setSummary.invoke(preference_version, loaded_version);

                if (Helper.officialZhihu) {
                    String real_version = null;
                    try {
                        real_version = Helper.context.getPackageManager().getResourcesForApplication(modulePackage).getString(R.string.app_version);
                    } catch (Exception ignore) {
                    }
                    if (real_version == null || loaded_version.equals(real_version)) {
                        setVisible.invoke(preference_status, false);
                    } else {
                        setOnPreferenceClickListener.invoke(preference_status, thisObject);
                        Object category_eula = findPreference.invoke(thisObject, "category_eula");
                        Object category_ads = findPreference.invoke(thisObject, "category_ads");
                        Object category_misc = findPreference.invoke(thisObject, "category_misc");
                        Object category_ui = findPreference.invoke(thisObject, "category_ui");
                        Object category_nav = findPreference.invoke(thisObject, "category_nav");
                        Object category_answer = findPreference.invoke(thisObject, "category_answer");
                        Object category_filter = findPreference.invoke(thisObject, "category_filter");
                        Object category_webview = findPreference.invoke(thisObject, "category_webview");
                        Object category_cleaner = findPreference.invoke(thisObject, "category_cleaner");
                        Object category_debug = findPreference.invoke(thisObject, "category_debug");
                        setVisible.invoke(category_eula, false);
                        setVisible.invoke(category_ads, false);
                        setVisible.invoke(category_misc, false);
                        setVisible.invoke(category_ui, false);
                        setVisible.invoke(category_nav, false);
                        setVisible.invoke(category_answer, false);
                        setVisible.invoke(category_filter, false);
                        setVisible.invoke(category_webview, false);
                        setVisible.invoke(category_cleaner, false);
                        setVisible.invoke(category_debug, false);
                        return null;
                    }
                } else {
                    setVisible.invoke(preference_status, false);
                }

                setIcon.invoke(preference_status, Helper.modRes.getDrawable(R.drawable.ic_refresh));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_mainswitch"), Helper.modRes.getDrawable(R.drawable.ic_toggle_on));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_launchad"), Helper.modRes.getDrawable(R.drawable.ic_ad_units));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_feedad"), Helper.modRes.getDrawable(R.drawable.ic_table_rows));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_answerlistad"), Helper.modRes.getDrawable(R.drawable.ic_format_list));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_commentad"), Helper.modRes.getDrawable(R.drawable.ic_comment));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_sharead"), Helper.modRes.getDrawable(R.drawable.ic_share));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_answerad"), Helper.modRes.getDrawable(R.drawable.ic_notes));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_searchad"), Helper.modRes.getDrawable(R.drawable.ic_search));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_video"), Helper.modRes.getDrawable(R.drawable.ic_play_circle));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_removearticle"), Helper.modRes.getDrawable(R.drawable.ic_article));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_pin"), Helper.modRes.getDrawable(R.drawable.ic_emoji_objects));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_marketcard"), Helper.modRes.getDrawable(R.drawable.ic_vip));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_club"), Helper.modRes.getDrawable(R.drawable.ic_group));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_goods"), Helper.modRes.getDrawable(R.drawable.ic_local_mall));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_related"), Helper.modRes.getDrawable(R.drawable.ic_search_off));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_searchwords"), Helper.modRes.getDrawable(R.drawable.ic_plagiarism));
                setIcon.invoke(switch_externlink, Helper.modRes.getDrawable(R.drawable.ic_link));
                setIcon.invoke(switch_externlinkex, Helper.modRes.getDrawable(R.drawable.ic_link));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_colormode"), Helper.modRes.getDrawable(R.drawable.ic_color));
                setIcon.invoke(switch_tag, Helper.modRes.getDrawable(R.drawable.ic_label));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_statusbar"), Helper.modRes.getDrawable(R.drawable.ic_fullscreen));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_fullscreen"), Helper.modRes.getDrawable(R.drawable.ic_fullscreen_exit));
                setIcon.invoke(switch_thirdpartylogin, Helper.modRes.getDrawable(R.drawable.ic_login));
                setIcon.invoke(switch_autorefresh, Helper.modRes.getDrawable(R.drawable.ic_refresh));
                setIcon.invoke(switch_reddot, Helper.modRes.getDrawable(R.drawable.ic_mark_chat_unread));
                setIcon.invoke(switch_vipbanner, Helper.modRes.getDrawable(R.drawable.ic_vip_banner));
                setIcon.invoke(switch_homenav, Helper.modRes.getDrawable(R.drawable.ic_rss_feed));
                setIcon.invoke(switch_messagenav, Helper.modRes.getDrawable(R.drawable.ic_notifications_off));
                setIcon.invoke(switch_profilenav, Helper.modRes.getDrawable(R.drawable.ic_person));
                setIcon.invoke(switch_panelnav, Helper.modRes.getDrawable(R.drawable.ic_add_circle));
                setIcon.invoke(switch_findnav, Helper.modRes.getDrawable(R.drawable.ic_cross_star));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_hotbanner"), Helper.modRes.getDrawable(R.drawable.ic_whatshot));
                setIcon.invoke(switch_article, Helper.modRes.getDrawable(R.drawable.ic_article));
                setIcon.invoke(switch_navres, Helper.modRes.getDrawable(R.drawable.ic_event));
                setIcon.invoke(switch_nextanswer, Helper.modRes.getDrawable(R.drawable.ic_circle_down));
                setIcon.invoke(findPreference.invoke(thisObject, "edit_title"), Helper.regex_title != null ? Helper.modRes.getDrawable(R.drawable.ic_check) : Helper.modRes.getDrawable(R.drawable.ic_close));
                setIcon.invoke(findPreference.invoke(thisObject, "edit_author"), Helper.regex_author != null ? Helper.modRes.getDrawable(R.drawable.ic_check) : Helper.modRes.getDrawable(R.drawable.ic_close));
                setIcon.invoke(findPreference.invoke(thisObject, "edit_content"), Helper.regex_content != null ? Helper.modRes.getDrawable(R.drawable.ic_check) : Helper.modRes.getDrawable(R.drawable.ic_close));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_webview_debug"), Helper.modRes.getDrawable(R.drawable.ic_code));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_watermark"), Helper.modRes.getDrawable(R.drawable.ic_layers));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_imagesource"), Helper.modRes.getDrawable(R.drawable.ic_image));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_subscribe"), Helper.modRes.getDrawable(R.drawable.ic_person_add_alt));
                setIcon.invoke(findPreference.invoke(thisObject, "edit_js"), Helper.modRes.getDrawable(R.drawable.ic_javascript));
                setIcon.invoke(preference_clean, Helper.modRes.getDrawable(R.drawable.ic_delete));
                setIcon.invoke(switch_autoclean, Helper.modRes.getDrawable(R.drawable.ic_auto_delete));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_silenceclean"), Helper.modRes.getDrawable(R.drawable.ic_notifications_off));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_hidetoast"), Helper.modRes.getDrawable(R.drawable.ic_tooltip));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_hooktrace"), Helper.modRes.getDrawable(R.drawable.ic_scroll_text));
                setIcon.invoke(preference_version, Helper.modRes.getDrawable(R.drawable.ic_info));
                setIcon.invoke(preference_author, Helper.modRes.getDrawable(R.drawable.ic_person));
                setIcon.invoke(preference_help, Helper.modRes.getDrawable(R.drawable.ic_help));
                setIcon.invoke(preference_channel, Helper.modRes.getDrawable(R.drawable.ic_rss_feed));
                setIcon.invoke(preference_telegram, Helper.modRes.getDrawable(R.drawable.ic_telegram));
                setIcon.invoke(preference_sourcecode, Helper.modRes.getDrawable(R.drawable.ic_github));
                setIcon.invoke(preference_donate, Helper.modRes.getDrawable(R.drawable.ic_monetization));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_feedtophot"), Helper.modRes.getDrawable(R.drawable.ic_whatshot));
                setIcon.invoke(findPreference.invoke(thisObject, "switch_minehybrid"), Helper.modRes.getDrawable(R.drawable.ic_viewcard));

                if (Helper.prefs.getBoolean("accept_eula", false)) {
                    Object category_eula = findPreference.invoke(thisObject, "category_eula");
                    setVisible.invoke(category_eula, false);
                } else {
                    Object switch_main = findPreference.invoke(param.thisObject, "switch_mainswitch");
                    setChecked.invoke(switch_main, false);
                }

                setSummary.invoke(preference_clean, "广告缓存和日志文件 " + Cleaner.humanReadableByteCount(Cleaner.getFileSize(Cleaner.getCacheFiles())));

                return null;
            }
        });
        XposedHelpers.findAndHookMethod(DebugFragment, onPreferenceClick_MethodName, Preference, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                Object preference = param.args[0];
                switch ((String) getKey.invoke(preference)) {
                    case "preference_status":
                        System.exit(0);
                        break;
                    case "preference_version":
                        version_click++;
                        if (version_click == 5) {
                            Helper.toast("点我次数再多，更新也不会变快哦", Toast.LENGTH_SHORT);
                            version_click = 0;
                        }
                        break;
                    case "preference_author":
                        author_click++;
                        if (author_click == 5) {
                            Helper.toast(Helper.modRes.getStringArray(R.array.click_author)[new Random().nextInt(4)], Toast.LENGTH_SHORT);
                            author_click = 0;
                        }
                        break;
                    case "preference_help":
                        Uri uri_help = Uri.parse("https://github.com/shatyuka/Zhiliao/wiki");
                        Intent intent_help = new Intent(Intent.ACTION_VIEW, uri_help);
                        ((Context) getContext.invoke(param.thisObject)).startActivity(intent_help);
                        break;
                    case "preference_channel":
                        Uri uri_channel = Uri.parse("https://t.me/zhiliao");
                        Intent intent_channel = new Intent(Intent.ACTION_VIEW, uri_channel);
                        ((Context) getContext.invoke(param.thisObject)).startActivity(intent_channel);
                        break;
                    case "preference_telegram":
                        Uri uri_telegram = Uri.parse("https://t.me/joinchat/OibCWxbdCMkJ2fG8J1DpQQ");
                        Intent intent_telegram = new Intent(Intent.ACTION_VIEW, uri_telegram);
                        ((Context) getContext.invoke(param.thisObject)).startActivity(intent_telegram);
                        break;
                    case "preference_sourcecode":
                        Uri uri_sourcecode = Uri.parse("https://github.com/shatyuka/Zhiliao");
                        Intent intent_sourcecode = new Intent(Intent.ACTION_VIEW, uri_sourcecode);
                        ((Context) getContext.invoke(param.thisObject)).startActivity(intent_sourcecode);
                        break;
                    case "preference_donate":
                        Uri uri_donate = Uri.parse("https://github.com/shatyuka/Zhiliao/wiki/Donate");
                        Intent intent_donate = new Intent(Intent.ACTION_VIEW, uri_donate);
                        ((Context) getContext.invoke(param.thisObject)).startActivity(intent_donate);
                        break;
                    case "switch_externlink":
                        Object switch_externlinkex = findPreference.invoke(param.thisObject, "switch_externlinkex");
                        setChecked.invoke(switch_externlinkex, false);
                        break;
                    case "switch_externlinkex":
                        Object switch_externlink = findPreference.invoke(param.thisObject, "switch_externlink");
                        setChecked.invoke(switch_externlink, false);
                        break;
                    case "preference_clean":
                        String size = Cleaner.humanReadableByteCount(Cleaner.doClean());
                        if (!Helper.prefs.getBoolean("switch_silenceclean", false)) {
                            Toast.makeText(Helper.context, "共清理 " + size + " 临时文件", Toast.LENGTH_SHORT).show();
                        }
                        setSummary.invoke(preference, "广告缓存和日志文件 " + Cleaner.humanReadableByteCount(Cleaner.getFileSize(Cleaner.getCacheFiles())));
                        break;
                    case "switch_navres":
                        Helper.deleteDirectory(Helper.context.getFilesDir() + "/bottom_nav");
                    case "switch_tag":
                    case "switch_thirdpartylogin":
                    case "switch_autorefresh":
                    case "switch_reddot":
                    case "switch_vipbanner":
                    case "switch_homenav":
                    case "switch_messagenav":
                    case "switch_profilenav":
                    case "switch_panelnav":
                    case "switch_findnav":
                    case "switch_article":
                    case "switch_nextanswer":
                    case "switch_autoclean":
                    case "switch_feedtophot":
                    case "switch_minehybrid":
                    case "switch_imagesource":
                        Helper.toast("重启知乎生效", Toast.LENGTH_SHORT);
                        break;
                }
                return false;
            }
        });
        XposedBridge.hookMethod(onPreferenceChange, new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                if ((boolean) param.args[1]) {
                    Object switch_main = findPreference.invoke(param.thisObject, "switch_mainswitch");
                    setChecked.invoke(switch_main, true);
                    Object category_eula = findPreference.invoke(param.thisObject, "category_eula");
                    setVisible.invoke(category_eula, false);
                }
                return true;
            }
        });
        XposedBridge.hookMethod(EditTextPreference_setText, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                Object thisObject = param.thisObject;
                switch ((String) getKey.invoke(thisObject)) {
                    case "edit_title":
                        Helper.regex_title = Helper.compileRegex((String) getText.invoke(thisObject));
                        setIcon.invoke(thisObject, Helper.regex_title != null ? Helper.modRes.getDrawable(R.drawable.ic_check) : Helper.modRes.getDrawable(R.drawable.ic_close));
                        break;
                    case "edit_author":
                        Helper.regex_author = Helper.compileRegex((String) getText.invoke(thisObject));
                        setIcon.invoke(thisObject, Helper.regex_author != null ? Helper.modRes.getDrawable(R.drawable.ic_check) : Helper.modRes.getDrawable(R.drawable.ic_close));
                        break;
                    case "edit_content":
                        Helper.regex_content = Helper.compileRegex((String) getText.invoke(thisObject));
                        setIcon.invoke(thisObject, Helper.regex_content != null ? Helper.modRes.getDrawable(R.drawable.ic_check) : Helper.modRes.getDrawable(R.drawable.ic_close));
                        break;
                }
            }
        });

        XposedHelpers.findAndHookMethod(Dialog.class, "dismiss", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                Dialog dialog = (Dialog) param.thisObject;
                if (dialog.isShowing()) {
                    View view = dialog.getWindow().getCurrentFocus();
                    if (view != null) {
                        InputMethodManager imm = (InputMethodManager) Helper.context.getSystemService(Activity.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(view.getRootView().getWindowToken(), 0);
                    }
                }
            }
        });

        XposedHelpers.findAndHookMethod(SettingsFragment, "onViewCreated", View.class, Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Helper.settingsView = param.args[0];
            }
        });

        XposedHelpers.findAndHookMethod(BasePreferenceFragment, "onDestroyView", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.thisObject.getClass() == SettingsFragment) {
                    Helper.settingsView = null;
                }
            }
        });
    }
}
