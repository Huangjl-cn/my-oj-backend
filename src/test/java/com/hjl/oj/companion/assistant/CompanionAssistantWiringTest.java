package com.hjl.oj.companion.assistant;

import com.hjl.oj.ai.key.AiCredential;
import com.hjl.oj.ai.model.mock.MockChatModelProvider;
import com.hjl.oj.companion.memory.MysqlChatMemoryStore;
import com.hjl.oj.companion.mock.CompanionMockReplies;
import com.hjl.oj.companion.note.NoteEditSession;
import com.hjl.oj.companion.note.NoteProposal;
import com.hjl.oj.companion.tool.NoteTools;
import com.hjl.oj.companion.trace.TraceRecorder;
import com.hjl.oj.model.entity.CompanionTraceStep;
import com.hjl.oj.model.entity.User;
import com.hjl.oj.service.CompanionTraceService;
import com.hjl.oj.service.NoteService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
    void mockChatCallsTheNoteToolAndAnswersAfterIt() throws Exception {
        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(100);
        NoteEditSession notes = new NoteEditSession(user(), 7L, "两数之和", 0, "", mock(NoteService.class));
        NoteTools tools = new NoteTools(user(), 7L, mock(NoteService.class), notes, 4000);
        StringBuilder streamed = new StringBuilder();
        CompletableFuture<String> finished = new CompletableFuture<>();

        factory.chatAssistant(credential, memory, List.of(tools))
                .chat(SYSTEM_WITH_BRACES, "【场景】聊天\n\n【用户说】\n把这段对话整理成一篇笔记")
                .onPartialResponse(streamed::append)
                .onCompleteResponse(response -> finished.complete(response.aiMessage().text()))
                .onError(finished::completeExceptionally)
                .start();

        assertTrue(finished.get(10, TimeUnit.SECONDS).contains("整理成了一篇笔记"));
        assertTrue(streamed.toString().contains("整理成了一篇笔记"));
        assertEquals(1, notes.proposals().size());
        NoteProposal proposal = notes.proposals().get(0);
        assertEquals(NoteProposal.KIND_CREATE, proposal.getKind());
        assertEquals(7L, proposal.getQuestionId());
        // 记忆里依次是：系统、用户、发起工具调用、工具结果、最后的回答
        List<ChatMessage> messages = memory.messages();
        assertTrue(((AiMessage) messages.get(2)).hasToolExecutionRequests());
        assertInstanceOf(ToolExecutionResultMessage.class, messages.get(3));
        assertInstanceOf(AiMessage.class, messages.get(4));
    }

    @Test
    void mockChatWithoutNoteWordsDoesNotCallTools() throws Exception {
        NoteEditSession notes = new NoteEditSession(user(), 7L, "两数之和", 0, "", mock(NoteService.class));
        CompletableFuture<String> finished = new CompletableFuture<>();

        factory.chatAssistant(credential, MessageWindowChatMemory.withMaxMessages(100),
                        List.of(new NoteTools(user(), 7L, mock(NoteService.class), notes, 4000)))
                .chat(SYSTEM_WITH_BRACES, USER_WITH_BRACES)
                .onPartialResponse(ignored -> {
                })
                .onCompleteResponse(response -> finished.complete(response.aiMessage().text()))
                .onError(finished::completeExceptionally)
                .start();

        assertTrue(finished.get(10, TimeUnit.SECONDS).contains("模拟模式"));
        assertTrue(notes.proposals().isEmpty());
    }

    @Test
    void theTraceRecorderSeesEveryModelCallAndTool() throws Exception {
        CompanionTraceService store = mock(CompanionTraceService.class);
        TraceRecorder trace = new TraceRecorder(store, 5L, 7L);
        NoteEditSession notes = new NoteEditSession(user(), 7L, "两数之和", 0, "", mock(NoteService.class));
        NoteTools tools = new NoteTools(user(), 7L, mock(NoteService.class), notes, 4000);
        CompletableFuture<String> finished = new CompletableFuture<>();

        factory.chatAssistant(credential, MessageWindowChatMemory.withMaxMessages(100), List.of(tools), trace)
                .chat(SYSTEM_WITH_BRACES, "【场景】聊天\n\n【用户说】\n把这段对话整理成一篇笔记")
                .onToolExecuted(trace::recordTool)
                .onPartialResponse(ignored -> {
                })
                .onCompleteResponse(response -> finished.complete(response.aiMessage().text()))
                .onError(finished::completeExceptionally)
                .start();
        finished.get(10, TimeUnit.SECONDS);

        verify(store).describeTurn(eq(5L), eq(7L), eq(SYSTEM_WITH_BRACES), contains("把这段对话整理成一篇笔记"),
                argThat(list -> list.stream().anyMatch(tool -> tool.getName().equals("createNote"))));
        // 模型发起工具调用、工具执行、模型给出回答，各记一步（写库先后取决于框架回调的顺序，不断言）
        ArgumentCaptor<CompanionTraceStep> captor = ArgumentCaptor.forClass(CompanionTraceStep.class);
        verify(store, times(3)).saveStep(captor.capture());
        List<CompanionTraceStep> steps = captor.getAllValues();
        assertTrue(steps.stream().anyMatch(step -> "model".equals(step.getKind()) && step.getToolCalls() != null
                && step.getToolCalls().contains("createNote")));
        assertTrue(steps.stream().anyMatch(step -> "tool".equals(step.getKind())
                && step.getOutput().startsWith("已记录")));
        assertTrue(steps.stream().anyMatch(step -> "model".equals(step.getKind()) && step.getOutput() != null
                && step.getOutput().contains("整理成了一篇笔记")));
    }

    private static User user() {
        User user = new User();
        user.setId(1L);
        return user;
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
