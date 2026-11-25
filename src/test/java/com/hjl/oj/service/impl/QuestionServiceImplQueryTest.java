package com.hjl.oj.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hjl.oj.model.dto.question.QuestionQueryRequest;
import com.hjl.oj.model.entity.Question;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionServiceImplQueryTest {

    private final QuestionServiceImpl service = new QuestionServiceImpl();

    @Test
    void difficultyMatchesBothWaysOfWritingTheTag() {
        QuestionQueryRequest request = new QuestionQueryRequest();
        request.setDifficulty("简单");

        QueryWrapper<Question> wrapper = service.getQueryWrapper(request);
        String sql = wrapper.getSqlSegment();

        assertTrue(sql.contains(" OR "), sql);
        assertTrue(wrapper.getParamNameValuePairs().containsValue("%\"easy\"%"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue("%\"简单\"%"));
    }

    @Test
    void unknownDifficultyIsIgnored() {
        QuestionQueryRequest request = new QuestionQueryRequest();
        request.setDifficulty("超难");

        QueryWrapper<Question> wrapper = service.getQueryWrapper(request);

        assertFalse(wrapper.getParamNameValuePairs().containsValue("%\"超难\"%"));
        assertFalse(wrapper.getSqlSegment().contains(" OR "));
    }
}
