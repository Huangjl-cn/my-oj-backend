package com.hjl.oj.companion.stream;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 陪练流式回答的 SSE 通道。事件名：
 * <ul>
 *     <li>{@code thinking}：思考过程片段（只有看答案时有）</li>
 *     <li>{@code delta}：回答正文片段</li>
 *     <li>{@code edits}：代码修改建议（CodeEditProposal）</li>
 *     <li>{@code notes}：笔记的新建、修改提议（NoteProposal 列表）</li>
 *     <li>{@code done}：结束，带消息 id、上下文占用和提示状态</li>
 *     <li>{@code error}：出错，带错误码和说明</li>
 * </ul>
 * 客户端断开后不再发送；模型回调线程和请求线程可能并发发送，这里串行化。
 */
public class CompanionSse {

    public static final String EVENT_THINKING = "thinking";

    public static final String EVENT_DELTA = "delta";

    public static final String EVENT_EDITS = "edits";

    public static final String EVENT_NOTES = "notes";

    public static final String EVENT_DONE = "done";

    public static final String EVENT_ERROR = "error";

    private final SseEmitter emitter;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    public CompanionSse(long timeoutMillis) {
        this.emitter = new SseEmitter(timeoutMillis);
    }

    public SseEmitter emitter() {
        return emitter;
    }

    /**
     * 连接结束（正常完成、超时、客户端断开）时执行，用于释放调用许可等
     */
    public void onFinish(Runnable callback) {
        emitter.onCompletion(callback);
        emitter.onTimeout(() -> {
            closed.set(true);
            callback.run();
        });
        emitter.onError(error -> {
            closed.set(true);
            callback.run();
        });
    }

    public synchronized void send(String event, Object data) {
        if (closed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException e) {
            // 客户端已断开：停止推送，模型那边的回调照常走完
            closed.set(true);
        }
    }

    public synchronized void complete() {
        if (closed.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    public boolean isClosed() {
        return closed.get();
    }
}
