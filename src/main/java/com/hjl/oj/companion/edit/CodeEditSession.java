package com.hjl.oj.companion.edit;

import com.hjl.oj.companion.hint.EditPermission;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 一次请求内的代码修改会话：针对请求带上来的代码快照记录修改，不直接改用户的编辑器。
 *
 * <p>每处修改要求原文在当前工作副本中唯一匹配，并按改代码权限校验；返回给模型的都是可读的结果说明，
 * 失败时模型可以据此调整后重试。
 */
public class CodeEditSession {

    static final int MAX_EDITS_PER_TURN = 10;

    /**
     * 模型偶尔会把展示用的行号前缀（如 "12| "）一起抄进原文
     */
    private static final Pattern LINE_NUMBER_PREFIX = Pattern.compile("(?m)^\\s*\\d+\\| ?");

    private final String baseHash;

    private final String language;

    private final EditPermission permission;

    private final List<CodeEdit> edits = new ArrayList<>();

    private String workingCode;

    public CodeEditSession(String code, String language, EditPermission permission) {
        String original = code == null ? "" : code;
        this.baseHash = sha256(original);
        this.workingCode = normalize(original);
        this.language = language;
        this.permission = permission;
    }

    public EditPermission permission() {
        return permission;
    }

    public synchronized String replace(String oldText, String newText, String reason) {
        if (edits.size() >= MAX_EDITS_PER_TURN) {
            return "修改失败：这一轮的修改已经达到 " + MAX_EDITS_PER_TURN + " 处上限";
        }
        if (oldText == null || oldText.isEmpty()) {
            return "修改失败：原文不能为空。要在某处插入内容，请把插入点所在的那一行作为原文，连同新内容一起替换";
        }
        String target = normalize(oldText);
        String replacement = normalize(newText == null ? "" : newText);
        int occurrences = countOccurrences(workingCode, target);
        if (occurrences == 0) {
            String withoutLineNumbers = LINE_NUMBER_PREFIX.matcher(target).replaceAll("");
            if (!withoutLineNumbers.equals(target) && countOccurrences(workingCode, withoutLineNumbers) == 1) {
                target = withoutLineNumbers;
                replacement = LINE_NUMBER_PREFIX.matcher(replacement).replaceAll("");
                occurrences = 1;
            }
        }
        if (occurrences == 0) {
            return "修改失败：在用户代码里没找到这段原文。请从代码中原样复制（不要带行号前缀，注意缩进）";
        }
        if (occurrences > 1) {
            return "修改失败：这段原文在代码里出现了 " + occurrences + " 次，请多带几行上下文让它唯一";
        }
        if (target.equals(replacement)) {
            return "修改失败：新内容和原文一样";
        }
        Optional<String> rejection = CodeEditPolicy.check(target, replacement, permission, language);
        if (rejection.isPresent()) {
            return "修改被拒绝：" + rejection.get();
        }
        int index = workingCode.indexOf(target);
        int startLine = lineNumberAt(workingCode, index);
        workingCode = workingCode.substring(0, index) + replacement + workingCode.substring(index + target.length());
        edits.add(new CodeEdit(CodeEdit.KIND_REPLACE, target, replacement, startLine, reason));
        return "已记录修改（第 " + startLine + " 行起），用户预览后决定是否应用";
    }

    public synchronized String rewrite(String newCode, String reason) {
        if (permission != EditPermission.UNRESTRICTED) {
            return "修改被拒绝：当前等级不能整体替换代码";
        }
        if (newCode == null || newCode.isBlank()) {
            return "修改失败：新代码不能为空";
        }
        workingCode = normalize(newCode);
        edits.add(new CodeEdit(CodeEdit.KIND_REWRITE, null, workingCode, 1, reason));
        return "已记录整体替换，用户预览后决定是否应用";
    }

    public synchronized Optional<CodeEditProposal> proposal() {
        if (edits.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new CodeEditProposal(baseHash, List.copyOf(edits), workingCode));
    }

    static String normalize(String text) {
        return text.replace("\r\n", "\n");
    }

    private static int countOccurrences(String text, String target) {
        int count = 0;
        for (int from = text.indexOf(target); from >= 0; from = text.indexOf(target, from + 1)) {
            count++;
        }
        return count;
    }

    private static int lineNumberAt(String text, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (text.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    public static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-256", e);
        }
    }
}
