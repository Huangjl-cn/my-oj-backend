package com.hjl.oj.ai.call;

import com.hjl.oj.common.ErrorCode;
import com.hjl.oj.exception.BusinessException;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 每个用户同一时间只允许一个进行中的 AI 调用，保护服务器线程与连接，也避免同一会话的记忆被并发改写。
 *
 * <p>只在单实例内生效；多实例部署时同一用户的请求可能落到不同实例，届时需要换成分布式锁。
 */
public class AiCallGuard {

    private final Set<Long> busyUsers = ConcurrentHashMap.newKeySet();

    /**
     * @throws BusinessException 该用户已有进行中的调用
     */
    public Permit acquire(long userId) {
        if (!busyUsers.add(userId)) {
            throw new BusinessException(ErrorCode.AI_BUSY);
        }
        return new Permit(userId);
    }

    /**
     * 调用许可，close 可重复调用；流式调用要在完成、出错、超时任一回调中释放。
     */
    public final class Permit implements AutoCloseable {

        private final long userId;

        private final AtomicBoolean released = new AtomicBoolean(false);

        private Permit(long userId) {
            this.userId = userId;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                busyUsers.remove(userId);
            }
        }
    }
}
