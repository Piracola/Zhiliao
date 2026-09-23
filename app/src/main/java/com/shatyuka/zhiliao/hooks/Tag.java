package com.shatyuka.zhiliao.hooks;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.R;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

public class Tag implements IHook {
    static Drawable[] backgrounds;

    static Class<?> BaseTemplateNewFeedHolder;
    static Class<?> TemplateFeed;
    static Class<?> ViewHolder;
    static Class<?> SugarHolder;
    static Class<?> TemplateRoot;
    static Class<?> Element;
    static Class<?> Line;
    static Class<?> Avatar;

    static Method onBindData;
    static Method card_onBindData;

    static Field ViewHolder_itemView;
    static Field SugarHolder_mData;
    static Field TemplateRoot_unique;
    static Field SDUICard_view;
    static Field Card_elements;
    static Field Card_extra;
    static Field Card_Extra_contentType;
    static Field Element_id;
    static Field Line_elements;

    private final int TAG_ID = 0xABCDEF;

    @Override
    public String getName() {
        return "显示卡片类别";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        Helper.requireTarget(Helper.DataUnique_type, "com.zhihu.android.api.model.template.DataUnique.type");
        BaseTemplateNewFeedHolder = classLoader.loadClass("com.zhihu.android.app.feed.ui.holder.template.optimal.BaseTemplateNewFeedHolder");
        TemplateFeed = classLoader.loadClass("com.zhihu.android.api.model.template.TemplateFeed");
        ViewHolder = classLoader.loadClass("androidx.recyclerview.widget.RecyclerView$ViewHolder");
        SugarHolder = classLoader.loadClass("com.zhihu.android.sugaradapter.SugarHolder");
        TemplateRoot = classLoader.loadClass("com.zhihu.android.api.model.template.TemplateRoot");

        ViewHolder_itemView = ViewHolder.getField("itemView");
        try {
            SugarHolder_mData = SugarHolder.getDeclaredField("c");
        } catch (NoSuchFieldException e) {
            SugarHolder_mData = SugarHolder.getDeclaredField("mData");
        }
        SugarHolder_mData.setAccessible(true);
        TemplateRoot_unique = TemplateRoot.getField("unique");

        try {
            onBindData = BaseTemplateNewFeedHolder.getMethod("onBindData", Object.class);
        } catch (NoSuchMethodException e) {
            onBindData = BaseTemplateNewFeedHolder.getDeclaredMethod("a", TemplateFeed);
        }

        Class<?> SDUICard = null;
        try {
            SDUICard = classLoader.loadClass("com.zhihu.android.app.feed.ui.holder.template.optimal.SDUICard");
        } catch (ClassNotFoundException ignored) {
        }
        if (SDUICard != null) {
            card_onBindData = SDUICard.getMethod("onBindData", Object.class);

            SDUICard_view = Helper.findFieldByType(SDUICard, View.class);
            if (SDUICard_view == null) {
                throw new NoSuchFieldException("view");
            }
            SDUICard_view.setAccessible(true);

            Class<?> Card = classLoader.loadClass("com.zhihu.android.ui.shared.sdui.model.Card");
            Card_elements = Card.getDeclaredField("elements");
            Card_elements.setAccessible(true);
            Card_extra = Card.getDeclaredField("extra");
            Card_extra.setAccessible(true);
            Card_Extra_contentType = Card_extra.getType().getDeclaredField("contentType");
            Card_Extra_contentType.setAccessible(true);

            Element = classLoader.loadClass("com.zhihu.android.ui.shared.sdui.model.Element");
            Element_id = Element.getDeclaredField("id");
            Element_id.setAccessible(true);
            Line = classLoader.loadClass("com.zhihu.android.ui.shared.sdui.model.Line");
            Line_elements = Line.getDeclaredField("elements");
            Line_elements.setAccessible(true);
            Avatar = classLoader.loadClass("com.zhihu.android.ui.shared.sdui.model.Avatar");
        }
    }

    @SuppressLint("DiscouragedApi")
    @Override
    public void hook() throws Throwable {
        if (Helper.prefs.getBoolean("switch_mainswitch", false) && Helper.prefs.getBoolean("switch_tag", false)) {

            XposedBridge.hookMethod(onBindData, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object thisObject = param.thisObject;
                    Object itemView = ViewHolder_itemView.get(thisObject);
                    if (!(itemView instanceof ViewGroup)) {
                        return;
                    }
                    ViewGroup viewGroup = (ViewGroup) itemView;

                    TextView title = viewGroup.findViewById(Helper.context.getResources().getIdentifier("title", "id", Helper.hookPackage));
                    View author = viewGroup.findViewById(Helper.context.getResources().getIdentifier("author", "id", Helper.hookPackage));
                    if (title == null) {
                        return;
                    }

                    Object templateFeed = SugarHolder_mData.get(thisObject);
                    if (!TemplateRoot.isInstance(templateFeed)) {
                        return;
                    }
                    Object unique = TemplateRoot_unique.get(templateFeed);
                    if (!Helper.DataUnique_type.getDeclaringClass().isInstance(unique)) {
                        return;
                    }
                    String type = (String) Helper.DataUnique_type.get(unique);

                    postProcessTag(title, author, viewGroup, type, false);
                }
            });

