package com.hjl.oj.companion.assistant;

import com.hjl.oj.ai.key.AiCredential;
import com.hjl.oj.ai.model.mock.MockChatModelProvider;
import com.hjl.oj.companion.memory.MysqlChatMemoryStore;
import com.hjl.oj.companion.mock.CompanionMockReplies;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 用模拟模型驱动真实的 LangChain4j AI Service，验证接口注解、结构化输出、护栏、流式和记忆的接线。
 * 提示词里特意带上 "{{"：题目和代码常含这种文本，不能被当作模板变量。
 */
class CompanionAssistantWiringTest {

    private static final String SYSTEM_WITH_BRACES = "系统提示，题目里有 {{1,2},{3,4}}";

    private static final String USER_WITH_BRACES = "【用户说】\nvector<vector<int>> m = {{1,2}};";

    private final AiCredential credential = new AiCredential(null, "deepseek-flash");

    private final CompanionAssistantFactory factory = new CompanionAssistantFactory(
            new MockChatModelProvider(new CompanionMockReplies()), mock(MysqlChatMemoryStore.class));

    @Test
    void reactionReturnsStructuredAnswer() {
        ReactionAnswer answer = factory.reactionAssistant(credential)
                .react(SYSTEM_WITH_BRACES, USER_WITH_BRACES)
                .content();

        assertNotNull(answer.getMood());
        assertEquals(2, answer.getIntensity());
        assertNotNull(answer.getTrack());
        assertTrue(answer.getMessage().contains("模拟模式"));
    }

    @Test
    void hintReturnsStructuredAnswerWithEmptyEdits() {
        HintAnswer answer = factory.hintAssistant(credential).hint(SYSTEM_WITH_BRACES, USER_WITH_BRACES).content();

        assertNotNull(answer.getTrack());
        assertTrue(answer.getMessage().contains("模拟提示"));
        assertTrue(answer.getEdits().isEmpty());
    }

    @Test
    void compactionReturnsNote() {
        String note = factory.compactionAssistant(credential).compact(SYSTEM_WITH_BRACES, USER_WITH_BRACES).content();

        assertTrue(note.contains("## 解题进展"));
    }

    @Test
    void chatStreamsAndWritesMemoryWithSystemMessageFirst() throws Exception {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .id("test")
                .maxMessages(100)
                .chatMemoryStore(new InMemoryChatMemoryStore())
                .alwaysKeepSystemMessageFirst(true)
                .build();
        StringBuilder streamed = new StringBuilder();
        CompletableFuture<String> finished = new CompletableFuture<>();

        factory.chatAssistant(credential, memory, List.of())
                .chat(SYSTEM_WITH_BRACES, USER_WITH_BRACES)
                .onPartialResponse(streamed::append)
                .onCompleteResponse(response -> finished.complete(response.aiMessage().text()))
                .onError(finished::completeExceptionally)
                .start();

        String text = finished.get(10, TimeUnit.SECONDS);
        assertEquals(text, streamed.toString());
        List<ChatMessage> messages = memory.messages();
        assertInstanceOf(SystemMessage.class, messages.get(0));
        assertEquals(SYSTEM_WITH_BRACES, ((SystemMessage) messages.get(0)).text());
        assertEquals(USER_WITH_BRACES, ((UserMessage) messages.get(1)).singleText());
        assertInstanceOf(AiMessage.class, messages.get(2));
    }

    @Test
    void solutionStreamsThinkingBeforeAnswer() throws Exception {
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(100);
        StringBuilder thinking = new StringBuilder();
        CompletableFuture<String> finished = new CompletableFuture<>();

        factory.solutionAssistant(credential, memory)
                .solve(SYSTEM_WITH_BRACES, USER_WITH_BRACES)
                .onPartialThinking(partial -> thinking.append(partial.text()))
                .onPartialResponse(ignored -> {
                })
                .onCompleteResponse(response -> finished.complete(response.aiMessage().text()))
                .onError(finished::completeExceptionally)
                .start();

        assertTrue(finished.get(10, TimeUnit.SECONDS).contains("```"));
        assertTrue(thinking.length() > 0);
    }
}
