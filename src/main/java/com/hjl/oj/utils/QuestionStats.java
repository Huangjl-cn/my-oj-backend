package com.hjl.oj.utils;

import cn.hutool.json.JSONException;
import cn.hutool.json.JSONUtil;
import com.hjl.oj.model.dto.questionsubmit.QuestionProgressRow;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.enums.QuestionDifficultyEnum;
import com.hjl.oj.model.vo.DifficultyCountVO;
import com.hjl.oj.model.vo.QuestionProgressVO;
import com.hjl.oj.model.vo.QuestionTagVO;
import com.hjl.oj.model.vo.TagAttemptsVO;
import com.hjl.oj.model.vo.TagProgressVO;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 题库统计：按难度计数、做题进度、标签使用次数。纯计算，不访问数据库。
 */
public final class QuestionStats {

    public static final String STATUS_PASSED = "passed";

    public static final String STATUS_FAILED = "failed";

    public static final String STATUS_JUDGING = "judging";

    private QuestionStats() {
    }

    /**
     * 题目的 tags 列（JSON 数组字符串）解析成列表，格式不对时当作没有标签
     */
    public static List<String> parseTags(String tagsJson) {
        if (StringUtils.isBlank(tagsJson)) {
            return List.of();
        }
        try {
            return JSONUtil.toList(tagsJson, String.class).stream().filter(StringUtils::isNotBlank).toList();
        } catch (JSONException | ClassCastException e) {
            return List.of();
        }
    }

    /**
     * @param questions 题库里的题（只需要 id 和 tags）
     * @param rows      用户在各题上的提交汇总
     */
    public static QuestionProgressVO buildProgress(List<Question> questions, List<QuestionProgressRow> rows) {
        Map<Long, QuestionProgressRow> rowByQuestion = new HashMap<>();
        for (QuestionProgressRow row : rows) {
            rowByQuestion.put(row.getQuestionId(), row);
        }
        DifficultyCountVO total = new DifficultyCountVO();
        DifficultyCountVO solved = new DifficultyCountVO();
        Map<String, String> statuses = new LinkedHashMap<>();
        for (Question question : questions) {
            Optional<QuestionDifficultyEnum> difficulty = QuestionDifficultyEnum.fromTags(parseTags(question.getTags()));
            count(total, difficulty);
            QuestionProgressRow row = rowByQuestion.get(question.getId());
            if (row == null) {
                continue;
            }
            String status = statusOf(row);
            statuses.put(String.valueOf(question.getId()), status);
            if (STATUS_PASSED.equals(status)) {
                count(solved, difficulty);
            }
        }
        QuestionProgressVO vo = new QuestionProgressVO();
        vo.setTotal(total);
        vo.setSolved(solved);
        vo.setStatuses(statuses);
        return vo;
    }

    /**
     * 通过过就算已通过；否则有提交还在判题算判题中；其余算没通过
     */
    static String statusOf(QuestionProgressRow row) {
        if (isTrue(row.getAccepted())) {
            return STATUS_PASSED;
        }
        return isTrue(row.getPending()) ? STATUS_JUDGING : STATUS_FAILED;
    }

