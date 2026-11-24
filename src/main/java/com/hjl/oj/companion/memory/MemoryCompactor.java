package com.hjl.oj.companion.memory;

import com.hjl.oj.companion.assistant.CompanionCompactionAssistant;
import com.hjl.oj.companion.prompt.CompanionPrompts;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 记忆压缩，做法参照 Claude Code：较早的对话整理成分节的解题笔记，用户的提问原文保留，最近几轮原样保留。
 *
 * <p>压缩后的记忆 = [笔记（用户消息）, 确认（陪练消息）] + 最近几轮。再次压缩时用"旧笔记 + 新一段对话"合并出新笔记；
 * 待整理的内容超过单次处理上限时分段滚动压缩，因此不管历史多长都能跑完。系统消息不保存，下一次调用时由 AI Service 重新加上。
 */
@Component
public class MemoryCompactor {

    static final String NOTE_PREFIX = "【之前对话的整理笔记】\n以下笔记代替了更早的原始对话：\n\n";

    static final String NOTE_ACK = "好的，我记住了之前的进展。";

    /**
     * 单次压缩调用最多处理的对话字符数，超过时分段
     */
    static final int CHUNK_CHARS = 200_000;

    static final int MAX_QUESTION_CHARS = 300;

    static final int MAX_QUESTIONS = 50;

    private final ChatMemoryStore chatMemoryStore;

    private final CompanionPrompts companionPrompts;

    public MemoryCompactor(MysqlChatMemoryStore chatMemoryStore, CompanionPrompts companionPrompts) {
        this.chatMemoryStore = chatMemoryStore;
        this.companionPrompts = companionPrompts;
    }

    /**
     * @param keepRecentRounds 原样保留的最近轮数；历史不足时至少留下最后一轮
     * @param userQuestions    用户在本题问过的问题（原文，按时间正序），写进笔记
     * @return 没有可整理的内容时返回空
     */
    public Optional<CompactionResult> compact(SessionMemoryId memoryId, CompanionCompactionAssistant assistant,
                                              String systemPrompt, int keepRecentRounds, List<String> userQuestions) {
        List<ChatMessage> messages = chatMemoryStore.getMessages(memoryId);
        String previousNote = null;
        List<List<ChatMessage>> rounds = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message instanceof SystemMessage) {
                continue;
            }
            if (i <= 1 && isNote(message)) {
                previousNote = ((UserMessage) message).singleText().substring(NOTE_PREFIX.length());
                if (i + 1 < messages.size() && messages.get(i + 1) instanceof AiMessage) {
                    i++;
                }
                continue;
            }
            if (message instanceof UserMessage || rounds.isEmpty()) {
                rounds.add(new ArrayList<>());
            }
            rounds.get(rounds.size() - 1).add(message);
        }
        int keep = Math.min(keepRecentRounds, rounds.size() - 1);
        if (keep < 0 || rounds.size() - keep <= 0) {
            return Optional.empty();
        }
        List<ChatMessage> older = rounds.subList(0, rounds.size() - keep).stream().flatMap(List::stream).toList();
        List<ChatMessage> recent = rounds.subList(rounds.size() - keep, rounds.size()).stream().flatMap(List::stream).toList();
        if (older.isEmpty()) {
            return Optional.empty();
        }

        String note = previousNote;
        for (String chunk : chunks(MemoryTranscript.render(older, Integer.MAX_VALUE))) {
            note = assistant.compact(systemPrompt, companionPrompts.compactionMessage(note, chunk)).content();
        }
        String fullNote = withUserQuestions(note == null ? "" : note.strip(), userQuestions);

        List<ChatMessage> compacted = new ArrayList<>();
        compacted.add(UserMessage.from(NOTE_PREFIX + fullNote));
        compacted.add(AiMessage.from(NOTE_ACK));
        compacted.addAll(recent);
        chatMemoryStore.updateMessages(memoryId, compacted);
        int estimatedTokens = (systemPrompt.length() + ChatMessageSerializer.messagesToJson(compacted).length()) / 2;
        return Optional.of(new CompactionResult(fullNote, rounds.size() - keep, keep, estimatedTokens));
    }

    static boolean isNote(ChatMessage message) {
        return message instanceof UserMessage userMessage && userMessage.hasSingleText()
                && userMessage.singleText().startsWith(NOTE_PREFIX);
    }

    static List<String> chunks(String transcript) {
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < transcript.length(); start += CHUNK_CHARS) {
            chunks.add(transcript.substring(start, Math.min(transcript.length(), start + CHUNK_CHARS)));
        }
        return chunks;
    }

    static String withUserQuestions(String note, List<String> userQuestions) {
        StringBuilder text = new StringBuilder(note);
        if (!userQuestions.isEmpty()) {
            text.append("\n\n## 用户问过的问题（原文）\n");
            List<String> latest = userQuestions.subList(Math.max(0, userQuestions.size() - MAX_QUESTIONS), userQuestions.size());
            for (String question : latest) {
                String trimmed = question.strip();
                if (trimmed.length() > MAX_QUESTION_CHARS) {
                    trimmed = trimmed.substring(0, MAX_QUESTION_CHARS) + "……";
                }
                text.append("- ").append(trimmed.replace("\n", " ")).append("\n");
            }
        }
        return text.toString().strip();
    }

    /**
     * @param note             整理后的笔记全文
     * @param compactedRounds  被整理掉的轮数
     * @param keptRounds       原样保留的轮数
     * @param estimatedTokens  压缩后上下文的粗略 token 数（下一次调用后以模型返回的实际值为准）
     */
    public record CompactionResult(String note, int compactedRounds, int keptRounds, int estimatedTokens) {
    }
}
