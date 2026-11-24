package com.hjl.oj.companion.memory;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hjl.oj.mapper.CompanionSessionMapper;
import com.hjl.oj.model.entity.CompanionSession;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 会话记忆存进 companion_session.memory（LangChain4j 自带的序列化格式，含工具调用和工具结果）。
 *
 * <p>写入前去掉 AiMessage 里的思考内容：思考不需要回传给模型，留着只会让记忆膨胀。会话行由业务层先创建。
 */
@Slf4j
@Component
public class MysqlChatMemoryStore implements ChatMemoryStore {

    private final CompanionSessionMapper companionSessionMapper;

    public MysqlChatMemoryStore(CompanionSessionMapper companionSessionMapper) {
        this.companionSessionMapper = companionSessionMapper;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        SessionMemoryId id = cast(memoryId);
        CompanionSession session = companionSessionMapper.selectOne(new QueryWrapper<CompanionSession>()
                .select("memory")
                .eq("userId", id.userId())
                .eq("questionId", id.questionId()));
        if (session == null || session.getMemory() == null || session.getMemory().isBlank()) {
            return List.of();
        }
        return ChatMessageDeserializer.messagesFromJson(session.getMemory());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        SessionMemoryId id = cast(memoryId);
        String json = ChatMessageSerializer.messagesToJson(withoutThinking(messages));
        int updated = companionSessionMapper.update(null, new UpdateWrapper<CompanionSession>()
                .eq("userId", id.userId())
                .eq("questionId", id.questionId())
                .set("memory", json)
                .set("memoryUpdateTime", new Date()));
        if (updated == 0) {
            log.warn("写入陪练记忆时会话不存在 userId={}, questionId={}", id.userId(), id.questionId());
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        SessionMemoryId id = cast(memoryId);
        companionSessionMapper.update(null, new UpdateWrapper<CompanionSession>()
                .eq("userId", id.userId())
                .eq("questionId", id.questionId())
                .set("memory", null)
                .set("memoryUpdateTime", new Date()));
    }

    static List<ChatMessage> withoutThinking(List<ChatMessage> messages) {
        return messages.stream()
                .map(message -> message instanceof AiMessage aiMessage && aiMessage.thinking() != null
                        ? aiMessage.toBuilder().thinking(null).build()
                        : message)
                .toList();
    }

    private static SessionMemoryId cast(Object memoryId) {
        if (memoryId instanceof SessionMemoryId id) {
            return id;
        }
        throw new IllegalArgumentException("不支持的记忆 ID：" + memoryId);
    }
}
