package com.hjl.oj.companion.note;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteContentPolicyTest {

    private static final String SOLUTION = """
            思路：哈希表
            ```java
            Map<Integer, Integer> seen = new HashMap<>();
            for (int i = 0; i < nums.length; i++) {
                if (seen.containsKey(target - nums[i])) {
                    return new int[]{seen.get(target - nums[i]), i};
                }
                seen.put(nums[i], i);
            }
            ```""";

    @Test
    void solutionCodeIsRejectedBeforeTheAnswerLevel() {
        assertTrue(NoteContentPolicy.check(SOLUTION, 4).orElseThrow().contains("看答案"));
        assertTrue(NoteContentPolicy.check(SOLUTION, 5).isEmpty());
    }

    @Test
    void textShortSnippetsAndBracesAreAllowed() {
        assertTrue(NoteContentPolicy.check("先排序，再用两个指针从两头往中间走", 0).isEmpty());
        assertTrue(NoteContentPolicy.check("```\nlong sum = 0;\n}\n}\n}\n```", 0).isEmpty());
    }

    @Test
    void theUsersOwnCodeCanBeCopiedIn() {
        String userCode = SOLUTION.lines().filter(line -> !line.startsWith("```") && !line.startsWith("思路"))
                .reduce("", (a, b) -> a + b + "\n");
        assertTrue(NoteContentPolicy.check(SOLUTION, 0, userCode).isEmpty());
        // 笔记原来就有的代码也一样
        assertTrue(NoteContentPolicy.check(SOLUTION, 0, "", userCode).isEmpty());
    }

    @Test
    void anUnclosedFenceStillCountsAsCode() {
        assertTrue(NoteContentPolicy.check("```\na = 1\nb = 2\nc = 3\nd = 4", 2).isPresent());
    }
}
