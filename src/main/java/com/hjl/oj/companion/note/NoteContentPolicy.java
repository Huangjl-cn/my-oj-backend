package com.hjl.oj.companion.note;

import com.hjl.oj.companion.hint.HintLadder;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 鸭鸭往笔记里写的内容同样受提示档位约束：还没到「看答案」时，不能把解法代码写进笔记。
 *
 * <p>只数代码块里新写的代码行：用户自己代码里、笔记原文里已有的行照抄不算，只有括号这类没有字母数字的行也不算。
 * 文字部分的分寸和聊天回答一样由提示词约束。
 */
public final class NoteContentPolicy {

    /**
     * 「看答案」之前，一次最多新写这么多行代码（够写一行修正、一个关键表达式，写不出完整解法）
     */
    static final int MAX_NEW_CODE_LINES = 3;

    private static final Pattern FENCE = Pattern.compile("^ {0,3}(```|~~~)");

    private static final Pattern MEANINGFUL = Pattern.compile("[\\p{L}\\p{N}]");

    private NoteContentPolicy() {
    }

    /**
     * @param addedText 这次要写进笔记的内容
     * @param level     当前生效的提示档位
     * @param known     用户自己写过的内容（编辑器里的代码、笔记原来的正文）
     * @return 越界时返回拒绝原因（会转告模型），不越界返回空
     */
    public static Optional<String> check(String addedText, int level, String... known) {
        if (level >= HintLadder.SOLUTION_LEVEL || addedText == null || addedText.isBlank()) {
            return Optional.empty();
        }
        Set<String> knownLines = new HashSet<>();
        for (String text : known) {
            if (text != null) {
                text.replace("\r\n", "\n").lines().map(String::strip).forEach(knownLines::add);
            }
        }
        int newCodeLines = 0;
        boolean inCode = false;
        for (String line : addedText.replace("\r\n", "\n").split("\n", -1)) {
            if (FENCE.matcher(line).find()) {
                inCode = !inCode;
                continue;
            }
            String trimmed = line.strip();
            if (inCode && MEANINGFUL.matcher(trimmed).find() && !knownLines.contains(trimmed)) {
                newCodeLines++;
            }
        }
        if (newCodeLines > MAX_NEW_CODE_LINES) {
            return Optional.of("还没到「看答案」这一档，笔记里不能写解法代码（这次新写了 " + newCodeLines
                    + " 行代码，最多 " + MAX_NEW_CODE_LINES + " 行）。思路和步骤用文字记；用户自己的代码可以原样收录");
        }
        return Optional.empty();
    }
}
