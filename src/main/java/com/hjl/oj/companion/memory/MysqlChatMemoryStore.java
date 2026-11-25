package com.hjl.oj.companion.memory;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.hjl.oj.mapper.CompanionConversationMapper;
import com.hjl.oj.model.entity.CompanionConversation;
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
 * 对话记忆存进 companion_conversation.memory（LangChain4j 自带的序列化格式，含工具调用和工具结果）。
 *
 * <p>写入前去掉 AiMessage 里的思考内容：思考不需要回传给模型，留着只会让记忆膨胀。对话行由业务层先创建。
 */
@Slf4j
@Component
public class MysqlChatMemoryStore implements ChatMemoryStore {

    private final CompanionConversationMapper conversationMapper;

    public MysqlChatMemoryStore(CompanionConversationMapper conversationMapper) {
        this.conversationMapper = conversationMapper;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        ConversationMemoryId id = cast(memoryId);
        CompanionConversation conversation = conversationMapper.selectOne(new QueryWrapper<CompanionConversation>()
                .select("memory")
                .eq("id", id.conversationId()));
        if (conversation == null || conversation.getMemory() == null || conversation.getMemory().isBlank()) {
            return List.of();
        }
        return ChatMessageDeserializer.messagesFromJson(conversation.getMemory());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        ConversationMemoryId id = cast(memoryId);
        String json = ChatMessageSerializer.messagesToJson(withoutThinking(messages));
        int updated = conversationMapper.update(null, new UpdateWrapper<CompanionConversation>()
                .eq("id", id.conversationId())
                .set("memory", json)
                .set("memoryUpdateTime", new Date()));
        if (updated == 0) {
            log.warn("写入陪练记忆时对话不存在 conversationId={}", id.conversationId());
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        ConversationMemoryId id = cast(memoryId);
        conversationMapper.update(null, new UpdateWrapper<CompanionConversation>()
                .eq("id", id.conversationId())
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

    private static ConversationMemoryId cast(Object memoryId) {
        if (memoryId instanceof ConversationMemoryId id) {
            return id;
        }
        throw new IllegalArgumentException("不支持的记忆 ID：" + memoryId);
    }
}
