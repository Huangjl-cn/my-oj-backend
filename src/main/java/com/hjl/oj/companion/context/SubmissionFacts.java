package com.hjl.oj.companion.context;

/**
 * 一次提交的事实，由代码计算好交给模型，模型只负责表达。
 *
 * @param attemptNumber       这是用户在本题的第几次提交
 * @param verdict             本次结果
 * @param previousVerdict     上一次的结果，首次提交为 null
 * @param trend               和上一次相比的变化
 * @param sameVerdictStreak   连续出现本次结果的次数（含本次）
 * @param minutesSincePrevious 距上一次提交的分钟数，首次提交为 null
 * @param acceptedBefore      在这次之前是否已经通过过本题
 * @param timeMs              耗时（毫秒）
 * @param memoryKb            内存（KB）
 * @param timeLimitMs         题目时间限制（毫秒）
 * @param memoryLimitKb       题目内存限制（KB）
 * @param diagnostic          诊断内容（已截断）
 */
public record SubmissionFacts(
        int attemptNumber,
        Verdict verdict,
        Verdict previousVerdict,
        Trend trend,
        int sameVerdictStreak,
        Long minutesSincePrevious,
        boolean acceptedBefore,
        Long timeMs,
        Long memoryKb,
        Long timeLimitMs,
        Long memoryLimitKb,
        String diagnostic) {

    public enum Trend {
        FIRST("第一次提交"),
        IMPROVED("比上次更接近通过"),
        SAME("和上次结果一样"),
        REGRESSED("比上次离通过更远"),
        NOT_COMPARABLE("和上次无法比较");

        private final String text;

        Trend(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }
    }

    /**
     * 写进提示词的事实描述
     */
    public String describe() {
        StringBuilder text = new StringBuilder();
        text.append("- 这是第 ").append(attemptNumber).append(" 次提交，结果：").append(verdict.getText()).append("\n");
        if (previousVerdict != null) {
            text.append("- 上一次结果：").append(previousVerdict.getText()).append("（").append(trend.text()).append("）\n");
        } else {
            text.append("- ").append(trend.text()).append("\n");
        }
        if (sameVerdictStreak > 1) {
            text.append("- 已经连续 ").append(sameVerdictStreak).append(" 次是「").append(verdict.getText()).append("」\n");
        }
        if (minutesSincePrevious != null) {
            text.append("- 距上一次提交约 ").append(minutesSincePrevious).append(" 分钟\n");
        }
        if (acceptedBefore) {
            text.append("- 用户之前已经通过过这道题（这次可能在尝试别的写法或优化）\n");
        }
        if (timeMs != null || memoryKb != null) {
            text.append("- 耗时 ").append(timeMs == null ? "未知" : timeMs + " ms")
                    .append("（限制 ").append(timeLimitMs == null ? "未知" : timeLimitMs + " ms").append("），内存 ")
                    .append(memoryKb == null ? "未知" : memoryKb + " KB")
                    .append("（限制 ").append(memoryLimitKb == null ? "未知" : memoryLimitKb + " KB").append("）\n");
        }
        if (diagnostic != null && !diagnostic.isBlank()) {
            text.append("- 诊断信息：\n").append(diagnostic).append("\n");
        }
        return text.toString();
    }
}
