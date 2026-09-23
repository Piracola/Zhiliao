package com.shatyuka.zhiliao.hooks;

import com.shatyuka.zhiliao.Helper;
import com.shatyuka.zhiliao.xposed.XC_MethodHook;
import com.shatyuka.zhiliao.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.util.List;

public class CustomFilter implements IHook {
    static Class<?> InnerDeserializer;
    static Class<?> ApiTemplateRoot;
    static Class<?> ApiFeedCard;
    static Class<?> MarketCard;
    static Class<?> ApiFeedContent;
    static Class<?> ApiText;
    static Class<?> ApiLine;
    static Class<?> ApiElement;
    static Class<?> Card;
    static Class<?> Element;
    static Class<?> TextElement;
    static Class<?> HtmlTextElement;
    static Class<?> Line;
    static Class<?> Avatar;
    static Class<?> Video;
    static Class<?> Author;

    static Field ApiTemplateRoot_extra;
    static Field ApiTemplateRoot_common_card;
    static Field ApiFeedCard_feed_content;
    static Field ApiFeedContent_title;
    static Field ApiFeedContent_content;
    static Field ApiFeedContent_sourceLine;
    static Field ApiFeedContent_video;
    static Field ApiText_panel_text;
    static Field ApiLine_elements;
    static Field ApiElement_text;
    static Field Card_elements;
    static Field Card_extra;
    static Field Card_Extra_contentType;
    static Field Element_id;
    static Field Line_elements;
    static Field TextElement_text;
    static Field HtmlTextElement_text;
    static Field Author_fistLine;

    @Override
    public String getName() {
        return "自定义过滤";
    }

    @Override
    public void init(ClassLoader classLoader) throws Throwable {
        Helper.requireTarget(Helper.DataUnique_type, "com.zhihu.android.api.model.template.DataUnique.type");
        InnerDeserializer = classLoader.loadClass("com.zhihu.android.api.util.ZHObjectRegistryCenter$InnerDeserializer");
        ApiTemplateRoot = classLoader.loadClass("com.zhihu.android.api.model.template.api.ApiTemplateRoot");
        ApiFeedCard = classLoader.loadClass("com.zhihu.android.api.model.template.api.ApiFeedCard");
        MarketCard = classLoader.loadClass("com.zhihu.android.api.model.MarketCard");
        ApiFeedContent = classLoader.loadClass("com.zhihu.android.api.model.template.api.ApiFeedContent");
        ApiText = classLoader.loadClass("com.zhihu.android.api.model.template.api.ApiText");
        ApiLine = classLoader.loadClass("com.zhihu.android.api.model.template.api.ApiLine");
        ApiElement = classLoader.loadClass("com.zhihu.android.api.model.template.api.ApiElement");
        try {
            Card = classLoader.loadClass("com.zhihu.android.ui.shared.sdui.model.Card");
        } catch (ClassNotFoundException ignored) {
        }
        if (Card != null) {
            Card_elements = Card.getDeclaredField("elements");
            Card_elements.setAccessible(true);
            Card_extra = Card.getDeclaredField("extra");
            Card_extra.setAccessible(true);
            Card_Extra_contentType = Card_extra.getType().getDeclaredField("contentType");
            Card_Extra_contentType.setAccessible(true);

            Element = classLoader.loadClass("com.zhihu.android.ui.shared.sdui.model.Element");
            Element_id = Element.getDeclaredField("id");
            Element_id.setAccessible(true);
            TextElement = classLoader.loadClass("com.zhihu.android.ui.shared.sdui.model.TextElement");
            TextElement_text = TextElement.getDeclaredField("text");
            TextElement_text.setAccessible(true);
            try {
                HtmlTextElement = classLoader.loadClass("com.zhihu.android.ui.shared.sdui.model.HtmlTextElement");
                HtmlTextElement_text = HtmlTextElement.getDeclaredField("text");
                HtmlTextElement_text.setAccessible(true);
            } catch (ClassNotFoundException | NoSuchFieldException ignored) {
                HtmlTextElement = null;
                HtmlTextElement_text = null;
            }
            Line = classLoader.loadClass("com.zhihu.android.ui.shared.sdui.model.Line");
            Line_elements = Line.getDeclaredField("elements");
            Line_elements.setAccessible(true);
            Avatar = classLoader.loadClass("com.zhihu.android.ui.shared.sdui.model.Avatar");
            Video = classLoader.loadClass("com.zhihu.android.ui.shared.sdui.model.Video");
            Author = null;
            Author_fistLine = null;
            try {
                Author = classLoader.loadClass("com.zhihu.android.ui.shared.sdui.model.Author");
                Author_fistLine = Author.getDeclaredField("fistLine");
                Author_fistLine.setAccessible(true);
            } catch (ClassNotFoundException | NoSuchFieldException ignored) {
                Author = null;
                Author_fistLine = null;
            }
        }

        ApiTemplateRoot_extra = ApiTemplateRoot.getField("extra");
        ApiTemplateRoot_common_card = ApiTemplateRoot.getField("common_card");
        ApiFeedCard_feed_content = ApiFeedCard.getField("feed_content");
        ApiFeedContent_title = ApiFeedContent.getField("title");
        ApiFeedContent_content = ApiFeedContent.getField("content");
        ApiFeedContent_sourceLine = ApiFeedContent.getField("sourceLine");
        ApiFeedContent_video = ApiFeedContent.getField("video");
        ApiText_panel_text = ApiText.getField("panel_text");
        ApiLine_elements = ApiLine.getField("elements");
        ApiElement_text = ApiElement.getField("text");
    }

