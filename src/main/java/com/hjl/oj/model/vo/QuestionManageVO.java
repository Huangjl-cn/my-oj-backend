package com.hjl.oj.model.vo;

import com.hjl.oj.model.dto.question.JudgeCaseConfig;
import com.hjl.oj.model.dto.question.JudgeConfig;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 题目创建者或管理员编辑题目时使用的完整数据。
 */
@Data
public class QuestionManageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long userId;

    private String title;

    private String content;

    private List<String> tags;

    private String answer;

    private Integer submitNum;

    private Integer acceptedNum;

    private JudgeConfig judgeConfig;

    private JudgeCaseConfig judgeCase;

    private Date createTime;

    private Date updateTime;
}
