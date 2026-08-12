USE oj_db;

CREATE TABLE IF NOT EXISTS user
(
    id           BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    userAccount  VARCHAR(256)                           NOT NULL COMMENT '账号',
    userPassword VARCHAR(512)                           NOT NULL COMMENT '密码',
    unionId      VARCHAR(256)                           NULL COMMENT '微信开放平台id',
    mpOpenId     VARCHAR(256)                           NULL COMMENT '公众号openId',
    userName     VARCHAR(256)                           NULL COMMENT '用户昵称',
    userAvatar   VARCHAR(1024)                          NULL COMMENT '用户头像',
    userProfile  VARCHAR(512)                           NULL COMMENT '用户简介',
    userRole     VARCHAR(256) DEFAULT 'user'            NOT NULL COMMENT '用户角色：user/admin/ban',
    createTime   DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime   DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete     TINYINT      DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_unionId (unionId)
) COMMENT '用户' COLLATE = utf8mb4_unicode_ci;
-- 添加默认admin用户，密码为12345678
insert into user (userAccount, userPassword, userName, userRole)
values ('admin', '8c03c9839cb10c023d7ea362e4b42f69', 'admin', 'admin')
on duplicate key update userPassword = '8c03c9839cb10c023d7ea362e4b42f69',
                        userRole     = 'admin';

CREATE TABLE IF NOT EXISTS question
(
    id          BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    userId      BIGINT                             NOT NULL COMMENT '创建用户 id',
    title       VARCHAR(512)                       NULL COMMENT '标题',
    content     TEXT                               NULL COMMENT '内容',
    tags        VARCHAR(1024)                      NULL COMMENT '标签列表（json 数组）',
    answer      TEXT                               NULL COMMENT '题目答案',
    submitNum   INT      DEFAULT 0                 NOT NULL COMMENT '题目提交数',
    acceptedNum INT      DEFAULT 0                 NOT NULL COMMENT '题目通过数',
    judgeConfig TEXT                               NULL COMMENT '判题配置（json 数组）',
    judgeCase   TEXT                               NULL COMMENT '判题用例（json 对象）',
    createTime  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete    TINYINT  DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_userId (userId)
) COMMENT '题目' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS question_starter_code
(
    id          BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    questionId  BIGINT                             NOT NULL COMMENT '题目 id',
    language    VARCHAR(32)                        NOT NULL COMMENT '编程语言',
    starterCode TEXT                               NOT NULL COMMENT '编辑器初始代码模板',
    createTime  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime  DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_question_language (questionId, language)
) COMMENT '题目多语言初始代码模板' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS question_submit
(
    id         BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    questionId BIGINT                             NOT NULL COMMENT '题目 id',
    userId     BIGINT                             NOT NULL COMMENT '创建用户 id',
    language   VARCHAR(128)                       NOT NULL COMMENT '编程语言',
    code       TEXT                               NULL COMMENT '用户提交代码',
    judgeInfo  TEXT                               NULL COMMENT '判题信息（json 对象）',
    status     INT                                NOT NULL DEFAULT 0 COMMENT '判题状态（0-待判题，1-判题中，2-成功，3-失败）',
    createTime DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete   TINYINT  DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_postId (questionId),
    INDEX idx_userId (userId),
    INDEX idx_user_question_time (userId, questionId, createTime)
) COMMENT '题目提交';
