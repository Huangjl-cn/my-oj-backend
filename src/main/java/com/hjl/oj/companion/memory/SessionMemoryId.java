package com.hjl.oj.companion.memory;

/**
 * 会话记忆的 ID：一个用户在一道题下一段记忆。
 */
public record SessionMemoryId(long userId, long questionId) {
}
