package com.hjl.oj.model.vo;

import cn.hutool.json.JSONUtil;
import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.model.entity.QuestionSubmit;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 题目提交列表摘要，不包含提交代码
 */
@Data
public class QuestionSubmitSummaryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long questionId;

    private String language;

    private JudgeInfo judgeInfo;

    private Integer status;

    private Date createTime;

    private Date updateTime;

    public static QuestionSubmitSummaryVO objToVo(QuestionSubmit questionSubmit) {
        if (questionSubmit == null) {
            return null;
        }
        QuestionSubmitSummaryVO summaryVO = new QuestionSubmitSummaryVO();
        BeanUtils.copyProperties(questionSubmit, summaryVO);
        if (questionSubmit.getJudgeInfo() != null) {
            summaryVO.setJudgeInfo(JSONUtil.toBean(questionSubmit.getJudgeInfo(), JudgeInfo.class));
        }
        return summaryVO;
    }
}