            if (card_onBindData != null) {
                XposedBridge.hookMethod(card_onBindData, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object thisObject = param.thisObject;
                        if (param.args.length == 0 || !Card_extra.getDeclaringClass().isInstance(param.args[0])) {
                            return;
                        }
                        Object card = param.args[0];
                        Object extra = Card_extra.get(card);
                        if (extra == null) {
                            return;
                        }
                        String type = (String) Card_Extra_contentType.get(extra);

                        Object viewObject = SDUICard_view.get(thisObject);
                        if (!(viewObject instanceof FrameLayout)) {
                            return;
                        }
                        FrameLayout layout = (FrameLayout) viewObject;
                        if (layout.getChildCount() != 1) {
                            return;
                        }
                        View layoutChild = layout.getChildAt(0);
                        if (!(layoutChild instanceof ViewGroup)) {
                            return;
                        }
                        ViewGroup cardView = (ViewGroup) layoutChild;

                        List<?> elements = (List<?>) Card_elements.get(card);
                        if (elements == null) {
                            return;
                        }
                        boolean hasReason = false;
                        TextView title = null;
                        View author = null;
                        for (Object element : elements) {
                            if (!Element.isInstance(element)) {
                                continue;
                            }
                            String id = (String) Element_id.get(element);
                            if (id == null) {
                                continue;
                            }
                            if (title == null && author == null && id.equals("Text")) {
                                int index = elements.indexOf(element);
                                if (index != 0) {
                                    hasReason = true;
                                }
                                View child = cardView.getChildAt(index);
                                if (child instanceof TextView) {
                                    title = (TextView) child;
                                }
                                continue;
                            }
                            if (author == null && element.getClass() == Line && id.equals("0")) {
                                List<?> lineElements = (List<?>) Line_elements.get(element);
                                if (lineElements != null && !lineElements.isEmpty()) {
                                    Object lineElement = lineElements.get(0);
                                    if (lineElement != null && lineElement.getClass() == Avatar) {
                                        int index = elements.indexOf(element);
                                        if (title == null && index != 0) {
                                            hasReason = true;
                                        }
                                        View child = cardView.getChildAt(index);
                                        if (child instanceof ViewGroup) {
                                            author = child;
                                        }
                                        break;
                                    }
                                }
                            }
                        }

                        postProcessTag(title, author, cardView, type, hasReason);
                    }
                });
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private void postProcessTag(TextView title, View author, ViewGroup viewGroup, String type, boolean hasReason) {
        if (author == null) {
            return;
        }

        // 按需创建tag
        RelativeLayout tagLayout;
        TextView tag = viewGroup.findViewById(TAG_ID);
        if (tag == null) {
            tag = new TextView(viewGroup.getContext());
            tag.setId(TAG_ID);

            tagLayout = new RelativeLayout(viewGroup.getContext());
            tagLayout.addView(tag);
            viewGroup.addView(tagLayout);
        } else {
            ViewParent tagParent = tag.getParent();
            if (!(tagParent instanceof RelativeLayout)) {
                return;
            }
            tagLayout = (RelativeLayout) tagParent;
        }

        // 设置tag属性
        tag.setTextColor(-1);
        tag.setText(getType(type));
        tag.setBackground(getBackground(type));

        // 调整X坐标
        int baseX = 0;
        if (title != null && title.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            baseX = ((ViewGroup.MarginLayoutParams) title.getLayoutParams()).leftMargin;
            if (baseX != 0 && author.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) author.getLayoutParams()).leftMargin = baseX;
            }
        }
        if (baseX != 0) {
            tagLayout.setX(baseX);
        }

        boolean hasTitle = title != null && title.getVisibility() == View.VISIBLE;

        // 调整Y坐标
        float baseY = 0;
        if (hasReason) {
            baseY = Helper.scale * 21;
        }
        if (hasTitle) {
            tagLayout.setY((float) (Helper.scale * 3 + 0.5) + baseY);
        } else {
            tagLayout.setY(baseY);
        }

        // 为tag留出空间
        if (hasTitle) {
            title.setText("　　 " + title.getText());
        } else if (author.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) author.getLayoutParams()).leftMargin = (int) (Helper.scale * 40 + 0.5 + baseX);
        }
    }

    static String getType(String type) {
        if (type == null) {
            return "其他";
        }
        switch (type) {
            case "answer":
            case "Answer":
                return "问题";
            case "article":
            case "Post":
                return "文章";
            case "zvideo":
                return "视频";
            case "drama":
                return "直播";
            case "pin":
                return "想法";
            default:
                return "其他";
        }
    }

    @SuppressWarnings("deprecation")
    static Drawable getBackground(String type) {
        if (backgrounds == null) {
            backgrounds = new Drawable[5];
            backgrounds[0] = Helper.modRes.getDrawable(R.drawable.bg_answer);
            backgrounds[1] = Helper.modRes.getDrawable(R.drawable.bg_article);
            backgrounds[2] = Helper.modRes.getDrawable(R.drawable.bg_video);
            backgrounds[3] = Helper.modRes.getDrawable(R.drawable.bg_pin);
            backgrounds[4] = Helper.modRes.getDrawable(R.drawable.bg_others);
        }
        if (type == null) {
            return backgrounds[4];
        }
        switch (type) {
            case "answer":
            case "Answer":
                return backgrounds[0];
            case "article":
            case "Post":
                return backgrounds[1];
            case "zvideo":
            case "drama":
                return backgrounds[2];
            case "pin":
                return backgrounds[3];
            default:
                return backgrounds[4];
        }
    }
}
