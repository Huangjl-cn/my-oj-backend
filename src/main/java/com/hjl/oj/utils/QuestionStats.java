package com.hjl.oj.utils;

import cn.hutool.json.JSONException;
import cn.hutool.json.JSONUtil;
import com.hjl.oj.model.dto.questionsubmit.QuestionProgressRow;
import com.hjl.oj.model.entity.Question;
import com.hjl.oj.model.enums.QuestionDifficultyEnum;
import com.hjl.oj.model.vo.DifficultyCountVO;
import com.hjl.oj.model.vo.QuestionProgressVO;
import com.hjl.oj.model.vo.QuestionTagVO;
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
