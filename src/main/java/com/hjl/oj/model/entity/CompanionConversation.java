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
 * AI 陪练对话：同一道题下可以有多段，各有各的模型记忆和上下文占用
 */
@TableName(value = "companion_conversation")
@Data
public class CompanionConversation implements Serializable {

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long questionId;

    /**
     * 第一次提问的开头，还没提问时为空
     */
    private String title;

    /**
     * 模型记忆（LangChain4j 消息序列化后的 JSON），读写只经过 MysqlChatMemoryStore
     */
    @ToString.Exclude
    private String memory;

    /**
     * 最近一次调用的上下文 token 数
     */
    private Integer contextTokens;

    private Date memoryUpdateTime;

    private Date createTime;

    private Date updateTime;
}
