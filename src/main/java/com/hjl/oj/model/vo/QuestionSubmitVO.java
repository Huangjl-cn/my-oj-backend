package com.hjl.oj.model.vo;

import cn.hutool.json.JSONUtil;
import com.hjl.oj.judge.codesandbox.model.JudgeInfo;
import com.hjl.oj.model.entity.QuestionSubmit;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 题目提交封装类
 */
@Data
public class QuestionSubmitVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    /**
     * id
     */
    private Long id;
    /**
     * 题目 id
     */
    private Long questionId;
    /**
     * 创建用户 id
     */
    private Long userId;
    /**
     * 编程语言
     */
    private String language;
    /**
     * 用户提交代码
     */
    private String code;
    /**
     * 判题信息（json 对象）
     */
    private JudgeInfo judgeInfo;
    /**
     * 判题状态（0-待判题，1-判题中，2-成功，3-失败）
     */
    private Integer status;
    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 更新时间
     */
    private Date updateTime;
    /**
     * 创建题目人的信息
     */
    private UserVO userVO;
    /**
     * 题目信息
     */
    private QuestionVO questionVO;
    /**
     * 题目标题（归档列表摘要）
     */
    @Schema(description = "题目标题")
    private String questionTitle;
    /**
     * 题目标签列表（归档列表摘要）
     */
    @Schema(description = "题目标签列表")
    private List<String> questionTags;
    /**
     * 提交用户昵称（归档列表摘要）
     */
    @Schema(description = "提交用户昵称")
    private String userName;
    /**
     * 提交用户头像（归档列表摘要）
     */
    @Schema(description = "提交用户头像")
    private String userAvatar;
    /**
     * 派生判题结果（accepted/failed/pending，与 status 和 judgeInfo.message 的对应关系一致）
     */
    @Schema(description = "派生判题结果，可选 accepted/failed/pending；status=2 仅代表判题完成，是否通过以本字段及 judgeInfo.message 为准",
            example = "accepted")
    private String judgeResult;

    /**
     * 包装类转对象
     */
    public static QuestionSubmit voToObj(QuestionSubmitVO questionSubmitVO) {
        if (questionSubmitVO == null) {
            return null;
        }
        QuestionSubmit questionSubmit = new QuestionSubmit();
        BeanUtils.copyProperties(questionSubmitVO, questionSubmit);
        JudgeInfo judgeInfoVO = questionSubmitVO.getJudgeInfo();
        if (judgeInfoVO != null) {
            questionSubmit.setJudgeInfo(JSONUtil.toJsonStr(judgeInfoVO));
        }
        return questionSubmit;
    }

    /**
     * 对象转包装类
     */
    public static QuestionSubmitVO objToVo(QuestionSubmit questionSubmit) {
        if (questionSubmit == null) {
            return null;
        }
        QuestionSubmitVO questionSubmitVO = new QuestionSubmitVO();
        BeanUtils.copyProperties(questionSubmit, questionSubmitVO);
        String judgeInfo = questionSubmit.getJudgeInfo();
        if (judgeInfo != null) {
            questionSubmitVO.setJudgeInfo(JSONUtil.toBean(judgeInfo, JudgeInfo.class));
        }
        return questionSubmitVO;
    }
}