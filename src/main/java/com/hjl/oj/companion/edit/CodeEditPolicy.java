package com.hjl.oj.companion.edit;

import com.hjl.oj.companion.hint.EditPermission;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * 按改代码权限校验一处修改是否越界。只看能确定判断的东西：改了几行有效代码、是否只加了注释、单行改动幅度，
 * 不去猜修改的"意图"。
 */
public final class CodeEditPolicy {

    /**
     * 只修语法时，单行改动允许的最大编辑距离（字符数）
     */
    static final int SYNTAX_MAX_CHAR_DISTANCE = 12;

    static final int SMALL_CHANGE_MAX_LINES = 3;

    static final int MAX_COMMENT_LINES = 10;

    /**
     * 超过这个行数的修改不做逐行比对，非"不限制"权限下直接拒绝
     */
    private static final int MAX_DIFF_LINES = 400;

    private static final String PYTHON = "python";

    private CodeEditPolicy() {
    }

    /**
     * @return 越界时返回拒绝原因（会转告模型），不越界返回空
     */
    public static Optional<String> check(String oldText, String newText, EditPermission permission, String language) {
        if (permission == EditPermission.UNRESTRICTED) {
            return Optional.empty();
        }
        List<String> oldLines = lines(oldText);
        List<String> newLines = lines(newText);
        if (oldLines.size() > MAX_DIFF_LINES || newLines.size() > MAX_DIFF_LINES) {
            return Optional.of("这处修改太大，当前等级不允许，请改用文字提示");
        }
        Changes changes = Changes.between(oldLines, newLines, language);
        return switch (permission) {
            case SYNTAX_ONLY -> {
                if (changes.commentLines > 0) {
                    yield Optional.of("当前等级还不能在代码里加批注，请改用文字提示");
                }
                yield checkSyntaxOnly(changes);
            }
            case ANNOTATE -> {
                if (changes.commentLines > MAX_COMMENT_LINES) {
                    yield Optional.of("批注太多了，每处最多加 " + MAX_COMMENT_LINES + " 行批注");
                }
                yield checkSyntaxOnly(changes);
            }
            case SMALL_CHANGE -> {
                if (changes.commentLines > MAX_COMMENT_LINES) {
                    yield Optional.of("批注太多了，每处最多加 " + MAX_COMMENT_LINES + " 行批注");
                }
                int changedLines = Math.max(changes.addedCode.size(), changes.removedCode.size());
                if (changedLines > SMALL_CHANGE_MAX_LINES) {
                    yield Optional.of("当前等级每处最多改动 " + SMALL_CHANGE_MAX_LINES + " 行有效代码，这处改了 "
                            + changedLines + " 行，请缩小范围或改用文字提示");
                }
                yield Optional.empty();
            }
            case UNRESTRICTED -> Optional.empty();
        };
    }

    private static Optional<String> checkSyntaxOnly(Changes changes) {
        List<String> added = changes.addedCode;
        List<String> removed = changes.removedCode;
        if (added.isEmpty() && removed.isEmpty()) {
            return Optional.empty();
        }
        if (added.size() == 1 && removed.size() == 1
                && editDistance(added.get(0).strip(), removed.get(0).strip()) <= SYNTAX_MAX_CHAR_DISTANCE) {
            return Optional.empty();
        }
        if (added.size() == 1 && removed.isEmpty() && isImportLike(added.get(0))) {
            return Optional.empty();
        }
        return Optional.of("当前等级只能修语法、编译错误这类很小的改动（每处改一行、改动很少），这处修改超出范围，请改用文字提示");
    }

    static List<String> lines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return List.of(text.replace("\r\n", "\n").split("\n", -1));
    }

    static boolean isComment(String line, String language) {
        String trimmed = line.strip();
        if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
            return true;
        }
        return PYTHON.equals(language) && trimmed.startsWith("#");
    }

    /**
     * 去掉行尾注释，只留代码部分（简单处理，不识别字符串里的注释符号）
     */
    static String codePart(String line, String language) {
        String code = line;
        int slashComment = code.indexOf("//");
        if (slashComment >= 0) {
            code = code.substring(0, slashComment);
        }
        if (PYTHON.equals(language)) {
            int hashComment = code.indexOf('#');
            if (hashComment >= 0) {
                code = code.substring(0, hashComment);
            }
        }
        return code.strip();
    }

    private static boolean isImportLike(String line) {
        String trimmed = line.strip();
        return trimmed.startsWith("import ") || trimmed.startsWith("#include") || trimmed.startsWith("using ")
                || trimmed.startsWith("from ") || trimmed.startsWith("package ");
    }

    static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    /**
     * 一处修改按行比对后的变化：新增 / 删除的有效代码行，以及新增的批注行数。
     * 只在行尾加了注释的代码行（代码部分不变）算作批注，不算改代码。
     */
    private static final class Changes {

        private final List<String> addedCode = new ArrayList<>();

        private final List<String> removedCode = new ArrayList<>();

        private int commentLines;

        static Changes between(List<String> oldLines, List<String> newLines, String language) {
            List<String> added = new ArrayList<>();
            List<String> removed = new ArrayList<>();
            diff(oldLines, newLines, added, removed);
            Changes changes = new Changes();
            for (String line : added) {
                if (line.isBlank()) {
                    continue;
                }
                if (isComment(line, language)) {
                    changes.commentLines++;
                } else {
                    changes.addedCode.add(line);
                }
            }
            for (String line : removed) {
                if (!line.isBlank() && !isComment(line, language)) {
                    changes.removedCode.add(line);
                }
            }
            // 代码部分没变、只多了行尾注释的行：视为批注
            Iterator<String> addedIterator = changes.addedCode.iterator();
            while (addedIterator.hasNext()) {
                String addedLine = addedIterator.next();
                String addedCodePart = codePart(addedLine, language);
                for (Iterator<String> removedIterator = changes.removedCode.iterator(); removedIterator.hasNext(); ) {
                    if (codePart(removedIterator.next(), language).equals(addedCodePart)) {
                        removedIterator.remove();
                        addedIterator.remove();
                        changes.commentLines++;
                        break;
                    }
                }
            }
            return changes;
        }

        /**
         * 基于最长公共子序列的行级比对，忽略首尾空白
         */
        private static void diff(List<String> oldLines, List<String> newLines, List<String> added, List<String> removed) {
            int n = oldLines.size();
            int m = newLines.size();
            int[][] lcs = new int[n + 1][m + 1];
            for (int i = n - 1; i >= 0; i--) {
                for (int j = m - 1; j >= 0; j--) {
                    lcs[i][j] = oldLines.get(i).strip().equals(newLines.get(j).strip())
                            ? lcs[i + 1][j + 1] + 1
                            : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
            int i = 0;
            int j = 0;
            while (i < n && j < m) {
                if (oldLines.get(i).strip().equals(newLines.get(j).strip())) {
                    i++;
                    j++;
                } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                    removed.add(oldLines.get(i++));
                } else {
                    added.add(newLines.get(j++));
                }
            }
            while (i < n) {
                removed.add(oldLines.get(i++));
            }
            while (j < m) {
                added.add(newLines.get(j++));
            }
        }
    }
}