    /**
     * 各标签被多少道题使用，难度标签不算；按使用次数从多到少，次数相同按首次出现的顺序
     */
    public static List<QuestionTagVO> countTags(List<Question> questions) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Question question : questions) {
            parseTags(question.getTags()).stream()
                    .map(String::strip)
                    .distinct()
                    .filter(tag -> QuestionDifficultyEnum.fromTag(tag).isEmpty())
                    .forEach(tag -> counts.merge(tag, 1, Integer::sum));
        }
        List<QuestionTagVO> tags = new ArrayList<>();
        counts.forEach((tag, count) -> tags.add(new QuestionTagVO(tag, count)));
        tags.sort(Comparator.comparing(QuestionTagVO::getCount).reversed());
        return tags;
    }

    /**
     * 各标签下的做题情况（难度标签不算）：题目数、已通过数、做过但没通过数；
     * 按题目数从多到少，题目数相同按首次出现的顺序
     *
     * @param questions 题库里的题（只需要 id 和 tags）
     * @param rows      用户在各题上的提交汇总
     */
    public static List<TagProgressVO> buildTagProgress(List<Question> questions, List<QuestionProgressRow> rows) {
        Map<Long, String> statusByQuestion = new HashMap<>();
        for (QuestionProgressRow row : rows) {
            statusByQuestion.put(row.getQuestionId(), statusOf(row));
        }
        // 每个标签：[题目数, 已通过, 做过没通过]
        Map<String, int[]> counts = new LinkedHashMap<>();
        for (Question question : questions) {
            String status = statusByQuestion.get(question.getId());
            parseTags(question.getTags()).stream()
                    .map(String::strip)
                    .distinct()
                    .filter(tag -> QuestionDifficultyEnum.fromTag(tag).isEmpty())
                    .forEach(tag -> {
                        int[] count = counts.computeIfAbsent(tag, key -> new int[3]);
                        count[0]++;
                        if (STATUS_PASSED.equals(status)) {
                            count[1]++;
                        } else if (status != null) {
                            count[2]++;
                        }
                    });
        }
        List<TagProgressVO> tags = new ArrayList<>();
        counts.forEach((tag, count) -> tags.add(new TagProgressVO(tag, count[0], count[1], count[2])));
        tags.sort(Comparator.comparing(TagProgressVO::getTotal).reversed());
        return tags;
    }

    /**
     * 各标签下已通过的题平均第几次提交才通过，难度标签不算；平均次数从多到少，取前 limit 个。
     * 通过的题够 minSolved 道的标签才算，免得一两道题就排到最前；都不够时放宽到 1 道
     *
     * @param attemptsByQuestion 已通过的题 → 第几次提交才通过
     */
    public static List<TagAttemptsVO> buildTagAttempts(List<Question> questions, Map<Long, Integer> attemptsByQuestion,
                                                       int minSolved, int limit) {
        // 每个标签：[通过的题目数, 次数之和]
        Map<String, int[]> sums = new LinkedHashMap<>();
        for (Question question : questions) {
            Integer attempts = attemptsByQuestion.get(question.getId());
            if (attempts == null || attempts < 1) {
                continue;
            }
            parseTags(question.getTags()).stream()
                    .map(String::strip)
                    .distinct()
                    .filter(tag -> QuestionDifficultyEnum.fromTag(tag).isEmpty())
                    .forEach(tag -> {
                        int[] sum = sums.computeIfAbsent(tag, key -> new int[2]);
                        sum[0]++;
                        sum[1] += attempts;
                    });
        }
        int threshold = sums.values().stream().anyMatch(sum -> sum[0] >= minSolved) ? minSolved : 1;
        List<TagAttemptsVO> result = new ArrayList<>();
        sums.forEach((tag, sum) -> {
            if (sum[0] >= threshold) {
                result.add(new TagAttemptsVO(tag, sum[0], Math.round(sum[1] * 10.0 / sum[0]) / 10.0));
            }
        });
        result.sort(Comparator.comparing(TagAttemptsVO::getAverageAttempts).reversed()
                .thenComparing(Comparator.comparing(TagAttemptsVO::getSolved).reversed()));
        return result.size() > limit ? List.copyOf(result.subList(0, limit)) : result;
    }

    private static void count(DifficultyCountVO counter, Optional<QuestionDifficultyEnum> difficulty) {
        counter.setAll(counter.getAll() + 1);
        difficulty.ifPresent(value -> {
            switch (value) {
                case EASY -> counter.setEasy(counter.getEasy() + 1);
                case MEDIUM -> counter.setMedium(counter.getMedium() + 1);
                case HARD -> counter.setHard(counter.getHard() + 1);
            }
        });
    }

    private static boolean isTrue(Integer flag) {
        return flag != null && flag > 0;
    }
}
