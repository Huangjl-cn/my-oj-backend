package com.hjl.oj.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * 笔记列表的摘要：把 Markdown 正文粗略转成纯文字，取开头一段。纯计算，不访问数据库。
 */
public final class NoteSnippets {

    private static final Pattern CODE_FENCE = Pattern.compile("(?s)```.*?(```|$)");
    private static final Pattern IMAGE = Pattern.compile("!\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern LINE_MARKS = Pattern.compile("(?m)^\\s{0,3}(#{1,6}\\s+|>\\s?|[-*+]\\s+\\[[ xX]]\\s+|[-*+]\\s+|\\d+[.)]\\s+)");
    private static final Pattern INLINE_MARKS = Pattern.compile("[*_~`]+");
    private static final Pattern RULE = Pattern.compile("(?m)^\\s*([-*_]\\s*){3,}$");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private NoteSnippets() {
    }

    /**
     * @param markdown 正文
     * @param maxLength 最多多少个字，超出用「…」结尾
     */
    public static String plain(String markdown, int maxLength) {
        if (StringUtils.isBlank(markdown)) {
            return "";
        }
        String text = CODE_FENCE.matcher(markdown).replaceAll(" [代码] ");
        text = IMAGE.matcher(text).replaceAll("$1");
        text = LINK.matcher(text).replaceAll("$1");
        text = RULE.matcher(text).replaceAll(" ");
        text = LINE_MARKS.matcher(text).replaceAll("");
        text = INLINE_MARKS.matcher(text).replaceAll("");
        text = SPACES.matcher(text).replaceAll(" ").strip();
        if (text.codePointCount(0, text.length()) <= maxLength) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, maxLength)).stripTrailing() + "…";
    }

    /**
     * 卡片墙用的正文片段，仍是 Markdown：超长时尽量在整行处截断；截在代码块中间就补上结尾的 ```，后面加一行「…」
     *
     * @param markdown  正文（可以只是开头一段）
     * @param maxLength 最多多少个字
     */
    public static String excerpt(String markdown, int maxLength) {
        if (StringUtils.isBlank(markdown)) {
            return "";
        }
        String text = markdown.replace("\r\n", "\n").strip();
        if (text.codePointCount(0, text.length()) <= maxLength) {
            return text;
        }
        String cut = text.substring(0, text.offsetByCodePoints(0, maxLength));
        int lineEnd = cut.lastIndexOf('\n');
        if (lineEnd > cut.length() / 2) {
            cut = cut.substring(0, lineEnd);
        }
        cut = cut.stripTrailing();
        if (StringUtils.countMatches(cut, "```") % 2 == 1) {
            cut += "\n```";
        }
        return cut + "\n\n…";
    }
}