    @Override
    public void hook() throws Throwable {
        XposedBridge.hookAllMethods(InnerDeserializer, "deserialize", new XC_MethodHook() {
            @SuppressWarnings("rawtypes")
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!Helper.prefs.getBoolean("switch_mainswitch", false)) {
                    return;
                }
                Object result = param.getResult();
                if (result == null) {
                    return;
                }
                Class<?> resultClass = result.getClass();
                if (resultClass == ApiTemplateRoot) {
                    Object extra = ApiTemplateRoot_extra.get(result);
                    String type = extra == null ? null : (String) Helper.DataUnique_type.get(extra);
                    boolean hasVideo = false;
                    String title = "";
                    String author = "";
                    String content = "";
                    Object commonCard = ApiTemplateRoot_common_card.get(result);
                    Object feed_content = ApiFeedCard.isInstance(commonCard) ? ApiFeedCard_feed_content.get(commonCard) : null;
                    if (feed_content != null) {
                        hasVideo = ApiFeedContent_video.get(feed_content) != null;

                        String panelTitle = readApiText(ApiFeedContent_title.get(feed_content));
                        if (panelTitle != null) {
                            title = panelTitle;
                        }

                        Object sourceLine = ApiFeedContent_sourceLine.get(feed_content);
                        List elements = ApiLine.isInstance(sourceLine) ? (List) ApiLine_elements.get(sourceLine) : null;
                        if (elements != null && elements.size() > 1) {
                            String panelAuthor = readApiElementText(elements.get(1));
                            if (panelAuthor != null) {
                                author = panelAuthor;
                            }
                        }

                        String panelContent = readApiText(ApiFeedContent_content.get(feed_content));
                        if (panelContent != null) {
                            content = panelContent;
                        }
                    }

                    if (filter(type, hasVideo, title, author, content)) {
                        param.setResult(null);
                    }
                } else if (resultClass == Card) {
                    Object extra = Card_extra.get(result);
                    if (extra == null) {
                        return;
                    }
                    String type = (String) Card_Extra_contentType.get(extra);
                    boolean hasVideo = false;
                    String title = null;
                    String author = null;
                    String content = null;
                    List<?> elements = (List<?>) Card_elements.get(result);
                    if (elements != null) {
                        for (Object element : elements) {
                            if (!Element.isInstance(element)) {
                                continue;
                            }
                            String id = (String) Element_id.get(element);
                            if (id == null) {
                                continue;
                            }
                            if (title == null && author == null && id.equals("Text")) {
                                title = readElementText(element);
                                continue;
                            }
                            if (author == null && content == null && element.getClass() == Line && id.equals("0")) {
                                List<?> lineElements = (List<?>) Line_elements.get(element);
                                if (lineElements != null && !lineElements.isEmpty()) {
                                    Object avatarElement = lineElements.get(0);
                                    if (avatarElement != null && avatarElement.getClass() == Avatar) {
                                        author = readLineFirstText(lineElements);
                                        continue;
                                    }
                                }
                            }
                            // 11.10.0 起作者行也可能由 Author 元素承载，取其中第一行的首个文本元素
                            if (author == null && content == null && Author != null && element.getClass() == Author) {
                                Object firstLine = Author_fistLine.get(element);
                                String authorText = firstLine != null && firstLine.getClass() == Line ? readLineFirstText((List<?>) Line_elements.get(firstLine)) : null;
                                if (authorText != null) {
                                    author = authorText;
                                    continue;
                                }
                            }
                            if (content == null && id.endsWith("_summary")) {
                                content = readElementText(element);
                            }
                            if (element.getClass() == Video) {
                                hasVideo = true;
                            }
                        }
                    }

                    if (filter(type, hasVideo, title, author, content)) {
                        param.setResult(null);
                    }
                } else if (resultClass == MarketCard) {
                    if (Helper.prefs.getBoolean("switch_marketcard", false)) {
                        param.setResult(null);
                    }
                }
            }
        });
    }

    private static String readApiText(Object apiText) throws IllegalAccessException {
        if (!ApiText.isInstance(apiText)) {
            return null;
        }
        Object text = ApiText_panel_text.get(apiText);
        return text instanceof String ? (String) text : null;
    }

    private static String readApiElementText(Object apiElement) throws IllegalAccessException {
        if (!ApiElement.isInstance(apiElement)) {
            return null;
        }
        return readApiText(ApiElement_text.get(apiElement));
    }

    private static String readElementText(Object element) throws IllegalAccessException {
        if (element == null) {
            return null;
        }
        if (element.getClass() == TextElement) {
            return (String) TextElement_text.get(element);
        }
        if (element.getClass() == HtmlTextElement) {
            return (String) HtmlTextElement_text.get(element);
        }
        return null;
    }

    private static String readLineFirstText(List<?> lineElements) throws IllegalAccessException {
        if (lineElements == null) {
            return null;
        }
        for (Object lineElement : lineElements) {
            String text = readElementText(lineElement);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private boolean filter(String type, boolean hasVideo, String title, String author, String content) {
        if (type == null) {
            return false;
        }
        if (Helper.prefs.getBoolean("switch_video", false) && (type.equals("zvideo") || type.equals("drama") || hasVideo)) {
            return true;
        }
        if (Helper.prefs.getBoolean("switch_removearticle", false) && ("article".equals(type) || "Post".equals(type))) {
            return true;
        }
        if (Helper.prefs.getBoolean("switch_pin", false) && "pin".equals(type)) {
            return true;
        }
        if (Helper.prefs.getBoolean("switch_feedad", true) && "SvipActivity".equals(type)) {
            return true;
        }

        if (Helper.regex_title != null && title != null && Helper.regex_title.matcher(title).find()) {
            return true;
        }
        if (Helper.regex_author != null && author != null && Helper.regex_author.matcher(author).find()) {
            return true;
        }
        if (Helper.regex_content != null && content != null && Helper.regex_content.matcher(content).find()) {
            return true;
        }

        return false;
    }
}
