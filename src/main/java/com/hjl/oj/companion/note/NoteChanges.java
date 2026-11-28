package com.hjl.oj.companion.note;

import java.util.List;

/**
 * 把修改应用到笔记正文上。提议时（鸭鸭调用工具）和应用时（笔记在这之后可能又被改过）共用这一份逻辑。
 */
public final class NoteChanges {

    private NoteChanges() {
    }

    /**
     * 一处修改的结果：成功时 content 是修改后的正文，失败时 failure 是给模型看的原因
     */
    public record Outcome(String content, String failure) {

        public boolean ok() {
            return failure == null;
        }

        static Outcome success(String content) {
            return new Outcome(content, null);
        }

        static Outcome fail(String failure) {
            return new Outcome(null, failure);
        }
    }

    /**
     * 在笔记的新内容上重新应用一组修改的结果
     *
     * @param content   应用后的正文
     * @param applied   应用上的修改数
     * @param failed    原文找不到或不唯一、被跳过的修改数
     * @param rewritten 其中有整篇改写：会覆盖笔记在提议之后的改动
     */
    public record Replay(String content, int applied, int failed, boolean rewritten) {
    }

    public static Outcome apply(String content, NoteChange change) {
        String current = normalize(content);
        String newText = normalize(change.getNewText());
        return switch (change.getKind()) {
            case NoteChange.KIND_REPLACE -> replace(current, normalize(change.getOldText()), newText);
            case NoteChange.KIND_APPEND -> newText.isBlank()
                    ? Outcome.fail("要追加的内容不能为空")
                    : Outcome.success(append(current, newText));
            case NoteChange.KIND_REWRITE -> newText.isBlank()
                    ? Outcome.fail("新的正文不能为空")
                    : Outcome.success(newText);
            default -> Outcome.fail("不认识的修改类型 " + change.getKind());
        };
    }

    /**
     * 依次应用，失败的跳过（笔记在提议之后被改过，原文对不上时）
     */
    public static Replay replay(String content, List<NoteChange> changes) {
        String current = normalize(content);
        int applied = 0;
        int failed = 0;
        boolean rewritten = false;
        for (NoteChange change : changes) {
            Outcome outcome = apply(current, change);
            if (outcome.ok()) {
                current = outcome.content();
                applied++;
                rewritten |= NoteChange.KIND_REWRITE.equals(change.getKind());
            } else {
                failed++;
            }
        }
        return new Replay(current, applied, failed, rewritten);
    }

    private static Outcome replace(String content, String oldText, String newText) {
        if (oldText.isEmpty()) {
            return Outcome.fail("原文不能为空；要加在末尾请用 appendToNote");
        }
        int occurrences = countOccurrences(content, oldText);
        if (occurrences == 0) {
            return Outcome.fail("在笔记里没找到这段原文，请从 readNote 读到的内容里原样复制");
        }
        if (occurrences > 1) {
            return Outcome.fail("这段原文在笔记里出现了 " + occurrences + " 次，请多带一点上下文让它唯一");
        }
        if (oldText.equals(newText)) {
            return Outcome.fail("新内容和原文一样");
        }
        int index = content.indexOf(oldText);
        return Outcome.success(content.substring(0, index) + newText + content.substring(index + oldText.length()));
    }

    /**
     * 追加到末尾：和前文之间空一行
     */
    static String append(String content, String text) {
        String body = text.strip();
        String head = content.stripTrailing();
        return head.isEmpty() ? body + "\n" : head + "\n\n" + body + "\n";
    }

    static String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n");
    }

    private static int countOccurrences(String text, String target) {
        int count = 0;
        for (int from = text.indexOf(target); from >= 0; from = text.indexOf(target, from + 1)) {
            count++;
        }
        return count;
    }
}
