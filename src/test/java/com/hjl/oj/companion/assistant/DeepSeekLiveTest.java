package com.hjl.oj.companion.assistant;

import com.hjl.oj.ai.config.AiProperties;
import com.hjl.oj.ai.key.AiCredential;
import com.hjl.oj.ai.key.DeepSeekApiKeyVerifier;
import com.hjl.oj.ai.model.DeepSeekChatModelProvider;
import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.companion.context.SubmissionFacts;
import com.hjl.oj.companion.context.Verdict;
import com.hjl.oj.companion.edit.CodeEditSession;
import com.hjl.oj.companion.hint.HintLadder;
import com.hjl.oj.companion.hint.HintState;
import com.hjl.oj.companion.memory.MemoryTranscript;
import com.hjl.oj.companion.prompt.CompanionPrompts;
import com.hjl.oj.companion.tool.CodeEditTools;
import com.hjl.oj.exception.BusinessException;
import com.hjl.oj.model.dto.companion.CodeSnapshot;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.enums.HintModeEnum;
import com.hjl.oj.model.enums.HintTrackEnum;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.store.memory.chat.InMemoryChatMemoryStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用真实的 DeepSeek 跑一遍陪练的各个场景，检查提示词、结构化输出、护栏、流式、工具调用和多轮记忆在真模型下能用。
 *
 * <p>会产生少量费用，平时不跑：只有设置了环境变量 {@code DEEPSEEK_API_KEY} 时才执行，
 * 可用 {@code DEEPSEEK_MODEL} 指定模型。回答内容打印到标准输出，供人工查看质量。
 */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class DeepSeekLiveTest {

    private static final String BASE_URL = "https://api.deepseek.com";

    private static final String OVERFLOW_CODE = """
            import java.util.Scanner;

            public class Main {
                public static void main(String[] args) {
                    Scanner in = new Scanner(System.in);
                    int a = in.nextInt();
                    int b = in.nextInt();
                    System.out.println(a + b);
                }
            }
            """;

    private static final String MISSING_SEMICOLON = OVERFLOW_CODE.replace("System.out.println(a + b);",
            "System.out.println(a + b)");

    private final String apiKey = System.getenv("DEEPSEEK_API_KEY");

    private final AiCredential credential = new AiCredential(apiKey,
            System.getenv().getOrDefault("DEEPSEEK_MODEL", "deepseek-flash"));

    private final AiProperties properties = properties();

    private final CompanionPrompts prompts = new CompanionPrompts(properties);

    private final CompanionAssistantFactory factory = new CompanionAssistantFactory(
            new DeepSeekChatModelProvider(properties.getDeepseek()), null);

    private final String systemPrompt = prompts.systemMessage(question());

    @Test
    void verifierAcceptsRealKeyAndRejectsFakeOne() {
        DeepSeekApiKeyVerifier verifier = new DeepSeekApiKeyVerifier(BASE_URL, Duration.ofSeconds(10));

        verifier.verify(apiKey);
        BusinessException error = assertThrows(BusinessException.class, () -> verifier.verify("sk-not-a-real-key"));
        assertEquals(ErrorCode.AI_KEY_INVALID.getCode(), error.getCode());
    }

    @Test
    void reactsToOverflowWrongAnswer() {
        HintState state = new HintState(HintTrackEnum.IDEA, 0, HintModeEnum.GUIDED);
        SubmissionFacts facts = new SubmissionFacts(2, Verdict.WRONG_ANSWER, Verdict.COMPILE_ERROR,
                SubmissionFacts.Trend.IMPROVED, 1, 3L, false, 35L, 14_000L, 1000L, 65_536L,
                "第 3 个测试点：输入 2000000000 2000000000，期望输出 4000000000，实际输出 -294967296");

        Result<ReactionAnswer> result = factory.reactionAssistant(credential).react(systemPrompt,
                prompts.reactionMessage(state, facts, OVERFLOW_CODE, "java", List.of("别急，先看看报错信息"), ""));
        ReactionAnswer answer = result.content();

        print("reaction", answer.getMood() + " x" + answer.getIntensity() + " / " + answer.getTrack()
                + "\n" + answer.getMessage(), result.tokenUsage());
        assertNotNull(answer.getMood());
        assertTrue(answer.getIntensity() >= 1 && answer.getIntensity() <= 3);
        assertFalse(answer.getMessage().isBlank());
    }

    @Test
    void hintAtExactLineLevelProposesSmallEdit() {
        HintState state = new HintState(HintTrackEnum.BUG, 3, HintModeEnum.GUIDED);
        CodeSnapshot snapshot = snapshot(OVERFLOW_CODE);

        Result<HintAnswer> result = factory.hintAssistant(credential).hint(systemPrompt,
                prompts.hintMessage(state, 4, snapshot, "【最近的提交与你的反应】\n- 第 2 次提交：答案错误", ""));
        HintAnswer answer = result.content();

        CodeEditSession session = new CodeEditSession(snapshot.getCode(), "java",
                HintLadder.editPermission(4));
        StringBuilder applied = new StringBuilder();
        for (HintEditAnswer edit : answer.getEdits()) {
            applied.append("\n  -> ").append(session.replace(edit.getOldText(), edit.getNewText(), edit.getReason()));
        }
        print("hint", answer.getTrack() + "\n" + answer.getMessage() + "\n edits=" + answer.getEdits().size()
                + applied + session.proposal().map(p -> "\n" + p.resultCode()).orElse(""), result.tokenUsage());
        assertFalse(answer.getMessage().isBlank());
    }

    @Test
    void chatFixesSyntaxThroughToolThenSolutionThinksInSameMemory() throws Exception {
        ChatMemory memory = MessageWindowChatMemory.builder()
                .id("live")
                .maxMessages(100)
                .chatMemoryStore(new InMemoryChatMemoryStore())
                .alwaysKeepSystemMessageFirst(true)
                .build();
        HintState state = new HintState(HintTrackEnum.BUG, 0, HintModeEnum.GUIDED);

        CodeEditSession firstEdits = new CodeEditSession(MISSING_SEMICOLON, "java", state.editPermission());
        ChatResponse first = await(factory.chatAssistant(credential, memory, List.of(new CodeEditTools(firstEdits)))
                .chat(systemPrompt, prompts.chatMessage(state, snapshot(MISSING_SEMICOLON), "",
                        "编译报错了，帮我看看哪里写错了，直接帮我改一下")), null);
        print("chat#1", first.aiMessage().text() + firstEdits.proposal().map(p -> "\n[edits] " + p.edits()
                + "\n" + p.resultCode()).orElse("\n[edits] 无"), first.tokenUsage());

        CodeEditSession secondEdits = new CodeEditSession(OVERFLOW_CODE, "java", state.editPermission());
        ChatResponse second = await(factory.chatAssistant(credential, memory, List.of(new CodeEditTools(secondEdits)))
                .chat(systemPrompt, prompts.chatMessage(state, snapshot(OVERFLOW_CODE),
                        "【最近的提交与你的反应】\n- 第 2 次提交：答案错误，第 3 个测试点输入 2000000000 2000000000",
                        "编译过了，但是答案错误，为什么？")), null);
        print("chat#2", second.aiMessage().text() + secondEdits.proposal().map(p -> "\n[edits] " + p.edits())
                .orElse("\n[edits] 无"), second.tokenUsage());

        StringBuilder thinking = new StringBuilder();
        HintState direct = new HintState(HintTrackEnum.IDEA, HintLadder.SOLUTION_LEVEL, HintModeEnum.GUIDED);
        ChatResponse solution = await(factory.solutionAssistant(credential, memory)
                .solve(systemPrompt, prompts.solutionMessage(direct, snapshot(OVERFLOW_CODE))), thinking);
        print("solution", "[thinking " + thinking.length() + " 字]\n" + solution.aiMessage().text(),
                solution.tokenUsage());
        assertTrue(solution.aiMessage().text().contains("```"));

        Result<String> note = factory.compactionAssistant(credential).compact(systemPrompt,
                prompts.compactionMessage("", MemoryTranscript.render(memory.messages(), 6000)));
        print("compaction", note.content(), note.tokenUsage());
        assertFalse(note.content().isBlank());
    }

    private static ChatResponse await(TokenStream stream, StringBuilder thinking) throws Exception {
        CompletableFuture<ChatResponse> finished = new CompletableFuture<>();
        stream.onPartialResponse(ignored -> {
                })
                .onPartialThinking(partial -> {
                    if (thinking != null) {
                        thinking.append(partial.text());
                    }
                })
                .onIntermediateResponse(response -> System.out.println("[tool round] "
                        + response.aiMessage().toolExecutionRequests()))
                .onCompleteResponse(finished::complete)
                .onError(finished::completeExceptionally)
                .start();
        return finished.get(3, TimeUnit.MINUTES);
    }

    private static void print(String scene, String content, Object usage) {
        System.out.println("\n===== LIVE " + scene + " =====\n" + content + "\n----- usage: " + usage + "\n");
    }

    private static CodeSnapshot snapshot(String code) {
        CodeSnapshot snapshot = new CodeSnapshot();
        snapshot.setCode(code);
        snapshot.setLanguage("java");
        return snapshot;
    }

    private static Question question() {
        Question question = new Question();
        question.setTitle("A + B");
        question.setContent("输入两个整数 a 和 b（|a|, |b| ≤ 2×10^9），输出 a + b。");
        question.setAnswer("用 long 读入并求和：long a = in.nextLong(); long b = in.nextLong(); 输出 a + b。");
        question.setJudgeConfig("{\"timeLimit\":1000,\"memoryLimit\":65536}");
        return question;
    }

    private static AiProperties properties() {
        AiProperties properties = new AiProperties();
        properties.getDeepseek().setBaseUrl(BASE_URL);
        properties.getDeepseek().setTimeout(Duration.ofMinutes(3));
        properties.getCompanion().setPersonaName("栗子");
        properties.getCompanion().setMaxCodeChars(20_000);
        return properties;
    }
}
