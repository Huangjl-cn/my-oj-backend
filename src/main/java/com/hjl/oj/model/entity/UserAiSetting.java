package com.hjl.oj.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 用户 AI 设置
 */
@TableName(value = "user_ai_setting")
@Data
public class UserAiSetting implements Serializable {

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    /**
     * AI 厂商
     */
    private String provider;

    /**
     * 所选模型，为空时使用默认模型
     */
    private String model;

    /**
     * 加密后的 API Key，见 ApiKeyCipher
     */
    @ToString.Exclude
    private String encryptedKey;

    /**
     * API Key 后 4 位
     */
    private String keyHint;

    /**
     * 提示模式，见 HintModeEnum
     */
    private String hintMode;

    private Date createTime;

    private Date updateTime;
}
